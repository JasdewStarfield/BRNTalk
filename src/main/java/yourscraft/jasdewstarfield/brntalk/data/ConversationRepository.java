package yourscraft.jasdewstarfield.brntalk.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brntalk.Brntalk;

import java.util.*;

/** 拥有剧本候选校验、活动集合及最近失败报告，失败不会覆盖活动版本。 */
public final class ConversationRepository {
    private static final Gson GSON = new GsonBuilder().create();
    private record Snapshot(Map<String, TalkConversation> scripts, ConversationLoadReport report,
                            long version, boolean applied) {}
    private volatile Snapshot snapshot = new Snapshot(Map.of(), ConversationLoadReport.empty(), 0, true);

    public TalkConversation get(String id) { return snapshot.scripts().get(id); }
    public ConversationLoadReport lastReport() { return snapshot.report(); }
    public boolean wasLastReloadApplied() { return snapshot.applied(); }
    public long activeVersion() { return snapshot.version(); }

    /** 所有候选都校验通过才发布；空候选保留合法删除语义。 */
    private synchronized boolean publish(Map<String, TalkConversation> candidate, ConversationLoadReport report) {
        Snapshot previous = snapshot;
        boolean applied = !report.hasProblems();
        snapshot = new Snapshot(applied ? Map.copyOf(candidate) : previous.scripts(), report,
                previous.version() + (applied ? 1 : 0), applied);
        return applied;
    }

    public void preparationFailed(ResourceLocation source, Exception error) {
        var report = new ConversationLoadReport.Builder();
        report.addFileLoadFailure(source, error);
        publish(Map.of(), report.build());
    }

    /** 兼容程序注册入口：校验整个候选，失败保留原集合并明确报告给调用方。 */
    public void register(TalkConversation conversation) {
        Map<String, TalkConversation> candidate = new LinkedHashMap<>(snapshot.scripts());
        candidate.put(conversation.getId(), conversation);
        replace(candidate);
    }

    public void replace(Map<String, TalkConversation> candidate) {
        var report = new ConversationLoadReport.Builder();
        for (var entry : candidate.entrySet()) {
            TalkConversation script = entry.getValue();
            if (!entry.getKey().equals(script.getId())) throw new IllegalArgumentException("Conversation key differs from ID");
            Set<String> seen = new HashSet<>();
            Set<String> duplicates = new LinkedHashSet<>();
            for (var message : script.getMessages()) if (!seen.add(message.getId())) duplicates.add(message.getId());
            var validation = ConversationValidator.validate("programmatic:" + script.getId(), script.getId(), script.getMessages(), duplicates);
            if (validation.hasErrors()) report.addInvalidConversation(validation);
            else report.incrementLoaded();
        }
        if (!publish(candidate, report.build())) throw new IllegalArgumentException("Invalid conversation replacement; active scripts retained");
    }

    public void clear() { publish(Map.of(), ConversationLoadReport.empty()); }

    public void reload(Map<ResourceLocation, JsonElement> jsons) {
        Map<String, TalkConversation> candidate = new LinkedHashMap<>();
        ConversationLoadReport.Builder reportBuilder = new ConversationLoadReport.Builder();

        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            ResourceLocation rl = entry.getKey();
            JsonElement element = entry.getValue();

            try {
                // 如果 JSON 根是对象
                if (element.isJsonObject()) {
                    JsonObject root = element.getAsJsonObject();

                    // 情况 A: 包含 "conversations" 数组
                    if (root.has("conversations")) {
                        RawConversation[] convArray = GSON.fromJson(root.get("conversations"), RawConversation[].class);
                        if (convArray != null) {
                            for (RawConversation rawConv : convArray) {
                                LoadSingleResult result = loadSingleConversation(candidate, rawConv, rl);
                                if (result.isLoaded()) {
                                    reportBuilder.incrementLoaded();
                                } else {
                                    reportBuilder.addInvalidConversation(result.report());
                                }
                            }
                        } else throw new IllegalArgumentException("conversations must be an array");
                    }
                    // 情况 B: 根本身就是单个 Conversation (兼容旧格式)
                    else {
                        RawConversation rawConv = GSON.fromJson(root, RawConversation.class);
                        LoadSingleResult result = loadSingleConversation(candidate, rawConv, rl);
                        if (result.isLoaded()) {
                            reportBuilder.incrementLoaded();
                        } else {
                            reportBuilder.addInvalidConversation(result.report());
                        }
                    }
                } else throw new IllegalArgumentException("Conversation file must be a JSON object");
            } catch (Exception e) {
                Brntalk.LOGGER.error("[BRNTalk] Failed to load conversation file: {}", rl, e);
                reportBuilder.addFileLoadFailure(rl, e);
            }
        }

        ConversationLoadReport report = reportBuilder.build();
        boolean applied = publish(candidate, report);
        if (!applied) {
            Brntalk.LOGGER.error("[BRNTalk] Rejected conversation reload; retaining all active scripts and threads.");
        }
        Brntalk.LOGGER.info("[BRNTalk] Candidate validation: {} valid conversations, {} invalid conversations, {} file load failures.",
                report.loadedConversations(),
                report.skippedConversations(),
                report.failedFileCount());
    }

    private static class RawMessage {
        String id;
        String type = "text";           // 默认值
        String speakerType = "npc";     // 默认值
        String speaker = "&c**EMPTY SPEAKER**"; // 默认值
        String text = "&c**EMPTY TEXT**";
        String action;
        String nextId;

        @SerializedName("continue")     // 处理 Java 关键字冲突
        boolean autoContinue = false;   // 默认值

        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        List<RawChoice> choices = new ArrayList<>();        // 允许为空
    }

    private static class RawChoice {
        String id;
        String text = "&c**EMPTY CHOICE TEXT**";
        String nextId;
    }

    private static class RawConversation {
        String id;

        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        List<RawMessage> messages = new ArrayList<>();
    }

    private static LoadSingleResult loadSingleConversation(Map<String, TalkConversation> candidate,
                                                           RawConversation rawConv,
                                                           ResourceLocation fileId) {
        // 1. 处理 ID
        String convId = normalizedOrDefault(rawConv.id, fileId.getPath());
        TalkConversation conv = new TalkConversation(convId);

        if (rawConv.messages == null || rawConv.messages.isEmpty()) {
            ConversationValidator.ValidationReport report = new ConversationValidator.ValidationReport(fileId.toString(), convId);
            report.error("Script contains no messages.");
            report.logProblems();
            Brntalk.LOGGER.error("[BRNTalk] Skipping script '{}' due to {} validation error(s).", convId, report.errorCount());
            return LoadSingleResult.invalid(report);
        }

        List<TalkMessage> parsedMessages = new ArrayList<>();
        Set<String> duplicateMessageIds = new LinkedHashSet<>();

        // Build the message id list up front so validation and auto-continue
        // inference both see the same effective IDs.
        List<String> idList = new ArrayList<>();
        Set<String> seenMessageIds = new HashSet<>();
        for (int i = 0; i < rawConv.messages.size(); i++) {
            RawMessage rawMsg = rawConv.messages.get(i);
            // 如果 rawMsg.id 为空，生成默认 id
            String id = normalizedOrDefault(rawMsg.id, "msg_" + i);
            idList.add(id);
            if (!seenMessageIds.add(id)) {
                duplicateMessageIds.add(id);
            }
        }

        // Convert raw JSON data into runtime messages only after we know the
        // final effective IDs for this script.
        for (int i = 0; i < rawConv.messages.size(); i++) {
            RawMessage rawMsg = rawConv.messages.get(i);
            String msgId = idList.get(i);

            // 类型转换
            TalkMessage.Type type = TalkMessage.Type.fromString(rawMsg.type);
            TalkMessage.SpeakerType speakerType = TalkMessage.SpeakerType.fromString(rawMsg.speakerType);

            // 自动推断nextId
            String nextId = normalizedOptional(rawMsg.nextId);
            if (rawMsg.autoContinue && nextId == null) {
                if (i + 1 < idList.size()) {
                    nextId = idList.get(i + 1);
                }
            }

            TalkMessage msg = new TalkMessage(
                    msgId,
                    type,
                    speakerType,
                    rawMsg.speaker,
                    rawMsg.text,
                    normalizedOptional(rawMsg.action),
                    System.currentTimeMillis(),
                    nextId
            );

            // 如果是 choice 类型，就读取 choices 数组
            if (type == TalkMessage.Type.CHOICE && rawMsg.choices != null) {
                int choiceIdx = 0;
                for (RawChoice rawChoice : rawMsg.choices) {
                    String cId = normalizedOrDefault(rawChoice.id, msgId + "_choice_" + choiceIdx);
                    String cText = (rawChoice.text != null) ? rawChoice.text : "&c**EMPTY CHOICE TEXT**";
                    String cNext = normalizedOptional(rawChoice.nextId);
                    if (cNext == null) {
                        cNext = "";
                    }

                    TalkMessage.Choice choice = new TalkMessage.Choice(cId, cText, cNext);
                    msg.addChoice(choice);
                    choiceIdx++;
                }
            }

            conv.addMessage(msg);
            parsedMessages.add(msg);
        }

        ConversationValidator.ValidationReport report = ConversationValidator.validate(fileId.toString(), convId, parsedMessages, duplicateMessageIds);
        report.logProblems();
        if (report.hasErrors()) {
            Brntalk.LOGGER.error("[BRNTalk] Skipping script '{}' due to {} validation error(s).", convId, report.errorCount());
            return LoadSingleResult.invalid(report);
        }

        if (candidate.putIfAbsent(convId, conv) != null) {
            throw new IllegalArgumentException("Duplicate conversation ID: " + convId);
        }
        return LoadSingleResult.loaded();
    }

    private static String normalizedOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizedOrDefault(String value, String fallback) {
        String normalized = normalizedOptional(value);
        return normalized != null ? normalized : fallback;
    }

    private static final class LoadSingleResult {
        private final boolean loaded;
        private final ConversationValidator.ValidationReport report;

        private LoadSingleResult(boolean loaded, ConversationValidator.ValidationReport report) {
            this.loaded = loaded;
            this.report = report;
        }

        static LoadSingleResult loaded() {
            return new LoadSingleResult(true, null);
        }

        static LoadSingleResult invalid(ConversationValidator.ValidationReport report) {
            return new LoadSingleResult(false, report);
        }

        boolean isLoaded() {
            return loaded;
        }

        // Present only when the conversation was rejected by validation.
        ConversationValidator.ValidationReport report() {
            return report;
        }
    }
}

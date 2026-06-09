package yourscraft.jasdewstarfield.brntalk.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.*;

public class ConversationLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    // The latest reload report is shared with the datapack sync listener so
    // admins can be notified after /reload finishes.
    private static volatile ConversationLoadReport lastLoadReport = ConversationLoadReport.empty();

    public ConversationLoader() {
        super(GSON, "brntalk/dialogues");
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

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons,
                         @NotNull ResourceManager resourceManager,
                         @NotNull ProfilerFiller profiler) {

        TalkManager manager = TalkManager.getInstance();
        manager.clear();
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
                                LoadSingleResult result = loadSingleConversation(manager, rawConv, rl);
                                if (result.isLoaded()) {
                                    reportBuilder.incrementLoaded();
                                } else {
                                    reportBuilder.addInvalidConversation(result.report());
                                }
                            }
                        }
                    }
                    // 情况 B: 根本身就是单个 Conversation (兼容旧格式)
                    else {
                        RawConversation rawConv = GSON.fromJson(root, RawConversation.class);
                        LoadSingleResult result = loadSingleConversation(manager, rawConv, rl);
                        if (result.isLoaded()) {
                            reportBuilder.incrementLoaded();
                        } else {
                            reportBuilder.addInvalidConversation(result.report());
                        }
                    }
                }
            } catch (Exception e) {
                Brntalk.LOGGER.error("[BRNTalk] Failed to load conversation file: {}", rl, e);
                reportBuilder.addFileLoadFailure(rl, e);
            }
        }

        lastLoadReport = reportBuilder.build();
        Brntalk.LOGGER.info("[BRNTalk] Loaded {} conversations, skipped {} invalid conversations, {} file load failures.",
                lastLoadReport.loadedConversations(),
                lastLoadReport.skippedConversations(),
                lastLoadReport.failedFileCount());
    }

    public static ConversationLoadReport getLastLoadReport() {
        return lastLoadReport;
    }

    private static LoadSingleResult loadSingleConversation(TalkManager manager,
                                                           RawConversation rawConv,
                                                           ResourceLocation fileId) {
        // 1. 处理 ID
        String convId = normalizedOrDefault(rawConv.id, fileId.getPath());
        TalkConversation conv = new TalkConversation(convId);

        if (rawConv.messages == null || rawConv.messages.isEmpty()) {
            ConversationValidator.ValidationReport report = new ConversationValidator.ValidationReport(convId);
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

        ConversationValidator.ValidationReport report = ConversationValidator.validate(convId, parsedMessages, duplicateMessageIds);
        report.logProblems();
        if (report.hasErrors()) {
            Brntalk.LOGGER.error("[BRNTalk] Skipping script '{}' due to {} validation error(s).", convId, report.errorCount());
            return LoadSingleResult.invalid(report);
        }

        manager.registerConversation(conv);
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

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

        int loadedCount = 0;

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
                                loadSingleConversation(manager, rawConv, rl);
                                loadedCount++;
                            }
                        }
                    }
                    // 情况 B: 根本身就是单个 Conversation (兼容旧格式)
                    else {
                        RawConversation rawConv = GSON.fromJson(root, RawConversation.class);
                        loadSingleConversation(manager, rawConv, rl);
                        loadedCount++;
                    }
                }
            } catch (Exception e) {
                Brntalk.LOGGER.error("[BRNTalk] Failed to load conversation file: {}", rl, e);
            }
        }

        Brntalk.LOGGER.info("[BRNTalk] Loaded {} conversations.", loadedCount);
    }

    private static void loadSingleConversation(TalkManager manager,
                                               RawConversation rawConv,
                                               ResourceLocation fileId) {
        // 1. 处理 ID
        String convId = (rawConv.id != null && !rawConv.id.isEmpty()) ? rawConv.id : fileId.getPath();
        TalkConversation conv = new TalkConversation(convId);

        if (rawConv.messages == null || rawConv.messages.isEmpty()) {
            return;
        }

        List<TalkMessage> parsedMessages = new ArrayList<>();
        Map<String, TalkMessage> msgMap = new HashMap<>();

        // 预处理 ID 列表，用于自动生成 id 和 nextId 推断
        List<String> idList = new ArrayList<>();
        for (int i = 0; i < rawConv.messages.size(); i++) {
            RawMessage rawMsg = rawConv.messages.get(i);
            // 如果 rawMsg.id 为空，生成默认 id
            String id = (rawMsg.id != null) ? rawMsg.id : "msg_" + i;
            idList.add(id);
        }

        // 2. 遍历 POJO 列表，转换为运行时 TalkMessage 对象
        for (int i = 0; i < rawConv.messages.size(); i++) {
            RawMessage rawMsg = rawConv.messages.get(i);
            String msgId = idList.get(i);

            // 类型转换
            TalkMessage.Type type = TalkMessage.Type.fromString(rawMsg.type);
            TalkMessage.SpeakerType speakerType = TalkMessage.SpeakerType.fromString(rawMsg.speakerType);

            // 自动推断nextId
            String nextId = rawMsg.nextId;
            if (rawMsg.autoContinue && (nextId == null || nextId.isEmpty())) {
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
                    rawMsg.action,
                    System.currentTimeMillis(),
                    nextId
            );

            // 如果是 choice 类型，就读取 choices 数组
            if (type == TalkMessage.Type.CHOICE && rawMsg.choices != null) {
                int choiceIdx = 0;
                for (RawChoice rawChoice : rawMsg.choices) {
                    String cId = (rawChoice.id != null) ? rawChoice.id : (msgId + "_choice_" + choiceIdx);
                    String cText = (rawChoice.text != null) ? rawChoice.text : "&c**EMPTY CHOICE TEXT**";
                    String cNext = (rawChoice.nextId != null) ? rawChoice.nextId : "";

                    TalkMessage.Choice choice = new TalkMessage.Choice(cId, cText, cNext);
                    msg.addChoice(choice);
                    choiceIdx++;
                }
            }

            conv.addMessage(msg);
            parsedMessages.add(msg);
            msgMap.put(msgId, msg);
        }

        validateConversation(convId, parsedMessages, msgMap.keySet());

        validateTextLoops(convId, parsedMessages, msgMap);

        manager.registerConversation(conv);
    }

    private static void validateConversation(String convId, List<TalkMessage> messages, Set<String> validIds) {
        for (TalkMessage msg : messages) {
            // A. 检查消息本身的 nextId 跳转
            String next = msg.getNextId();
            if (next != null && !next.isEmpty() && !validIds.contains(next)) {
                Brntalk.LOGGER.warn("[BRNTalk] Validation: Broken Link in Script '{}': Message '{}' points to missing nextId '{}'",
                        convId, msg.getId(), next);
            }

            // B. 检查选项 (Choice) 的跳转
            if (msg.getType() == TalkMessage.Type.CHOICE) {
                for (TalkMessage.Choice c : msg.getChoices()) {
                    String cNext = c.getNextId();
                    if (cNext != null && !cNext.isEmpty() && !validIds.contains(cNext)) {
                        Brntalk.LOGGER.warn("[BRNTalk] Validation: Broken Link in Script '{}': Choice '{}' (in msg '{}') points to missing nextId '{}'",
                                convId, c.getId(), msg.getId(), cNext);
                    }
                }
            }
        }
    }

    private static void validateTextLoops(String convId, List<TalkMessage> messages, Map<String, TalkMessage> msgMap) {
        Map<String, Integer> visitState = new HashMap<>();
        // 初始化状态 0
        for (TalkMessage msg : messages) {
            visitState.put(msg.getId(), 0);
        }

        for (TalkMessage msg : messages) {
            // 只从 TEXT 节点开始检查，且只检查未访问过的
            if (msg.getType() == TalkMessage.Type.TEXT && visitState.get(msg.getId()) == 0) {
                detectLoopDFS(convId, msg.getId(), msgMap, visitState);
            }
        }
    }

    private static boolean detectLoopDFS(String convId, String currId, Map<String, TalkMessage> msgMap, Map<String, Integer> visitState) {
        visitState.put(currId, 1); // 标记为灰色

        TalkMessage currMsg = msgMap.get(currId);
        if (currMsg == null) {
            visitState.put(currId, 2);
            return false; // 节点不存在，这里算无环
        }

        // 如果当前节点不是 TEXT，说明它会阻断自动推进，因此对于"自动推进死循环"来说，它是安全的终点。
        if (currMsg.getType() != TalkMessage.Type.TEXT) {
            visitState.put(currId, 2); // 标记为黑色
            return false;
        }

        String nextId = currMsg.getNextId();

        // 如果没有后续，或者为空，那就是终点，安全
        if (nextId != null && !nextId.isEmpty()) {
            Integer nextState = visitState.getOrDefault(nextId, 0);

            if (nextState == 1) {
                // 撞到了正在访问的节点 -> 发现环！
                Brntalk.LOGGER.error("[BRNTalk] Validation: **INFINITE LOOP** Detected in Script '{}'! The loop closes at message '{}'.",
                        convId, currId);
                return true;
            }

            if (nextState == 0) {
                // 继续递归
                if (detectLoopDFS(convId, nextId, msgMap, visitState)) {
                    return true;
                }
            }
            // 如果是 2，说明已经检查过且安全，跳过
        }

        visitState.put(currId, 2); // 标记为黑色
        return false;
    }
}

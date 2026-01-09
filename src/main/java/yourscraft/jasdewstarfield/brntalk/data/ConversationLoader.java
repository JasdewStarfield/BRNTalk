package yourscraft.jasdewstarfield.brntalk.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.GsonHelper;

import java.util.*;

public class ConversationLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public ConversationLoader() {
        super(GSON, "brntalk/dialogues");
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
                if (!element.isJsonObject()) continue;
                JsonObject root = element.getAsJsonObject();

                if (root.has("conversations")) {
                    JsonArray convArray = GsonHelper.getAsJsonArray(root, "conversations");
                    for (JsonElement convEl : convArray) {
                        if (!convEl.isJsonObject()) continue;
                        loadSingleConversation(manager, convEl.getAsJsonObject(), rl);
                        loadedCount++;
                    }
                } else {
                    loadSingleConversation(manager, root, rl);
                    loadedCount++;
                }
            } catch (Exception e) {
                Brntalk.LOGGER.error("[BRNTalk] Failed to load conversation file: {}", rl, e);
            }
        }

        Brntalk.LOGGER.info("[BRNTalk] Loaded {} conversations.", loadedCount);
    }

    private static void loadSingleConversation(TalkManager manager,
                                               JsonObject convObj,
                                               ResourceLocation fileId) {

        String convId = GsonHelper.getAsString(convObj, "id", fileId.getPath());
        TalkConversation conv = new TalkConversation(convId);

        JsonArray messages = GsonHelper.getAsJsonArray(convObj, "messages");

        List<TalkMessage> parsedMessages = new ArrayList<>();
        Map<String, TalkMessage> msgMap = new HashMap<>();

        List<String> idList = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).isJsonObject()) {
                JsonObject msgObj = messages.get(i).getAsJsonObject();
                String id = GsonHelper.getAsString(msgObj, "id", "msg_" + i);
                idList.add(id);
            } else {
                idList.add(null);
            }
        }

        int idx = 0;
        for (JsonElement msgEl : messages) {
            if (!msgEl.isJsonObject()) continue;
            JsonObject msgObj = msgEl.getAsJsonObject();

            String msgId = idList.get(idx);

            String typeStr = GsonHelper.getAsString(msgObj, "type", "text");
            TalkMessage.Type type = TalkMessage.Type.fromString(typeStr);

            String speakerTypeStr = GsonHelper.getAsString(msgObj, "speakerType", "npc");
            TalkMessage.SpeakerType speakerType = TalkMessage.SpeakerType.fromString(speakerTypeStr);

            String speaker = GsonHelper.getAsString(msgObj, "speaker", "&c**EMPTY SPEAKER**");
            String text = GsonHelper.getAsString(msgObj, "text", "&c**EMPTY TEXT**");

            String action = null;
            if (msgObj.has("action") && !msgObj.get("action").isJsonNull()) {
                action = GsonHelper.getAsString(msgObj, "action", null);
            }

            String nextId = null;
            if (msgObj.has("nextId") && !msgObj.get("nextId").isJsonNull()) {
                nextId = GsonHelper.getAsString(msgObj, "nextId", null);
            }

            boolean autoContinue = GsonHelper.getAsBoolean(msgObj, "continue", false);

            // 自动推断nextId
            if (autoContinue && nextId == null) {
                if (idx + 1 < idList.size()) {
                    nextId = idList.get(idx + 1);
                }
            }

            TalkMessage msg = new TalkMessage(
                    msgId,
                    type,
                    speakerType,
                    speaker,
                    text,
                    action,
                    System.currentTimeMillis(),
                    nextId
            );

            // 如果是 choice 类型，就读取 choices 数组
            if (type == TalkMessage.Type.CHOICE && msgObj.has("choices")) {
                JsonArray choicesArr = msgObj.getAsJsonArray("choices");
                int choiceIdx = 0;
                for (JsonElement choiceEl : choicesArr) {
                    if (!choiceEl.isJsonObject()) continue;
                    JsonObject choiceObj = choiceEl.getAsJsonObject();

                    String choiceId = GsonHelper.getAsString(choiceObj, "id", msgId + "_choice_" + choiceIdx);
                    String choiceText = GsonHelper.getAsString(choiceObj, "text", "&c**EMPTY CHOICE TEXT**");
                    String cNextId = "";
                    if (choiceObj.has("nextId") && !choiceObj.get("nextId").isJsonNull()) {
                        cNextId = GsonHelper.getAsString(choiceObj, "nextId", "");
                    }

                    TalkMessage.Choice choice = new TalkMessage.Choice(
                            choiceId,
                            choiceText,
                            cNextId
                    );
                    msg.addChoice(choice);
                    choiceIdx++;
                }
            }

            conv.addMessage(msg);

            parsedMessages.add(msg);
            msgMap.put(msgId, msg);

            idx++;
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

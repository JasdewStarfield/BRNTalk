package yourscraft.jasdewstarfield.brntalk.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConversationLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public ConversationLoader() {
        super(GSON, "dialogues");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons,
                         @NotNull ResourceManager resourceManager,
                         @NotNull ProfilerFiller profiler) {

        TalkManager manager = TalkManager.getInstance();
        manager.clear();

        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            ResourceLocation rl = entry.getKey();
            JsonElement element = entry.getValue();

            if (!element.isJsonObject()) continue;
            JsonObject root = element.getAsJsonObject();

            if (root.has("conversations")) {
                // 新格式：有 "conversations" 数组
                JsonArray convArray = GsonHelper.getAsJsonArray(root, "conversations");
                for (JsonElement convEl : convArray) {
                    if (!convEl.isJsonObject()) continue;
                    JsonObject convObj = convEl.getAsJsonObject();

                    loadSingleConversation(manager, convObj, rl);
                }
            } else {
                // 旧格式：整个文件就是一个对话
                loadSingleConversation(manager, root, rl);
            }
        }
    }

    private static void loadSingleConversation(TalkManager manager,
                                               JsonObject convObj,
                                               ResourceLocation fileId) {

        String convId = GsonHelper.getAsString(convObj, "id", fileId.getPath());
        TalkConversation conv = new TalkConversation(convId);

        JsonArray messages = GsonHelper.getAsJsonArray(convObj, "messages");

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
            String nextId = GsonHelper.getAsString(msgObj, "nextId", null);
            boolean autoContinue = GsonHelper.getAsBoolean(msgObj, "continue", false);
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
                    String cNextId = GsonHelper.getAsString(choiceObj, "nextId", "");

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
            idx++;
        }

        manager.registerConversation(conv);
    }
}

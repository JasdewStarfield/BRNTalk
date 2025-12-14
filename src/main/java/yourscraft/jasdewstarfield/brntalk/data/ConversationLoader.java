package yourscraft.jasdewstarfield.brntalk.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.GsonHelper;

import java.util.Map;

public class ConversationLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public ConversationLoader() {
        super(GSON, "dialogues");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {

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
        int idx = 0;

        for (JsonElement msgEl : messages) {
            if (!msgEl.isJsonObject()) continue;
            JsonObject msgObj = msgEl.getAsJsonObject();

            String msgId = GsonHelper.getAsString(msgObj, "id", "msg_" + idx);

            String nextId = GsonHelper.getAsString(msgObj, "nextId", null);
            if (nextId == null) {
                // 兼容旧写法：如果有 nextConversation 字段，也可以读进来当 nextId
                nextId = GsonHelper.getAsString(msgObj, "nextConversation", null);
            }

            String typeStr = GsonHelper.getAsString(msgObj, "type", "text");
            TalkMessage.Type type = TalkMessage.Type.fromString(typeStr);
            String speaker = GsonHelper.getAsString(msgObj, "speaker", "");
            String text = GsonHelper.getAsString(msgObj, "text", "");

            TalkMessage msg = new TalkMessage(
                    msgId,
                    type,
                    speaker,
                    text,
                    System.currentTimeMillis(),
                    nextId
            );

            // 如果是 choice 类型，就读取 choices 数组
            if (type == TalkMessage.Type.CHOICE && msgObj.has("choices")) {
                JsonArray choicesArr = msgObj.getAsJsonArray("choices");
                for (JsonElement choiceEl : choicesArr) {
                    if (!choiceEl.isJsonObject()) continue;
                    JsonObject choiceObj = choiceEl.getAsJsonObject();

                    String choiceId = GsonHelper.getAsString(choiceObj, "id", "");
                    String choiceText = GsonHelper.getAsString(choiceObj, "text", "");

                    // 读取 Choice 的 Next ID
                    // 优先读 nextId，其次兼容 nextConversation
                    String cNextId = GsonHelper.getAsString(choiceObj, "nextId",
                            GsonHelper.getAsString(choiceObj, "nextConversation", "")
                    );

                    TalkMessage.Choice choice = new TalkMessage.Choice(
                            choiceId,
                            choiceText,
                            cNextId
                    );
                    msg.addChoice(choice);
                }
            }

            conv.addMessage(msg);
            idx++;
        }

        manager.registerConversation(conv);
    }
}

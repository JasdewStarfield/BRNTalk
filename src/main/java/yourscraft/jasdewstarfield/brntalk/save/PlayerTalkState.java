package yourscraft.jasdewstarfield.brntalk.save;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.*;

public class PlayerTalkState {

    // threadId -> 该线程依次经历过的 conversationId 列表
    private final Map<String, List<String>> threads = new HashMap<>();

    /** 默认构造：空状态 */
    public PlayerTalkState() {}

    // ---------- 运行时操作 ----------

    /** 线程开始：重置该 threadId 的记录，并写入第一段 conversationId */
    public void startThread(String threadId, String firstConversationId) {
        List<String> list = new ArrayList<>();
        list.add(firstConversationId);
        threads.put(threadId, list);
    }

    /** 在某个线程上追加一段新的 conversationId */
    public void appendConversation(String threadId, String conversationId) {
        List<String> list = threads.computeIfAbsent(threadId, t -> new ArrayList<>());
        list.add(conversationId);
    }

    public List<String> getConversationChain(String threadId) {
        return threads.get(threadId);
    }

    public Set<String> getThreadIds() {
        return threads.keySet();
    }

    public boolean hasThread(String threadId) {
        return threads.containsKey(threadId);
    }

    // --------- NBT 序列化 / 反序列化 ---------

    /** 把当前玩家的对话记录写到 NBT 里 */
    public void saveToNbt(CompoundTag tag) {
        CompoundTag threadsTag = new CompoundTag();

        for (Map.Entry<String, List<String>> entry : threads.entrySet()) {
            String threadId = entry.getKey();
            List<String> convs = entry.getValue();

            ListTag listTag = new ListTag();
            for (String convId : convs) {
                listTag.add(StringTag.valueOf(convId));
            }
            threadsTag.put(threadId, listTag);
        }

        tag.put("threads", threadsTag);
    }

    /** 从 NBT 读取一个 PlayerTalkState */
    public static PlayerTalkState fromNbt(CompoundTag tag) {
        PlayerTalkState state = new PlayerTalkState();

        if (!tag.contains("threads", Tag.TAG_COMPOUND)) {
            return state;
        }

        CompoundTag threadsTag = tag.getCompound("threads");
        for (String threadId : threadsTag.getAllKeys()) {
            Tag t = threadsTag.get(threadId);
            if (!(t instanceof ListTag listTag)) continue;

            List<String> convs = new ArrayList<>();
            for (Tag elem : listTag) {
                if (elem instanceof StringTag s) {
                    convs.add(s.getAsString());
                }
            }
            state.threads.put(threadId, convs);
        }

        return state;
    }
}

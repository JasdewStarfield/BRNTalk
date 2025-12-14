package yourscraft.jasdewstarfield.brntalk.save;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.*;

public class PlayerTalkState {

    /**
     * 内部类：保存单个对话线程的存档数据
     * 包含：
     * 1. scriptId: 该线程使用的剧本 ID (对应 TalkConversation 的 ID)
     * 2. history: 已经经历过的 Message ID 列表
     */
    public static class SavedThread {
        private final String scriptId;
        private final List<String> history = new ArrayList<>();

        public SavedThread(String scriptId) {
            this.scriptId = scriptId;
        }

        public String getScriptId() {
            return scriptId;
        }

        public List<String> getHistory() {
            return history;
        }

        public void addMessage(String msgId) {
            history.add(msgId);
        }

        public void addAllMessages(List<String> msgIds) {
            history.addAll(msgIds);
        }

        // --- NBT 转换 ---

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString("scriptId", scriptId);

            ListTag list = new ListTag();
            for (String msgId : history) {
                list.add(StringTag.valueOf(msgId));
            }
            tag.put("history", list);
            return tag;
        }

        public static SavedThread fromNbt(CompoundTag tag) {
            String scriptId = tag.getString("scriptId");
            SavedThread st = new SavedThread(scriptId);

            if (tag.contains("history", Tag.TAG_LIST)) {
                ListTag list = tag.getList("history", Tag.TAG_STRING);
                for (Tag t : list) {
                    st.addMessage(t.getAsString());
                }
            }
            return st;
        }
    }

    // threadId -> SavedThread 列表
    private final Map<String, SavedThread> threads = new HashMap<>();

    /** 默认构造：空状态 */
    public PlayerTalkState() {}

    // ---------- 运行时操作 ----------

    /** 线程开始：记录剧本ID，并记录第一条消息ID */
    public void startThread(String threadId, String scriptId, String startMsgId) {
        SavedThread st = new SavedThread(scriptId);
        if (startMsgId != null) {
            st.addMessage(startMsgId);
        }
        threads.put(threadId, st);
    }

    /** * 追加一条新的消息 ID
     */
    public void appendMessage(String threadId, String messageId) {
        SavedThread st = threads.get(threadId);
        if (st != null) {
            st.addMessage(messageId);
        }
    }

    /** * 追加多条新的消息 ID
     */
    public void appendMessages(String threadId, List<String> messageIds) {
        SavedThread st = threads.get(threadId);
        if (st != null) {
            st.addAllMessages(messageIds);
        }
    }

    public SavedThread getThread(String threadId) {
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

        for (Map.Entry<String, SavedThread> entry : threads.entrySet()) {
            threadsTag.put(entry.getKey(), entry.getValue().toNbt());
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
            if (t instanceof CompoundTag threadCompound) {
                SavedThread st = SavedThread.fromNbt(threadCompound);
                state.threads.put(threadId, st);
            }
        }

        return state;
    }
}

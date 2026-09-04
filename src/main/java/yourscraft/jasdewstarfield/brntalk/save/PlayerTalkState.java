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
        private long startTime;
        private long lastReadTime = 0;

        public SavedThread(String scriptId, long startTime) {
            this.scriptId = scriptId;
            this.startTime = startTime;
        }

        public long getStartTime() {
            return startTime;
        }

        public String getScriptId() {
            return scriptId;
        }

        public List<String> getHistory() {
            return history;
        }

        public long getLastReadTime() { return lastReadTime; }

        public void setLastReadTime(long time) { this.lastReadTime = time; }

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
            tag.putLong("startTime", startTime);
            tag.putLong("lastReadTime", lastReadTime);

            ListTag list = new ListTag();
            for (String msgId : history) {
                list.add(StringTag.valueOf(msgId));
            }
            tag.put("history", list);
            return tag;
        }

        public static SavedThread fromNbt(CompoundTag tag) {
            String scriptId = tag.getString("scriptId");
            long startTime = tag.contains("startTime") ? tag.getLong("startTime") : System.currentTimeMillis();
            SavedThread st = new SavedThread(scriptId, startTime);

            if (tag.contains("lastReadTime")) {
                st.setLastReadTime(tag.getLong("lastReadTime"));
            }

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
    // 剧本级完成账本独立于线程，防止重登或事件重放重复触发联动。
    private final Set<String> completedScripts = new HashSet<>();
    // 外部奖励先记录 PENDING 再执行副作用；崩溃恢复时宁可留下可对账记录，也不重复开对话。
    private final Map<String, ExternalActionReceipt> externalActionReceipts = new HashMap<>();

    public enum ExternalActionStatus {
        PENDING,
        SUCCEEDED,
        FAILED
    }

    /** 持久化跨模组动作的用途和最终状态，供管理员诊断未完成的奖励投递。 */
    public record ExternalActionReceipt(String action, ExternalActionStatus status) {}

    /** 默认构造：空状态 */
    public PlayerTalkState() {}

    // ---------- 运行时操作 ----------

    /** 线程开始：记录剧本ID，并记录第一条消息ID */
    public void startThread(String threadId, String scriptId, String startMsgId) {
        SavedThread st = new SavedThread(scriptId, System.currentTimeMillis());
        if (startMsgId != null) {
            st.addMessage(startMsgId);
        }
        threads.put(threadId, st);
    }

    /** * 追加多条新的消息 ID
     */
    public void appendMessages(String threadId, List<String> messageIds) {
        SavedThread st = threads.get(threadId);
        if (st != null) {
            st.addAllMessages(messageIds);
        }
    }

    /**
     * 移除指定的对话线程
     */
    public void removeThread(String threadId) {
        threads.remove(threadId);
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

    /** 首次记录剧本完成时返回 true，供调用方决定是否发布完成事件。 */
    public boolean markConversationCompleted(String scriptId) {
        return scriptId != null && !scriptId.isBlank() && completedScripts.add(scriptId);
    }

    public boolean hasCompletedConversation(String scriptId) {
        return scriptId != null && completedScripts.contains(scriptId);
    }

    /** 清除剧本时同步清除完成账本，允许作者显式重新开始该剧情。 */
    public boolean clearCompletedConversation(String scriptId) {
        return scriptId != null && completedScripts.remove(scriptId);
    }

    /**
     * 在执行跨模组副作用前占用稳定键。只有首次调用返回 true，重放不会再次执行动作。
     */
    public boolean beginExternalAction(String key, String action) {
        if (key == null || key.isBlank() || action == null || action.isBlank()) return false;
        return externalActionReceipts.putIfAbsent(key,
                new ExternalActionReceipt(action, ExternalActionStatus.PENDING)) == null;
    }

    /** 更新已占用动作的结果；未知键不会被事后凭空创建。 */
    public void finishExternalAction(String key, boolean succeeded) {
        externalActionReceipts.computeIfPresent(key, (ignored, receipt) -> new ExternalActionReceipt(
                receipt.action(), succeeded ? ExternalActionStatus.SUCCEEDED : ExternalActionStatus.FAILED));
    }

    public ExternalActionReceipt getExternalActionReceipt(String key) {
        return key == null ? null : externalActionReceipts.get(key);
    }

    public boolean isEmpty() {
        return threads.isEmpty() && completedScripts.isEmpty() && externalActionReceipts.isEmpty();
    }

    /**
     * 检查玩家是否在指定的剧本中看到过某条消息
     * @param scriptId 剧本 ID (Conversation ID)
     * @param messageId 消息 ID
     * @return 如果找到记录返回 true
     */
    public boolean hasSeenMessage(String scriptId, String messageId) {
        // 遍历该玩家所有的对话线程
        for (SavedThread thread : threads.values()) {
            // 1. 匹配剧本 ID (如果不关心是哪个剧本里的，scriptId 可以传 null，这里我们严格匹配)
            if (scriptId != null && !scriptId.equals(thread.getScriptId())) {
                continue;
            }

            // 2. 检查历史记录
            if (thread.getHistory().contains(messageId)) {
                return true;
            }
        }
        return false;
    }

    public void updateLastReadTime(String threadId, long time) {
        SavedThread st = threads.get(threadId);
        if (st != null) {
            st.setLastReadTime(time);
        }
    }

    // --------- NBT 序列化 / 反序列化 ---------

    /** 把当前玩家的对话记录写到 NBT 里 */
    public void saveToNbt(CompoundTag tag) {
        CompoundTag threadsTag = new CompoundTag();

        for (Map.Entry<String, SavedThread> entry : threads.entrySet()) {
            threadsTag.put(entry.getKey(), entry.getValue().toNbt());
        }

        tag.put("threads", threadsTag);
        ListTag completedTag = new ListTag();
        completedScripts.stream().sorted().forEach(scriptId -> completedTag.add(StringTag.valueOf(scriptId)));
        tag.put("completedScripts", completedTag);

        CompoundTag receiptsTag = new CompoundTag();
        externalActionReceipts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag receiptTag = new CompoundTag();
            receiptTag.putString("action", entry.getValue().action());
            receiptTag.putString("status", entry.getValue().status().name());
            receiptsTag.put(entry.getKey(), receiptTag);
        });
        tag.put("externalActionReceipts", receiptsTag);
    }

    /** 从 NBT 读取一个 PlayerTalkState */
    public static PlayerTalkState fromNbt(CompoundTag tag) {
        PlayerTalkState state = new PlayerTalkState();

        if (tag.contains("threads", Tag.TAG_COMPOUND)) {
            CompoundTag threadsTag = tag.getCompound("threads");
            for (String threadId : threadsTag.getAllKeys()) {
                Tag t = threadsTag.get(threadId);
                if (t instanceof CompoundTag threadCompound) {
                    SavedThread st = SavedThread.fromNbt(threadCompound);
                    state.threads.put(threadId, st);
                }
            }
        }

        if (tag.contains("completedScripts", Tag.TAG_LIST)) {
            ListTag completedTag = tag.getList("completedScripts", Tag.TAG_STRING);
            for (Tag entry : completedTag) {
                String scriptId = entry.getAsString();
                if (!scriptId.isBlank()) state.completedScripts.add(scriptId);
            }
        }

        if (tag.contains("externalActionReceipts", Tag.TAG_COMPOUND)) {
            CompoundTag receiptsTag = tag.getCompound("externalActionReceipts");
            for (String key : receiptsTag.getAllKeys()) {
                CompoundTag receiptTag = receiptsTag.getCompound(key);
                String action = receiptTag.getString("action");
                try {
                    ExternalActionStatus status = ExternalActionStatus.valueOf(receiptTag.getString("status"));
                    if (!key.isBlank() && !action.isBlank()) {
                        state.externalActionReceipts.put(key, new ExternalActionReceipt(action, status));
                    }
                } catch (IllegalArgumentException ignored) {
                    // Unknown future/corrupt statuses are skipped instead of preventing player login.
                }
            }
        }

        return state;
    }
}

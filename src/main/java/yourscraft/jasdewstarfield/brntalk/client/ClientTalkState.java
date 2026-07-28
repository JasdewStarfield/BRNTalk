package yourscraft.jasdewstarfield.brntalk.client;

import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.*;

public class ClientTalkState {
    private static final ClientTalkState INSTANCE = new ClientTalkState();

    private final List<TalkThread> threads = new ArrayList<>();
    private final Set<StateListener> listeners = new LinkedHashSet<>();
    private String selectedThreadId;

    public enum ChangeType {
        THREADS_REPLACED,
        THREAD_ADDED,
        MESSAGES_APPENDED,
        READ_TIME_UPDATED,
        SELECTION_CHANGED,
        CLEARED
    }

    /**
     * threadId 表示本次变化直接关联的线程；全量替换和清空时允许为 null。
     */
    public record StateChange(ChangeType type, String threadId) {
    }

    /**
     * 客户端状态变化监听器不依赖具体 Screen，后续 HUD 或其他界面也可以复用。
     */
    @FunctionalInterface
    public interface StateListener {
        void onStateChanged(StateChange change);
    }

    public static ClientTalkState get() {
        return INSTANCE;
    }

    public void setThreads(List<TalkThread> newThreads) {
        // 全量同步以 threadId 去重；同一 ID 重复出现时保留服务端最后发送的对象。
        Map<String, TalkThread> uniqueThreads = new LinkedHashMap<>();
        for (TalkThread thread : newThreads) {
            uniqueThreads.put(thread.getId(), thread);
        }

        threads.clear();
        threads.addAll(uniqueThreads.values());
        ensureValidSelection();
        notifyListeners(new StateChange(ChangeType.THREADS_REPLACED, null));
    }

    public List<TalkThread> getThreads() {
        return List.copyOf(threads);
    }

    /**
     * 返回确定性排序的状态快照，避免界面刷新时因相同活动时间而随机换位。
     */
    public List<TalkThread> getThreadsByRecentActivity() {
        return threads.stream()
                .sorted(Comparator.comparingLong(TalkThread::getLastActivityTime)
                        .reversed()
                        .thenComparing(TalkThread::getId))
                .toList();
    }

    public TalkThread getThread(String threadId) {
        if (threadId == null) {
            return null;
        }

        for (TalkThread thread : threads) {
            if (threadId.equals(thread.getId())) {
                return thread;
            }
        }
        return null;
    }

    public String getSelectedThreadId() {
        return selectedThreadId;
    }

    public TalkThread getSelectedThread() {
        return getThread(selectedThreadId);
    }

    /**
     * 选择只能指向当前状态内的线程，避免界面保留已经被全量同步移除的对象。
     */
    public boolean selectThread(String threadId) {
        if (getThread(threadId) == null || Objects.equals(selectedThreadId, threadId)) {
            return false;
        }

        selectedThreadId = threadId;
        notifyListeners(new StateChange(ChangeType.SELECTION_CHANGED, threadId));
        return true;
    }

    public boolean hasUnread(TalkThread thread) {
        if (thread == null) return false;
        long lastActivity = thread.getLastActivityTime();
        long lastRead = thread.getLastReadTime();
        return lastActivity > lastRead;
    }

    public void addThread(TalkThread newThread) {
        // 防止重复添加
        threads.removeIf(t -> t.getId().equals(newThread.getId()));

        threads.add(newThread);
        ensureValidSelection();
        notifyListeners(new StateChange(ChangeType.THREAD_ADDED, newThread.getId()));
    }

    public void appendMessages(String threadId, List<TalkMessage> newMsgs) {
        TalkThread target = getThread(threadId);

        if (target != null && !newMsgs.isEmpty()) {
            for (TalkMessage m : newMsgs) {
                target.appendMessage(m);
            }
            notifyListeners(new StateChange(ChangeType.MESSAGES_APPENDED, threadId));
        }
    }

    public void updateReadTime(String threadId, long newTime) {
        TalkThread thread = getThread(threadId);
        // 已读时间只允许前进，忽略重复包或较晚到达的旧状态。
        if (thread != null && newTime > thread.getLastReadTime()) {
            thread.setLastReadTime(newTime);
            notifyListeners(new StateChange(ChangeType.READ_TIME_UPDATED, threadId));
        }
    }

    public void addListener(StateListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void removeListener(StateListener listener) {
        listeners.remove(listener);
    }

    private void ensureValidSelection() {
        if (getThread(selectedThreadId) != null) {
            return;
        }

        List<TalkThread> sortedThreads = getThreadsByRecentActivity();
        selectedThreadId = sortedThreads.isEmpty() ? null : sortedThreads.get(0).getId();
    }

    private void notifyListeners(StateChange change) {
        // 使用快照允许监听器在回调期间安全地取消订阅。
        for (StateListener listener : List.copyOf(listeners)) {
            listener.onStateChanged(change);
        }
    }

    public void clear() {
        if (threads.isEmpty() && selectedThreadId == null) {
            return;
        }
        threads.clear();
        selectedThreadId = null;
        notifyListeners(new StateChange(ChangeType.CLEARED, null));
    }
}

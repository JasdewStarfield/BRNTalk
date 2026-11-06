package yourscraft.jasdewstarfield.brntalk.client.ui;

import yourscraft.jasdewstarfield.brntalk.client.ClientTalkState;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** 只管理待确认的已读意图；正式已读时间仍由服务端确认后写入客户端状态。 */
public final class TalkReadCoordinator {
    private record PendingRead(String sessionId, long activityTime) {}
    private final Map<String, PendingRead> pending = new HashMap<>();
    private final Consumer<String> sendRead;

    public TalkReadCoordinator(Consumer<String> sendRead) { this.sendRead = sendRead; }

    public void reconcile(ClientTalkState state) {
        pending.entrySet().removeIf(entry -> {
            var thread = state.getThread(entry.getKey());
            var request = entry.getValue();
            return thread == null || !thread.getSessionId().equals(request.sessionId())
                    || thread.getLastReadTime() >= request.activityTime();
        });
    }

    public void requestIfNeeded(TalkThread selected, boolean playbackFinished) {
        if (selected == null || !playbackFinished) return;
        String id = selected.getId();
        long activity = selected.getLastActivityTime();
        if (selected.getLastReadTime() >= activity) {
            pending.remove(id);
            return;
        }
        var previous = pending.get(id);
        if (previous != null && previous.sessionId().equals(selected.getSessionId())
                && previous.activityTime() >= activity) return;
        // 发包前占用活动时间，防止同步回调或后续帧重复发送。
        pending.put(id, new PendingRead(selected.getSessionId(), activity));
        sendRead.accept(id);
    }

    public void clear() { pending.clear(); }
}

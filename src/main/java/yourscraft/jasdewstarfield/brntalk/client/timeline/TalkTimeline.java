package yourscraft.jasdewstarfield.brntalk.client.timeline;

import yourscraft.jasdewstarfield.brntalk.client.text.ClientTextFormatter;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

/**
 * 计算打字机时间轴。该类只消费线程数据和时间，不持有 Screen 状态。
 */
public final class TalkTimeline {
    private TalkTimeline() {
    }

    public static int getCharDelay() {
        return BrntalkConfig.CLIENT.charDelay.get();
    }

    public static int getMessagePause() {
        return BrntalkConfig.CLIENT.msgPause.get();
    }

    public static long calculateDuration(TalkMessage message) {
        return calculateDuration(message, getCharDelay());
    }

    /**
     * 显式传入字符延迟，便于在不启动 Screen 的情况下验证持续时间。
     */
    public static long calculateDuration(TalkMessage message, int charDelay) {
        if (message == null) {
            return 0;
        }
        String processed = ClientTextFormatter.process(message.getText());
        String clean = ClientTextFormatter.stripColor(processed).replace("\n", "");
        return (long) clean.length() * charDelay;
    }

    public static TimelineState calculate(TalkThread thread) {
        return calculate(thread, System.currentTimeMillis());
    }

    /**
     * 显式传入当前时间，使同一输入能够得到可重复验证的时间轴结果。
     */
    public static TimelineState calculate(TalkThread thread, long now) {
        TimelineState state = new TimelineState();
        state.isFinished = true;

        if (thread == null || thread.getMessages().isEmpty()) {
            return state;
        }

        long cachedDuration = thread.getTotalDurationCache();
        if (cachedDuration != -1 && now >= thread.getStartTime() + cachedDuration) {
            state.activeMessage = thread.getCurrentMessage();
            return state;
        }

        long previousVisualEndTime = 0;
        int messagePause = getMessagePause();

        for (TalkMessage message : thread.getMessages()) {
            long duration = calculateDuration(message);
            long visualStartTime;
            if (message.getTimestamp() == 0) {
                visualStartTime = 0;
                previousVisualEndTime = 0;
            } else {
                visualStartTime = Math.max(message.getTimestamp(), previousVisualEndTime + messagePause);
                previousVisualEndTime = visualStartTime + duration;
            }

            if (now < visualStartTime) {
                state.isFinished = false;
                return state;
            }
            if (now < previousVisualEndTime) {
                state.activeMessage = message;
                state.activeStartTime = visualStartTime;
                state.isFinished = false;
                return state;
            }

            state.activeMessage = message;
            state.activeStartTime = visualStartTime;
        }

        thread.updateDurationCache(previousVisualEndTime);
        return state;
    }

    public static boolean isFinished(TalkThread thread) {
        return calculate(thread).isFinished;
    }

    /**
     * 保留可变字段以兼容现有渲染调用点，实例本身只代表一次计算快照。
     */
    public static class TimelineState {
        public boolean isFinished;
        public TalkMessage activeMessage;
        public long activeStartTime;
    }
}

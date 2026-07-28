package yourscraft.jasdewstarfield.brntalk.client.preview;

import yourscraft.jasdewstarfield.brntalk.client.text.ClientTextFormatter;
import yourscraft.jasdewstarfield.brntalk.client.timeline.TalkTimeline;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

/**
 * 生成线程列表和 Toast 使用的单行文本预览。
 */
public final class TalkPreview {
    private TalkPreview() {
    }

    public static String getThreadTimelinePreview(TalkThread thread, int widthLimit) {
        TalkTimeline.TimelineState state = TalkTimeline.calculate(thread);
        if (state.activeMessage == null) {
            return "";
        }

        if (state.isFinished) {
            return getSingleLinePreview(state.activeMessage, widthLimit);
        }
        return generateTypewriterPreview(state.activeMessage, state.activeStartTime, widthLimit,
                System.currentTimeMillis());
    }

    /**
     * 显式传入当前时间，避免预览验证依赖真实帧时钟。
     */
    public static String generateTypewriterPreview(TalkMessage message, long startTime, int widthLimit, long now) {
        String speaker = ClientTextFormatter.stripColor(ClientTextFormatter.process(message.getSpeaker()));
        String fullText = ClientTextFormatter.stripColor(
                ClientTextFormatter.process(message.getText()).replace("\n", " ")
        );

        long timePassed = now - startTime;
        int charDelay = TalkTimeline.getCharDelay();
        long fullDuration = (long) fullText.length() * charDelay;

        String visibleText;
        if (timePassed <= 0) {
            visibleText = "";
        } else if (timePassed >= fullDuration) {
            visibleText = fullText;
        } else {
            int charCount = Math.min((int) (timePassed / charDelay), fullText.length());
            visibleText = fullText.substring(0, charCount);
        }

        return ClientTextFormatter.trimToWidth(speaker + ": " + visibleText, widthLimit);
    }

    public static String getSingleLinePreview(TalkMessage message, int widthLimit) {
        if (message == null) {
            return "";
        }

        String speaker = ClientTextFormatter.stripColor(ClientTextFormatter.process(message.getSpeaker()));
        String text = ClientTextFormatter.stripColor(
                ClientTextFormatter.process(message.getText()).replace("\n", " ")
        );
        return ClientTextFormatter.trimToWidth(speaker + ": " + text, widthLimit);
    }
}

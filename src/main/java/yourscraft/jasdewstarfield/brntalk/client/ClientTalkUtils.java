package yourscraft.jasdewstarfield.brntalk.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brntalk.client.preview.TalkPreview;
import yourscraft.jasdewstarfield.brntalk.client.render.TalkRenderUtils;
import yourscraft.jasdewstarfield.brntalk.client.text.ClientTextFormatter;
import yourscraft.jasdewstarfield.brntalk.client.timeline.TalkTimeline;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.List;

/**
 * 1.3 迁移期兼容入口。新代码应直接使用 text、timeline、preview 和 render 包中的单一职责类。
 */
@Deprecated
public final class ClientTalkUtils {
    private ClientTalkUtils() {
    }

    public static int getCharDelay() {
        return TalkTimeline.getCharDelay();
    }

    public static int getMsgPause() {
        return TalkTimeline.getMessagePause();
    }

    public static String processText(String text) {
        return ClientTextFormatter.process(text);
    }

    public static void clearCache() {
        ClientTextFormatter.clearCache();
    }

    public static String stripColor(String text) {
        return ClientTextFormatter.stripColor(text);
    }

    public static long calculateDuration(TalkMessage message) {
        return TalkTimeline.calculateDuration(message);
    }

    public static String trimToWidth(String text, int maxWidth) {
        return ClientTextFormatter.trimToWidth(text, maxWidth);
    }

    public static TimelineState calculateTimeline(TalkThread thread) {
        TalkTimeline.TimelineState calculated = TalkTimeline.calculate(thread);
        TimelineState compatible = new TimelineState();
        compatible.isFinished = calculated.isFinished;
        compatible.activeMessage = calculated.activeMessage;
        compatible.activeStartTime = calculated.activeStartTime;
        return compatible;
    }

    public static boolean isThreadFinished(TalkThread thread) {
        return TalkTimeline.isFinished(thread);
    }

    public static String getThreadTimelinePreview(TalkThread thread, int widthLimit) {
        return TalkPreview.getThreadTimelinePreview(thread, widthLimit);
    }

    public static String getSingleLinePreview(TalkMessage message, int widthLimit) {
        return TalkPreview.getSingleLinePreview(message, widthLimit);
    }

    public static void drawTiledTexture(GuiGraphics graphics, ResourceLocation texture,
                                        int x, int y, int width, int height,
                                        int u, int v, int tileWidth, int tileHeight,
                                        int uOffset, int vOffset,
                                        int textureWidth, int textureHeight) {
        TalkRenderUtils.drawTiledTexture(graphics, texture, x, y, width, height,
                u, v, tileWidth, tileHeight, uOffset, vOffset, textureWidth, textureHeight);
    }

    public static void drawRepeatedTexture(GuiGraphics graphics, ResourceLocation texture,
                                           int x, int y, int width, int height,
                                           int textureWidth, int textureHeight) {
        TalkRenderUtils.drawRepeatedTexture(graphics, texture, x, y, width, height,
                textureWidth, textureHeight);
    }

    public static void drawTextureFrame(GuiGraphics graphics, ResourceLocation texture,
                                        int x, int y, int width, int height,
                                        int borderWidth, int borderHeight,
                                        int textureWidth, int textureHeight) {
        TalkRenderUtils.drawTextureFrame(graphics, texture, x, y, width, height,
                borderWidth, borderHeight, textureWidth, textureHeight);
    }

    public static void drawCustomScrollbar(GuiGraphics graphics, int x, int y, int viewHeight,
                                           int totalHeight, double scrollAmount, int maxScroll) {
        TalkRenderUtils.drawCustomScrollbar(graphics, x, y, viewHeight, totalHeight, scrollAmount, maxScroll);
    }

    public static class TimelineState extends TalkTimeline.TimelineState {
    }

    public static class MessageLayoutCache {
        private final yourscraft.jasdewstarfield.brntalk.client.render.MessageLayoutCache delegate =
                new yourscraft.jasdewstarfield.brntalk.client.render.MessageLayoutCache();
        public String processedText;
        public List<net.minecraft.util.FormattedCharSequence> lines;

        public List<net.minecraft.util.FormattedCharSequence> getLines(Font font, TalkMessage message, int maxWidth) {
            List<net.minecraft.util.FormattedCharSequence> result = delegate.getLines(font, message, maxWidth);
            this.processedText = delegate.processedText;
            this.lines = delegate.lines;
            return result;
        }
    }
}

package yourscraft.jasdewstarfield.brntalk.client.ui.chat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import yourscraft.jasdewstarfield.brntalk.client.render.MessageCacheKey;
import yourscraft.jasdewstarfield.brntalk.client.render.MessageLayoutCache;
import yourscraft.jasdewstarfield.brntalk.client.text.ClientTextFormatter;
import yourscraft.jasdewstarfield.brntalk.client.timeline.TalkTimeline;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static yourscraft.jasdewstarfield.brntalk.client.ui.TalkUIStyles.*;

/**
 * 负责聊天消息的时间轴缓存、内容测量和绘制，不管理 Screen 或滚动控件生命周期。
 */
public final class TalkChatContentRenderer {
    private static final int MAX_RENDER_CACHE_ENTRIES = 2048;

    private final Map<MessageCacheKey, MessageRenderCache> renderCache =
            new LinkedHashMap<>(128, 0.75f, true);
    private final Map<MessageCacheKey, Long> messageStartTimes = new HashMap<>();
    private String timelineThreadId;
    private int cachedMessageCount = -1;

    public int calculateScrollableHeight(TalkThread thread, Font font, int textMaxWidth) {
        if (thread == null) {
            return 0;
        }

        int messageHeight = calculateMessageHeight(thread, font, textMaxWidth);
        if (messageHeight <= 0) {
            return 0;
        }
        return CHAT_CONTENTS_Y_OFFSET + messageHeight + CHAT_CONTENTS_BOTTOM_PADDING;
    }

    public void render(GuiGraphics graphics, Font font, TalkThread thread,
                       int x, int contentYOffset, int width, int textMaxWidth,
                       double currentScroll, int widgetScreenY, int widgetHeight) {
        if (thread == null) {
            return;
        }

        List<TalkMessage> messages = thread.getMessages();
        int currentY = contentYOffset;
        int lineHeight = font.lineHeight;
        long now = System.currentTimeMillis();
        String lastSpeaker = null;
        long previousVisualEndTime = 0;
        int charDelay = TalkTimeline.getCharDelay();
        int messagePause = TalkTimeline.getMessagePause();

        for (TalkMessage message : messages) {
            MessageCacheKey cacheKey = MessageCacheKey.of(thread, message);
            MessageRenderCache cache = getOrCreateRenderCache(cacheKey);
            cache.updateLayoutIfNeeded(message, textMaxWidth, font);

            long visualStartTime;
            if (message.getTimestamp() == 0) {
                visualStartTime = 0;
                previousVisualEndTime = 0;
            } else {
                visualStartTime = Math.max(message.getTimestamp(), previousVisualEndTime + messagePause);
                previousVisualEndTime = visualStartTime + cache.duration;
            }

            String currentSpeaker = cache.speakerComponent.getString();
            boolean showName = lastSpeaker == null || !lastSpeaker.equals(currentSpeaker);
            lastSpeaker = currentSpeaker;

            long timePassed = now - visualStartTime;
            String fullText = cache.layoutCache.processedText;
            if (timePassed < 0) {
                continue;
            }

            String textToShow;
            if (timePassed >= cache.duration) {
                textToShow = fullText;
            } else {
                int charCount = Math.max(0, Math.min((int) (timePassed / charDelay), fullText.length()));
                textToShow = fullText.substring(0, charCount);
            }

            List<FormattedCharSequence> linesToDraw = textToShow.length() == fullText.length()
                    ? cache.layoutCache.getLines(font, message, textMaxWidth)
                    : font.split(Component.literal(textToShow), textMaxWidth);

            int contentHeight = linesToDraw.size() * lineHeight;
            int bubbleWidth = 0;
            for (FormattedCharSequence line : linesToDraw) {
                bubbleWidth = Math.max(bubbleWidth, font.width(line));
            }
            bubbleWidth += BUBBLE_PADDING_X * 2;
            int bubbleHeight = contentHeight + BUBBLE_PADDING_Y * 2;
            int nameHeight = showName ? lineHeight + 2 : 0;
            int entryHeight = nameHeight + bubbleHeight;

            if (currentY + entryHeight + MSG_SPACING < currentScroll
                    || currentY > currentScroll + widgetHeight) {
                currentY += entryHeight + MSG_SPACING;
                continue;
            }

            boolean playerMessage = message.getSpeakerType() == TalkMessage.SpeakerType.PLAYER;
            int bubbleX = x + (playerMessage ? width - bubbleWidth - 10 : 10);
            int drawY = widgetScreenY + currentY;
            if (showName) {
                int nameX = x + (playerMessage ? width - cache.speakerNameWidth - 10 : 10);
                int nameColor = playerMessage ? COLOR_PLAYER_NAME : COLOR_NPC_NAME;
                graphics.drawString(font, cache.speakerComponent, nameX, drawY, nameColor);
                drawY += nameHeight;
            }

            int backgroundColor = playerMessage ? COLOR_PLAYER_BUBBLE_BG : COLOR_NPC_BUBBLE_BG;
            int borderColor = playerMessage ? COLOR_PLAYER_BUBBLE_BORDER : COLOR_NPC_BUBBLE_BORDER;
            drawBubble(graphics, bubbleX, drawY, bubbleWidth, bubbleHeight, backgroundColor, borderColor);

            int textY = drawY + BUBBLE_PADDING_Y;
            int textX = bubbleX + BUBBLE_PADDING_X;
            for (FormattedCharSequence line : linesToDraw) {
                graphics.drawString(font, line, textX, textY, COLOR_TEXT_NORMAL, false);
                textY += lineHeight;
            }

            currentY += entryHeight + MSG_SPACING;
        }
    }

    private int calculateMessageHeight(TalkThread thread, Font font, int textMaxWidth) {
        updateTimelineCache(thread);

        int totalHeight = 0;
        int lineHeight = font.lineHeight;
        long now = System.currentTimeMillis();
        int charDelay = TalkTimeline.getCharDelay();
        String lastSpeaker = null;

        for (TalkMessage message : thread.getMessages()) {
            MessageCacheKey cacheKey = MessageCacheKey.of(thread, message);
            long visualStartTime = messageStartTimes.getOrDefault(cacheKey, now + 1);
            if (now < visualStartTime) {
                break;
            }

            MessageRenderCache cache = getOrCreateRenderCache(cacheKey);
            cache.updateLayoutIfNeeded(message, textMaxWidth, font);

            String currentSpeaker = cache.speakerComponent.getString();
            boolean showName = lastSpeaker == null || !lastSpeaker.equals(currentSpeaker);
            lastSpeaker = currentSpeaker;
            int nameHeight = showName ? lineHeight + 2 : 0;
            long timePassed = now - visualStartTime;

            if (timePassed >= cache.duration) {
                totalHeight += nameHeight + cache.bubbleHeight + MSG_SPACING;
                continue;
            }

            String fullText = cache.layoutCache.processedText;
            int charCount = Math.max(0, Math.min((int) (timePassed / charDelay), fullText.length()));
            String textToShow = fullText.substring(0, charCount);
            int lineCount = font.split(Component.literal(textToShow), textMaxWidth).size();
            int bubbleHeight = lineCount * lineHeight + BUBBLE_PADDING_Y * 2;
            totalHeight += nameHeight + bubbleHeight + MSG_SPACING;
        }
        return totalHeight;
    }

    private void updateTimelineCache(TalkThread thread) {
        List<TalkMessage> messages = thread.getMessages();
        if (thread.getId().equals(timelineThreadId) && messages.size() == cachedMessageCount) {
            return;
        }

        messageStartTimes.clear();
        timelineThreadId = thread.getId();
        cachedMessageCount = messages.size();
        long previousVisualEndTime = 0;
        int messagePause = TalkTimeline.getMessagePause();
        Set<MessageCacheKey> activeMessageKeys = new HashSet<>();

        for (TalkMessage message : messages) {
            MessageCacheKey cacheKey = MessageCacheKey.of(thread, message);
            activeMessageKeys.add(cacheKey);
            long visualStartTime;
            if (message.getTimestamp() == 0) {
                visualStartTime = 0;
                previousVisualEndTime = 0;
            } else {
                visualStartTime = Math.max(message.getTimestamp(), previousVisualEndTime + messagePause);
                previousVisualEndTime = visualStartTime + TalkTimeline.calculateDuration(message);
            }
            messageStartTimes.put(cacheKey, visualStartTime);
        }

        // 全量同步删除消息或替换线程内容后，不保留已经失效的消息布局。
        renderCache.keySet().removeIf(cacheKey -> !activeMessageKeys.contains(cacheKey));
    }

    private MessageRenderCache getOrCreateRenderCache(MessageCacheKey cacheKey) {
        MessageRenderCache cache = renderCache.computeIfAbsent(cacheKey, ignored -> new MessageRenderCache());
        while (renderCache.size() > MAX_RENDER_CACHE_ENTRIES) {
            MessageCacheKey eldestKey = renderCache.keySet().iterator().next();
            renderCache.remove(eldestKey);
        }
        return cache;
    }

    private static void drawBubble(GuiGraphics graphics, int x, int y, int width, int height,
                                   int backgroundColor, int borderColor) {
        graphics.fill(x, y, x + width, y + height, backgroundColor);
        graphics.fill(x, y, x + width, y + 1, borderColor);
        graphics.fill(x, y + height - 1, x + width, y + height, borderColor);
        graphics.fill(x, y, x + 1, y + height, borderColor);
        graphics.fill(x + width - 1, y, x + width, y + height, borderColor);
    }

    /**
     * 状态事件可能在消息数量不变时替换内容，因此强制下一帧重算时间轴。
     */
    public void invalidateTimeline() {
        timelineThreadId = null;
        cachedMessageCount = -1;
    }

    public void clear() {
        renderCache.clear();
        messageStartTimes.clear();
        invalidateTimeline();
    }

    private static final class MessageRenderCache {
        private final MessageLayoutCache layoutCache = new MessageLayoutCache();
        private Component speakerComponent;
        private int speakerNameWidth;
        private int bubbleHeight = -1;
        private long duration = -1;
        private int cachedLayoutWidth = -1;

        private void updateLayoutIfNeeded(TalkMessage message, int widthLimit, Font font) {
            if (cachedLayoutWidth == widthLimit && speakerComponent != null) {
                return;
            }

            cachedLayoutWidth = widthLimit;
            List<FormattedCharSequence> lines = layoutCache.getLines(font, message, widthLimit);
            if (speakerComponent == null) {
                String speakerName = ClientTextFormatter.process(message.getSpeaker());
                speakerComponent = Component.literal(speakerName);
                speakerNameWidth = font.width(speakerName);
                duration = TalkTimeline.calculateDuration(message);
            }
            bubbleHeight = lines.size() * font.lineHeight + BUBBLE_PADDING_Y * 2;
        }
    }
}

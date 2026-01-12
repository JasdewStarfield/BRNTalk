package yourscraft.jasdewstarfield.brntalk.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.client.ClientTalkUtils;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;

import java.util.*;

import static yourscraft.jasdewstarfield.brntalk.client.ui.TalkUIStyles.*;

public class TalkHud {

    public static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath("brntalk", "hud");

    // 配置常量
    private static final int MAX_DISPLAY_COUNT = 4;
    private static final long THREAD_TIMEOUT = 10000;
    private static final long NOTIFICATION_DURATION = 4000;

    // 状态变量
    private static String activeThreadId = null;        // 当前专注的线程 ID
    private static long lastActivityTime = 0;           // 最后一次活动时间

    // 主显示队列
    private static final LinkedList<HudEntry> DISPLAY_QUEUE = new LinkedList<>();
    // 待处理通知 (ThreadId -> 说话人名字)
    private static final Map<String, NotificationState> PENDING_NOTIFICATIONS = new LinkedHashMap<>();

    /**
     * 添加消息的入口
     * @param message 消息对象
     * @param threadId 所属线程 ID
     */
    public static void addMessage(TalkMessage message, String threadId) {
        if (message == null || threadId == null) return;

        long now = System.currentTimeMillis();

        // 1. 检查专注权是否过期
        if (activeThreadId != null && (now - lastActivityTime > THREAD_TIMEOUT) && DISPLAY_QUEUE.isEmpty()) {
            activeThreadId = null;
        }

        // 2. 抢占或保持专注
        if (activeThreadId == null) {
            activeThreadId = threadId;
            // 切换了线程，清理旧的显示队列和对应的 Pending 提示
            DISPLAY_QUEUE.clear();
            PENDING_NOTIFICATIONS.remove(threadId);
        }

        // 3. 分流，判断是否属于当前专注的线程
        if (threadId.equals(activeThreadId)) {
            // 是当前线程，加入播放队列
            lastActivityTime = now;
            addEntryToQueue(message, now);
        } else {
            // 是其他线程，加入折叠提示
            String speaker = ClientTalkUtils.stripColor(ClientTalkUtils.processText(message.getSpeaker()));
            PENDING_NOTIFICATIONS.put(threadId, new NotificationState(speaker, now));
        }
    }

    private static void addEntryToQueue(TalkMessage msg, long now) {
        int msgPause = BrntalkConfig.CLIENT.msgPause.get();

        // 计算纯文本长度和所需播放时间
        long playDuration = ClientTalkUtils.calculateDuration(msg);

        // 计算开始时间：必须等上一条消息播完 + 暂停时间
        long startTime = now;
        if (!DISPLAY_QUEUE.isEmpty()) {
            HudEntry lastAdded = DISPLAY_QUEUE.getFirst();
            startTime = Math.max(now, lastAdded.visualEndTime + msgPause);
        }

        long endTime = startTime + playDuration;

        // 插入到队首
        DISPLAY_QUEUE.addFirst(new HudEntry(msg, startTime, endTime));

        // 限制历史数量
        while (DISPLAY_QUEUE.size() > MAX_DISPLAY_COUNT + 2) {
            DISPLAY_QUEUE.removeLast();
        }
    }

    public static void render(GuiGraphics gfx, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen instanceof TalkScreen) return;

        if (BrntalkConfig.CLIENT.notificationMode.get() != BrntalkConfig.NotificationMode.HUD) {
            if (!DISPLAY_QUEUE.isEmpty()) {
                DISPLAY_QUEUE.clear();
            }
            return;
        }

        float scale = BrntalkConfig.CLIENT.hudScale.get().floatValue();
        int offsetY = BrntalkConfig.CLIENT.hudOffsetY.get();
        int topLimit = BrntalkConfig.CLIENT.hudTopMargin.get();

        long now = System.currentTimeMillis();
        int fontHeight = mc.font.lineHeight;

        gfx.pose().pushPose();
        gfx.pose().scale(scale, scale, 1.0f);

        boolean isFirstMessage = true;

        int baseX = 1;
        int currentBottomY = (int) ((((float) mc.getWindow().getGuiScaledHeight() / 2) + offsetY) / scale);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // --- 1. 渲染主消息队列 ---
        List<HudEntry> entries = new ArrayList<>(DISPLAY_QUEUE);
        for (int i = 0; i < entries.size(); i++) {
            HudEntry entry = entries.get(i);

            if (now < entry.visualStartTime) {
                continue;
            }

            // 计算该消息显示了多久
            long fadeOutStart = 5000;
            long fadeDuration = 1000;
            long timeSinceEnd = now - entry.visualEndTime;

            if (timeSinceEnd > fadeOutStart + fadeDuration) {
                DISPLAY_QUEUE.remove(entry);
                continue;
            }

            // 透明度
            float alpha = 1.0f;
            if (timeSinceEnd > fadeOutStart) {
                alpha = 1.0f - (float) (timeSinceEnd - fadeOutStart) / fadeDuration;
            }
            alpha = Mth.clamp(alpha, 0f, 1f);
            if (alpha < 0.05f) continue;
            int alphaInt = (int) (alpha * 255);

            // 检测是否显示名字
            boolean showName = true;
            if (i + 1 < entries.size()) {
                HudEntry olderEntry = entries.get(i + 1);
                String currentSpeaker = ClientTalkUtils.processText(entry.msg.getSpeaker());
                String olderSpeaker = ClientTalkUtils.processText(olderEntry.msg.getSpeaker());
                if (currentSpeaker.equals(olderSpeaker)) {
                    showName = false;
                }
            }

            // 文本处理
            List<FormattedCharSequence> allLines = entry.layout.getLines(mc.font, entry.msg, HUD_WIDTH - 10);
            String fullText = entry.layout.processedText;

            int displayedCharCount = fullText.length();

            boolean typeFinished = (now >= entry.visualEndTime);
            if (!typeFinished) {
                long displayedTime = now - entry.visualStartTime;
                int charDelay = Math.max(1, BrntalkConfig.CLIENT.charDelay.get());
                displayedCharCount = (int) (displayedTime / charDelay);
            }

            // 布局
            int contentHeight = allLines.size() * fontHeight;
            int nameHeight = showName ? (fontHeight + 2) : 0;
            int totalEntryHeight = nameHeight + contentHeight + + (HUD_PADDING * 2);

            boolean showWaiting = (entry.msg.getType() == TalkMessage.Type.CHOICE && typeFinished);
            if (showWaiting) {
                totalEntryHeight += fontHeight;
            }

            int drawY = currentBottomY - totalEntryHeight;

            // 如果计算出的顶部坐标太靠上（超过了安全线），则停止绘制这条及更早的消息
            if (drawY < topLimit) {
                // 如果是最新的那条消息被跳过，说明 HUD 设置有问题或者消息太长
                if (isFirstMessage) {
                    if (!entry.loggedOverflow) {
                        Brntalk.LOGGER.warn("[BRNTalk] HUD Alert: The latest message from '{}' was skipped because it exceeds the top safety limit.", entry.msg.getSpeaker());
                        Brntalk.LOGGER.warn("Draw Y: {}, Safety Limit: {}. Please adjust 'hudTopMargin', 'hudOffsetY' or 'hudScale' in config.", drawY, topLimit);
                        entry.loggedOverflow = true; // 锁定，防止刷屏
                    }
                }
                break;
            }
            isFirstMessage = false;

            // --- 绘制 ---
            // 绘制背景
            int bgAlpha = (int) (alpha * 160);
            int bgColor = (bgAlpha << 24) | (HUD_BG_COLOR & 0x00FFFFFF);
            gfx.fill(baseX, drawY, baseX + HUD_WIDTH, drawY + totalEntryHeight, bgColor);

            // 绘制侧边条
            boolean isPlayer = entry.msg.getSpeakerType() == TalkMessage.SpeakerType.PLAYER;
            int barColorBase = isPlayer ? HUD_BAR_PLAYER : HUD_BAR_NPC;
            int barColor = (alphaInt << 24) | barColorBase & 0x00FFFFFF;
            gfx.fill(baseX, drawY, baseX + 2, drawY + totalEntryHeight, barColor);

            // 绘制名字
            if (showName) {
                String speaker = ClientTalkUtils.trimToWidth(ClientTalkUtils.processText(entry.msg.getSpeaker()), HUD_WIDTH - 10);
                int nameColorBase = (alphaInt << 24) | (isPlayer ? HUD_TEXT_NAME_PLAYER : HUD_TEXT_NAME_NPC);
                int nameColor = (alphaInt << 24) | (nameColorBase & 0x00FFFFFF);
                gfx.drawString(mc.font, speaker, baseX + 6, drawY + HUD_PADDING, nameColor, false);
            }

            // --- 智能绘制行 ---
            int textY = drawY + HUD_PADDING + nameHeight;
            int contentColor = (alphaInt << 24) | (HUD_TEXT_CONTENT & 0x00FFFFFF);

            for (FormattedCharSequence line : allLines) {
                if (typeFinished) {
                    gfx.drawString(mc.font, line, baseX + 6, textY, contentColor, false);
                } else {
                    break;
                }
                textY += fontHeight;
            }

            // 特殊处理：正在打字的那一条
            if (!typeFinished) {
                String subText = fullText.substring(0, Math.min(displayedCharCount, fullText.length()));
                // 重新 split，但只针对这一条正在变化的消息
                var dynamicLines = mc.font.split(Component.literal(subText), HUD_WIDTH - 10);
                int dynamicY = drawY + HUD_PADDING + nameHeight;
                for (var line : dynamicLines) {
                    gfx.drawString(mc.font, line, baseX + 6, dynamicY, contentColor, false);
                    dynamicY += fontHeight;
                }
            }

            if (showWaiting) {
                long blink = (now / 500) % 2;
                String suffix = (blink == 0) ? " _" : "";
                // 确保它画在所有文本下方
                int waitColor = (alphaInt << 24) | (HUD_TEXT_WAITING & 0x00FFFFFF);
                int waitY = drawY + HUD_PADDING + fontHeight + 2 + (allLines.size() * fontHeight);
                String waitingText = I18n.get("gui.brntalk.hud_awaiting_response") + suffix;
                gfx.drawString(
                        mc.font,
                        waitingText,
                        baseX + 6,
                        waitY,
                        waitColor,
                        false
                );
            }

            currentBottomY = drawY - 4;
        }

        // --- 2. 渲染 Pending Notifications ---
        if (!PENDING_NOTIFICATIONS.isEmpty()) {
            int notifY = (int) ((((float) mc.getWindow().getGuiScaledHeight() / 2) + offsetY + 5) / scale);

            // 使用迭代器以便在遍历时删除
            Iterator<Map.Entry<String, NotificationState>> notifIt = PENDING_NOTIFICATIONS.entrySet().iterator();

            while (notifIt.hasNext()) {
                Map.Entry<String, NotificationState> mapEntry = notifIt.next();
                NotificationState state = mapEntry.getValue();

                // 检查超时
                if (now - state.timestamp > NOTIFICATION_DURATION) {
                    notifIt.remove();
                    continue;
                }

                String label = I18n.get("gui.brntalk.hud_pending_notification", state.speaker);
                int labelW = mc.font.width(label) + 8;

                // 简单的淡出效果
                float nAlpha = 1.0f;
                long nAge = now - state.timestamp;
                if (nAge > NOTIFICATION_DURATION - 500) {
                    nAlpha = 1.0f - (float)(nAge - (NOTIFICATION_DURATION - 500)) / 500f;
                }
                int nAlphaInt = (int)(nAlpha * 255);
                int nBgAlpha = (int)(nAlpha * 128);

                if (nAlphaInt > 5) {
                    gfx.fill(baseX, notifY, baseX + labelW, notifY + fontHeight + 4, (nBgAlpha << 24));
                    int nContentColor = (nAlphaInt << 24) | (HUD_TEXT_CONTENT & 0x00FFFFFF);
                    gfx.drawString(mc.font, label, baseX + 4, notifY + 2, nContentColor, false);
                    notifY += (fontHeight + 6);
                }
            }
        }

        RenderSystem.disableBlend();
        gfx.pose().popPose();
    }

    private static class HudEntry {
        final TalkMessage msg;
        final long visualStartTime;
        final long visualEndTime;
        final ClientTalkUtils.MessageLayoutCache layout = new ClientTalkUtils.MessageLayoutCache();

        boolean loggedOverflow = false;

        HudEntry(TalkMessage msg, long startTime, long endTime) {
            this.msg = msg;
            this.visualStartTime = startTime;
            this.visualEndTime = endTime;
        }
    }

    private record NotificationState(String speaker, long timestamp) {}
}

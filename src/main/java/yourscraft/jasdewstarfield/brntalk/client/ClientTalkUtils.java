package yourscraft.jasdewstarfield.brntalk.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brntalk.BrntalkConfig;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

public class ClientTalkUtils {

    // 读取打字机参数
    public static int getCharDelay() {
        return BrntalkConfig.CLIENT.charDelay.get();
    }

    public static int getMsgPause() {
        return BrntalkConfig.CLIENT.msgPause.get();
    }

    /**
     * 核心处理逻辑：I18n + 颜色代码 + 玩家名占位符
     */
    public static String processText(String text) {
        if (text == null) return "";

        // 1. I18n 翻译 (如果 text 是 lang key，则翻译；否则原样返回)
        String translated = I18n.get(text);

        // 2. 颜色代码 (& -> §)
        String processing = translated.replace("&", "§");

        // 3. 玩家名占位符 {player}
        if (processing.contains("{player}")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                processing = processing.replace("{player}", mc.player.getName().getString());
            }
        }

        // 4. 自动优化中英文排版
        processing = processing.replaceAll("(?<=[\\u4e00-\\u9fa5]) (?=[a-zA-Z0-9])", "\u00A0");
        processing = processing.replaceAll("(?<=[a-zA-Z0-9]) (?=[\\u4e00-\\u9fa5])", "\u00A0");

        return processing;
    }

    /**
     * 去除文本的颜色格式
     *
     * @param text 文本对象
     */
    public static String stripColor(String text) {
        return ChatFormatting.stripFormatting(text);
    }

    /**
     * 基于像素宽度截断文本
     * 如果文本超过 maxWidth 像素，截断并添加 "..."
     *
     * @param text 文本对象
     * @param maxWidth 最大像素宽度
     */
    public static String trimToWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return "";

        Font font = Minecraft.getInstance().font;

        // 1. 如果本来就没超宽，直接返回
        if (font.width(text) <= maxWidth) {
            return text;
        }

        // 2. 计算省略号的宽度
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);

        // 3. 使用 Minecraft 原生方法获取"能塞进剩下宽度"的子字符串
        String trimmed = font.plainSubstrByWidth(text, maxWidth - ellipsisWidth);

        return trimmed + ellipsis;
    }



    // --- [新增] 核心时间轴状态类 ---
    public static class TimelineState {
        public boolean isFinished;          // 整个对话是否已播完
        public TalkMessage activeMessage;   // 当前正在播放（或刚播完）的那条消息
        public long activeStartTime;        // 这条消息的“视觉开始时间”
    }

    /**
     * 返回一个对话线程的State
     *
     * @param thread 消息串对象
     */
    public static TimelineState calculateTimeline(TalkThread thread) {
        TimelineState state = new TimelineState();
        state.isFinished = true; // 默认假设结束，除非找到正在进行的消息

        if (thread == null || thread.getMessages().isEmpty()) return state;

        long now = System.currentTimeMillis();
        long previousVisualEndTime = 0;

        int charDelay = getCharDelay();
        int msgPause = getMsgPause();

        for (TalkMessage msg : thread.getMessages()) {
            // 计算时长 (使用去色后的文本长度)
            String cleanText = stripColor(processText(msg.getText())).replace("\n", "");
            long duration = (long) cleanText.length() * charDelay;

            long visualStartTime;
            if (msg.getTimestamp() == 0) {
                visualStartTime = 0;
                previousVisualEndTime = 0;
            } else {
                visualStartTime = Math.max(msg.getTimestamp(), previousVisualEndTime + msgPause);
                previousVisualEndTime = visualStartTime + duration;
            }

            // 判断状态
            if (now < visualStartTime) {
                // 情况A：还没到这条消息（处于上一条消息结束后的暂停期）
                // 此时 activeMessage 保持为上一条消息，状态为未完成
                state.isFinished = false;
                return state;
            } else if (now < previousVisualEndTime) {
                // 情况B：正在播放这条消息
                state.activeMessage = msg;
                state.activeStartTime = visualStartTime;
                state.isFinished = false;
                return state;
            } else {
                // 情况C：这条消息已经播完
                // 更新 activeMessage 为当前这条（它是目前为止最新的）
                state.activeMessage = msg;
                state.activeStartTime = visualStartTime;
                // 继续循环，看看下一条消息是否开始了
            }
        }

        // 如果循环走完，说明时间超过了最后一条的结束时间
        return state;
    }

    /**
     * 判断一个对话线程的打字机动画是否已经全部播放完毕
     *
     * @param thread 消息串对象
     */
    public static boolean isThreadFinished(TalkThread thread) {
        return calculateTimeline(thread).isFinished;
    }

    /**
     * 获取带有Timeline的打字机消息预览
     *
     * @param thread 消息串对象
     * @param widthLimit 像素宽度限制（超过则加 "..."）
     */
    public static String getThreadTimelinePreview(TalkThread thread, int widthLimit) {
        TimelineState state = calculateTimeline(thread);

        if (state.activeMessage == null) return "";

        // 如果已经结束了，直接显示最终文本，不用去算 substring (性能优化)
        if (state.isFinished) {
            String speaker = stripColor(processText(state.activeMessage.getSpeaker()));
            String text = stripColor(processText(state.activeMessage.getText()).replace("\n", " "));
            return trimToWidth(speaker + ": " + text, widthLimit);
        }

        // 如果没结束，跑打字机生成器
        return generateTypewriterPreview(state.activeMessage, state.activeStartTime, widthLimit);
    }

    /**
     * 获取当前消息Thread打字机的消息预览
     * 格式： "Speaker: Text"
     *
     * @param msg 消息对象
     * @param startTime 消息开始时间
     * @param widthLimit 像素宽度限制（超过则加 "..."）
     */
    private static String generateTypewriterPreview(TalkMessage msg, long startTime, int widthLimit) {
        String speakerWithColor = processText(msg.getSpeaker());
        String fullTextWithColor = processText(msg.getText()).replace("\n", " ");
        String speaker = stripColor(speakerWithColor);
        String fullText = stripColor(fullTextWithColor);

        long now = System.currentTimeMillis();
        long timePassed = now - startTime;

        int charDelay = getCharDelay();

        String visibleText;
        long fullDuration = (long) fullText.length() * charDelay;
        if (timePassed <= 0) {
            visibleText = "";
        } else if (timePassed >= fullDuration) {
            visibleText = fullText;
        } else {
            int charCount = (int) (timePassed / charDelay);
            charCount = Math.min(charCount, fullText.length());
            visibleText = fullText.substring(0, charCount);
        }

        String fullPreview = speaker + ": " + visibleText;
        return trimToWidth(fullPreview, widthLimit);
    }

    /**
     * 获取带有完整格式处理的单行消息预览
     * 格式： "Speaker: Text"
     *
     * @param msg 消息对象
     * @param widthLimit 像素宽度限制（超过则加 "..."）
     */
    public static String getSingleLinePreview(TalkMessage msg, int widthLimit) {
        if (msg == null) return "";

        String speaker = processText(msg.getSpeaker());
        String text = processText(msg.getText()).replace("\n", " ");
        // 去除颜色
        speaker = stripColor(speaker);
        text = stripColor(text);

        String full = speaker + ": " + text;
        return trimToWidth(full, widthLimit);
    }

    /**
     * 平铺绘制纹理 (用于背景和分割线)
     * 会自动循环重复贴图来填满指定区域
     *
     * @param gfx GuiGraphics
     * @param texture 贴图 ID
     * @param x 绘制区域 X
     * @param y 绘制区域 Y
     * @param width 绘制区域总宽
     * @param height 绘制区域总高
     * @param texW 贴图单元宽度 (如 16)
     * @param texH 贴图单元高度 (如 16)
     */
    public static void drawRepeatedTexture(GuiGraphics gfx, ResourceLocation texture,
                                           int x, int y, int width, int height,
                                           int texW, int texH) {
        RenderSystem.setShaderTexture(0, texture);

        // 双重循环铺满区域
        for (int dx = 0; dx < width; dx += texW) {
            for (int dy = 0; dy < height; dy += texH) {
                // 计算当前这块砖的实际显示大小 (处理边缘裁切)
                int drawW = Math.min(texW, width - dx);
                int drawH = Math.min(texH, height - dy);

                gfx.blit(texture,
                        x + dx, y + dy,     // 屏幕坐标
                        0, 0,               // UV 起点
                        drawW, drawH,       // 截取大小
                        texW, texH          // 纹理总大小
                );
            }
        }
    }

    /**
     * 绘制 9-Slice 外框 (带平铺边缘)
     *
     * @param borderW 横向边框宽度
     * @param borderH 纵向边框宽度
     */
    public static void drawTextureFrame(GuiGraphics gfx, ResourceLocation texture,
                                        int x, int y, int width, int height,
                                        int borderW, int borderH,
                                        int texW, int texH) {
        RenderSystem.setShaderTexture(0, texture);

        int innerW = width - borderW * 2;
        int innerH = height - borderH * 2;
        int texInnerW = texW - borderW * 2;
        int texInnerH = texH - borderH * 2;

        // 1. 四个角 (Corners)
        // 左上
        gfx.blit(texture, x, y, 0, 0, borderW, borderH, texW, texH);
        // 右上
        gfx.blit(texture, x + width - borderW, y, texW - borderW, 0, borderW, borderH, texW, texH);
        // 左下
        gfx.blit(texture, x, y + height - borderH, 0, texH - borderH, borderW, borderH, texW, texH);
        // 右下
        gfx.blit(texture, x + width - borderW, y + height - borderH, texW - borderW, texH - borderH, borderW, borderH, texW, texH);

        // 2. 上下边 (水平平铺)
        for (int dx = 0; dx < innerW; dx += texInnerW) {
            int w = Math.min(texInnerW, innerW - dx);
            // Top
            gfx.blit(texture, x + borderW + dx, y, borderW, 0, w, borderH, texW, texH);
            // Bottom
            gfx.blit(texture, x + borderW + dx, y + height - borderH, borderW, texH - borderH, w, borderH, texW, texH);
        }

        // 3. 左右边 (垂直平铺)
        for (int dy = 0; dy < innerH; dy += texInnerH) {
            int h = Math.min(texInnerH, innerH - dy);
            // Left
            gfx.blit(texture, x, y + borderH + dy, 0, borderH, borderW, h, texW, texH);
            // Right
            gfx.blit(texture, x + width - borderW, y + borderH + dy, texW - borderW, borderH, borderW, h, texW, texH);
        }
    }
}

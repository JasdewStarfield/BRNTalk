package yourscraft.jasdewstarfield.brntalk.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

public class ClientTalkUtils {

    // 统一的打字机参数
    public static final int CHAR_DELAY_MS = 20;
    public static final int MSG_PAUSE_MS = 500;

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

    /**
     * 获取带有Timeline的打字机消息预览
     *
     * @param thread 消息串对象
     * @param widthLimit 像素宽度限制（超过则加 "..."）
     */
    public static String getThreadTimelinePreview(TalkThread thread, int widthLimit) {
        if (thread == null || thread.getMessages().isEmpty()) return "";

        long now = System.currentTimeMillis();
        long previousVisualEndTime = 0;

        TalkMessage activeMsg = null;
        long activeMsgStartTime = 0;

        // --- 1. 模拟时间轴，找到当前正在播放（或刚播放完）的那条消息 ---
        for (TalkMessage msg : thread.getMessages()) {
            // 预计算该消息的“理论播放时长”
            String rawText = processText(msg.getText());
            String cleanText = stripColor(rawText).replace("\n", "");
            // 去除换行符计算长度，防止长度误判
            int textLen = cleanText.length();
            long duration = (long) textLen * CHAR_DELAY_MS;

            // 计算该消息的“视觉开始时间”
            // 逻辑与 TalkScreen 保持严格一致
            long visualStartTime;
            if (msg.getTimestamp() == 0) { // 历史消息
                visualStartTime = 0;
                previousVisualEndTime = 0; // 历史消息不造成后续延迟
            } else {
                visualStartTime = Math.max(msg.getTimestamp(), previousVisualEndTime + MSG_PAUSE_MS);
                previousVisualEndTime = visualStartTime + duration;
            }

            // 判断：当前时间是否已经达到了这条消息的开始时间？
            if (now >= visualStartTime) {
                // 是的，我们可以显示这条消息（或它的后续消息）
                activeMsg = msg;
                activeMsgStartTime = visualStartTime;
            } else {
                // 还没到这条消息的时间，说明用户还在看上一条
                // 停止循环，保持 activeMsg 为上一条
                break;
            }
        }

        if (activeMsg == null) return ""; // 还没开始播放任何消息

        // --- 2. 对找到的 activeMsg 生成打字机预览 ---
        return generateTypewriterPreview(activeMsg, activeMsgStartTime, widthLimit);
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

        String visibleText;
        long fullDuration = (long) fullText.length() * CHAR_DELAY_MS;
        if (timePassed <= 0) {
            visibleText = "";
        } else if (timePassed >= fullDuration) {
            visibleText = fullText;
        } else {
            int charCount = (int) (timePassed / CHAR_DELAY_MS);
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
}

package yourscraft.jasdewstarfield.brntalk.client.text;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一处理客户端可见文本，不依赖任何具体 Screen 的生命周期。
 */
public final class ClientTextFormatter {
    private static final Map<TextCacheKey, String> TEXT_CACHE = new HashMap<>();

    private ClientTextFormatter() {
    }

    /**
     * 执行翻译、颜色代码、玩家名占位符和中英文间距处理。
     */
    public static String process(String text) {
        if (text == null) {
            return "";
        }

        Minecraft minecraft = Minecraft.getInstance();
        String playerName = minecraft.player == null ? "" : minecraft.player.getName().getString();
        TextCacheKey cacheKey = new TextCacheKey(text, playerName);
        return TEXT_CACHE.computeIfAbsent(cacheKey, ignored -> processUncached(text, playerName));
    }

    private static String processUncached(String text, String playerName) {
        // 如果 text 是语言键则翻译，否则 I18n 会保留原文。
        String processed = I18n.get(text).replace("&", "§");
        if (!playerName.isEmpty() && processed.contains("{player}")) {
            processed = processed.replace("{player}", playerName);
        }

        // 使用不换行空格保持原有的中英文混排效果。
        processed = processed.replaceAll("(?<=[\\u4e00-\\u9fa5]) (?=[a-zA-Z0-9])", "\u00A0");
        return processed.replaceAll("(?<=[a-zA-Z0-9]) (?=[\\u4e00-\\u9fa5])", "\u00A0");
    }

    public static String stripColor(String text) {
        return ChatFormatting.stripFormatting(text);
    }

    /**
     * 按像素宽度截断文本，并保留原有的三个点省略号行为。
     */
    public static String trimToWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        Font font = Minecraft.getInstance().font;
        if (font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int availableWidth = Math.max(0, maxWidth - font.width(ellipsis));
        return font.plainSubstrByWidth(text, availableWidth) + ellipsis;
    }

    /**
     * 语言资源或客户端会话变化时清空派生文本。
     */
    public static void clearCache() {
        TEXT_CACHE.clear();
    }

    private record TextCacheKey(String sourceText, String playerName) {
    }
}

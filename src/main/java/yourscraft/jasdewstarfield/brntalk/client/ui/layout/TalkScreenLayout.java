package yourscraft.jasdewstarfield.brntalk.client.ui.layout;

import net.minecraft.util.Mth;

import static yourscraft.jasdewstarfield.brntalk.client.ui.TalkUIStyles.*;

/**
 * TalkScreen 的不可变布局结果。窗口重建时计算一次，所有区域共享同一组坐标。
 */
public record TalkScreenLayout(
        int windowX,
        int windowY,
        int windowWidth,
        int windowHeight,
        int innerX,
        int innerY,
        int innerWidth,
        int innerHeight,
        int listAreaX,
        int listAreaWidth,
        int dividerX,
        int chatAreaX,
        int chatAreaWidth
) {
    public static TalkScreenLayout calculate(int screenWidth, int screenHeight) {
        int tileUnitX = FRAME_W - FRAME_BORDER_W * 2;
        int tileUnitY = FRAME_H - FRAME_BORDER_H * 2;
        int maxWidth = screenWidth - WIN_MARGIN_X * 2;
        int maxHeight = screenHeight - WIN_MARGIN_Y * 2;

        int tilesX = Math.max(1, (maxWidth - FRAME_BORDER_W * 2) / tileUnitX);
        int tilesY = Math.max(1, (maxHeight - FRAME_BORDER_H * 2) / tileUnitY);
        int windowWidth = FRAME_BORDER_W * 2 + tilesX * tileUnitX;
        int windowHeight = FRAME_BORDER_H * 2 + tilesY * tileUnitY;
        int windowX = (screenWidth - windowWidth) / 2;
        int windowY = (screenHeight - windowHeight) / 2;

        int innerX = windowX + FRAME_BORDER_W - FRAME_INNER_PADDING;
        int innerY = windowY + FRAME_BORDER_H - FRAME_INNER_PADDING;
        int innerWidth = windowWidth - (FRAME_BORDER_W - FRAME_INNER_PADDING) * 2;
        int innerHeight = windowHeight - (FRAME_BORDER_H - FRAME_INNER_PADDING) * 2;

        int listAreaWidth = alignListWidth(innerWidth);
        int dividerX = innerX + listAreaWidth;
        int chatAreaX = dividerX + DIVIDER_WIDTH;
        int chatAreaWidth = innerWidth - listAreaWidth - DIVIDER_WIDTH;

        return new TalkScreenLayout(
                windowX, windowY, windowWidth, windowHeight,
                innerX, innerY, innerWidth, innerHeight,
                innerX, listAreaWidth, dividerX, chatAreaX, chatAreaWidth
        );
    }

    private static int alignListWidth(int innerWidth) {
        int targetWidth = Mth.clamp(
                (int) (innerWidth * LIST_WIDTH_RATIO),
                LIST_MIN_WIDTH,
                LIST_MAX_WIDTH
        );
        int remainder = targetWidth % 16;
        if (remainder == 0) {
            return targetWidth;
        }
        return remainder >= 8 ? targetWidth + 16 - remainder : targetWidth - remainder;
    }
}

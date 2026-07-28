package yourscraft.jasdewstarfield.brntalk.client.ui.scroll;

import net.minecraft.util.Mth;

/**
 * 与具体滚动控件无关的物理滚动偏移计算。
 */
public final class TalkScrollMath {
    private static final int MIN_SCROLLBAR_HEIGHT = 14;

    private TalkScrollMath() {
    }

    public static int calculatePhysicalOffset(int viewHeight, int contentHeight,
                                              double scrollAmount, int maxScroll) {
        if (maxScroll <= 0) {
            return 0;
        }

        int barHeight = (int) ((float) (viewHeight * viewHeight) / (float) contentHeight);
        barHeight = Mth.clamp(barHeight, MIN_SCROLLBAR_HEIGHT, viewHeight);
        int trackLength = viewHeight - barHeight;
        if (trackLength <= 0) {
            return 0;
        }
        return (int) ((scrollAmount / (float) maxScroll) * trackLength);
    }
}

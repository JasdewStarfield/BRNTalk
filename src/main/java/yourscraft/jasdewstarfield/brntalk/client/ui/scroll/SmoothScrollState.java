package yourscraft.jasdewstarfield.brntalk.client.ui.scroll;

import net.minecraft.util.Mth;

/**
 * 可由不同原版滚动组件组合使用的平滑滚动状态，不依赖具体控件继承体系。
 */
public final class SmoothScrollState {
    private double target;
    private boolean animating;

    public void addToTarget(double amount, double maxScroll) {
        this.target = Mth.clamp(this.target + amount, 0.0, Math.max(0.0, maxScroll));
        this.animating = true;
    }

    public void animateTo(double amount, double maxScroll) {
        this.target = Mth.clamp(amount, 0.0, Math.max(0.0, maxScroll));
        this.animating = true;
    }

    public void setImmediately(double amount) {
        this.target = amount;
        this.animating = false;
    }

    /**
     * 返回当前帧应写回原版控件的滚动值。
     */
    public double tick(double current, double maxScroll, double smoothFactor) {
        this.target = Mth.clamp(this.target, 0.0, Math.max(0.0, maxScroll));
        if (!animating) {
            return current;
        }
        if (Math.abs(target - current) > 0.1) {
            return current + (target - current) * smoothFactor;
        }
        this.animating = false;
        return target;
    }

    public boolean isAtBottom(double current, double maxScroll, double tolerance) {
        boolean currentAtBottom = maxScroll - current <= tolerance;
        boolean targetAtBottom = animating && maxScroll - target <= tolerance;
        return currentAtBottom || targetAtBottom;
    }

    public double target() {
        return target;
    }

    public boolean isAnimating() {
        return animating;
    }
}

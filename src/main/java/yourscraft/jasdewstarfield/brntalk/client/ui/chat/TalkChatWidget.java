package yourscraft.jasdewstarfield.brntalk.client.ui.chat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.client.render.TalkRenderUtils;
import yourscraft.jasdewstarfield.brntalk.client.ui.scroll.SmoothScrollState;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;

import java.util.function.IntSupplier;

import static yourscraft.jasdewstarfield.brntalk.client.ui.TalkUIStyles.CHAT_CONTENTS_Y_OFFSET;

/**
 * 独立聊天滚动组件。内容高度和绘制由调用方提供，组件只负责输入与滚动生命周期。
 */
public final class TalkChatWidget extends AbstractScrollWidget {
    private final IntSupplier contentHeightSupplier;
    private final ContentRenderer contentRenderer;
    private final SmoothScrollState smoothScroll = new SmoothScrollState();

    public TalkChatWidget(int x, int y, int width, int height,
                          IntSupplier contentHeightSupplier, ContentRenderer contentRenderer) {
        super(x, y, width, height, Component.empty());
        this.contentHeightSupplier = contentHeightSupplier;
        this.contentRenderer = contentRenderer;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.visible) {
            return false;
        }
        smoothScroll.addToTarget(-scrollY * this.scrollRate(), this.getMaxScrollAmount());
        return true;
    }

    public boolean isScrolledToBottom(double tolerance) {
        return smoothScroll.isAtBottom(this.scrollAmount(), this.getMaxScrollAmount(), tolerance);
    }

    public double getScrollAmountVal() {
        return this.scrollAmount();
    }

    @Override
    protected void setScrollAmount(double amount) {
        super.setScrollAmount(amount);
        smoothScroll.setImmediately(amount);
    }

    public void scrollBy(double amount) {
        this.setScrollAmount(this.scrollAmount() + amount);
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (smoothScroll.isAnimating()) {
            double next = smoothScroll.tick(
                    this.scrollAmount(),
                    this.getMaxScrollAmount(),
                    BrntalkConfig.CLIENT.smoothFactor.get()
            );
            super.setScrollAmount(next);
        }
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationOutput) {
        this.defaultButtonNarrationText(narrationOutput);
    }

    @Override
    protected int getInnerHeight() {
        return contentHeightSupplier.getAsInt();
    }

    @Override
    protected double scrollRate() {
        return BrntalkConfig.CLIENT.scrollRate.get();
    }

    @Override
    protected void renderContents(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        contentRenderer.render(graphics, this.getX(), CHAT_CONTENTS_Y_OFFSET, this.getWidth());
    }

    @Override
    protected void renderDecorations(@NotNull GuiGraphics graphics) {
        if (BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            super.renderDecorations(graphics);
            return;
        }
        if (this.scrollbarVisible()) {
            TalkRenderUtils.drawCustomScrollbar(
                    graphics,
                    this.getX() + this.getWidth() + 2,
                    this.getY(),
                    this.getHeight(),
                    contentHeightSupplier.getAsInt(),
                    this.scrollAmount(),
                    this.getMaxScrollAmount()
            );
        }
    }

    @Override
    protected void renderBackground(@NotNull GuiGraphics graphics) {
    }

    @Override
    protected void renderBorder(@NotNull GuiGraphics graphics, int x, int y, int width, int height) {
    }

    public void scrollToBottom() {
        smoothScroll.animateTo(this.getMaxScrollAmount(), this.getMaxScrollAmount());
    }

    public void scrollToBottomImmediately() {
        double bottom = this.getMaxScrollAmount();
        smoothScroll.setImmediately(bottom);
        super.setScrollAmount(bottom);
    }

    public void restoreScroll(double amount) {
        smoothScroll.setImmediately(amount);
        super.setScrollAmount(amount);
    }

    public double getTargetScroll() {
        return smoothScroll.target();
    }

    public int getMaxScroll() {
        return this.getMaxScrollAmount();
    }

    @FunctionalInterface
    public interface ContentRenderer {
        void render(GuiGraphics graphics, int x, int contentYOffset, int width);
    }
}

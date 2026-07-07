package yourscraft.jasdewstarfield.brntalk.client.ui;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;
import yourscraft.jasdewstarfield.brntalk.client.ClientTalkState;
import yourscraft.jasdewstarfield.brntalk.client.ClientTalkUtils;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import static yourscraft.jasdewstarfield.brntalk.client.ui.TalkUIStyles.*;

public class TalkThreadList extends ObjectSelectionList<TalkThreadList.Entry> {
    private final TalkScreen parent;

    private double targetScrollAmount = 0.0;
    private boolean draggingCustomScrollbar = false;
    private boolean draggingCustomScrollbarThumb = false;
    private double customScrollbarGrabOffsetY = 0.0;
    private double customScrollbarDragStartY = 0.0;
    private double customScrollbarDragStartScroll = 0.0;

    /**
     * @param parent      TalkScreen
     * @param mc          Minecraft 实例
     * @param x           列表左上角 X
     * @param top         列表左上角 Y（也是 top）
     * @param width       列表宽度
     * @param height      列表高度
     */

    public TalkThreadList(TalkScreen parent,
                          Minecraft mc,
                          int x,
                          int top,
                          int width,
                          int height
    ) {
        super(mc, width, height, top, top + height, THREAD_LIST_ENTRY_HEIGHT);

        this.parent = parent;

        this.x0 = x;
        this.x1 = x + width;

        /*
         * AbstractSelectionList 的默认背景/遮罩会按原版列表假设盖住外层 UI。
         * BRNTalk 在 TalkScreen 或本列表中自行绘制背景和选中态，因此统一关闭父类层。
         */
        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false);
        this.setRenderSelection(false);
    }

    // 用于在重建 UI 时恢复滚动位置
    public void restoreScroll(double scroll) {
        this.setScrollAmount(scroll);
        this.targetScrollAmount = scroll;
    }

    public void setListArea(int x, int top, int width, int height) {
        /*
         * ObjectSelectionList 不是 AbstractWidget，开屏动画不能通过 setY 移动。
         * 这里显式更新列表边界，让文字、滚动条、裁剪区域和鼠标命中一起移动。
         */
        this.updateSize(width, height, top, top + height);
        this.setLeftPos(x);
    }

    // 重写鼠标判定区域，让滚动条能被选中
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseY >= this.y0 && mouseY <= this.y1 &&
                mouseX >= this.x0 - 3 && mouseX <= this.x1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        // 获取列表最大滚动范围
        double maxScroll = Math.max(0, this.getMaxScroll());

        // 根据滚轮方向更新目标值
        this.targetScrollAmount -= scrollY * BrntalkConfig.CLIENT.scrollRate.get();

        // 限制目标值在合法范围内
        this.targetScrollAmount = Mth.clamp(this.targetScrollAmount, 0, maxScroll);

        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button != 0 || !this.isMouseOver(mouseX, mouseY)) {
            return false;
        }

        if (isMouseOverCustomScrollbarThumb(mouseX, mouseY)) {
            /*
             * 原版列表只支持右侧滚动条命中。自定义 UI 把滚动条放在左侧，
             * 因此点击/拖拽要由这里维护。按下时只记录抓取点，不立即
             * 改变滚动位置；真正的滚动等鼠标拖动时再发生。
             */
            ScrollbarThumb thumb = getCustomScrollbarThumb();
            this.draggingCustomScrollbar = true;
            this.draggingCustomScrollbarThumb = true;
            this.customScrollbarGrabOffsetY = mouseY - thumb.y();
            return true;
        }
        if (isMouseOverCustomScrollbarTrack(mouseX, mouseY)) {
            /*
             * 槽位空白处也允许开始拖动，但单击本身不跳转。
             * 这里记录按下点和当前滚动值，后续按鼠标位移增量滚动。
             */
            ScrollbarThumb thumb = getCustomScrollbarThumb();
            if (thumb != null) {
                this.draggingCustomScrollbar = true;
                this.draggingCustomScrollbarThumb = false;
                this.customScrollbarDragStartY = mouseY;
                this.customScrollbarDragStartScroll = this.getScrollAmount();
            }
            return true;
        }

        Entry entry = this.getEntryAtPosition(mouseX, mouseY);
        if (entry != null && entry.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(entry);
            this.setDragging(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!BrntalkConfig.CLIENT.useVanillaStyleUI.get() && this.draggingCustomScrollbar && button == 0) {
            if (this.draggingCustomScrollbarThumb) {
                updateCustomScrollbarScroll(mouseY - this.customScrollbarGrabOffsetY);
            } else {
                updateCustomScrollbarScrollByDelta(mouseY - this.customScrollbarDragStartY);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!BrntalkConfig.CLIENT.useVanillaStyleUI.get() && this.draggingCustomScrollbar) {
            this.draggingCustomScrollbar = false;
            this.draggingCustomScrollbarThumb = false;
            this.customScrollbarGrabOffsetY = 0.0;
            this.customScrollbarDragStartY = 0.0;
            this.customScrollbarDragStartScroll = 0.0;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void setScrollAmount(double scroll) {
        // 拖拽：直接更新
        super.setScrollAmount(scroll);
        this.targetScrollAmount = scroll;
    }

    // 在渲染时进行平滑插值
    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        double currentScroll = this.getScrollAmount();
        double maxScroll = Math.max(0, this.getMaxScroll());

        // 1. 确保 target 也没越界 (防止 resizing 等情况导致 maxScroll 变小)
        this.targetScrollAmount = Mth.clamp(this.targetScrollAmount, 0, maxScroll);

        // 2. 平滑插值
        if (Math.abs(this.targetScrollAmount - currentScroll) > 0.1) {
            double newScroll = currentScroll + (this.targetScrollAmount - currentScroll) * BrntalkConfig.CLIENT.smoothFactor.get();
            super.setScrollAmount(newScroll);
        } else {
            super.setScrollAmount(this.targetScrollAmount);
        }

        super.render(gfx, mouseX, mouseY, partialTick);

        // 手动补画滚动条
        if (!BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            if (this.getMaxScroll() > 0) {
                int scrollbarX = this.getCustomScrollbarPosition();
                int listHeight = this.getHeight();
                int listY = this.y0;

                ClientTalkUtils.drawCustomScrollbar(gfx,
                        scrollbarX,
                        listY,
                        listHeight,
                        this.getMaxScroll() + listHeight,
                        this.getScrollAmount(),
                        this.getMaxScroll()
                );
            }
        }
    }

    @Override
    protected void updateScrollingState(double mouseX, double mouseY, int button) {
        super.updateScrollingState(mouseX, mouseY, button);
        // 把 target 也同步过去，避免松手后回弹
        this.targetScrollAmount = this.getScrollAmount();
    }

    @Override
    protected void renderBackground(@NotNull GuiGraphics guiGraphics) {
        if (BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            // 原版风格仍使用 BRNTalk 自己的半透明底色，避免父类 dirt 背景遮挡聊天 UI。
            guiGraphics.fill(this.x0, this.y0, this.x1, this.y1, COLOR_VANILLA_BG);
        }
    }

    @Override
    protected int getScrollbarPosition() {
        if (BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            /*
             * 1.20.1 的默认实现只根据列表宽度计算位置，没有加上 x0。
             * 这里把原版风格滚动条放回当前列表右边界内侧。
             */
            return this.x1 - 6;
        }
        /*
         * 父类会无条件绘制一条黑白默认滚动条，而且命中测试假定条目在滚动条左边。
         * 自定义 UI 使用左侧贴图滚动条，所以把父类滚动条移出屏幕，只保留自绘版本。
         */
        return this.x1 + 1000;
    }

    private int getCustomScrollbarPosition() {
        return this.x0 - 3;
    }

    private boolean isMouseOverCustomScrollbarTrack(double mouseX, double mouseY) {
        if (this.getMaxScroll() <= 0) {
            return false;
        }
        int scrollbarX = getCustomScrollbarPosition();
        return mouseX >= scrollbarX
                && mouseX < scrollbarX + DECO_SCROLL_BAR_W
                && mouseY >= this.y0
                && mouseY <= this.y1;
    }

    private boolean isMouseOverCustomScrollbarThumb(double mouseX, double mouseY) {
        ScrollbarThumb thumb = getCustomScrollbarThumb();
        return thumb != null
                && mouseX >= thumb.x()
                && mouseX < thumb.x() + thumb.width()
                && mouseY >= thumb.y()
                && mouseY < thumb.y() + thumb.height();
    }

    private ScrollbarThumb getCustomScrollbarThumb() {
        int maxScroll = this.getMaxScroll();
        if (maxScroll <= 0) {
            return null;
        }

        int listHeight = this.getHeight();
        int totalHeight = maxScroll + listHeight;
        int barHeight = (int) ((float) (listHeight * listHeight) / (float) totalHeight);
        barHeight = Mth.clamp(barHeight, DECO_SCROLL_BAR_H, listHeight);

        int barY = this.y0 + (int) ((this.getScrollAmount() / (float) maxScroll) * (listHeight - barHeight));
        return new ScrollbarThumb(getCustomScrollbarPosition(), barY, DECO_SCROLL_BAR_W, barHeight);
    }

    private void updateCustomScrollbarScroll(double targetThumbY) {
        int maxScroll = this.getMaxScroll();
        if (maxScroll <= 0) {
            return;
        }

        ScrollbarThumb thumb = getCustomScrollbarThumb();
        if (thumb == null) {
            return;
        }

        int listHeight = this.getHeight();
        int barHeight = thumb.height();
        double movableHeight = Math.max(1, listHeight - barHeight);
        double ratio = (targetThumbY - this.y0) / movableHeight;
        ratio = Mth.clamp(ratio, 0.0, 1.0);

        this.targetScrollAmount = ratio * maxScroll;
        super.setScrollAmount(this.targetScrollAmount);
    }

    private void updateCustomScrollbarScrollByDelta(double dragDeltaY) {
        int maxScroll = this.getMaxScroll();
        if (maxScroll <= 0) {
            return;
        }

        ScrollbarThumb thumb = getCustomScrollbarThumb();
        if (thumb == null) {
            return;
        }

        int listHeight = this.getHeight();
        double movableHeight = Math.max(1, listHeight - thumb.height());
        double scrollDelta = (dragDeltaY / movableHeight) * maxScroll;

        this.targetScrollAmount = Mth.clamp(this.customScrollbarDragStartScroll + scrollDelta, 0.0, maxScroll);
        super.setScrollAmount(this.targetScrollAmount);
    }

    private record ScrollbarThumb(int x, int y, int width, int height) {}


    @Override
    public int getRowWidth() {
        return this.width - 8;
    }

    public void setThreads(java.util.List<TalkThread> threads) {
        this.clearEntries();
        for (TalkThread thread : threads) {
            this.addEntry(new Entry(thread));
        }
    }

    // 单个条目
    public class Entry extends ObjectSelectionList.Entry<Entry> {

        private final TalkThread thread;

        public Entry(TalkThread thread) {
            this.thread = thread;
        }

        public TalkThread getThread() {
            return thread;
        }

        @Override
        public void render(@NotNull GuiGraphics gfx,
                           int index,
                           int top,
                           int left,
                           int width,
                           int height,
                           int mouseX,
                           int mouseY,
                           boolean isHovered,
                           float partialTick) {
            // 背景高亮
            if (isHovered || TalkThreadList.this.getSelected() == this) {
                gfx.fill(left, top, left + width - 4, top + height, COLOR_LIST_HOVER_BG);
            }

            var timelineState = ClientTalkUtils.calculateTimeline(thread);

            boolean isUnread = ClientTalkState.get().hasUnread(thread);

            // 检查是否等待选项：看最后一条消息是否为 CHOICE
            TalkMessage lastMsg = thread.getCurrentMessage();
            boolean isWaitingForChoice = (lastMsg != null && lastMsg.getType() == TalkMessage.Type.CHOICE);

            // 点的绘制位置 (右上角)
            int dotSize = 4;
            int dotX = left + width - 10;
            int dotY = top + 4;

            if (!timelineState.isFinished) {
                // 闪烁绿点（正在输入（打字机动画在播放））
                long frame = (System.currentTimeMillis() / 200) % 2;
                if (frame == 0) {
                    gfx.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, COLOR_DOT_TYPING);
                }
            } else if (isUnread) {
                // 红点 (未读)
                gfx.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, COLOR_DOT_UNREAD);
            } else if (isWaitingForChoice) {
                // 黄点 (等待选择)
                gfx.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, COLOR_DOT_WAITING);
            }

            String timeStr = thread.getFormattedTime();
            String preview = timelineState.isFinished
                    ? ClientTalkUtils.getSingleLinePreview(timelineState.activeMessage, 115) // 静态
                    : ClientTalkUtils.getThreadTimelinePreview(thread, 115); // 动态

            //时间
            gfx.drawString(Minecraft.getInstance().font, timeStr, left + 4, top + 4, COLOR_LIST_TIME);
            //消息预览
            gfx.drawString(Minecraft.getInstance().font, preview, left + 4, top + 14, COLOR_LIST_PREVIEW);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) { // 左键
                TalkThreadList.this.setSelected(this);
                parent.onThreadSelected(thread);
                return true;
            }
            return false;
        }

        @Override
        public @NotNull Component getNarration() {
            return Component.literal(thread.getLastMessagePreview());
        }
    }
}

package yourscraft.jasdewstarfield.brntalk.client.ui;

import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;
import yourscraft.jasdewstarfield.brntalk.client.ClientTalkState;
import yourscraft.jasdewstarfield.brntalk.client.preview.TalkPreview;
import yourscraft.jasdewstarfield.brntalk.client.render.TalkRenderUtils;
import yourscraft.jasdewstarfield.brntalk.client.timeline.TalkTimeline;
import yourscraft.jasdewstarfield.brntalk.client.ui.scroll.SmoothScrollState;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import static yourscraft.jasdewstarfield.brntalk.client.ui.TalkUIStyles.*;

public class TalkThreadList extends ObjectSelectionList<TalkThreadList.Entry> {
    private final TalkScreen parent;

    private final SmoothScrollState smoothScroll = new SmoothScrollState();

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
        super(mc, width, height, top, THREAD_LIST_ENTRY_HEIGHT);

        this.parent = parent;

        this.setX(x);
    }

    // 用于在重建 UI 时恢复滚动位置
    public void restoreScroll(double scroll) {
        this.setScrollAmount(scroll);
    }

    // 重写鼠标判定区域，让滚动条能被选中
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseY >= this.getY() && mouseY <= this.getY() + this.getHeight() &&
                mouseX >= this.getX() - 3 && mouseX <= this.getX() + this.getWidth();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 获取列表最大滚动范围
        double maxScroll = Math.max(0, this.getMaxScroll());

        smoothScroll.addToTarget(-scrollY * BrntalkConfig.CLIENT.scrollRate.get(), maxScroll);

        return true;
    }

    @Override
    public void setScrollAmount(double scroll) {
        // 拖拽：直接更新
        super.setScrollAmount(scroll);
        smoothScroll.setImmediately(scroll);
    }

    // 在渲染时进行平滑插值
    @Override
    public void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        double currentScroll = this.getScrollAmount();
        double maxScroll = Math.max(0, this.getMaxScroll());

        double nextScroll = smoothScroll.tick(
                currentScroll,
                maxScroll,
                BrntalkConfig.CLIENT.smoothFactor.get()
        );
        super.setScrollAmount(nextScroll);

        super.renderWidget(gfx, mouseX, mouseY, partialTick);

        // 手动补画滚动条
        if (!BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            if (this.getMaxScroll() > 0) {
                int scrollbarX = this.getScrollbarPosition();
                int listHeight = this.getHeight();
                int listY = this.getY();

                TalkRenderUtils.drawCustomScrollbar(gfx,
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
        smoothScroll.setImmediately(this.getScrollAmount());
    }

    @Override
    protected void renderListBackground(@NotNull GuiGraphics guiGraphics) {
        // 开启配置时才渲染
        if (BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            super.renderListBackground(guiGraphics);
        }
    }

    @Override
    protected void renderListSeparators(@NotNull GuiGraphics guiGraphics) {
        // 开启配置时才渲染
        if (BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            super.renderListSeparators(guiGraphics);
        }
    }

    @Override
    protected boolean scrollbarVisible() {
        if (!BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            return false;
        }
        return super.scrollbarVisible();
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getX() - 3;
    }


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

            var timelineState = TalkTimeline.calculate(thread);

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
            // 按当前列表项的实际宽度截断预览，让不同 UI 尺寸都能充分利用空间且不越界。
            int previewMaxWidth = Math.max(0, width - 8);
            String preview = timelineState.isFinished
                    ? TalkPreview.getSingleLinePreview(timelineState.activeMessage, previewMaxWidth) // 静态
                    : TalkPreview.getThreadTimelinePreview(thread, previewMaxWidth); // 动态

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

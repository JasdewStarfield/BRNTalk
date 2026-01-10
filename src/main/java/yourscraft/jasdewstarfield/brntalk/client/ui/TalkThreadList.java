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
        this.targetScrollAmount = scroll;
    }

    // 重写鼠标判定区域，让滚动条能被选中
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseY >= this.getY() && mouseY <= this.getY() + this.getHeight() &&
                mouseX >= this.getX() && mouseX <= this.getX() + this.width + 14;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 获取列表最大滚动范围
        double maxScroll = Math.max(0, this.getMaxScroll());

        // 根据滚轮方向更新目标值
        this.targetScrollAmount -= scrollY * BrntalkConfig.CLIENT.scrollRate.get();

        // 限制目标值在合法范围内
        this.targetScrollAmount = Mth.clamp(this.targetScrollAmount, 0, maxScroll);

        return true;
    }

    @Override
    public void setScrollAmount(double scroll) {
        // 拖拽：直接更新
        super.setScrollAmount(scroll);
        this.targetScrollAmount = scroll;
    }

    // 在渲染时进行平滑插值
    @Override
    public void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
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

        super.renderWidget(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    protected void updateScrollingState(double mouseX, double mouseY, int button) {
        super.updateScrollingState(mouseX, mouseY, button);
        // 把 target 也同步过去，避免松手后回弹
        this.targetScrollAmount = this.getScrollAmount();
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
    protected int getScrollbarPosition() {
        return this.getX() + this.getWidth() + 3;
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

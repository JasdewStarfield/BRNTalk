package yourscraft.jasdewstarfield.brntalk.client.ui;

import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.client.ClientTalkState;
import yourscraft.jasdewstarfield.brntalk.client.ClientTalkUtils;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

public class TalkThreadList extends ObjectSelectionList<TalkThreadList.Entry> {
    private final TalkScreen parent;
    private final int listX;
    private final int listWidth;

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
        super(mc, width, height, top, 28);

        this.parent = parent;
        this.listX = x;
        this.listWidth = width;

        this.setX(x);
    }

    @Override
    public int getRowWidth() {
        // 行的内容宽度，用左侧列表的宽度
        return this.listWidth;
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
                int bgColor = 0x40FFFFFF; // 半透明白色背景
                gfx.fill(left, top, left + width - 4, top + height, bgColor);
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
                    gfx.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, 0xFF00FF00);
                }
            } else if (isUnread) {
                // 红点 (未读)
                gfx.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, 0xFFFF0000);
            } else if (isWaitingForChoice) {
                // 黄点 (等待选择)
                gfx.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, 0xFFFFFF00);
            }

            // 时间 + 最后一条消息
            String timeStr = thread.getFormattedTime();
            String preview = timelineState.isFinished
                    ? ClientTalkUtils.getSingleLinePreview(timelineState.activeMessage, 115) // 静态
                    : ClientTalkUtils.getThreadTimelinePreview(thread, 115); // 动态

            // 上面一行时间
            gfx.drawString(
                    Minecraft.getInstance().font,
                    timeStr,
                    left + 4,
                    top + 4,
                    0xFFFFFFFF
            );
            // 下面一行消息预览
            gfx.drawString(
                    Minecraft.getInstance().font,
                    preview,
                    left + 4,
                    top + 4 + 10,
                    0xFFAAAAAA
            );
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

package yourscraft.jasdewstarfield.brntalk.client.ui;

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
        public void render(GuiGraphics gfx,
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

            // 时间 + 最后一条消息
            String timeStr = thread.getFormattedTime();
            String preview = thread.getLastMessagePreview();
            if (preview == null) preview = "";
            if (preview.length() > 16) {
                preview = preview.substring(0, 16) + "...";
            }

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
        public Component getNarration() {
            return Component.literal(thread.getLastMessagePreview());
        }
    }
}

package yourscraft.jasdewstarfield.brntalk.client.ui;

import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class TalkScreen extends Screen {

    private TalkThreadList threadList;
    private TalkThread selectedThread;

    public TalkScreen() {
        super(Component.literal("BRNTalk"));
    }

    @Override
    protected void init() {
        super.init();
        rebuildUI();
    }

    private void rebuildUI() {

        this.clearWidgets();

        Minecraft mc = Minecraft.getInstance();

        // 左侧列表区域
        int listX = 20;
        int listTop = 20;
        int listWidth = 140;
        int listBottom = this.height - 40;
        int listHeight = listBottom - listTop;

        this.threadList = new TalkThreadList(
                this,
                mc,
                listX,
                listTop,
                listWidth,
                listHeight
        );
        this.addRenderableWidget(this.threadList);

        // 更新左侧列表内容
        reloadThreadList();

        // 底部中间“关闭”按钮
        int buttonWidth = 80;
        int buttonHeight = 20;
        int closeX = (this.width - buttonWidth) / 2;
        int closeY = this.height - 30;

        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.brntalk.close_screen"), btn -> this.onClose())
                        .bounds(closeX, closeY, buttonWidth, buttonHeight)
                        .build()
        );

        // 根据当前对话的最后一条消息，生成选项按钮（如果是 CHOICE 类型）
        addChoiceButtonsForCurrentConversation();
    }

    private void reloadThreadList() {
        var manager = TalkManager.getInstance();
        List<TalkThread> threads = manager.getActiveThreads().stream()
                .sorted(Comparator.comparingLong(TalkThread::getStartedAt).reversed()) // 最新在上
                .toList();

        this.threadList.setThreads(threads);

        // 如果还没有选中的聊天串，默认选第一个
        if (selectedThread == null && !threads.isEmpty()) {
            selectedThread = threads.getFirst();
            if (!threadList.children().isEmpty()) {
                threadList.setSelected(threadList.children().getFirst());
            }
        }
    }

    private void addChoiceButtonsForCurrentConversation() {
        TalkMessage last = getLastMessageOfSelected();
        if (last == null || last.getType() != TalkMessage.Type.CHOICE) {
            return;
        }

        List<TalkMessage.Choice> choices = last.getChoices();
        if (choices.isEmpty()) return;

        int choiceWidth = 140;
        int choiceHeight = 20;
        int spacing = 5;
        int startY = this.height - 60; // 靠近底部
        int centerX = (this.width + 130) / 2 + 20; // 大致在右半边中间，130 是左侧列表宽度

        int i = 0;
        for (TalkMessage.Choice choice : choices) {
            final TalkMessage.Choice c = choice;
            int cy = startY - (choiceHeight + spacing) * i;

            Button btn = Button.builder(
                            Component.literal(c.getText()),
                            b -> onChoiceClicked(c)
                    )
                    .bounds(centerX - choiceWidth / 2, cy, choiceWidth, choiceHeight)
                    .build();

            this.addRenderableWidget(btn);
            i++;
        }
    }

    // 工具方法：取当前选中聊天串的最后一条消息
    private TalkMessage getLastMessageOfSelected() {
        if (selectedThread == null) return null;
        TalkConversation conv = selectedThread.getConversation();
        var list = conv.getMessages();
        if (list == null || list.isEmpty()) return null;
        return list.getLast();
    }

    // 点击列表项目，选中不同聊天串时，重新构建 UI
    public void onThreadSelected(TalkThread thread) {
        this.selectedThread = thread;
        rebuildUI();
    }

    // 选项按钮点击：根据 nextConversationId 跳转到新的对话脚本，同时创建/更新对应聊天串
    private void onChoiceClicked(TalkMessage.Choice choice) {
        String nextId = choice.getNextConversationId();
        if (nextId == null || nextId.isEmpty()) {
            return;
        }

        TalkManager manager = TalkManager.getInstance();

        // 触发新的聊天串（如果已经存在则更新时间）
        TalkThread nextThread = manager.startThread(nextId);
        if (nextThread == null) {
            // 找不到对应脚本，简单提示
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("[BRNTalk] 未找到对话脚本: " + nextId),
                        false
                );
            }
            return;
        }

        this.selectedThread = nextThread;
        // 跳转后重建界面，刷新列表 & 右侧内容 & 选项按钮
        rebuildUI();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx, mouseX, mouseY, partialTick);

        // 先交给父类渲染列表、按钮等组件
        super.render(gfx, mouseX, mouseY, partialTick);

        // 右侧聊天内容区域
        if (selectedThread != null) {
            TalkConversation conv = selectedThread.getConversation();

            int left = this.threadList.getX() + this.threadList.getWidth() + 10;
            int top = 20;
            int maxWidth = this.width - left - 20;
            int lineHeight = 12;

            int y = top;
            for (TalkMessage msg : conv.getMessages()) {
                String line = msg.getSpeaker() + ": " + msg.getText();

                // 简单一行一行画（后续可以换成自动换行、气泡 UI）
                gfx.drawString(this.font, line, left, y, 0xFFFFFF);
                y += lineHeight;
                if (y > this.height - 80) {
                    // 先不做真正滚动，简单防止画出屏幕外
                    break;
                }
            }
        } else {
            // 没有任何聊天串时的提示
            gfx.drawString(
                    this.font,
                    "暂无聊天串。可以通过 /brntalk start <id> 来触发。",
                    this.threadList.getX() + this.threadList.getWidth() + 10,
                    this.height / 2,
                    0xFFFFFF
            );
        }
    }

    @Override
    public boolean isPauseScreen() {
        // 返回 false：打开对话 UI 时，游戏不会暂停
        return false;
    }

    @Override
    public void onClose() {
        // 关闭界面时回到游戏
        Minecraft.getInstance().setScreen(null);
    }
}
package yourscraft.jasdewstarfield.brntalk.client.ui;

import net.minecraft.client.gui.components.AbstractWidget;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.client.ClientPayloadSender;
import yourscraft.jasdewstarfield.brntalk.client.ClientTalkState;
import yourscraft.jasdewstarfield.brntalk.client.ClientTalkUtils;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TalkScreen extends Screen {

    private TalkThreadList threadList;
    private TalkThread selectedThread;

    @Nullable
    private String selectedThreadId;

    // --- 滚动与动画控制变量 ---
    private float scrollAmount = 0.0f;
    private int totalContentHeight = 0;

    // --- 样式常量 ---
    private static final int BUBBLE_PADDING_X = 8;
    private static final int BUBBLE_PADDING_Y = 6;
    private static final int ENTRY_SPACING = 10;
    private static final float MAX_BUBBLE_WIDTH_RATIO = 0.75f;

    private boolean needScrollToBottom = true;

    private final List<AbstractWidget> choiceButtons = new ArrayList<>();

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
        this.choiceButtons.clear();

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

        // 根据当前对话的最后一条消息，生成选项按钮（如果是 CHOICE 类型）
        addChoiceButtonsForCurrentConversation();
    }

    private void reloadThreadList() {
        List<TalkThread> threads = ClientTalkState.get().getThreads().stream()
                .sorted(Comparator.comparingLong(TalkThread::getLastActivityTime).reversed())
                .toList();

        this.threadList.setThreads(threads);

        // 用 id 恢复选中状态，避免 selectedThread 指向旧对象
        TalkThread newSelected = null;
        if (selectedThreadId != null) {
            for (TalkThread t : threads) {
                if (selectedThreadId.equals(t.getId())) {
                    newSelected = t;
                    break;
                }
            }
        }

        // 如果还没有选中的聊天串，默认选第一个
        if (newSelected == null && !threads.isEmpty()) {
            newSelected = threads.getFirst();
        }

        if (this.selectedThread != newSelected) {
            this.selectedThread = newSelected;
            this.selectedThreadId = newSelected != null ? newSelected.getId() : null;
            this.needScrollToBottom = true;
        }

        // 同步左侧列表的选中视觉状态
        if (this.selectedThread != null) {
            var children = threadList.children();
            for (var entry : children) {
                if (entry.getThread().getId().equals(this.selectedThread.getId())) {
                    threadList.setSelected(entry);
                    break;
                }
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
        int startY = this.height - 55;
        int listRight = this.threadList.getX() + this.threadList.getWidth();
        int availableWidth = this.width - listRight;
        int centerX = listRight + availableWidth / 2;


        for (int i = 0; i < choices.size(); i++) {
            TalkMessage.Choice c = choices.get(choices.size() - 1 - i);
            int cy = startY - (choiceHeight + spacing) * (i + 1);

            Button btn = Button.builder(
                            Component.literal(ClientTalkUtils.processText(c.getText())),
                            b -> onChoiceClicked(c)
                    )
                    .bounds(centerX - choiceWidth / 2, cy, choiceWidth, choiceHeight)
                    .build();

            this.addRenderableWidget(btn);
            this.choiceButtons.add(btn);
        }
    }

    private int getChatBottomY() {
        int defaultBottom = this.height - 60; // 默认底部（只有关闭按钮时）

        TalkMessage last = getLastMessageOfSelected();
        if (last != null && last.getType() == TalkMessage.Type.CHOICE) {
            int choiceCount = last.getChoices().size();
            if (choiceCount > 0) {
                int buttonAreaHeight = choiceCount * 25;

                int buttonsTop = (this.height - 55) - buttonAreaHeight;
                return Math.min(defaultBottom, buttonsTop - 5);
            }
        }
        return defaultBottom;
    }

    // 工具方法：取当前选中聊天串的最后一条消息
    private TalkMessage getLastMessageOfSelected() {
        if (selectedThread == null) return null;
        return selectedThread.getCurrentMessage();
    }

    public void onThreadSelected(TalkThread thread) {
        setSelectedThread(thread);
    }

    // 选项按钮点击：根据 nextConversationId 跳转到新的对话脚本，同时创建/更新对应聊天串
    private void onChoiceClicked(TalkMessage.Choice choice) {
        if (this.selectedThread == null) {
            return;
        }

        String threadId = this.selectedThread.getId();
        String choiceId = choice.getId();

        ClientPayloadSender.sendSelectChoice(threadId, choiceId);
    }

    public void onThreadsSynced() {
        this.needScrollToBottom = true;
        this.reloadThreadList();
        this.rebuildUI();
    }

    public void setSelectedThread(TalkThread thread) {
        if (this.selectedThread != thread) {
            this.selectedThread = thread;
            this.selectedThreadId = thread != null ? thread.getId() : null;
            this.needScrollToBottom = true;
        }
        rebuildUI();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 如果鼠标在右侧区域，则允许滚动
        if (mouseX > this.threadList.getX() + this.threadList.getWidth()) {
            this.scrollAmount = (float) (this.scrollAmount - scrollY * 20); // 每次滚动 20 像素
            this.clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void clampScroll() {
        int listTop = 20;
        int listBottom = getChatBottomY();
        int viewHeight = listBottom - listTop;

        // 只有内容高度超过视口高度才允许滚动
        int maxScroll = Math.max(0, this.totalContentHeight - viewHeight);
        this.scrollAmount = Mth.clamp(this.scrollAmount, 0, maxScroll);
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        if (this.selectedThread != null) {
            boolean isFinished = ClientTalkUtils.isThreadFinished(this.selectedThread);

            // 遍历所有选项按钮，设置它们的可见性
            for (AbstractWidget btn : this.choiceButtons) {
                btn.visible = isFinished;
            }
        }

        this.renderBackground(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);

        if (selectedThread != null) {
            renderChatArea(gfx, mouseX, mouseY);
        } else {
            // 没有任何聊天串时的提示
            gfx.drawString(
                    this.font,
                    Component.translatable("gui.brntalk.no_conversation").getString(),
                    this.threadList.getX() + this.threadList.getWidth() + 10,
                    this.height / 2,
                    0xFFFFFF
            );
        }
    }

    private void renderChatArea(GuiGraphics gfx, int mouseX, int mouseY) {
        int listRight = this.threadList.getX() + this.threadList.getWidth();
        int areaLeft = listRight + 10; // 文字左边距
        int areaRight = this.width - 25;  // 文字右边距
        int areaWidth = areaRight - areaLeft;

        int areaTop = 20;
        int areaBottom = getChatBottomY();
        int viewHeight = areaBottom - areaTop;

        // 1. 在渲染内容之前，检查当前是否处于"底部状态"
        // 使用上一帧计算出的 totalContentHeight
        float maxScrollPre = Math.max(0, this.totalContentHeight - viewHeight);
        // 如果当前滚动位置接近最大值(允许 5 像素误差)，或者有强制置底信号，则认为需要"粘"在底部
        boolean isAtBottom = (this.scrollAmount >= maxScrollPre - 5) || this.needScrollToBottom;

        if (areaRight - areaLeft < 10 || areaBottom - areaTop < 10) return;

        // 2. 开启裁剪 (Scissor Test)，只在指定矩形内绘制，防止溢出
        gfx.enableScissor(areaLeft, areaTop, areaRight, areaBottom);
        gfx.pose().pushPose();

        try {
            // 应用滚动偏移
            gfx.pose().translate(0, -this.scrollAmount, 0);

            List<TalkMessage> msgs = selectedThread.getMessages();
            int currentY = areaTop;

            int lineHeight = this.font.lineHeight;

            boolean isThreadFinished = ClientTalkUtils.isThreadFinished(selectedThread);

            long now = System.currentTimeMillis();
            long previousVisualEndTime = 0;
            int charDelay = ClientTalkUtils.CHAR_DELAY_MS;
            int msgPause = ClientTalkUtils.MSG_PAUSE_MS;

            int maxBubbleWidth = (int) (areaWidth * MAX_BUBBLE_WIDTH_RATIO);

            for (TalkMessage msg : msgs) {
                // 1. 预处理文本（替换占位符、颜色等），因为长度会变，所以要先处理
                String rawText = ClientTalkUtils.processText(msg.getText());
                String speakerName = ClientTalkUtils.processText(msg.getSpeaker());

                String textToShow;
                if (isThreadFinished) {
                    // 快速路径：如果对话已结束，直接显示全文
                    textToShow = rawText;
                } else {
                    // 慢速路径：动态计算打字机效果
                    String cleanText = ClientTalkUtils.stripColor(rawText).replace("\n", "");
                    long typingDuration = (long) cleanText.length() * charDelay;

                    long visualStartTime;
                    if (msg.getTimestamp() == 0) {
                        visualStartTime = 0;
                        previousVisualEndTime = 0;
                    } else {
                        visualStartTime = Math.max(msg.getTimestamp(), previousVisualEndTime + msgPause);
                        previousVisualEndTime = visualStartTime + typingDuration;
                    }

                    long timePassed = now - visualStartTime;

                    if (timePassed < 0) {
                        // 还没轮到这条消息，跳过渲染
                        continue;
                    } else if (timePassed >= typingDuration) {
                        // 播完了
                        textToShow = rawText;
                    } else {
                        // 正在打字
                        int charCount = (int) (timePassed / ClientTalkUtils.CHAR_DELAY_MS);
                        // 防溢出保护
                        int safeLen = rawText.length();
                        charCount = Math.max(0, Math.min(charCount, safeLen));
                        textToShow = rawText.substring(0, charCount);
                    }
                }

                Component fullTextComp = Component.literal(rawText);
                List<FormattedCharSequence> allLines = this.font.split(fullTextComp, maxBubbleWidth - (2 * BUBBLE_PADDING_X));
                int contentWidth = 0;
                for (FormattedCharSequence seq : allLines) {
                    int w = this.font.width(seq);
                    if (w > contentWidth) contentWidth = w;
                }
                int contentHeight = allLines.size() * this.font.lineHeight;

                // 气泡最终尺寸
                int bubbleWidth = contentWidth + (2 * BUBBLE_PADDING_X);
                int bubbleHeight = contentHeight + (2 * BUBBLE_PADDING_Y);

                boolean isPlayer = (msg.getSpeakerType() == TalkMessage.SpeakerType.PLAYER);

                int bubbleX;
                if (isPlayer) {
                    // 玩家气泡靠右对齐
                    bubbleX = areaRight - bubbleWidth;
                } else {
                    // NPC气泡靠左对齐
                    bubbleX = areaLeft;
                }

                // --- 渲染说话人 ---
                Component speakerComp = Component.literal(speakerName);
                int nameWidth = this.font.width(speakerName);
                int nameX;
                if (isPlayer) {
                    // 玩家名字靠右
                    nameX = areaRight - nameWidth;
                } else {
                    // NPC名字靠左
                    nameX = areaLeft;
                }

                gfx.drawString(this.font, speakerComp, nameX, currentY, 0xFFFFAA00);
                currentY += lineHeight + 2;

                // --- 绘制气泡背景 ---
                // 玩家用深绿色半透明，NPC用深灰色半透明
                int bubbleColor = isPlayer ? 0xD0004000 : 0xD0333333;
                int borderColor = isPlayer ? 0xFF008000 : 0xFF666666;

                // 填充
                gfx.fill(bubbleX, currentY, bubbleX + bubbleWidth, currentY + bubbleHeight, bubbleColor);
                // 边框 (上下左右四条线)
                gfx.fill(bubbleX, currentY, bubbleX + bubbleWidth, currentY + 1, borderColor); // Top
                gfx.fill(bubbleX, currentY + bubbleHeight - 1, bubbleX + bubbleWidth, currentY + bubbleHeight, borderColor); // Bottom
                gfx.fill(bubbleX, currentY, bubbleX + 1, currentY + bubbleHeight, borderColor); // Left
                gfx.fill(bubbleX + bubbleWidth - 1, currentY, bubbleX + bubbleWidth, currentY + bubbleHeight, borderColor); // Right

                // --- 渲染正文 ---
                Component textComp = Component.literal(textToShow);
                List<FormattedCharSequence> lines = this.font.split(textComp, maxBubbleWidth - (2 * BUBBLE_PADDING_X));

                int textY = currentY + BUBBLE_PADDING_Y;
                int textX = bubbleX + BUBBLE_PADDING_X;

                for (FormattedCharSequence line : lines) {
                    if (textY + lineHeight <= currentY + bubbleHeight) {
                        gfx.drawString(this.font, line, textX, textY, 0xFFFFFFFF, false);
                        textY += lineHeight;
                    }
                }

                currentY += bubbleHeight + ENTRY_SPACING;
            }

            // 计算总高度，用于滚动条逻辑
            this.totalContentHeight = currentY - areaTop;

        } catch (Exception e) {
            // 捕获异常，防止渲染崩溃导致 Scissor 状态锁死
            e.printStackTrace();
        } finally {
            // 恢复矩阵和关闭裁剪
            gfx.pose().popPose();
            gfx.disableScissor();
        }

        // 3. 渲染结束后，应用粘性滚动
        float maxScrollPost = Math.max(0, this.totalContentHeight - viewHeight);

        if (isAtBottom) {
            // 如果渲染前就在底部（或强制要求），那么渲染后我们将滚动条更新到新的底部
            this.scrollAmount = maxScrollPost;
            // 消耗掉强制信号
            this.needScrollToBottom = false;
        }

        // 4. 绘制滚动条
        if (this.totalContentHeight > viewHeight) {
            int scrollbarX = areaRight + 8;
            int scrollbarWidth = 2;

            // 计算滑块高度和位置
            float ratio = (float) viewHeight / this.totalContentHeight;
            int barHeight = Math.max(10, (int) (viewHeight * ratio));

            int barTop = areaTop;
            if (maxScrollPost > 0) {
                barTop += (int) ((this.scrollAmount / maxScrollPost) * (viewHeight - barHeight));
            }

            gfx.fill(scrollbarX, areaTop, scrollbarX + scrollbarWidth, areaBottom, 0x20FFFFFF); // 轨道
            gfx.fill(scrollbarX, barTop, scrollbarX + scrollbarWidth, barTop + barHeight, 0xFFCCCCCC); // 滑块
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
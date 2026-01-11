package yourscraft.jasdewstarfield.brntalk.client.ui;

import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;
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
import java.util.*;

import static yourscraft.jasdewstarfield.brntalk.client.ui.TalkUIStyles.*;

public class TalkScreen extends Screen {

    private TalkThreadList threadList;
    private TalkThread selectedThread;
    private ChatWidget chatWidget;

    @Nullable
    private String selectedThreadId;

    // --- 滚动与动画控制变量 ---
    private int totalContentHeight = 0;
    private boolean needScrollToBottom = true;

    private final List<AbstractWidget> choiceButtons = new ArrayList<>();
    private final Map<String, MessageRenderCache> renderCacheMap = new HashMap<>();
    private final Map<String, Long> messageStartTimeCache = new HashMap<>();
    private int cachedMessageCount = -1; // 用于检测是否需要刷新缓存

    private int winX, winY, winW, winH;
    private int innerX, innerY, innerW, innerH;
    private int listAreaX, listAreaW;
    private int dividerX;
    private int chatAreaX, chatAreaW;

    private Button closeButton;

    public TalkScreen() {
        super(Component.literal("BRNTalk"));
    }

    @Override
    protected void init() {
        super.init();
        this.renderCacheMap.clear();

        // 1. 初始化布局坐标
        this.winX = WIN_MARGIN_X;
        this.winY = WIN_MARGIN_Y;
        this.winW = this.width - (WIN_MARGIN_X * 2);
        this.winH = this.height - (WIN_MARGIN_Y * 2);

        this.innerX = winX + FRAME_BORDER_W;
        this.innerY = winY + FRAME_BORDER_H;
        this.innerW = winW - (FRAME_BORDER_W * 2);
        this.innerH = winH - (FRAME_BORDER_H * 2);

        // 左侧列表区域
        this.listAreaX = innerX;
        this.listAreaW = LEFT_AREA_WIDTH; // 使用常量

        // 分割线
        this.dividerX = innerX + listAreaW;

        // 右侧聊天区域
        this.chatAreaX = dividerX + DIVIDER_WIDTH;
        this.chatAreaW = innerW - listAreaW - DIVIDER_WIDTH;

        this.chatWidget = new ChatWidget(chatAreaX, innerY, chatAreaW, innerH);



        rebuildUI();
    }

    private void rebuildUI() {
        // 清理
        cachedMessageCount = -1;
        this.clearWidgets();
        this.choiceButtons.clear();

        // 列表
        int listPadding = 2;
        double listScroll = (this.threadList != null) ? this.threadList.getScrollAmount() : 0;
        this.threadList = new TalkThreadList(
                this,
                Minecraft.getInstance(),
                listAreaX + listPadding,
                innerY + listPadding,
                listAreaW - (listPadding * 2),
                innerH - (listPadding * 2)
        );
        this.addRenderableWidget(this.threadList);
        // 更新左侧列表内容
        reloadThreadList();
        // 恢复位置
        this.threadList.restoreScroll(listScroll);

        // 根据当前对话的最后一条消息，生成选项按钮（如果是 CHOICE 类型）
        addChoiceButtonsForCurrentConversation();

        // 在创建 ChatWidget 之前，立即预计算正确的内容高度
        if (this.selectedThread != null) {
            int maxBubbleWidth = (int) (this.chatAreaW * MAX_BUBBLE_WIDTH_RATIO);
            int textMaxWidth = maxBubbleWidth - (2 * BUBBLE_PADDING_X);
            // 提前计算高度
            this.totalContentHeight = calculateTotalHeight(this.selectedThread.getMessages(), textMaxWidth);
        } else {
            this.totalContentHeight = 0;
        }

        // ChatWidget
        double chatScroll = (this.chatWidget != null) ? this.chatWidget.getTargetScroll() : 0;
        int chatWidgetHeight = getChatViewHeight();
        this.chatWidget = new ChatWidget(chatAreaX, innerY, chatAreaW, chatWidgetHeight);

        if (this.needScrollToBottom) {
            this.chatWidget.scrollToBottom();
            this.needScrollToBottom = false;
        } else {
            this.chatWidget.restoreScroll(chatScroll);
        }

        this.addRenderableWidget(this.chatWidget);

        // 退出按钮
        int closeBtnSize = 16;
        int closeX = this.width - closeBtnSize - 7;
        int closeY = 6;

        this.closeButton = Button.builder(Component.literal("×"), btn -> this.onClose())
                .bounds(closeX, closeY, closeBtnSize, closeBtnSize)
                .build();

        this.addWidget(this.closeButton);
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
        if (last == null || !hasChoice()) {
            return;
        }

        List<TalkMessage.Choice> choices = last.getChoices();
        if (choices.isEmpty()) return;

        int choiceWidth = 140;
        int choiceHeight = 20;
        int spacing = 5;
        int startY = this.height - 10;
        int centerX = chatAreaX + chatAreaW / 2;


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

    private int getChatViewHeight() {
        int defaultHeight = this.innerH;

        TalkMessage last = getLastMessageOfSelected();
        if (last != null && hasChoice()) {
            int choiceCount = last.getChoices().size();
            if (ClientTalkUtils.isThreadFinished(selectedThread)) {
                if (choiceCount > 0) {
                    int buttonAreaHeight = choiceCount * 25;
                    return Math.max(10, defaultHeight - buttonAreaHeight);
                }
            }
        }
        return defaultHeight;
    }

    // 工具方法：判定当前是否有选项按钮
    private boolean hasChoice() {
        TalkMessage last = getLastMessageOfSelected();
        if (last == null) return false;
        return last.getType() == TalkMessage.Type.CHOICE;
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
        cachedMessageCount = -1;
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

    // ----- 渲染 -----

    @Override
    public void renderBackground(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // 让父类先渲染背景模糊和变暗等
        super.renderBackground(gfx, mouseX, mouseY, partialTick);

        if (BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            // 1. 左侧列表背景（交由 TalkThreadList 自行实现）

            // 2. 右侧聊天区域背景
            if (chatAreaW > 0) {
                // 绘制一个深色矩形作为聊天背景
                gfx.fill(chatAreaX, innerY, chatAreaX + chatAreaW, innerY + innerH, COLOR_VANILLA_BG);
            }
        } else {
            // 1. 左背景
            if (listAreaW > 0) {
                ClientTalkUtils.drawRepeatedTexture(gfx, TEX_BG_LEFT,
                        innerX, innerY, listAreaW, innerH, 16, 16);
            }
            // 2. 右背景
            if (chatAreaW > 0) {
                ClientTalkUtils.drawRepeatedTexture(gfx, TEX_BG_RIGHT,
                        chatAreaX, innerY, chatAreaW, innerH, 16, 16);
            }
            // 3. 分割线
            ClientTalkUtils.drawRepeatedTexture(gfx, TEX_DIVIDER,
                    dividerX, innerY, DIVIDER_WIDTH, innerH, 9, 16);
            // 4. 外框
            ClientTalkUtils.drawTextureFrame(gfx, TEX_FRAME,
                    winX, winY, winW, winH,
                    FRAME_BORDER_W, FRAME_BORDER_H,
                    FRAME_W, FRAME_H);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        if (this.selectedThread != null) {
            boolean isFinished = ClientTalkUtils.isThreadFinished(this.selectedThread);

            // 遍历所有选项按钮，设置它们的可见性
            for (AbstractWidget btn : this.choiceButtons) {
                btn.visible = isFinished;
            }

            if (isFinished && ClientTalkState.get().hasUnread(this.selectedThread)) {
                ClientPayloadSender.sendMarkRead(this.selectedThread.getId());
                this.selectedThread.setLastReadTime(System.currentTimeMillis());
            }
        }

        // 绘制背景和 Widgets (包含 ChatWidget 和 ThreadList)
        super.render(gfx, mouseX, mouseY, partialTick);

        // 如果有选项，渲染一个分界线
        if (hasChoice()) {
            int buttonTopY = this.innerY + getChatViewHeight();
            gfx.fill(
                    chatAreaX + 5,
                    buttonTopY,
                    chatAreaX + chatAreaW - 5,
                    buttonTopY + 1,
                    COLOR_DIVISION
            );
        }

        if (selectedThread == null) {
            gfx.drawString(
                    this.font,
                    Component.translatable("gui.brntalk.no_conversation").getString(),
                    chatAreaX + 10,
                    this.height / 2,
                    COLOR_NO_MSG_TEXT
            );
        }

        // 5. 装饰层，在所有组件之上
        if (!BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 100);

            ClientTalkUtils.drawTextureFrame(gfx, TEX_DECO,
                    0, 0, this.width, this.height,
                    DECO_BORDER_W, DECO_BORDER_H,
                    DECO_W, DECO_H);

            gfx.pose().popPose();
        }

        // 6. 手动绘制关闭按钮
        if (this.closeButton != null) {
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 110);

            this.closeButton.render(gfx, mouseX, mouseY, partialTick);

            gfx.pose().popPose();
        }
    }

    public void renderChatContents(GuiGraphics gfx, int x, int yOffset, int width) {
        if (selectedThread == null) return;

        // 1. 在这一帧渲染开始前，先算出正确的内容总高度
        int maxBubbleWidth = (int) (width * MAX_BUBBLE_WIDTH_RATIO);
        int textMaxWidth = maxBubbleWidth - (2 * BUBBLE_PADDING_X);

        double currentScroll = this.chatWidget.getScrollAmountVal();
        boolean wasAtBottom = this.chatWidget.isScrolledToBottom(1.0);
        int oldContentHeight = this.totalContentHeight;

        this.totalContentHeight = calculateTotalHeight(selectedThread.getMessages(), textMaxWidth);

        if (this.needScrollToBottom) {
            this.chatWidget.scrollToBottom();
            this.needScrollToBottom = false;
            currentScroll = this.chatWidget.getScrollAmountVal();
        } else if (this.totalContentHeight > oldContentHeight && wasAtBottom) {
            // 如果之前在底部，且高度因为打字机增加了，强制吸附到底部
            this.chatWidget.scrollToBottom();
            currentScroll = this.chatWidget.getScrollAmountVal();
        }

        List<TalkMessage> msgs = selectedThread.getMessages();
        int currentY = yOffset;
        int lineHeight = this.font.lineHeight;

        long now = System.currentTimeMillis();

        long previousVisualEndTime = 0;
        int charDelay = ClientTalkUtils.getCharDelay();
        int msgPause = ClientTalkUtils.getMsgPause();

        int widgetScreenY = this.chatWidget.getY();
        int widgetHeight = this.chatWidget.getHeight();

        for (TalkMessage msg : msgs) {
            // 1. 获取或创建缓存
            MessageRenderCache cache = renderCacheMap.computeIfAbsent(msg.getId(), k -> new MessageRenderCache());

            // 2. 检查缓存是否过期（例如：首次加载、或宽度变化导致需要重新折行）
            cache.updateLayoutIfNeeded(msg, textMaxWidth, this.font);

            // 3. 计算时间轴
            long visualStartTime;
            if (msg.getTimestamp() == 0) {
                visualStartTime = 0;
                previousVisualEndTime = 0;
            } else {
                visualStartTime = Math.max(msg.getTimestamp(), previousVisualEndTime + msgPause);
                previousVisualEndTime = visualStartTime + cache.duration;
            }

            // 4. 判断当前消息的打字机进度
            String textToShow;
            long timePassed = now - visualStartTime;

            String fullText = cache.layoutCache.processedText;

            if (timePassed < 0) {
                // 还没轮到，直接跳过
                continue;
            } else if (timePassed >= cache.duration) {
                // 播完了，直接用缓存的完整文本
                textToShow = fullText;
            } else {
                int charCount = (int) (timePassed / charDelay);
                charCount = Math.max(0, Math.min(charCount, fullText.length()));
                textToShow = fullText.substring(0, charCount);
            }

            // 5. 获取行 (如果是动态的，需要临时计算；如果是完整的，使用缓存)
            List<FormattedCharSequence> linesToDraw;
            if (textToShow.length() == fullText.length()) {
                linesToDraw = cache.layoutCache.getLines(this.font, msg, textMaxWidth);
            } else {
                linesToDraw = this.font.split(Component.literal(textToShow), textMaxWidth);
            }

            // 气泡最终尺寸
            int contentH = linesToDraw.size() * lineHeight;
            int bubbleW = 0;
            for (FormattedCharSequence seq : linesToDraw) {
                int w = this.font.width(seq);
                if (w > bubbleW) bubbleW = w;
            }
            bubbleW += (2 * BUBBLE_PADDING_X);
            int bubbleH = contentH + (2 * BUBBLE_PADDING_Y);

            boolean isPlayer = (msg.getSpeakerType() == TalkMessage.SpeakerType.PLAYER);
            int bubbleX = x + (isPlayer ? (width - bubbleW - 10) : 10);

            int entryTotalHeight = lineHeight + 2 + bubbleH;

            // 块级Culling
            // 如果 底部 < 0 或者 顶部 > Widget 高度，则不画
            if (currentY + entryTotalHeight < currentScroll || currentY > currentScroll + widgetHeight) {
                currentY += entryTotalHeight + MSG_SPACING;
                continue;
            }

            // --- 渲染说话人 ---
            int nameX = x + (isPlayer ? (width - cache.speakerNameWidth - 10) : 10);
            int nameColor = isPlayer ? COLOR_PLAYER_NAME : COLOR_NPC_NAME;
            int drawY = widgetScreenY + currentY;
            gfx.drawString(this.font, cache.speakerComp, nameX, drawY, nameColor);
            drawY += lineHeight + 2;

            // --- 绘制气泡背景 ---
            int bgColor = isPlayer ? COLOR_PLAYER_BUBBLE_BG : COLOR_NPC_BUBBLE_BG;
            int borderColor = isPlayer ? COLOR_PLAYER_BUBBLE_BORDER : COLOR_NPC_BUBBLE_BORDER;

            // 填充
            gfx.fill(bubbleX, drawY, bubbleX + bubbleW, drawY + bubbleH, bgColor);
            // 简单的四边框绘制
            gfx.fill(bubbleX, drawY, bubbleX + bubbleW, drawY + 1, borderColor);
            gfx.fill(bubbleX, drawY + bubbleH - 1, bubbleX + bubbleW, drawY + bubbleH, borderColor);
            gfx.fill(bubbleX, drawY, bubbleX + 1, drawY + bubbleH, borderColor);
            gfx.fill(bubbleX + bubbleW - 1, drawY, bubbleX + bubbleW, drawY + bubbleH, borderColor);

            // --- 渲染正文 ---
            int textY = drawY + BUBBLE_PADDING_Y;
            int textX = bubbleX + BUBBLE_PADDING_X;

            for (FormattedCharSequence line : linesToDraw) {
                gfx.drawString(this.font, line, textX, textY, COLOR_TEXT_NORMAL, false);
                textY += lineHeight;
            }

            currentY += entryTotalHeight + MSG_SPACING;
        }
    }

    private int calculateTotalHeight(List<TalkMessage> msgs, int textMaxWidth) {
        updateTimelineCache(msgs);

        int currentTotal = 0;
        int lineHeight = this.font.lineHeight;
        long now = System.currentTimeMillis();
        int charDelay = ClientTalkUtils.getCharDelay();

        for (TalkMessage msg : msgs) {
            String msgId = msg.getId();

            Long visualStartTimeObj = messageStartTimeCache.get(msgId);
            long visualStartTime = (visualStartTimeObj != null) ? visualStartTimeObj : now + 1;

            // 如果还没开始显示，后面的肯定也没开始，直接跳出循环
            if (now < visualStartTime) break;

            // 获取渲染缓存
            MessageRenderCache cache = renderCacheMap.computeIfAbsent(msg.getId(), k -> new MessageRenderCache());
            cache.updateLayoutIfNeeded(msg, textMaxWidth, this.font);

            // 计算时间轴 (同渲染逻辑)
            long timePassed = now - visualStartTime;

            // 情况 A: 已播完
            if (timePassed >= cache.duration) {
                currentTotal += cache.fixedHeight;
            } else {    // 情况 B: 正在打字
                int charCount = (int) (timePassed / charDelay);
                String fullText = cache.layoutCache.processedText;
                charCount = Math.max(0, Math.min(charCount, fullText.length()));
                String textToShow = fullText.substring(0, charCount);

                int lines = this.font.split(Component.literal(textToShow), textMaxWidth).size();
                int bubbleH = (lines * lineHeight) + (2 * BUBBLE_PADDING_Y);
                int entryHeight = (lineHeight + 2) + bubbleH + MSG_SPACING;

                currentTotal += entryHeight;
            }
        }
        return currentTotal;
    }

    private void updateTimelineCache(List<TalkMessage> msgs) {
        if (msgs.size() == cachedMessageCount) return;

        int msgPause = ClientTalkUtils.getMsgPause();

        // 如果是清空了或者从头开始，清理缓存
        if (msgs.isEmpty()) {
            messageStartTimeCache.clear();
            cachedMessageCount = 0;
            return;
        }

        long previousVisualEndTime = 0;

        for (TalkMessage msg : msgs) {
            String id = msg.getId();

            // 计算开始时间
            long visualStartTime;
            if (msg.getTimestamp() == 0) {
                visualStartTime = 0;
                previousVisualEndTime = 0;
            } else {
                visualStartTime = Math.max(msg.getTimestamp(), previousVisualEndTime + msgPause);
                long duration = ClientTalkUtils.calculateDuration(msg);
                previousVisualEndTime = visualStartTime + duration;
            }

            messageStartTimeCache.put(id, visualStartTime);
        }

        cachedMessageCount = msgs.size();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // W 或 上箭头
        if (keyCode == 87 || keyCode == 265) {
            if (this.chatWidget != null) this.chatWidget.scrollBy(-30); // 向上滚
            return true;
        }
        // S 或 下箭头
        if (keyCode == 83 || keyCode == 264) {
            if (this.chatWidget != null) this.chatWidget.scrollBy(30);  // 向下滚
            return true;
        }
        // 先让父类处理 (比如 ESC 关闭)
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        // 检查是否按下了物品栏键 (默认为 E)
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        // 返回 false：打开对话 UI 时，游戏不会暂停
        return false;
    }

    @Override
    public void onClose() {
        // 关闭界面时清理缓存，回到游戏
        ClientTalkUtils.clearCache();
        this.renderCacheMap.clear();
        messageStartTimeCache.clear();
        cachedMessageCount = -1;
        super.onClose();
    }

    // 内部类：ChatWidget
    class ChatWidget extends AbstractScrollWidget {

        private double targetScroll = 0;
        private boolean isSmoothScrolling = false; // 标记是否正在进行平滑滚动

        public ChatWidget(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty());
        }

        // --- 平滑滚动逻辑 ---

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            // 滚轮事件：只更新目标值，不直接修改 scrollAmount
            if (!this.visible) return false;

            this.targetScroll -= scrollY * this.scrollRate();
            this.targetScroll = Mth.clamp(this.targetScroll, 0, this.getMaxScrollAmount());
            this.isSmoothScrolling = true;
            return true;
        }

        public boolean isScrolledToBottom(double tolerance) {
            return (this.getMaxScrollAmount() - this.scrollAmount()) <= tolerance;
        }

        public double getScrollAmountVal() {
            return this.scrollAmount();
        }

        @Override
        protected void setScrollAmount(double amount) {
            // 拖拽滚动条或键盘控制：直接更新，取消平滑动画
            super.setScrollAmount(amount);
            this.targetScroll = amount;
            this.isSmoothScrolling = false;
        }

        public void scrollBy(double amount) {
            double newVal = this.scrollAmount() + amount;
            this.setScrollAmount(newVal);
            // 同时也更新平滑滚动的目标值，防止冲突
            this.targetScroll = newVal;
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // 每一帧渲染前，计算插值
            if (this.isSmoothScrolling) {
                // 重新计算 maxScroll 防止窗口大小变化导致 target 越界
                this.targetScroll = Mth.clamp(this.targetScroll, 0, this.getMaxScrollAmount());

                double current = this.scrollAmount();
                // 2. 平滑插值
                if (Math.abs(this.targetScroll - current) > 0.1) {
                    double newScroll = current + (this.targetScroll - current) * BrntalkConfig.CLIENT.smoothFactor.get();
                    super.setScrollAmount(newScroll);
                } else {
                    super.setScrollAmount(this.targetScroll);
                }
            }

            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }

        // --- 实现抽象方法 ---

        @Override
        protected int getInnerHeight() {
            return TalkScreen.this.totalContentHeight;
        }

        @Override
        protected double scrollRate() {
            return BrntalkConfig.CLIENT.scrollRate.get(); // 滚动灵敏度
        }

        @Override
        protected void renderContents(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            TalkScreen.this.renderChatContents(gfx, this.getX(), CHAT_CONTENTS_Y_OFFSET, this.width);
        }

        @Override
        protected void renderBackground(@NotNull GuiGraphics guiGraphics) {
        }

        @Override
        protected void renderBorder(@NotNull GuiGraphics guiGraphics, int x, int y, int width, int height) {
        }

        // 供外部调用：强制滚动到底部
        public void scrollToBottom() {
            double max = this.getMaxScrollAmount();
            this.targetScroll = max;
            super.setScrollAmount(max);
        }

        // 供外部调用：恢复滚动位置
        public void restoreScroll(double val) {
            this.targetScroll = val;
            super.setScrollAmount(val);
        }

        public double getTargetScroll() {
            return targetScroll;
        }
    }

    private static class MessageRenderCache {
        // 委托给通用的 LayoutCache
        final ClientTalkUtils.MessageLayoutCache layoutCache = new ClientTalkUtils.MessageLayoutCache();

        // 屏幕特有的缓存数据 (气泡高度、名字渲染)
        Component speakerComp;
        int speakerNameWidth;
        int fixedHeight = -1;
        long duration = -1;
        private int cachedLayoutWidth = -1;

        void updateLayoutIfNeeded(TalkMessage msg, int widthLimit, net.minecraft.client.gui.Font font) {
            // 如果宽度没变且已经初始化过，无需更新
            if (cachedLayoutWidth == widthLimit && speakerComp != null) {
                return;
            }

            cachedLayoutWidth = widthLimit;

            // 1. 让通用 Cache 更新文本和折行
            List<FormattedCharSequence> lines = layoutCache.getLines(font, msg, widthLimit);

            // 2. 更新 Screen 特有的数据
            if (speakerComp == null) {
                String speakerName = ClientTalkUtils.processText(msg.getSpeaker());
                this.speakerComp = Component.literal(speakerName);
                this.speakerNameWidth = font.width(speakerName);
                this.duration = ClientTalkUtils.calculateDuration(msg);
            }

            int lineHeight = font.lineHeight;
            int bubbleH = (lines.size() * lineHeight) + (2 * BUBBLE_PADDING_Y);
            this.fixedHeight = (lineHeight + 2) + bubbleH + MSG_SPACING;
        }
    }
}
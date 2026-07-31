package yourscraft.jasdewstarfield.brntalk.client.ui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.client.ClientTalkState;
import yourscraft.jasdewstarfield.brntalk.client.render.TalkRenderUtils;
import yourscraft.jasdewstarfield.brntalk.client.text.ClientTextFormatter;
import yourscraft.jasdewstarfield.brntalk.client.timeline.TalkTimeline;
import yourscraft.jasdewstarfield.brntalk.client.ui.chat.TalkChatContentRenderer;
import yourscraft.jasdewstarfield.brntalk.client.ui.chat.TalkChatWidget;
import yourscraft.jasdewstarfield.brntalk.client.ui.layout.TalkScreenLayout;
import yourscraft.jasdewstarfield.brntalk.client.ui.scroll.TalkScrollMath;
import yourscraft.jasdewstarfield.brntalk.client.ui.button.ChainBoxButton;
import yourscraft.jasdewstarfield.brntalk.client.ui.button.CloseButton;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.platform.TalkNetworking;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.*;

import static yourscraft.jasdewstarfield.brntalk.client.ui.TalkUIStyles.*;

public class TalkScreen extends Screen {

    private static final int LIST_PADDING = 2;

    private TalkThreadList threadList;
    private TalkThread selectedThread;
    private TalkChatWidget chatWidget;

    // --- 滚动与动画控制变量 ---
    private int totalContentHeight = 0;
    private boolean needScrollToBottom = true;
    private boolean choiceControlsVisible = false;

    private final List<AbstractWidget> choiceButtons = new ArrayList<>();
    private final TalkChatContentRenderer chatContentRenderer = new TalkChatContentRenderer();
    private final Map<String, Long> pendingReadActivityTimes = new HashMap<>();
    private final ClientTalkState.StateListener stateListener = this::onTalkStateChanged;
    private boolean stateListenerRegistered = false;

    private int winX, winY, winW, winH;
    private int innerX, innerY, innerW, innerH;
    private int listAreaX, listAreaW;
    private int threadListX, threadListY, threadListW, threadListH;
    private int dividerX;
    private int chatAreaX, chatAreaW;

    private Button closeButton;
    private ChainBoxButton chainBoxButton;

    private final long openStartTime;
    private static final long ANIMATION_DURATION = 350L;

    public TalkScreen() {
        super(Component.literal("BRNTalk"));
        this.openStartTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        super.init();
        if (!stateListenerRegistered) {
            // Screen 只订阅通用状态事件，不再由状态层反向识别具体 UI 类型。
            ClientTalkState.get().addListener(stateListener);
            stateListenerRegistered = true;
        }
        clearRenderCache();

        TalkScreenLayout layout = TalkScreenLayout.calculate(this.width, this.height);
        this.winX = layout.windowX();
        this.winY = layout.windowY();
        this.winW = layout.windowWidth();
        this.winH = layout.windowHeight();
        this.innerX = layout.innerX();
        this.innerY = layout.innerY();
        this.innerW = layout.innerWidth();
        this.innerH = layout.innerHeight();
        this.listAreaX = layout.listAreaX();
        this.listAreaW = layout.listAreaWidth();
        this.dividerX = layout.dividerX();
        this.chatAreaX = layout.chatAreaX();
        this.chatAreaW = layout.chatAreaWidth();

        this.chatWidget = new TalkChatWidget(
                chatAreaX,
                innerY,
                chatAreaW,
                innerH,
                () -> this.totalContentHeight,
                this::renderChatContents
        );

        rebuildUI();
    }

    private void rebuildUI() {
        // 清理
        chatContentRenderer.invalidateTimeline();
        this.clearWidgets();
        this.choiceButtons.clear();

        // 锁链盒，功能待定
        if (!BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            int chainBoxX = this.winX + 1;
            int chainBoxY = this.winY;

            this.chainBoxButton = new ChainBoxButton(chainBoxX, chainBoxY, button -> {});
            this.addWidget(this.chainBoxButton);
        }

        // 退出按钮
        int closeBtnSize;
        int closeX;
        int closeY;

        if (BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            closeBtnSize = 16;
            closeX = this.width - closeBtnSize - 7;
            closeY = 6;

            this.closeButton = Button.builder(Component.literal("×"), btn -> this.onClose())
                    .bounds(closeX, closeY, closeBtnSize, closeBtnSize)
                    .build();
        } else {
            closeBtnSize = 28;
            closeX = this.winX + this.winW - closeBtnSize + 3;
            closeY = this.winY - 5;

            this.closeButton = new CloseButton(closeX, closeY, button -> this.onClose());
        }

        this.addWidget(this.closeButton);

        // 列表
        double listScroll = (this.threadList != null) ? this.threadList.getScrollAmount() : 0;
        this.threadListX = listAreaX + LIST_PADDING;
        this.threadListY = innerY + LIST_PADDING;
        this.threadListW = listAreaW - (LIST_PADDING * 2);
        this.threadListH = innerH - (LIST_PADDING * 2);
        this.threadList = new TalkThreadList(
                this,
                Minecraft.getInstance(),
                threadListX,
                threadListY,
                threadListW,
                threadListH
        );
        this.addRenderableWidget(this.threadList);
        // 更新左侧列表内容
        reloadThreadList();
        // 恢复位置
        this.threadList.restoreScroll(listScroll);

        // 选项按钮只有在打字机播放完毕后才显示，也只有此时才为按钮区预留高度。
        this.choiceControlsVisible = shouldShowChoiceControls();

        // 根据当前对话的最后一条消息，生成选项按钮（如果是 CHOICE 类型）
        addChoiceButtonsForCurrentConversation();

        // 在创建 ChatWidget 之前，立即预计算正确的内容高度
        if (this.selectedThread != null) {
            int maxBubbleWidth = (int) (this.chatAreaW * MAX_BUBBLE_WIDTH_RATIO);
            int textMaxWidth = maxBubbleWidth - (2 * BUBBLE_PADDING_X);
            // 提前计算高度
            this.totalContentHeight = chatContentRenderer.calculateScrollableHeight(
                    this.selectedThread,
                    this.font,
                    textMaxWidth
            );
        } else {
            this.totalContentHeight = 0;
        }

        // ChatWidget
        double chatScroll = (this.chatWidget != null) ? this.chatWidget.getTargetScroll() : 0;
        int chatWidgetHeight = getChatViewHeight();
        this.chatWidget = new TalkChatWidget(
                chatAreaX,
                innerY,
                chatAreaW,
                chatWidgetHeight,
                () -> this.totalContentHeight,
                this::renderChatContents
        );

        if (this.needScrollToBottom) {
            this.chatWidget.scrollToBottomImmediately();
            this.needScrollToBottom = false;
        } else {
            this.chatWidget.restoreScroll(chatScroll);
        }

        this.addRenderableWidget(this.chatWidget);
    }

    private void reloadThreadList() {
        ClientTalkState state = ClientTalkState.get();
        List<TalkThread> threads = state.getThreadsByRecentActivity();

        this.threadList.setThreads(threads);

        // 选中身份由状态层维护；全量同步后这里总是取得最新线程对象。
        TalkThread newSelected = state.getSelectedThread();

        updateSelectedThread(newSelected);

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
        int startY = this.innerY + this.innerH;
        int centerX = chatAreaX + chatAreaW / 2;


        for (int i = 0; i < choices.size(); i++) {
            TalkMessage.Choice c = choices.get(choices.size() - 1 - i);
            int cy = startY - (choiceHeight + spacing) * (i + 1);

            Button btn = Button.builder(
                            Component.literal(ClientTextFormatter.process(c.getText())),
                            b -> onChoiceClicked(c)
                    )
                    .bounds(centerX - choiceWidth / 2, cy, choiceWidth, choiceHeight)
                    .build();
            btn.visible = this.choiceControlsVisible;
            btn.active = this.choiceControlsVisible;

            this.addRenderableWidget(btn);
            this.choiceButtons.add(btn);
        }
    }

    private int getChatViewHeight() {
        int defaultHeight = this.innerH;

        TalkMessage last = getLastMessageOfSelected();
        if (last != null && this.choiceControlsVisible) {
            int choiceCount = last.getChoices().size();
            if (choiceCount > 0) {
                int buttonAreaHeight = choiceCount * 25 + 5;
                return Math.max(10, defaultHeight - buttonAreaHeight);
            }
        }
        return defaultHeight;
    }

    private boolean shouldShowChoiceControls() {
        return this.selectedThread != null && hasChoice() && TalkTimeline.isFinished(this.selectedThread);
    }

    private void syncChoiceButtonVisibility() {
        for (AbstractWidget btn : this.choiceButtons) {
            btn.visible = this.choiceControlsVisible;
            btn.active = this.choiceControlsVisible;
        }
    }

    private void updateChoiceControlLayoutIfNeeded() {
        boolean shouldShow = shouldShowChoiceControls();
        if (shouldShow == this.choiceControlsVisible) {
            syncChoiceButtonVisibility();
            return;
        }

        // 打字机播完时才重建布局：既避免提前空白，也让聊天区高度和滚动上限一起更新。
        boolean wasAtBottom = this.chatWidget == null || this.chatWidget.isScrolledToBottom(1.0);
        this.choiceControlsVisible = shouldShow;
        if (wasAtBottom) {
            this.needScrollToBottom = true;
        }
        rebuildUI();
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
        if (thread != null) {
            ClientTalkState.get().selectThread(thread.getId());
        }
    }

    // 选项按钮点击：根据 nextConversationId 跳转到新的对话脚本，同时创建/更新对应聊天串
    private void onChoiceClicked(TalkMessage.Choice choice) {
        if (this.selectedThread == null) {
            return;
        }

        String threadId = this.selectedThread.getId();
        String choiceId = choice.getId();

        TalkNetworking.sendSelectChoice(threadId, choiceId);
    }

    private void onTalkStateChanged(ClientTalkState.StateChange change) {
        chatContentRenderer.invalidateTimeline();
        ClientTalkState state = ClientTalkState.get();
        reconcilePendingReadRequests(state);
        String selectedId = state.getSelectedThreadId();
        boolean affectsSelectedThread = Objects.equals(selectedId, change.threadId());
        boolean selectedThreadWasAtBottom = this.chatWidget == null
                || this.chatWidget.isScrolledToBottom(1.0);
        // 其他线程新增或已读确认只影响列表，不应把当前聊天区拉到底部。
        boolean shouldScrollToBottom = switch (change.type()) {
            case THREADS_REPLACED, SELECTION_CHANGED, CLEARED -> true;
            case THREAD_ADDED -> affectsSelectedThread;
            // 阅读历史消息时保持当前视口；只有原本位于底部才继续跟随新增内容。
            case MESSAGES_APPENDED -> affectsSelectedThread && selectedThreadWasAtBottom;
            case READ_TIME_UPDATED -> false;
        };
        if (shouldScrollToBottom) {
            this.needScrollToBottom = true;
        }
        this.reloadThreadList();
        this.rebuildUI();
    }

    private void reconcilePendingReadRequests(ClientTalkState state) {
        pendingReadActivityTimes.entrySet().removeIf(entry -> {
            TalkThread thread = state.getThread(entry.getKey());
            return thread == null || thread.getLastReadTime() >= entry.getValue();
        });
    }

    private void updateSelectedThread(TalkThread thread) {
        if (this.selectedThread == thread) {
            return;
        }

        // 渲染与时间轴缓存只服务于当前线程；切换线程时必须清空，避免相同消息 ID 互相复用内容。
        clearRenderCache();
        this.selectedThread = thread;
        this.needScrollToBottom = true;
    }

    private void applyThreadListYOffset(int yOffset) {
        if (this.threadList == null) {
            return;
        }
        this.threadList.setListArea(threadListX, threadListY + yOffset, threadListW, threadListH);
    }

    // ----- 渲染 -----

    @Override
    public void renderBackground(@NotNull GuiGraphics gfx) {}

    private void renderWindowBackground(GuiGraphics gfx, int yOffset) {
        int currentInnerY = innerY + yOffset;
        int currentWinY = winY + yOffset;

        if (BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            // 1. 左侧列表背景（交由 TalkThreadList 自行实现）

            // 2. 右侧聊天区域背景
            if (chatAreaW > 0) {
                // 绘制一个深色矩形作为聊天背景
                gfx.fill(chatAreaX, currentInnerY, chatAreaX + chatAreaW, innerY + innerH, COLOR_VANILLA_BG);
            }
        } else {
            // 1. 左背景
            if (listAreaW > 0) {
                TalkRenderUtils.drawRepeatedTexture(gfx, TEX_BG_LEFT,
                        innerX, currentInnerY, listAreaW, innerH, 16, 16);
            }
            // 2. 右背景
            if (chatAreaW > 0) {
                TalkRenderUtils.drawRepeatedTexture(gfx, TEX_BG_RIGHT,
                        chatAreaX, currentInnerY, chatAreaW, innerH, 16, 16);
            }
            // 3. 分割线
            TalkRenderUtils.drawRepeatedTexture(gfx, TEX_DIVIDER,
                    dividerX, currentInnerY, DIVIDER_WIDTH, innerH, 9, 16);
            // 4. 外框
            TalkRenderUtils.drawTextureFrame(gfx, TEX_FRAME,
                    winX, currentWinY, winW, winH,
                    FRAME_BORDER_W, FRAME_BORDER_H,
                    FRAME_W, FRAME_H);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        updateChoiceControlLayoutIfNeeded();

        this.renderBackground(gfx);

        // --- 开屏动效 ---
        long now = System.currentTimeMillis();
        float progress = (float)(now - openStartTime) / (float)ANIMATION_DURATION;
        progress = Mth.clamp(progress, 0f, 1f);
        float ease = 1.0f - (float)Math.pow(1.0f - progress, 4);

        // 进场动画的垂直位移量
        int yOffset = (int) (this.height * (1.0f - ease));

        int texTotalW = TalkUIStyles.DECO_W;
        int texTotalH = TalkUIStyles.DECO_H;

        // ==========================================================
        // 层级 1: 动态层 (随进场动画移动)
        // ==========================================================

        // 绘制窗口背景
        renderWindowBackground(gfx, yOffset);

        // 锁链装饰
        if (!BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            // 计算 offset 以驱动锁链动画
            int leftChainOffset = 0;
            if (this.threadList != null) {
                // 对于 ObjectSelectionList，内容高度 ≈ maxScroll + viewHeight
                int listContentH = this.threadList.getMaxScroll() + this.threadList.getHeight();
                leftChainOffset = TalkScrollMath.calculatePhysicalOffset(
                        this.threadList.getHeight(),
                        listContentH,
                        this.threadList.getScrollAmount(),
                        this.threadList.getMaxScroll()
                );
            }

            int rightChainOffset = 0;
            if (this.chatWidget != null) {
                rightChainOffset = TalkScrollMath.calculatePhysicalOffset(
                        this.chatWidget.getHeight(),
                        this.totalContentHeight,
                        this.chatWidget.getScrollAmountVal(),
                        this.chatWidget.getMaxScroll()
                );
            }

            // 左侧竖链
            int leftChainX = this.winX + 8;
            TalkRenderUtils.drawTiledTexture(gfx, TEX_PARTS, leftChainX, 0, CHAIN_V_W, this.height,
                    CHAIN_V_U, CHAIN_V_V, CHAIN_V_W, CHAIN_V_H, 0, - yOffset - leftChainOffset, texTotalW, texTotalH);

            // 右侧竖链
            int rightChainX = this.winX + this.winW - 5;
            TalkRenderUtils.drawTiledTexture(gfx, TEX_PARTS, rightChainX, 0, CHAIN_V_W, this.height,
                    CHAIN_V_U, CHAIN_V_V, CHAIN_V_W, CHAIN_V_H, 0, - yOffset - rightChainOffset, texTotalW, texTotalH);
        }

        /*
         * 临时移动动态控件。TalkThreadList 不是 AbstractWidget，需要显式更新
         * 自己的列表边界，否则左侧文字和滚动条不会参与开屏动画。
         */
        applyThreadListYOffset(yOffset);
        try {
            for (GuiEventListener child : this.children()) {
                if (child instanceof AbstractWidget widget) {
                    if (widget == this.chainBoxButton) continue;
                    widget.setY(widget.getY() + yOffset);
                }
            }
            // 绘制背景和 Widgets (包含 ChatWidget 和 ThreadList)
            super.render(gfx, mouseX, mouseY, partialTick);
        } finally {
            for (GuiEventListener child : this.children()) {
                if (child instanceof AbstractWidget widget) {
                    if (widget == this.chainBoxButton) continue;
                    widget.setY(widget.getY() - yOffset);
                }
            }
            applyThreadListYOffset(0);
        }

        gfx.pose().pushPose();
        gfx.pose().translate(0, yOffset, 0);

        // 如果有选项，渲染一个分界线
        if (this.choiceControlsVisible) {
            int buttonTopY = this.innerY + getChatViewHeight();
            gfx.fill(
                    chatAreaX + 5,
                    buttonTopY,
                    chatAreaX + chatAreaW - 5,
                    buttonTopY + 1,
                    COLOR_DIVISION
            );
        }

        // 无消息时显示提示
        if (selectedThread == null) {
            gfx.drawString(
                    this.font,
                    Component.translatable("gui.brntalk.no_conversation").getString(),
                    chatAreaX + 10,
                    this.height / 2,
                    COLOR_NO_MSG_TEXT
            );
        }

        if (!BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            // 左下角电子管
            int decoX = this.winX + 12;
            int decoY = this.winY + this.winH - 15;

            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 10.0f);

            gfx.blit(TEX_PARTS, decoX, decoY, DECO_BL_U, DECO_BL_V, DECO_BL_W, DECO_BL_H, texTotalW, texTotalH);

            gfx.pose().popPose();
        }

        gfx.pose().popPose();

        // ==========================================================
        // 层级 2: 静态层
        // ==========================================================

        if (!BrntalkConfig.CLIENT.useVanillaStyleUI.get()) {
            int topChainY = this.winY + 4;

            // 横向锁链 1
            TalkRenderUtils.drawTiledTexture(gfx, TEX_PARTS, 0, topChainY, this.width, CHAIN_H_H,
                    CHAIN_H_U, CHAIN_H_V, CHAIN_H_W, CHAIN_H_H, 0, 0, texTotalW, texTotalH);

            // 横向锁链 2
            TalkRenderUtils.drawTiledTexture(gfx, TEX_PARTS, 0, topChainY + 8, this.width, CHAIN_H_H,
                    CHAIN_H_U, CHAIN_H_V, CHAIN_H_W, CHAIN_H_H, 0, 0, texTotalW, texTotalH);
        }
        // 手动绘制关闭按钮
        if (this.closeButton != null) {
            gfx.pose().pushPose();
            gfx.pose().translate(0, yOffset, 10.0f);

            this.closeButton.render(gfx, mouseX, mouseY, partialTick);

            gfx.pose().popPose();
        }

        // 手动绘制锁链盒按钮
        if (this.chainBoxButton != null) {
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 20.0f);

            this.chainBoxButton.render(gfx, mouseX, mouseY, partialTick);

            gfx.pose().popPose();
        }

        if (this.selectedThread != null) {
            boolean isFinished = TalkTimeline.isFinished(this.selectedThread);

            if (isFinished) {
                requestSelectedThreadReadIfNeeded();
            }
        }
    }

    private void requestSelectedThreadReadIfNeeded() {
        ClientTalkState state = ClientTalkState.get();
        if (!state.hasUnread(this.selectedThread)) {
            pendingReadActivityTimes.remove(this.selectedThread.getId());
            return;
        }

        String threadId = this.selectedThread.getId();
        long lastActivityTime = this.selectedThread.getLastActivityTime();
        long pendingActivityTime = pendingReadActivityTimes.getOrDefault(threadId, Long.MIN_VALUE);
        if (pendingActivityTime >= lastActivityTime) {
            return;
        }

        // 客户端只记录请求去重；正式 lastReadTime 必须等待服务端 UpdateState 确认。
        pendingReadActivityTimes.put(threadId, lastActivityTime);
        TalkNetworking.sendMarkRead(threadId);
    }

    public void renderChatContents(GuiGraphics gfx, int x, int yOffset, int width) {
        if (selectedThread == null) return;

        // 在这一帧绘制前先测量内容，滚动控件和渲染器共享同一宽度。
        int maxBubbleWidth = (int) (width * MAX_BUBBLE_WIDTH_RATIO);
        int textMaxWidth = maxBubbleWidth - (2 * BUBBLE_PADDING_X);

        double currentScroll = this.chatWidget.getScrollAmountVal();
        boolean wasAtBottom = this.chatWidget.isScrolledToBottom(1.0);
        int oldContentHeight = this.totalContentHeight;

        this.totalContentHeight = chatContentRenderer.calculateScrollableHeight(
                selectedThread,
                this.font,
                textMaxWidth
        );

        if (this.needScrollToBottom) {
            this.chatWidget.scrollToBottomImmediately();
            this.needScrollToBottom = false;
            currentScroll = this.chatWidget.getScrollAmountVal();
        } else if (this.totalContentHeight > oldContentHeight && wasAtBottom) {
            // 打字机换行导致内容增高时，平滑追随新的底部位置。
            this.chatWidget.scrollToBottom();
        }

        chatContentRenderer.render(
                gfx,
                this.font,
                selectedThread,
                x,
                yOffset,
                width,
                textMaxWidth,
                currentScroll,
                this.chatWidget.getY(),
                this.chatWidget.getHeight()
        );
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
        ClientTextFormatter.clearCache();
        this.chatContentRenderer.clear();
        super.onClose();
    }

    @Override
    public void removed() {
        if (stateListenerRegistered) {
            // Screen 被替换或关闭时解除监听，避免状态单例长期持有旧界面。
            ClientTalkState.get().removeListener(stateListener);
            stateListenerRegistered = false;
        }
        super.removed();
    }

    public void clearRenderCache() {
        this.chatContentRenderer.clear();
    }

}

package yourscraft.jasdewstarfield.brntalk.client.ui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
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

public class TalkScreen extends Screen {

    private static final ResourceLocation BACKGROUND_SPRITE =
            ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "talk_background");

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
    private static final float MAX_BUBBLE_WIDTH_RATIO = 0.85f;

    private boolean needScrollToBottom = true;

    private final List<AbstractWidget> choiceButtons = new ArrayList<>();

    private final Map<String, MessageRenderCache> renderCacheMap = new HashMap<>();

    public TalkScreen() {
        super(Component.literal("BRNTalk"));
    }

    @Override
    protected void init() {
        super.init();
        this.renderCacheMap.clear();
        rebuildUI();
    }

    private void rebuildUI() {

        this.clearWidgets();
        this.choiceButtons.clear();

        Minecraft mc = Minecraft.getInstance();

        // 左侧列表区域
        int listX = 20;
        int listTop = 25;
        int listWidth = 140;
        int listBottom = this.height - 20;
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

        int closeBtnSize = 16;
        int closeX = this.width - closeBtnSize - 5;
        int closeY = 5;

        this.addRenderableWidget(
                Button.builder(Component.literal("×"), btn -> this.onClose())
                        .bounds(closeX, closeY, closeBtnSize, closeBtnSize)
                        .build()
        );

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
        int startY = this.height - 20;
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
        int defaultBottom = this.height - 20; // 默认底部

        TalkMessage last = getLastMessageOfSelected();
        if (last != null && last.getType() == TalkMessage.Type.CHOICE) {
            int choiceCount = last.getChoices().size();
            if (choiceCount > 0) {
                int buttonAreaHeight = choiceCount * 25;

                int buttonsTop = (this.height - 25) - buttonAreaHeight;
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
        int listTop = 25;
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

            if (isFinished && ClientTalkState.get().hasUnread(this.selectedThread)) {
                ClientPayloadSender.sendMarkRead(this.selectedThread.getId());
                this.selectedThread.setLastReadTime(System.currentTimeMillis());
            }
        }

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

        int areaTop = 25;
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

            int maxBubbleWidth = (int) (areaWidth * MAX_BUBBLE_WIDTH_RATIO);
            int textMaxWidth = maxBubbleWidth - (2 * BUBBLE_PADDING_X);

            long now = System.currentTimeMillis();

            long previousVisualEndTime = 0;
            int charDelay = ClientTalkUtils.getCharDelay();
            int msgPause = ClientTalkUtils.getMsgPause();

            for (TalkMessage msg : msgs) {
                // 1. 获取或创建缓存
                MessageRenderCache cache = renderCacheMap.computeIfAbsent(msg.getId(), k -> new MessageRenderCache());

                // 2. 检查缓存是否过期（例如：首次加载、或宽度变化导致需要重新折行）
                // 只要 textMaxWidth 不变，font.split 的结果就是一样的
                if (!cache.isLayoutValid(textMaxWidth)) {
                    cache.updateLayout(msg, textMaxWidth, this.font);
                }

                // 3. 计算时间轴
                long visualStartTime;
                if (msg.getTimestamp() == 0) {
                    visualStartTime = 0;
                    previousVisualEndTime = 0;
                } else {
                    visualStartTime = Math.max(msg.getTimestamp(), previousVisualEndTime + msgPause);
                    // 使用缓存中计算好的纯文本长度
                    long duration = (long) cache.cleanTextLength * charDelay;
                    previousVisualEndTime = visualStartTime + duration;
                }

                // 4. 判断当前消息的打字机进度
                String textToShow;
                long timePassed = now - visualStartTime;

                if (timePassed < 0) {
                    // 还没轮到，直接跳过
                    continue;
                } else if (timePassed >= (long) cache.cleanTextLength * charDelay) {
                    // 播完了，直接用缓存的完整文本
                    textToShow = cache.processedText;
                } else {
                    int charCount = (int) (timePassed / charDelay);
                    charCount = Math.max(0, Math.min(charCount, cache.processedText.length()));
                    textToShow = cache.processedText.substring(0, charCount);
                }

                // 5. 再次折行 (只有当正在打字时，才需要实时折行；如果播完了，直接用缓存的 lines)
                List<FormattedCharSequence> linesToDraw;
                if (textToShow.length() == cache.processedText.length()) {
                    // 完整显示，使用缓存
                    linesToDraw = cache.cachedLines;
                } else {
                    // 动态显示，必须实时算，但只针对当前正在打字的那一条
                    linesToDraw = this.font.split(Component.literal(textToShow), textMaxWidth);
                }

                // 气泡最终尺寸
                int contentWidth = 0;
                for (FormattedCharSequence seq : linesToDraw) {
                    int w = this.font.width(seq);
                    if (w > contentWidth) contentWidth = w;
                }
                int contentHeight = linesToDraw.size() * lineHeight;

                int bubbleWidth = contentWidth + (2 * BUBBLE_PADDING_X);
                int bubbleHeight = contentHeight + (2 * BUBBLE_PADDING_Y);

                boolean isPlayer = (msg.getSpeakerType() == TalkMessage.SpeakerType.PLAYER);
                int bubbleX = isPlayer ? (areaRight - bubbleWidth) : areaLeft;

                int entryTotalHeight = lineHeight + 2 + bubbleHeight;
                float visualEntryTop = currentY - this.scrollAmount;
                float visualEntryBottom = visualEntryTop + entryTotalHeight;
                // 块级Culling
                if (visualEntryBottom < areaTop || visualEntryTop > areaBottom) {
                    currentY += entryTotalHeight + ENTRY_SPACING;
                    continue;
                }

                // --- 渲染说话人 ---
                int nameX = isPlayer ? (areaRight - cache.speakerNameWidth) : areaLeft;
                gfx.drawString(this.font, cache.speakerComp, nameX, currentY, 0xFFFFAA00);
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
                int textY = currentY + BUBBLE_PADDING_Y;
                int textX = bubbleX + BUBBLE_PADDING_X;

                float viewTop = areaTop + this.scrollAmount;
                float viewBottom = areaBottom + this.scrollAmount;

                for (FormattedCharSequence line : linesToDraw) {
                    // 文字Culling
                    if (textY + lineHeight > viewTop && textY < viewBottom) {
                        gfx.drawString(this.font, line, textX, textY, 0xFFFFFFFF, false);
                    }
                    textY += lineHeight;
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
    public void renderBackground(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int bgX = 10;
        int bgY = 15;
        int bgWidth = this.width - 20;  // 左右各留 10 像素
        int bgHeight = this.height - 30; // 上下各留 15 像素

        this.renderBlurredBackground(partialTick);
        this.renderMenuBackground(gfx);

        // 渲染自定义背景
        gfx.blitSprite(
                BACKGROUND_SPRITE,
                bgX, bgY,          // x, y
                bgWidth, bgHeight  // width, height
        );
    }

    @Override
    public boolean isPauseScreen() {
        // 返回 false：打开对话 UI 时，游戏不会暂停
        return false;
    }

    @Override
    public void onClose() {
        // 关闭界面时清理缓存，回到游戏
        this.renderCacheMap.clear();
        Minecraft.getInstance().setScreen(null);
    }

    private static class MessageRenderCache {
        // 缓存的文本数据
        String processedText;      // I18n处理后的完整文本
        String speakerName;        // I18n处理后的名字
        int cleanTextLength;       // 去色后的长度（用于时间轴计算）

        // 缓存的渲染组件
        Component speakerComp;
        int speakerNameWidth;

        // 缓存的布局数据
        List<FormattedCharSequence> cachedLines; // 完整文本的折行结果
        int cachedLayoutWidth;     // 上次计算时的最大宽度

        boolean isLayoutValid(int widthLimit) {
            return processedText != null && cachedLines != null && cachedLayoutWidth == widthLimit;
        }

        void updateLayout(TalkMessage msg, int widthLimit, net.minecraft.client.gui.Font font) {
            // 1. 只有第一次才处理文本
            if (processedText == null) {
                this.processedText = ClientTalkUtils.processText(msg.getText());
                this.speakerName = ClientTalkUtils.processText(msg.getSpeaker());

                String clean = ClientTalkUtils.stripColor(processedText).replace("\n", "");
                this.cleanTextLength = clean.length();

                this.speakerComp = Component.literal(speakerName);
                this.speakerNameWidth = font.width(speakerName);
            }

            // 2. 重新计算折行
            this.cachedLayoutWidth = widthLimit;
            // 缓存完整显示的折行结果
            this.cachedLines = font.split(Component.literal(processedText), widthLimit);
        }
    }
}
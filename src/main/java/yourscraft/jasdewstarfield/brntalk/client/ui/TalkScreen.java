package yourscraft.jasdewstarfield.brntalk.client.ui;

import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.client.ClientPayloadSender;
import yourscraft.jasdewstarfield.brntalk.client.ClientTalkState;
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
import java.util.Comparator;
import java.util.List;

public class TalkScreen extends Screen {

    private TalkThreadList threadList;
    private TalkThread selectedThread;

    @Nullable
    private String selectedThreadId;

    private float scrollAmount = 0.0f;
    private int totalContentHeight = 0;

    private boolean needScrollToBottom = true;

    private final long screenOpenTime;

    public TalkScreen() {
        super(Component.literal("BRNTalk"));
        // 记录当前时间
        this.screenOpenTime = System.currentTimeMillis();
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
        List<TalkThread> threads = ClientTalkState.get().getThreads().stream()
                .sorted(Comparator.comparingLong(TalkThread::getStartTime).reversed())
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
                            Component.translatable(c.getText()),
                            b -> onChoiceClicked(c)
                    )
                    .bounds(centerX - choiceWidth / 2, cy, choiceWidth, choiceHeight)
                    .build();

            this.addRenderableWidget(btn);
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

    private String processText(String text) {
        if (text == null) return "";

        // 支持翻译键
        String translated = I18n.get(text);

        // 1. 支持颜色代码：用 '&' 代替 '§' (例如 "&c" -> 红色)
        String processing = translated.replace("&", "§");

        // 2. 支持玩家名占位符："{player}" -> 当前玩家名
        if (processing.contains("{player}")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                processing = processing.replace("{player}", mc.player.getName().getString());
            }
        }

        return processing;
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
        int startX = listRight + 10; // 文字左边距
        int endX = this.width - 10;  // 文字右边距
        int textWidth = endX - startX;

        int startY = 20;
        int endY = getChatBottomY();
        int viewHeight = endY - startY;

        // 1. 在渲染内容之前，检查当前是否处于"底部状态"
        // 使用上一帧计算出的 totalContentHeight
        float maxScrollPre = Math.max(0, this.totalContentHeight - viewHeight);
        // 如果当前滚动位置接近最大值(允许 5 像素误差)，或者有强制置底信号，则认为需要"粘"在底部
        boolean isAtBottom = (this.scrollAmount >= maxScrollPre - 5) || this.needScrollToBottom;

        if (endX - startX < 10 || endY - startY < 10) return;

        // 2. 开启裁剪 (Scissor Test)，只在指定矩形内绘制，防止溢出
        gfx.enableScissor(startX, startY, endX, endY);
        gfx.pose().pushPose();

        try {
            // 应用滚动偏移
            gfx.pose().translate(0, -this.scrollAmount, 0);

            List<TalkMessage> msgs = selectedThread.getMessages();
            int currentY = startY;
            int lineHeight = this.font.lineHeight;
            int entrySpacing = 8; // 消息之间的间距
            long now = System.currentTimeMillis();
            long previousEndTime = 0;

            // 配置参数
            int charDelay = 50;   // ms
            int msgPause = 500;   // ms

            for (TalkMessage msg : msgs) {
                // 1. 预处理文本（替换占位符、颜色等），因为长度会变，所以要先处理
                String rawText = processText(msg.getText());
                int textLen = rawText.length();
                // 是否为历史消息（已经播完）
                boolean isHistory = msg.getTimestamp() < this.screenOpenTime;

                // 2. 计算这条消息的“视觉开始时间”
                // 逻辑：它必须晚于“它真实产生的时间”，同时也必须等“上一条消息播完 + 停顿”之后
                long visualStartTime;
                // 计算这条消息播完需要多久
                long typingDuration = (long) textLen * charDelay;
                if (isHistory) {
                    // 历史消息：视觉开始时间 = 0 (立即显示)
                    visualStartTime = 0;
                    // 历史消息不应该阻碍后续新消息的播放，所以重置排队计时
                    previousEndTime = 0;
                } else {
                    // 新消息：正常计算排队
                    // 如果上一条消息结束得太早(previousEndTime很小)，就用消息自己的时间戳
                    // 这样即使玩家手动跳过了上一条，下一条也能立即开始，而不会被强行推迟
                    visualStartTime = Math.max(msg.getTimestamp(), previousEndTime + msgPause);
                    previousEndTime = visualStartTime + typingDuration;
                }

                // 3. 计算当前时刻 (now) 处于什么阶段
                long timePassed = now - visualStartTime;
                // 如果时间还没到 (timePassed < 0)，说明上一条还没播完，这条直接跳过不渲染
                if (timePassed < 0) {
                    continue;
                }

                // --- 渲染说话人 ---
                String speakerName = processText(msg.getSpeaker());
                Component speakerComp = Component.literal(speakerName);
                gfx.drawString(this.font, speakerComp, startX, currentY, 0xFFFFAA00);
                currentY += lineHeight + 2;

                // --- 渲染正文 ---
                String textToShow;
                if (timePassed < typingDuration) {
                    // 阶段 A: 正在打字
                    int charCount = (int) (timePassed / charDelay);
                    charCount = Math.min(charCount, textLen); // 防止越界
                    textToShow = rawText.substring(0, charCount);
                } else {
                    // 阶段 B: 播放完毕，显示全部
                    textToShow = rawText;
                }

                Component textComp = Component.literal(textToShow);
                List<FormattedCharSequence> lines = this.font.split(textComp, textWidth);

                for (FormattedCharSequence line : lines) {
                    gfx.drawString(this.font, line, startX, currentY, 0xFFFFFF);
                    currentY += lineHeight;
                }

                currentY += entrySpacing;
            }

            // 计算总高度，用于滚动条逻辑
            this.totalContentHeight = currentY - startY;

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
            int scrollbarX = endX - 2;
            int scrollbarWidth = 2;

            // 计算滑块高度和位置
            float ratio = (float) viewHeight / this.totalContentHeight;
            int barHeight = Math.max(10, (int) (viewHeight * ratio));

            int barTop = startY;
            if (maxScrollPost > 0) {
                barTop += (int) ((this.scrollAmount / maxScrollPost) * (viewHeight - barHeight));
            }

            gfx.fill(scrollbarX, startY, scrollbarX + scrollbarWidth, endY, 0x20FFFFFF); // 轨道
            gfx.fill(scrollbarX, barTop, scrollbarX + scrollbarWidth, barTop + barHeight, 0xFFCCCCCC); // 滑块
        }
    }

    public void onThreadsSynced() {
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
package yourscraft.jasdewstarfield.brntalk.client.ui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brntalk.client.text.ClientTextFormatter;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** 拥有选项按钮、布局和点击上下文，不持有 Screen 或聊天渲染器。 */
public final class TalkChoicePanel {
    private final List<Button> buttons = new ArrayList<>();
    private final Consumer<ChoiceIntent> onChoice;
    private final UnaryOperator<String> formatText;
    private boolean visible;
    private int choiceCount;

    /** 按钮创建时固定会话和消息位置，延迟点击仍表达原先显示的问题。 */
    public record ChoiceIntent(String threadId, String choiceId, String sessionId, int position) {}
    public record Option(ChoiceIntent intent, String text, int x, int y, int width, int height) {}

    public TalkChoicePanel(Consumer<ChoiceIntent> onChoice) { this(onChoice, ClientTextFormatter::process); }

    // 文本替换依赖真实玩家；无窗口测试仅替换格式化函数，仍创建原版按钮。
    TalkChoicePanel(Consumer<ChoiceIntent> onChoice, UnaryOperator<String> formatText) {
        this.onChoice = onChoice;
        this.formatText = formatText;
    }
    public boolean isVisible() { return visible; }

    public static boolean shouldShow(TalkThread thread, boolean playbackFinished) {
        TalkMessage last = thread == null ? null : thread.getCurrentMessage();
        return last != null && last.getType() == TalkMessage.Type.CHOICE && playbackFinished;
    }

    /** 纯布局快照便于验证顺序、命中区域和显示上下文，尺寸沿用原 Screen。 */
    public static List<Option> layout(TalkThread thread, int chatX, int chatWidth, int bottomY) {
        TalkMessage last = thread == null ? null : thread.getCurrentMessage();
        if (last == null || last.getType() != TalkMessage.Type.CHOICE) return List.of();
        var choices = last.getChoices();
        List<Option> options = new ArrayList<>();
        for (int i = 0; i < choices.size(); i++) {
            var choice = choices.get(choices.size() - 1 - i);
            var intent = new ChoiceIntent(thread.getId(), choice.getId(), thread.getSessionId(), thread.getMessages().size());
            options.add(new Option(intent, choice.getText(), chatX + chatWidth / 2 - 70, bottomY - 25 * (i + 1), 140, 20));
        }
        return List.copyOf(options);
    }

    public void rebuild(TalkThread thread, boolean playbackFinished, int chatX, int chatWidth,
                        int bottomY, Consumer<Button> register) {
        buttons.clear();
        List<Option> options = layout(thread, chatX, chatWidth, bottomY);
        choiceCount = options.size();
        visible = shouldShow(thread, playbackFinished);
        for (var option : options) {
            Button button = Button.builder(Component.literal(formatText.apply(option.text())),
                            ignored -> onChoice.accept(option.intent()))
                    .bounds(option.x(), option.y(), option.width(), option.height()).build();
            buttons.add(button);
            register.accept(button);
        }
        syncVisibility();
    }

    public void syncVisibility() {
        for (Button button : buttons) {
            button.visible = visible;
            button.active = visible;
        }
    }

    public int chatViewHeight(int innerHeight) {
        return visible && choiceCount > 0 ? Math.max(10, innerHeight - choiceCount * 25 - 5) : innerHeight;
    }

    public void clear() { buttons.clear(); visible = false; choiceCount = 0; }
}

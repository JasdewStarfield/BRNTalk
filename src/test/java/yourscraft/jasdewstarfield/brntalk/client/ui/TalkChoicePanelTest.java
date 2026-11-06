package yourscraft.jasdewstarfield.brntalk.client.ui;

import org.junit.jupiter.api.Test;
import net.minecraft.client.gui.components.Button;
import java.util.function.UnaryOperator;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** 用可观察的布局和点击意图验证选项区，不依赖窗口或聊天绘制。 */
class TalkChoicePanelTest {
    private TalkThread question(String id) {
        var thread = new TalkThread(id, "script", 0, 0);
        var message = new TalkMessage("question", TalkMessage.Type.CHOICE, TalkMessage.SpeakerType.NPC,
                "NPC", "Choose", null, 0, null);
        message.addChoice(new TalkMessage.Choice("first", "First", ""));
        message.addChoice(new TalkMessage.Choice("second", "Second", ""));
        thread.appendMessage(message);
        return thread;
    }

    @Test void playbackControlsVisibilityAndPreservesTheOriginalButtonLayout() {
        var thread = question("thread");
        assertFalse(TalkChoicePanel.shouldShow(thread, false));
        assertTrue(TalkChoicePanel.shouldShow(thread, true));
        assertFalse(TalkChoicePanel.shouldShow(null, true));
        var options = TalkChoicePanel.layout(thread, 100, 200, 300);
        assertEquals(List.of("second", "first"), options.stream().map(o -> o.intent().choiceId()).toList());
        assertEquals(275, options.get(0).y());
        assertEquals(250, options.get(1).y());
        assertEquals(130, options.get(0).x());
        assertEquals(140, options.get(0).width());
        assertEquals(20, options.get(0).height());
        assertThrows(UnsupportedOperationException.class, () -> options.clear());
    }

    @Test void intentsStayBoundToTheDisplayedThreadSessionAndPositionAfterSwitchOrAppend() {
        var first = question("first");
        var intent = TalkChoicePanel.layout(first, 0, 200, 300).getFirst().intent();
        var second = question("second");
        first.appendMessage(second.getCurrentMessage());
        assertEquals("first", intent.threadId());
        assertEquals(first.getSessionId(), intent.sessionId());
        assertEquals(1, intent.position());
        assertEquals(2, TalkChoicePanel.layout(first, 0, 200, 300).getFirst().intent().position());
        assertEquals("second", TalkChoicePanel.layout(second, 0, 200, 300).getFirst().intent().threadId());
    }

    @Test void buttonsAndReservedHeightFollowPlaybackAndKeepCapturedIntents() {
        var thread = question("thread");
        var sent = new ArrayList<TalkChoicePanel.ChoiceIntent>();
        var widgets = new ArrayList<Button>();
        var panel = new TalkChoicePanel(sent::add, UnaryOperator.identity());
        panel.rebuild(thread, false, 100, 200, 300, widgets::add);
        assertEquals(2, widgets.size());
        assertTrue(widgets.stream().noneMatch(button -> button.visible || button.active));
        assertEquals(200, panel.chatViewHeight(200));
        widgets.clear();
        panel.rebuild(thread, true, 100, 200, 300, widgets::add);
        assertTrue(widgets.stream().allMatch(button -> button.visible && button.active));
        assertEquals(145, panel.chatViewHeight(200));
        thread.appendMessage(question("other").getCurrentMessage());
        widgets.getFirst().onPress();
        assertEquals(1, sent.getFirst().position());
        panel.clear();
        assertFalse(panel.isVisible());
        assertEquals(200, panel.chatViewHeight(200));
    }
}

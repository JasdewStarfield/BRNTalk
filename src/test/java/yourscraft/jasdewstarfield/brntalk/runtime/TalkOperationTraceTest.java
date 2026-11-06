package yourscraft.jasdewstarfield.brntalk.runtime;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 固定动作与内存推进的相对顺序，迁移服务时继续运行同一组行为断言。 */
class TalkOperationTraceTest {
    private final RecordingConversationPlayer player = new RecordingConversationPlayer();
    private final UUID playerId = player.id();
    private final List<String> trace = new ArrayList<>();
    private final TalkManager manager = new TalkManager();
    private final ConversationService service = manager.operations();

    TalkOperationTraceTest() {
        player.action = (thread, message) -> {
            assertSame(message, thread.getCurrentMessage());
            trace.add(message.getId());
        };
    }

    private void script(TalkMessage... messages) {
        var conversation = new TalkConversation("script");
        for (var message : messages) conversation.addMessage(message);
        manager.registerConversation(conversation);
    }

    private TalkMessage message(String id, TalkMessage.Type type, String next) {
        var message = new TalkMessage(id, type, TalkMessage.SpeakerType.NPC, "NPC", id, "action", 0, next);
        if (type == TalkMessage.Type.CHOICE) message.addChoice(new TalkMessage.Choice("yes", "Yes", ""));
        return message;
    }

    @Test void startAdvancesInMessageOrderAndWaitResumeStopsAtChoice() {
        script(message("first", TalkMessage.Type.TEXT, "wait"),
                message("wait", TalkMessage.Type.WAIT, "after"),
                message("after", TalkMessage.Type.TEXT, "question"),
                message("question", TalkMessage.Type.CHOICE, null));
        var thread = service.startRuntime(player, "script", null);
        assertEquals(List.of("first", "wait"), trace);
        assertSame(thread, manager.getActiveThread(playerId, thread.getId()));
        var appended = service.resumeRuntime(player, thread.getId());
        assertEquals(List.of("after", "question"), appended.stream().map(TalkMessage::getId).toList());
        assertEquals(List.of("first", "wait", "after", "question"), trace);
        assertTrue(service.resumeRuntime(player, thread.getId()).isEmpty());
    }

    @Test void commandCallbackChangesTheCurrentPositionBeforeAutomaticAdvance() {
        // 回调直接改变当前位置：后续推进必须读取最新位置，不能提前规划整条消息链。
        var reentrant = new TalkManager();
        player.action = (thread, message) -> {
            trace.add(message.getId());
            if (message.getId().equals("first")) thread.appendMessage(message("callback", TalkMessage.Type.WAIT, null));
        };
        var conversation = new TalkConversation("script");
        conversation.addMessage(message("first", TalkMessage.Type.TEXT, "skipped"));
        conversation.addMessage(message("skipped", TalkMessage.Type.TEXT, null));
        reentrant.registerConversation(conversation);
        var thread = reentrant.operations().startRuntime(player, "script", null);
        assertEquals(List.of("first"), trace);
        assertEquals(List.of("first", "callback"), thread.getMessages().stream().map(TalkMessage::getId).toList());
    }

    @Test void missingTargetsDoNothingAndClearingAllowsANewSession() {
        script(message("wait", TalkMessage.Type.WAIT, null));
        assertNull(service.startRuntime(player, "missing", null));
        assertNull(service.startRuntime(player, "script", "missing"));
        var first = service.startRuntime(player, "script", null);
        assertTrue(service.proceedRuntime(player, first.getId(), "missing").isEmpty());
        manager.clearThreadsForPlayer(playerId);
        var second = service.startRuntime(player, "script", null);
        assertNotEquals(first.getSessionId(), second.getSessionId());
        assertEquals(1, manager.getActiveThreads(playerId).size());
        assertEquals(List.of("wait", "wait"), trace);
    }
}

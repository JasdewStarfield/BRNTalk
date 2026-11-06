package yourscraft.jasdewstarfield.brntalk.runtime;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 检查状态、动作、事件及同步的可观察轨迹，包含平台回调重入。 */
class ConversationServiceTest {
    private final TalkManager manager = new TalkManager();
    private final ConversationService service = manager.operations();
    private final RecordingConversationPlayer player = new RecordingConversationPlayer();

    private TalkMessage message(String id, TalkMessage.Type type, String next) {
        return new TalkMessage(id, type, TalkMessage.SpeakerType.NPC, "NPC", id, "action", 0, next);
    }
    private TalkMessage question(String id, String next) {
        var message = message(id, TalkMessage.Type.CHOICE, null);
        message.addChoice(new TalkMessage.Choice("yes", "Yes", next));
        return message;
    }
    private void script(TalkMessage... messages) {
        var script = new TalkConversation("script");
        for (var message : messages) script.addMessage(message);
        manager.registerConversation(script);
    }

    @Test void startPersistsAllHistoryBeforeSeenAndCompletionBeforeSync() {
        script(message("first", TalkMessage.Type.TEXT, "end"), message("end", TalkMessage.Type.TEXT, null));
        player.onSeen = (script, id) -> assertTrue(player.state.hasSeenMessage(script, "end"));
        player.onCompleted = () -> assertTrue(player.state.hasCompletedConversation("script"));
        var result = service.start(player, "script", null);
        assertTrue(result.succeeded());
        assertEquals(List.of("action:first", "action:end", "seen:first", "seen:end", "complete", "add"), player.trace);
        assertThrows(UnsupportedOperationException.class, () -> result.messages().clear());
        result.thread().appendMessage(message("later", TalkMessage.Type.WAIT, null));
        assertEquals(2, result.messages().size());
    }

    @Test void waitResumeUsesTheSamePersistenceEventAndSyncOrder() {
        script(message("wait", TalkMessage.Type.WAIT, "end"), message("end", TalkMessage.Type.TEXT, null));
        var thread = service.start(player, "script", null).thread();
        assertFalse(player.state.hasCompletedConversation("script"));
        player.trace.clear();
        assertEquals(0, service.resume(player, "script", "other"));
        assertEquals(1, service.resume(player, "script", "wait"));
        assertEquals(List.of("action:end", "seen:end", "complete", "append"), player.trace);
        assertEquals(List.of("wait", "end"), player.state.getThread(thread.getId()).getHistory());
        assertEquals(0, service.resume(player, "script", null));
    }

    @Test void duplicateAndReentrantChoicesCannotSkipTheNextQuestion() {
        script(question("first", "next"), question("next", "end"), message("end", TalkMessage.Type.TEXT, null));
        var thread = service.start(player, "script", null).thread();
        String session = thread.getSessionId();
        player.trace.clear();
        player.action = (t, m) -> assertEquals(ConversationService.Status.REJECTED,
                service.select(player, t.getId(), "yes", session, 1).status());
        var result = service.select(player, thread.getId(), "yes", session, 1);
        assertTrue(result.succeeded());
        assertEquals(List.of("action:next", "seen:next", "append"), player.trace);
        assertEquals("next", thread.getCurrentMessage().getId());
        assertEquals(ConversationService.Status.REJECTED, service.select(player, thread.getId(), "yes", session, 1).status());
        assertFalse(player.state.hasCompletedConversation("script"));
        service.select(player, thread.getId(), "yes", session, 2);
        assertEquals(1, player.trace.stream().filter("complete"::equals).count());
        assertEquals(List.of("first", "next", "end"), player.state.getThread(thread.getId()).getHistory());
    }

    @Test void terminalChoiceAndCompletionCallbackAreIdempotent() {
        script(question("end", ""));
        var thread = service.start(player, "script", null).thread();
        player.trace.clear();
        player.onCompleted = () -> service.complete(player, thread);
        assertTrue(service.select(player, thread.getId(), "yes", thread.getSessionId(), 1).succeeded());
        assertEquals(ConversationService.Status.REJECTED,
                service.select(player, thread.getId(), "yes", thread.getSessionId(), 1).status());
        assertEquals(List.of("complete"), player.trace);
    }

    @Test void commandCallbackCanStartAnotherConversationBeforeOuterHistoryIsSaved() {
        script(message("wait", TalkMessage.Type.WAIT, null));
        player.action = (thread, message) -> {
            assertFalse(player.state.hasThread(thread.getId()));
            assertNull(manager.getActiveThread(player.id, thread.getId()));
            if (player.trace.size() == 1) service.start(player, "script", null);
        };
        service.start(player, "script", null);
        assertEquals(List.of("action:wait", "action:wait", "seen:wait", "add", "seen:wait", "add"), player.trace);
        assertEquals(2, player.state.getThreadIds().size());
    }

    @Test void clearPreservesTheExistingReceiptRulesAndAllowsRestart() {
        script(message("end", TalkMessage.Type.TEXT, null));
        var first = service.start(player, "script", null).thread();
        player.state.beginExternalAction("reward", "start");
        assertTrue(service.clear(player, "script"));
        assertNull(manager.getActiveThread(player.id, first.getId()));
        assertFalse(player.state.hasCompletedConversation("script"));
        assertNotNull(player.state.getExternalActionReceipt("reward"));
        assertFalse(service.clear(player, "script"));
        var second = service.start(player, "script", null).thread();
        assertNotEquals(first.getSessionId(), second.getSessionId());
        assertTrue(service.clearAll(player));
        assertTrue(player.state.isEmpty());
        assertTrue(manager.getActiveThreads(player.id).isEmpty());
        assertFalse(service.clearAll(player));
    }

    @Test void missingScriptsAndStaleSessionsHaveNoSideEffects() {
        assertEquals(ConversationService.Status.FAILURE, service.start(player, "missing", null).status());
        script(question("question", ""));
        var thread = service.start(player, "script", null).thread();
        player.trace.clear();
        assertEquals(ConversationService.Status.REJECTED,
                service.select(player, thread.getId(), "yes", "old-session", 1).status());
        assertTrue(player.trace.isEmpty());
        service.open(player);
        assertEquals(List.of("sync", "open"), player.trace);
    }
}

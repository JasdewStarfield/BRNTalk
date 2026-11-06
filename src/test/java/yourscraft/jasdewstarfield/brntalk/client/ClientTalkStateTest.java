package yourscraft.jasdewstarfield.brntalk.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 固定界面依赖的状态事件、选中身份和排序，避免职责迁移改变交互策略。 */
class ClientTalkStateTest {
    private final ClientTalkState state = ClientTalkState.get();
    private final List<ClientTalkState.StateChange> changes = new ArrayList<>();
    private final ClientTalkState.StateListener listener = changes::add;

    @BeforeEach void before() { state.clear(); state.addListener(listener); }
    @AfterEach void after() { state.removeListener(listener); state.clear(); }

    private TalkMessage message(long time) {
        return new TalkMessage("message", TalkMessage.Type.TEXT, TalkMessage.SpeakerType.NPC, "NPC", "Text", null, time, null);
    }
    private TalkThread thread(String id, long time) {
        var thread = new TalkThread(id, "script", 0, 0);
        thread.appendMessage(message(time));
        return thread;
    }

    @Test void fullReplacementKeepsSelectedIdentityAndUsesTheNewRuntimeObject() {
        var first = thread("first", 100);
        var second = thread("second", 200);
        state.setThreads(List.of(first, second));
        state.selectThread("first");
        var restored = thread("first", 100);
        state.setThreads(List.of(restored, thread("second", 200)));
        assertSame(restored, state.getSelectedThread());
        assertNotSame(first, state.getSelectedThread());
        state.setThreads(List.of(second));
        assertSame(second, state.getSelectedThread());
    }

    @Test void fullRecoveryAndIncrementalNotificationsRemainDistinctEvents() {
        state.setThreads(List.of(thread("first", 100)));
        state.addThread(thread("second", 200));
        state.appendMessages("first", List.of(message(300)));
        assertEquals(List.of(ClientTalkState.ChangeType.THREADS_REPLACED,
                        ClientTalkState.ChangeType.THREAD_ADDED, ClientTalkState.ChangeType.MESSAGES_APPENDED),
                changes.stream().map(ClientTalkState.StateChange::type).toList());
    }

    @Test void sortingUsesActivityThenIdAndDoesNotChangeTheCurrentSelection() {
        state.setThreads(List.of(thread("b", 100), thread("a", 100)));
        assertEquals(List.of("a", "b"), state.getThreadsByRecentActivity().stream().map(TalkThread::getId).toList());
        state.appendMessages("b", List.of(message(200)));
        assertEquals(List.of("b", "a"), state.getThreadsByRecentActivity().stream().map(TalkThread::getId).toList());
        assertEquals("a", state.getSelectedThreadId());
    }
}

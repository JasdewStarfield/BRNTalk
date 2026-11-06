package yourscraft.jasdewstarfield.brntalk.client.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brntalk.client.ClientTalkState;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 请求去重独立于聊天渲染；旧确认不能消除更新活动对应的待确认请求。 */
class TalkReadCoordinatorTest {
    private final ClientTalkState state = ClientTalkState.get();
    private final List<String> sent = new ArrayList<>();
    private final TalkReadCoordinator reads = new TalkReadCoordinator(sent::add);

    @BeforeEach void before() { state.clear(); }
    @AfterEach void after() { state.clear(); }

    private TalkMessage message(long timestamp) {
        return new TalkMessage("message", TalkMessage.Type.TEXT, TalkMessage.SpeakerType.NPC,
                "NPC", "Text", null, timestamp, null);
    }
    private TalkThread thread(String id, long activity) {
        var thread = new TalkThread(id, "script", 0, 0);
        thread.appendMessage(message(activity));
        state.addThread(thread);
        return thread;
    }

    @Test void waitsForPlaybackAndDeduplicatesAcrossFramesAndThreadSwitches() {
        var first = thread("first", 100);
        var second = thread("second", 100);
        reads.requestIfNeeded(first, false);
        assertTrue(sent.isEmpty());
        reads.requestIfNeeded(first, true);
        reads.requestIfNeeded(first, true);
        reads.requestIfNeeded(second, true);
        reads.requestIfNeeded(first, true);
        assertEquals(List.of("first", "second"), sent);
        assertEquals(0, first.getLastReadTime());
    }

    @Test void olderAcknowledgementDoesNotCoverNewActivityOrCauseRepeatedRequests() {
        var thread = thread("thread", 100);
        reads.requestIfNeeded(thread, true);
        state.appendMessages("thread", List.of(message(200)));
        reads.requestIfNeeded(thread, true);
        state.updateReadTime("thread", 100);
        reads.reconcile(state);
        reads.requestIfNeeded(thread, true);
        assertEquals(List.of("thread", "thread"), sent);
        assertTrue(state.hasUnread(thread));
        state.updateReadTime("thread", 200);
        state.updateReadTime("thread", 100);
        reads.reconcile(state);
        reads.requestIfNeeded(thread, true);
        assertEquals(200, thread.getLastReadTime());
        assertEquals(2, sent.size());
        assertFalse(state.hasUnread(thread));
    }

    @Test void replacementRemovalAndScreenCleanupReleasePendingRequests() {
        var old = thread("thread", 100);
        reads.requestIfNeeded(old, true);
        var replacement = new TalkThread("thread", "script", 0, 0);
        replacement.appendMessage(message(50));
        state.setThreads(List.of(replacement));
        reads.reconcile(state);
        reads.requestIfNeeded(replacement, true);
        assertEquals(2, sent.size());
        state.clear();
        reads.reconcile(state);
        state.addThread(replacement);
        reads.requestIfNeeded(replacement, true);
        reads.clear();
        reads.requestIfNeeded(replacement, true);
        assertEquals(4, sent.size());
    }
}

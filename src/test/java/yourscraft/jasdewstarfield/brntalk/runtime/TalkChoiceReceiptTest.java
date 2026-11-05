package yourscraft.jasdewstarfield.brntalk.runtime;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.network.PayloadSync;
import static org.junit.jupiter.api.Assertions.*;

/** Replays must stay tied to the original question, including loops and reloads. */
class TalkChoiceReceiptTest {
    private TalkMessage question(String id) {
        var message = new TalkMessage(id, TalkMessage.Type.CHOICE, TalkMessage.SpeakerType.NPC,
                "NPC", "Continue?", null, 0, null);
        message.addChoice(new TalkMessage.Choice("yes", "Yes", "next"));
        return message;
    }

    @Test void repeatedChoiceCannotAnswerTheNextQuestionOrALoop() {
        var thread = new TalkThread("thread", "script", 0, 0);
        thread.appendMessage(question("first"));
        String session = thread.getSessionId();
        assertNotNull(thread.consumeChoice(session, 1, "yes"));
        assertNull(thread.consumeChoice(session, 1, "yes"));
        thread.appendMessage(question("second"));
        assertNull(thread.consumeChoice(session, 1, "yes"));
        assertNotNull(thread.consumeChoice(session, 2, "yes"));
        thread.appendMessage(question("first"));
        assertNull(thread.consumeChoice(session, 1, "yes"));
        assertNotNull(thread.consumeChoice(session, 3, "yes"));
    }

    @Test void unknownChoicesDoNotConsumeTheQuestionAndReloadInvalidatesOldReceipts() {
        var old = new TalkThread("thread", "script", 0, 0);
        var current = new TalkThread("thread", "script", 0, 0);
        current.appendMessage(question("first"));
        assertNull(current.consumeChoice(old.getSessionId(), 1, "yes"));
        assertNull(current.consumeChoice(current.getSessionId(), 1, "missing"));
        assertNotNull(current.consumeChoice(current.getSessionId(), 1, "yes"));
    }

    @Test void snapshotKeepsTheServerSession() {
        var server = new TalkThread("thread", "script", 0, 0);
        server.appendMessage(question("first"));
        var client = PayloadSync.NetThread.fromThread(server).toThread();
        assertEquals(server.getSessionId(), client.getSessionId());
        assertNotNull(server.consumeChoice(client.getSessionId(), client.getMessages().size(), "yes"));
    }
}

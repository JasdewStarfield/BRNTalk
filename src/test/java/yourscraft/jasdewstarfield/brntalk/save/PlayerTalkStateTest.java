package yourscraft.jasdewstarfield.brntalk.save;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTalkStateTest {
    @Test
    void conversationCompletionIsIdempotentAndPersists() {
        PlayerTalkState state = new PlayerTalkState();

        assertTrue(state.markConversationCompleted("intro"));
        assertFalse(state.markConversationCompleted("intro"));

        CompoundTag saved = new CompoundTag();
        state.saveToNbt(saved);
        PlayerTalkState restored = PlayerTalkState.fromNbt(saved);

        assertTrue(restored.hasCompletedConversation("intro"));
        assertTrue(restored.clearCompletedConversation("intro"));
        assertFalse(restored.hasCompletedConversation("intro"));
        assertTrue(restored.isEmpty());
    }

    @Test
    void legacyStateWithoutCompletionLedgerStillLoadsThreads() {
        PlayerTalkState state = new PlayerTalkState();
        state.startThread("thread", "intro", "start");
        CompoundTag legacy = new CompoundTag();
        state.saveToNbt(legacy);
        legacy.remove("completedScripts");

        PlayerTalkState restored = PlayerTalkState.fromNbt(legacy);

        assertTrue(restored.hasSeenMessage("intro", "start"));
        assertFalse(restored.hasCompletedConversation("intro"));
    }

    @Test
    void externalActionReceiptPreventsReplayAndPersistsOutcome() {
        PlayerTalkState state = new PlayerTalkState();

        assertTrue(state.beginExternalAction("owner|reward|cycle", "start:intro"));
        assertFalse(state.beginExternalAction("owner|reward|cycle", "start:intro"));
        assertTrue(state.getExternalActionReceipt("owner|reward|cycle").status()
                == PlayerTalkState.ExternalActionStatus.PENDING);
        state.finishExternalAction("owner|reward|cycle", true);

        CompoundTag saved = new CompoundTag();
        state.saveToNbt(saved);
        PlayerTalkState restored = PlayerTalkState.fromNbt(saved);

        PlayerTalkState.ExternalActionReceipt receipt = restored.getExternalActionReceipt("owner|reward|cycle");
        assertTrue(receipt != null && receipt.action().equals("start:intro")
                && receipt.status() == PlayerTalkState.ExternalActionStatus.SUCCEEDED);
        assertFalse(restored.beginExternalAction("owner|reward|cycle", "start:intro"));
    }
}

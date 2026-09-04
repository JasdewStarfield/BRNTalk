package yourscraft.jasdewstarfield.brntalk.save;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression coverage for the version-neutral conversation completion ledger. */
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
}

package yourscraft.jasdewstarfield.brntalk.platform;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;

public final class BrntalkPlatform {
    private BrntalkPlatform() {
    }

    public static PlayerTalkState getTalkState(ServerPlayer player) {
        // Keep shared gameplay code away from the concrete player-data backend.
        return TalkStateStorage.get(player);
    }

    public static void setTalkState(ServerPlayer player, PlayerTalkState state) {
        // Forge 1.20.1 can replace this backend without touching dialogue logic.
        TalkStateStorage.set(player, state);
    }

    public static boolean migrateLegacyTalkStateOnLogin(ServerPlayer player) {
        /*
         * The NeoForge attachment branch migrates old SavedData into player attachments.
         * This Forge branch already uses SavedData as its live backend, so running that
         * migration here would only copy data back into the same store.
         */
        return false;
    }

    public static void postPlayerSeenMessage(ServerPlayer player, String scriptId, String messageId) {
        // Hide the concrete event bus so the public gameplay API stays portable.
        PlatformEvents.postPlayerSeenMessage(player, scriptId, messageId);
    }

    /** Marks a terminal text node complete; WAIT and CHOICE nodes require an explicit continuation decision. */
    public static void completeConversationIfTerminal(ServerPlayer player, TalkThread thread) {
        TalkMessage current = thread == null ? null : thread.getCurrentMessage();
        if (current != null && current.getType() == TalkMessage.Type.TEXT && current.getNextId() == null) {
            completeConversation(player, thread);
        }
    }

    /** Persists a completion exactly once before notifying integrations. */
    public static void completeConversation(ServerPlayer player, TalkThread thread) {
        if (player == null || thread == null) return;
        PlayerTalkState state = getTalkState(player);
        if (state.markConversationCompleted(thread.getScriptId())) {
            PlatformEvents.postPlayerCompletedConversation(player, thread.getScriptId(), thread.getId());
        }
    }
}

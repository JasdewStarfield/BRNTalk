package yourscraft.jasdewstarfield.brntalk.platform;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;
import yourscraft.jasdewstarfield.brntalk.save.TalkWorldData;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

public final class BrntalkPlatform {
    private BrntalkPlatform() {
    }

    public static PlayerTalkState getTalkState(ServerPlayer player) {
        // Keep shared gameplay code away from the concrete player-data backend.
        return TalkStateStorage.get(player);
    }

    public static void setTalkState(ServerPlayer player, PlayerTalkState state) {
        // Replace the concrete player-state backend without touching dialogue logic.
        TalkStateStorage.set(player, state);
    }

    @SuppressWarnings("deprecation")
    public static boolean migrateLegacyTalkStateOnLogin(ServerPlayer player) {
        /*
         * NeoForge stores live player state in data attachments. Older worlds may still
         * contain the pre-attachment SavedData, so migrate it once on login.
         */
        ServerLevel level = player.serverLevel();
        TalkWorldData oldGlobalData = TalkWorldData.get(level);
        PlayerTalkState oldState = oldGlobalData.get(player.getUUID());

        if (oldState == null) {
            return false;
        }

        setTalkState(player, oldState);
        oldGlobalData.removeAllThread(player.getUUID());
        return true;
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

    /** Persists a completion exactly once before notifying optional integrations. */
    public static void completeConversation(ServerPlayer player, TalkThread thread) {
        if (player == null || thread == null) return;
        PlayerTalkState state = getTalkState(player);
        if (state.markConversationCompleted(thread.getScriptId())) {
            PlatformEvents.postPlayerCompletedConversation(player, thread.getScriptId(), thread.getId());
        }
    }
}

package yourscraft.jasdewstarfield.brntalk.platform;

import net.minecraft.server.level.ServerPlayer;
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

    public static void postPlayerSeenMessage(ServerPlayer player, String scriptId, String messageId) {
        // Hide the concrete event bus so the public gameplay API stays portable.
        PlatformEvents.postPlayerSeenMessage(player, scriptId, messageId);
    }
}

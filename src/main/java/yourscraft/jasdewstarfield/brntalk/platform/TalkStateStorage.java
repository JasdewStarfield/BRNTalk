package yourscraft.jasdewstarfield.brntalk.platform;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brntalk.BrntalkRegistries;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;

public final class TalkStateStorage {
    private TalkStateStorage() {
    }

    public static PlayerTalkState get(ServerPlayer player) {
        // NeoForge stores per-player dialogue progress in a data attachment.
        return player.getData(BrntalkRegistries.PLAYER_TALK_STATE);
    }

    public static void set(ServerPlayer player, PlayerTalkState state) {
        // Replacing the whole state is used by clear and legacy-data migration flows.
        player.setData(BrntalkRegistries.PLAYER_TALK_STATE, state);
    }
}

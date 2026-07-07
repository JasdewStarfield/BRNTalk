package yourscraft.jasdewstarfield.brntalk.platform;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;
import yourscraft.jasdewstarfield.brntalk.save.TalkWorldData;

public final class TalkStateStorage {
    private TalkStateStorage() {
    }

    public static PlayerTalkState get(ServerPlayer player) {
        /*
         * Forge 1.20.1 没有新版本分支使用的数据附件。
         * 这里先用 SavedData 作为分支本地后端，后续若切 Capability 只需要替换本类。
         */
        TalkWorldData data = TalkWorldData.get(player.server.overworld());
        PlayerTalkState state = data.getOrCreate(player.getUUID());
        data.setDirty();
        return state;
    }

    public static void set(ServerPlayer player, PlayerTalkState state) {
        // Replacing the whole state is used by clear and legacy-data migration flows.
        TalkWorldData data = TalkWorldData.get(player.server.overworld());
        data.set(player.getUUID(), state);
    }
}

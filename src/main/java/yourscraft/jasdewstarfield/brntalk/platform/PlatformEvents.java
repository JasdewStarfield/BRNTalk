package yourscraft.jasdewstarfield.brntalk.platform;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import yourscraft.jasdewstarfield.brntalk.event.PlayerSeenMessageEvent;

public final class PlatformEvents {
    private PlatformEvents() {
    }

    public static void postPlayerSeenMessage(ServerPlayer player, String scriptId, String messageId) {
        // NeoForge owns the concrete event bus on this branch.
        NeoForge.EVENT_BUS.post(new PlayerSeenMessageEvent(player, scriptId, messageId));
    }
}

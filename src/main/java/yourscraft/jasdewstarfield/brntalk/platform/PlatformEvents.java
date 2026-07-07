package yourscraft.jasdewstarfield.brntalk.platform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import yourscraft.jasdewstarfield.brntalk.event.PlayerSeenMessageEvent;

public final class PlatformEvents {
    private PlatformEvents() {
    }

    public static void postPlayerSeenMessage(ServerPlayer player, String scriptId, String messageId) {
        // Forge owns the concrete event bus on this branch.
        MinecraftForge.EVENT_BUS.post(new PlayerSeenMessageEvent(player, scriptId, messageId));
    }
}

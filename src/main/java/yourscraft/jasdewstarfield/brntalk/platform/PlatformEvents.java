package yourscraft.jasdewstarfield.brntalk.platform;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import yourscraft.jasdewstarfield.brntalk.event.PlayerSeenMessageEvent;
import yourscraft.jasdewstarfield.brntalk.event.PlayerCompletedConversationEvent;

public final class PlatformEvents {
    private PlatformEvents() {
    }

    public static void postPlayerSeenMessage(ServerPlayer player, String scriptId, String messageId) {
        // NeoForge owns the concrete event bus on this branch.
        NeoForge.EVENT_BUS.post(new PlayerSeenMessageEvent(player, scriptId, messageId));
    }

    public static void postPlayerCompletedConversation(ServerPlayer player, String scriptId, String threadId) {
        // Completion is emitted only after PlayerTalkState accepted the first completion marker.
        NeoForge.EVENT_BUS.post(new PlayerCompletedConversationEvent(player, scriptId, threadId));
    }
}

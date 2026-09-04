package yourscraft.jasdewstarfield.brntalk.event;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Fired once after a conversation completion is persisted in the player's server state. */
public final class PlayerCompletedConversationEvent extends PlayerEvent {
    private final String scriptId;
    private final String threadId;

    public PlayerCompletedConversationEvent(Player player, String scriptId, String threadId) {
        super(player);
        this.scriptId = scriptId;
        this.threadId = threadId;
    }

    public String getScriptId() {
        return scriptId;
    }

    public String getThreadId() {
        return threadId;
    }
}

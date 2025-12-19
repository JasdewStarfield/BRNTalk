package yourscraft.jasdewstarfield.brntalk.event;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class PlayerSeenMessageEvent extends PlayerEvent {
    private final String scriptId;
    private final String messageId;

    public PlayerSeenMessageEvent(Player player, String scriptId, String messageId) {
        super(player);
        this.scriptId = scriptId;
        this.messageId = messageId;
    }

    public String getScriptId() {
        return scriptId;
    }

    public String getMessageId() {
        return messageId;
    }
}

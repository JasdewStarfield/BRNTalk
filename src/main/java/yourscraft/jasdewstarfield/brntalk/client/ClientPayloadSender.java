package yourscraft.jasdewstarfield.brntalk.client;

import net.neoforged.neoforge.network.PacketDistributor;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork;

public class ClientPayloadSender {
    public static void requestOpenTalk() {
        PacketDistributor.sendToServer(new TalkNetwork.RequestOpenTalkPayload());
    }
}

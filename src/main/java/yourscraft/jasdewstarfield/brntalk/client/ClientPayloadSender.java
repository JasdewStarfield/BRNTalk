package yourscraft.jasdewstarfield.brntalk.client;

import net.neoforged.neoforge.network.PacketDistributor;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork;

public class ClientPayloadSender {
    public static void requestOpenTalk() {
        PacketDistributor.sendToServer(new TalkNetwork.RequestOpenTalkPayload());
    }

    public static void sendSelectChoice(String threadId, String choiceId) {
        PacketDistributor.sendToServer(new TalkNetwork.SelectChoicePayload(threadId, choiceId));
    }

    public static void sendMarkRead(String threadId) {
        PacketDistributor.sendToServer(new TalkNetwork.MarkThreadReadPayload(threadId));
    }
}

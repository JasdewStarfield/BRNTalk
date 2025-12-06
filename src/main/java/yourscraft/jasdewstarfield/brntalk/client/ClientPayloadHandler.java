package yourscraft.jasdewstarfield.brntalk.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import yourscraft.jasdewstarfield.brntalk.client.ui.TalkScreen;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork;

public class ClientPayloadHandler {
    public static void handleOpenTalkScreen(final TalkNetwork.OpenTalkScreenPayload payload,
                                            final IPayloadContext context) {
        // 如果你以后把 registrar 改到 HandlerThread.NETWORK 之类，
        // 这里就要用 enqueueWork；现在默认主线程，其实可以直接 setScreen。
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new TalkScreen());
        });
    }
}

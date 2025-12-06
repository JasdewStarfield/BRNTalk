package yourscraft.jasdewstarfield.brntalk.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import yourscraft.jasdewstarfield.brntalk.client.ui.TalkScreen;
import yourscraft.jasdewstarfield.brntalk.network.PayloadSync;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.List;

public class ClientPayloadHandler {
    public static void handleOpenTalkScreen(final TalkNetwork.OpenTalkScreenPayload payload,
                                            final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new TalkScreen());
        });
    }

    public static void handleSyncThreads(PayloadSync.SyncThreadsPayload payload,
                                         IPayloadContext context) {
        context.enqueueWork(() -> {
            List<TalkThread> threads = payload.threads().stream()
                    .map(PayloadSync.NetThread::toThread)
                    .toList();
            ClientTalkState.get().setThreads(threads);
        });

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TalkScreen screen) {
            screen.onThreadsSynced();
        }
    }
}

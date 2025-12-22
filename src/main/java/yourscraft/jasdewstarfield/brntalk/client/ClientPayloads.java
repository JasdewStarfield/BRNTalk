package yourscraft.jasdewstarfield.brntalk.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.network.PayloadSync;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork.OpenTalkScreenPayload;

@EventBusSubscriber(modid = Brntalk.MODID, value = Dist.CLIENT)
// 这一段不会在 Dedicated Server 上执行
public class ClientPayloads {
    @SubscribeEvent
    public static void registerClient(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                OpenTalkScreenPayload.TYPE,
                OpenTalkScreenPayload.STREAM_CODEC,
                ClientPayloadHandler::handleOpenTalkScreen
        );

        registrar.playToClient(
                PayloadSync.SyncThreadsPayload.TYPE,
                PayloadSync.SyncThreadsPayload.STREAM_CODEC,
                ClientPayloadHandler::handleSyncThreads
        );

        registrar.playToClient(
                PayloadSync.AppendMessagesPayload.TYPE,
                PayloadSync.AppendMessagesPayload.STREAM_CODEC,
                ClientPayloadHandler::handleAppendMessages
        );

        registrar.playToClient(
                PayloadSync.AddThreadPayload.TYPE,
                PayloadSync.AddThreadPayload.STREAM_CODEC,
                ClientPayloadHandler::handleAddThread
        );

        registrar.playToClient(
                PayloadSync.UpdateStatePayload.TYPE,
                PayloadSync.UpdateStatePayload.STREAM_CODEC,
                ClientPayloadHandler::handleUpdateState
        );
    }
}

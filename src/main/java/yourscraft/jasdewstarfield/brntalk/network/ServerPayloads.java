package yourscraft.jasdewstarfield.brntalk.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import yourscraft.jasdewstarfield.brntalk.Brntalk;

@EventBusSubscriber(modid = Brntalk.MODID, value = Dist.DEDICATED_SERVER)
public class ServerPayloads {
    @SubscribeEvent
    public static void registerServer(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // 在服务端也注册同一个 TYPE 和 STREAM_CODEC, 但是为空，不会被调用
        registrar.playToClient(
                TalkNetwork.OpenTalkScreenPayload.TYPE,
                TalkNetwork.OpenTalkScreenPayload.STREAM_CODEC,
                (payload, context) -> {
                }
        );

        registrar.playToClient(
                PayloadSync.SyncThreadsPayload.TYPE,
                PayloadSync.SyncThreadsPayload.STREAM_CODEC,
                (payload, context) -> {
                }
        );

        registrar.playToClient(
                PayloadSync.AppendMessagesPayload.TYPE,
                PayloadSync.AppendMessagesPayload.STREAM_CODEC,
                (payload, context) -> {
                }
        );

        registrar.playToClient(
                PayloadSync.AddThreadPayload.TYPE,
                PayloadSync.AddThreadPayload.STREAM_CODEC,
                (payload, context) -> {
                }
        );

        registrar.playToClient(
                PayloadSync.UpdateStatePayload.TYPE,
                PayloadSync.UpdateStatePayload.STREAM_CODEC,
                (payload, context) -> {
                }
        );
    }
}

package yourscraft.jasdewstarfield.brntalk.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.client.ClientPayloadHandler;

@EventBusSubscriber(modid = Brntalk.MODID)
public class TalkNetwork {

    public record RequestOpenTalkPayload() implements CustomPacketPayload {
        public static final Type<RequestOpenTalkPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "request_open"));

        public static final StreamCodec<ByteBuf, RequestOpenTalkPayload> STREAM_CODEC =
                StreamCodec.unit(new RequestOpenTalkPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenTalkScreenPayload() implements CustomPacketPayload {
        public static final Type<OpenTalkScreenPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "open_screen"));

        public static final StreamCodec<ByteBuf, OpenTalkScreenPayload> STREAM_CODEC =
                StreamCodec.unit(new OpenTalkScreenPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // 客户端 -> 服务端 的包
        registrar.playToServer(
                RequestOpenTalkPayload.TYPE,
                RequestOpenTalkPayload.STREAM_CODEC,
                TalkNetwork::handleRequestOpenTalk
        );

        // 服务端 -> 客户端 的包
        registrar.playToClient(
                OpenTalkScreenPayload.TYPE,
                OpenTalkScreenPayload.STREAM_CODEC,
                ClientPayloadHandler::handleOpenTalkScreen
        );
    }

    public static void handleRequestOpenTalk(final RequestOpenTalkPayload payload, final IPayloadContext context) {
        // 这个 handler 只会在服务端逻辑侧被调用
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        PacketDistributor.sendToPlayer(serverPlayer, new OpenTalkScreenPayload());
    }
}

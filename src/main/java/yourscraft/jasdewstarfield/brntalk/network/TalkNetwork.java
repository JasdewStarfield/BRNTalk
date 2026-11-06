package yourscraft.jasdewstarfield.brntalk.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.client.ClientPayloadHandler;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.platform.TalkNetworking;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import yourscraft.jasdewstarfield.brntalk.runtime.ConversationService;
import yourscraft.jasdewstarfield.brntalk.platform.ServerConversationPlayer;

import java.util.List;

@EventBusSubscriber(modid = Brntalk.MODID)
public class TalkNetwork {

    public record RequestOpenTalkPayload() implements CustomPacketPayload {
        public static final Type<RequestOpenTalkPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "request_open"));

        public static final StreamCodec<ByteBuf, RequestOpenTalkPayload> STREAM_CODEC =
                StreamCodec.unit(new RequestOpenTalkPayload());

        @Override
        public CustomPacketPayload.@NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenTalkScreenPayload() implements CustomPacketPayload {
        public static final Type<OpenTalkScreenPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "open_screen"));

        public static final StreamCodec<ByteBuf, OpenTalkScreenPayload> STREAM_CODEC =
                StreamCodec.unit(new OpenTalkScreenPayload());

        @Override
        public CustomPacketPayload.@NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SelectChoicePayload(String threadId, String choiceId, String sessionId, int position) implements CustomPacketPayload {
        public static final Type<SelectChoicePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "select_choice"));

        public static final StreamCodec<ByteBuf, SelectChoicePayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, SelectChoicePayload::threadId,
                        ByteBufCodecs.STRING_UTF8, SelectChoicePayload::choiceId,
                        ByteBufCodecs.stringUtf8(36), SelectChoicePayload::sessionId,
                        ByteBufCodecs.VAR_INT, SelectChoicePayload::position,
                        SelectChoicePayload::new
                );

        @Override
        public CustomPacketPayload.@NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MarkThreadReadPayload(String threadId) implements CustomPacketPayload {

        public static final Type<MarkThreadReadPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "mark_read"));

        public static final StreamCodec<ByteBuf, MarkThreadReadPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, MarkThreadReadPayload::threadId,
                        MarkThreadReadPayload::new
                );

        @Override
        public CustomPacketPayload.@NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        // Choice receipts and bounded transfers require matching client/server protocol versions.
        final PayloadRegistrar registrar = event.registrar("2");
        registerClientPacket(registrar, TalkTransfer.Frame.TYPE, TalkTransfer.Frame.CODEC,
                ClientPacketDelegate::handleTransfer);

        // 客户端 -> 服务端 的包
        registrar.playToServer(
                RequestOpenTalkPayload.TYPE,
                RequestOpenTalkPayload.STREAM_CODEC,
                TalkNetwork::handleRequestOpenTalk
        );

        registrar.playToServer(
                SelectChoicePayload.TYPE,
                SelectChoicePayload.STREAM_CODEC,
                TalkNetwork::handleSelectChoice
        );

        registrar.playToServer(
                MarkThreadReadPayload.TYPE,
                MarkThreadReadPayload.STREAM_CODEC,
                TalkNetwork::handleMarkRead
        );

        // 服务端 -> 客户端 的包
        registerClientPacket(
                registrar,
                PayloadSync.SyncThreadsPayload.TYPE,
                PayloadSync.SyncThreadsPayload.STREAM_CODEC,
                ClientPacketDelegate::handleSyncThreads
        );

        registerClientPacket(
                registrar,
                PayloadSync.AddThreadPayload.TYPE,
                PayloadSync.AddThreadPayload.STREAM_CODEC,
                ClientPacketDelegate::handleAddThread
        );

        registerClientPacket(
                registrar,
                PayloadSync.AppendMessagesPayload.TYPE,
                PayloadSync.AppendMessagesPayload.STREAM_CODEC,
                ClientPacketDelegate::handleAppendMessages
        );

        registerClientPacket(
                registrar,
                PayloadSync.UpdateStatePayload.TYPE,
                PayloadSync.UpdateStatePayload.STREAM_CODEC,
                ClientPacketDelegate::handleUpdateState
        );

        registerClientPacket(
                registrar,
                OpenTalkScreenPayload.TYPE,
                OpenTalkScreenPayload.STREAM_CODEC,
                ClientPacketDelegate::handleOpenTalkScreen
        );
    }

    public static void handleRequestOpenTalk(final RequestOpenTalkPayload payload, final IPayloadContext context) {
        // 这个 handler 只会在服务端逻辑侧被调用
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ConversationService.getInstance().open(new ServerConversationPlayer(serverPlayer));
    }

    public static void handleSelectChoice(final SelectChoicePayload payload,
                                          final IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ConversationService.getInstance().select(new ServerConversationPlayer(player), payload.threadId(),
                payload.choiceId(), payload.sessionId(), payload.position());
    }

    public static void handleMarkRead(final MarkThreadReadPayload payload, final IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> ConversationService.getInstance().markRead(new ServerConversationPlayer(player), payload.threadId()));
    }

    // 保留旧入口，避免外部调用方在迁移期间立刻断裂。
    public static void syncThreadsTo(ServerPlayer player) {
        TalkNetworking.syncThreadsTo(player);
    }

    public static void sendAddThread(ServerPlayer player, TalkThread thread) {
        TalkNetworking.sendAddThread(player, thread);
    }

    public static void sendAppendMessages(ServerPlayer player, String threadId, List<TalkMessage> newMessages) {
        TalkNetworking.sendAppendMessages(player, threadId, newMessages);
    }

    public static void sendUpdateState(ServerPlayer player, String threadId, long lastReadTime) {
        TalkNetworking.sendUpdateState(player, threadId, lastReadTime);
    }

    /**
     * 辅助方法：安全地注册客户端包
     * 如果是客户端，使用提供的 realHandler；
     * 如果是服务端，注册一个空 Handler (No-op)。
     */
    private static <T extends CustomPacketPayload> void registerClientPacket(
            PayloadRegistrar registrar,
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super ByteBuf, T> codec,
            IPayloadHandler<T> clientHandlerProvider
    ) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // 在客户端：注册真正的处理逻辑
            registrar.playToClient(type, codec, clientHandlerProvider);
        } else {
            // 在服务端：注册占位符，仅为了握手同步
            registrar.playToClient(type, codec, (payload, context) -> {});
        }
    }

    private static class ClientPacketDelegate {
        public static void handleTransfer(TalkTransfer.Frame p, IPayloadContext c) {
            ClientPayloadHandler.handleTransfer(p, c);
        }
        public static void handleSyncThreads(PayloadSync.SyncThreadsPayload p, IPayloadContext c) {
            ClientPayloadHandler.handleSyncThreads(p, c);
        }
        public static void handleAddThread(PayloadSync.AddThreadPayload p, IPayloadContext c) {
            ClientPayloadHandler.handleAddThread(p, c);
        }
        public static void handleAppendMessages(PayloadSync.AppendMessagesPayload p, IPayloadContext c) {
            ClientPayloadHandler.handleAppendMessages(p, c);
        }
        public static void handleUpdateState(PayloadSync.UpdateStatePayload p, IPayloadContext c) {
            ClientPayloadHandler.handleUpdateState(p, c);
        }
        public static void handleOpenTalkScreen(TalkNetwork.OpenTalkScreenPayload p, IPayloadContext c) {
            ClientPayloadHandler.handleOpenTalkScreen(p, c);
        }
    }
}

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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.BrntalkRegistries;
import yourscraft.jasdewstarfield.brntalk.client.ClientPayloadHandler;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.event.PlayerSeenMessageEvent;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;

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

    public record SelectChoicePayload(String threadId, String choiceId) implements CustomPacketPayload {
        public static final Type<SelectChoicePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "select_choice"));

        public static final StreamCodec<ByteBuf, SelectChoicePayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, SelectChoicePayload::threadId,
                        ByteBufCodecs.STRING_UTF8, SelectChoicePayload::choiceId,
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
        final PayloadRegistrar registrar = event.registrar("1");

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
        TalkNetwork.syncThreadsTo(serverPlayer);
        PacketDistributor.sendToPlayer(serverPlayer, new OpenTalkScreenPayload());
    }

    public static void handleSelectChoice(final SelectChoicePayload payload,
                                          final IPayloadContext context) {
        // 这个 handler 在服务端逻辑线程上调用
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        String threadId = payload.threadId();
        String choiceId = payload.choiceId();

        TalkManager manager = TalkManager.getInstance();
        TalkThread thread = manager.getActiveThread(serverPlayer.getUUID(), threadId);

        if (thread == null) return;

        // 1. 在最后一条消息里找到玩家选的那个 Choice
        TalkMessage lastMsg = thread.getCurrentMessage();
        if (lastMsg == null) return;

        TalkMessage.Choice selected = null;
        for (TalkMessage.Choice c : lastMsg.getChoices()) {
            if (c.getId().equals(choiceId)) {
                selected = c;
                break;
            }
        }
        if (selected == null) return;

        // 2. 获取跳转目标 ID (nextId)
        String nextMsgId = selected.getNextId();
        if (nextMsgId == null || nextMsgId.isEmpty()) {
            // 没有后续 => 对话结束
            return;
        }

        // 3. 推进剧情 (在内存中追加消息)
        List<TalkMessage> newMsgs = manager.proceedThread(serverPlayer, threadId, nextMsgId);
        if (!newMsgs.isEmpty()) {
            List<String> newIds = newMsgs.stream().map(TalkMessage::getId).toList();
            // 4. 保存到存档 (批量追加)
            PlayerTalkState state = serverPlayer.getData(BrntalkRegistries.PLAYER_TALK_STATE);
            state.appendMessages(threadId, newIds);

            // 5. 触发 PlayerSeenMessageEvent 事件
            String scriptId = thread.getScriptId();
            for (String msgId : newIds) {
                NeoForge.EVENT_BUS.post(new PlayerSeenMessageEvent(serverPlayer, scriptId, msgId));
            }

            // 6. 同步给客户端
            TalkNetwork.sendAppendMessages(serverPlayer, threadId, newMsgs);
        }
    }

    public static void handleMarkRead(final MarkThreadReadPayload payload, final IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

        context.enqueueWork(() -> {
            String threadId = payload.threadId();
            long now = System.currentTimeMillis();

            PlayerTalkState state = serverPlayer.getData(BrntalkRegistries.PLAYER_TALK_STATE);
            state.updateLastReadTime(threadId, now);

            TalkManager manager = TalkManager.getInstance();
            TalkThread activeThread = manager.getActiveThread(serverPlayer.getUUID(), threadId);
            if (activeThread != null) {
                activeThread.setLastReadTime(now);
            }

            // 更新完 NBT 后，同步回客户端
            TalkNetwork.sendUpdateState(serverPlayer, threadId, now);
        });
    }

    // 全量同步
    public static void syncThreadsTo(ServerPlayer player) {
        var threads = TalkManager.getInstance().getActiveThreads(player.getUUID());

        List<PayloadSync.NetThread> netThreads = threads.stream()
                .map(PayloadSync.NetThread::fromThread)
                .toList();

        PacketDistributor.sendToPlayer(player, new PayloadSync.SyncThreadsPayload(netThreads));
    }

    // 新线程同步
    public static void sendAddThread(ServerPlayer player, TalkThread thread) {
        PayloadSync.NetThread netThread = PayloadSync.NetThread.fromThread(thread);
        PacketDistributor.sendToPlayer(player, new PayloadSync.AddThreadPayload(netThread));
    }

    // 增量消息同步
    public static void sendAppendMessages(ServerPlayer player, String threadId, List<TalkMessage> newMessages) {
        if (newMessages.isEmpty()) return;

        List<PayloadSync.NetMessage> netMsgs = newMessages.stream()
                .map(PayloadSync.NetMessage::fromMessage)
                .toList();

        PacketDistributor.sendToPlayer(player, new PayloadSync.AppendMessagesPayload(threadId, netMsgs));
    }

    public static void sendUpdateState(ServerPlayer player, String threadId, long lastReadTime) {
        PacketDistributor.sendToPlayer(player, new PayloadSync.UpdateStatePayload(threadId, lastReadTime));
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

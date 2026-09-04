package yourscraft.jasdewstarfield.brntalk.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.platform.BrntalkPlatform;
import yourscraft.jasdewstarfield.brntalk.platform.TalkNetworking;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class TalkNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Brntalk.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public record RequestOpenTalkPayload() {
        public static void encode(RequestOpenTalkPayload payload, FriendlyByteBuf buf) {
            // 空包：只表达客户端想打开对话界面的意图。
        }

        public static RequestOpenTalkPayload decode(FriendlyByteBuf buf) {
            return new RequestOpenTalkPayload();
        }
    }

    public record OpenTalkScreenPayload() {
        public static void encode(OpenTalkScreenPayload payload, FriendlyByteBuf buf) {
            // 空包：服务端只通知客户端打开界面。
        }

        public static OpenTalkScreenPayload decode(FriendlyByteBuf buf) {
            return new OpenTalkScreenPayload();
        }
    }

    public record SelectChoicePayload(String threadId, String choiceId) {
        public static void encode(SelectChoicePayload payload, FriendlyByteBuf buf) {
            buf.writeUtf(payload.threadId());
            buf.writeUtf(payload.choiceId());
        }

        public static SelectChoicePayload decode(FriendlyByteBuf buf) {
            return new SelectChoicePayload(buf.readUtf(), buf.readUtf());
        }
    }

    public record MarkThreadReadPayload(String threadId) {
        public static void encode(MarkThreadReadPayload payload, FriendlyByteBuf buf) {
            buf.writeUtf(payload.threadId());
        }

        public static MarkThreadReadPayload decode(FriendlyByteBuf buf) {
            return new MarkThreadReadPayload(buf.readUtf());
        }
    }

    public static void register() {
        registerServerPacket(RequestOpenTalkPayload.class, RequestOpenTalkPayload::encode,
                RequestOpenTalkPayload::decode, TalkNetwork::handleRequestOpenTalk);
        registerServerPacket(SelectChoicePayload.class, SelectChoicePayload::encode,
                SelectChoicePayload::decode, TalkNetwork::handleSelectChoice);
        registerServerPacket(MarkThreadReadPayload.class, MarkThreadReadPayload::encode,
                MarkThreadReadPayload::decode, TalkNetwork::handleMarkRead);

        registerClientPacket(PayloadSync.SyncThreadsPayload.class, PayloadSync.SyncThreadsPayload::encode,
                PayloadSync.SyncThreadsPayload::decode, TalkNetwork::handleSyncThreadsClient);
        registerClientPacket(PayloadSync.AddThreadPayload.class, PayloadSync.AddThreadPayload::encode,
                PayloadSync.AddThreadPayload::decode, TalkNetwork::handleAddThreadClient);
        registerClientPacket(PayloadSync.AppendMessagesPayload.class, PayloadSync.AppendMessagesPayload::encode,
                PayloadSync.AppendMessagesPayload::decode, TalkNetwork::handleAppendMessagesClient);
        registerClientPacket(PayloadSync.UpdateStatePayload.class, PayloadSync.UpdateStatePayload::encode,
                PayloadSync.UpdateStatePayload::decode, TalkNetwork::handleUpdateStateClient);
        registerClientPacket(OpenTalkScreenPayload.class, OpenTalkScreenPayload::encode,
                OpenTalkScreenPayload::decode, TalkNetwork::handleOpenTalkScreenClient);
    }

    public static void handleRequestOpenTalk(final RequestOpenTalkPayload payload,
                                             final Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer serverPlayer = context.getSender();
        if (serverPlayer == null) {
            return;
        }
        TalkNetworking.syncThreadsTo(serverPlayer);
        TalkNetworking.sendOpenTalkScreen(serverPlayer);
    }

    public static void handleSelectChoice(final SelectChoicePayload payload,
                                          final Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer serverPlayer = context.getSender();
        if (serverPlayer == null) {
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
            // 无后继选项是玩家在服务端明确选择的结束点。
            BrntalkPlatform.completeConversation(serverPlayer, thread);
            return;
        }

        // 3. 推进剧情 (在内存中追加消息)
        List<TalkMessage> newMsgs = manager.proceedThread(serverPlayer, threadId, nextMsgId);
        if (!newMsgs.isEmpty()) {
            List<String> newIds = newMsgs.stream().map(TalkMessage::getId).toList();
            // 4. 保存到存档 (批量追加)
            PlayerTalkState state = BrntalkPlatform.getTalkState(serverPlayer);
            state.appendMessages(threadId, newIds);

            // 5. 触发 PlayerSeenMessageEvent 事件
            String scriptId = thread.getScriptId();
            for (String msgId : newIds) {
                BrntalkPlatform.postPlayerSeenMessage(serverPlayer, scriptId, msgId);
            }

            BrntalkPlatform.completeConversationIfTerminal(serverPlayer, thread);

            // 6. 同步给客户端
            TalkNetworking.sendAppendMessages(serverPlayer, threadId, newMsgs);
        }
    }

    public static void handleMarkRead(final MarkThreadReadPayload payload,
                                      final Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer serverPlayer = context.getSender();
        if (serverPlayer == null) return;

        String threadId = payload.threadId();
        long now = System.currentTimeMillis();

        PlayerTalkState state = BrntalkPlatform.getTalkState(serverPlayer);
        state.updateLastReadTime(threadId, now);

        TalkManager manager = TalkManager.getInstance();
        TalkThread activeThread = manager.getActiveThread(serverPlayer.getUUID(), threadId);
        if (activeThread != null) {
            activeThread.setLastReadTime(now);
        }

        // 更新完 NBT 后，同步回客户端
        TalkNetworking.sendUpdateState(serverPlayer, threadId, now);
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

    private static void handleSyncThreadsClient(PayloadSync.SyncThreadsPayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        ClientPacketDelegate.handleSyncThreads(payload);
    }

    private static void handleAddThreadClient(PayloadSync.AddThreadPayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        ClientPacketDelegate.handleAddThread(payload);
    }

    private static void handleAppendMessagesClient(PayloadSync.AppendMessagesPayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        ClientPacketDelegate.handleAppendMessages(payload);
    }

    private static void handleUpdateStateClient(PayloadSync.UpdateStatePayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        ClientPacketDelegate.handleUpdateState(payload);
    }

    private static void handleOpenTalkScreenClient(OpenTalkScreenPayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        ClientPacketDelegate.handleOpenTalkScreen(payload);
    }

    private static <T> void registerServerPacket(
            Class<T> packetClass,
            BiConsumer<T, FriendlyByteBuf> encoder,
            FriendlyByteBuf.Reader<T> decoder,
            MessageHandler<T> handler
    ) {
        CHANNEL.messageBuilder(packetClass, nextPacketId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread(handler::handle)
                .add();
    }

    private static <T> void registerClientPacket(
            Class<T> packetClass,
            BiConsumer<T, FriendlyByteBuf> encoder,
            FriendlyByteBuf.Reader<T> decoder,
            ClientMessageHandler<T> handler
    ) {
        CHANNEL.messageBuilder(packetClass, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread((payload, contextSupplier) -> handler.handle(payload))
                .add();
    }

    /*
     * 客户端处理器会引用 Minecraft 客户端 UI 类。
     * 专用服务端只经过上面的 dist 判断，不加载这个内部委托，避免 common setup 阶段触发 DistCleaner。
     */
    private static class ClientPacketDelegate {
        private static void handleSyncThreads(PayloadSync.SyncThreadsPayload payload) {
            yourscraft.jasdewstarfield.brntalk.client.ClientPayloadHandler.handleSyncThreads(payload);
        }

        private static void handleAddThread(PayloadSync.AddThreadPayload payload) {
            yourscraft.jasdewstarfield.brntalk.client.ClientPayloadHandler.handleAddThread(payload);
        }

        private static void handleAppendMessages(PayloadSync.AppendMessagesPayload payload) {
            yourscraft.jasdewstarfield.brntalk.client.ClientPayloadHandler.handleAppendMessages(payload);
        }

        private static void handleUpdateState(PayloadSync.UpdateStatePayload payload) {
            yourscraft.jasdewstarfield.brntalk.client.ClientPayloadHandler.handleUpdateState(payload);
        }

        private static void handleOpenTalkScreen(OpenTalkScreenPayload payload) {
            yourscraft.jasdewstarfield.brntalk.client.ClientPayloadHandler.handleOpenTalkScreen(payload);
        }
    }

    private static int nextPacketId() {
        return packetId++;
    }

    @FunctionalInterface
    private interface MessageHandler<T> {
        void handle(T payload, Supplier<NetworkEvent.Context> contextSupplier);
    }

    @FunctionalInterface
    private interface ClientMessageHandler<T> {
        void handle(T payload);
    }
}

package yourscraft.jasdewstarfield.brntalk.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
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
import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import yourscraft.jasdewstarfield.brntalk.save.TalkWorldData;

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

        registrar.playToServer(
                SelectChoicePayload.TYPE,
                SelectChoicePayload.STREAM_CODEC,
                TalkNetwork::handleSelectChoice
        );

        // 服务端 -> 客户端 的包在 ClientPayloads 注册（服务端对应的包在ServerPayloads）
    }

    public static void handleRequestOpenTalk(final RequestOpenTalkPayload payload, final IPayloadContext context) {
        // 这个 handler 只会在服务端逻辑侧被调用
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        TalkNetwork.syncThreadsTo(serverPlayer);
        PacketDistributor.sendToPlayer(serverPlayer, new OpenTalkScreenPayload());
    }

    public static void syncThreadsTo(ServerPlayer player) {
        TalkManager manager = TalkManager.getInstance();

        var netThreads = manager.getActiveThreads().stream()
                .map(PayloadSync.NetThread::fromThread)
                .toList();

        PayloadSync.SyncThreadsPayload payload =
                new PayloadSync.SyncThreadsPayload(netThreads);

        PacketDistributor.sendToPlayer(player, payload);
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
        TalkThread thread = manager.getActiveThread(threadId); // 如果你没有这个方法，可以自己写个按 id 查的

        if (thread == null) {
            // 找不到线程就直接忽略（也可以给玩家发个 debug 信息）
            return;
        }

        // 1. 找到包含这个 choice 的最后一条消息
        TalkMessage msgWithChoice = null;
        TalkMessage.Choice selectedChoice = null;

        for (TalkMessage msg : thread.getMessages()) {
            for (TalkMessage.Choice c : msg.getChoices()) {
                if (c.getId().equals(choiceId)) {
                    msgWithChoice = msg;
                    selectedChoice = c;
                    break;
                }
            }
            if (selectedChoice != null) break;
        }

        if (selectedChoice == null) {
            // 客户端发来的 choiceId 无效，忽略
            return;
        }

        String nextId = selectedChoice.getNextConversationId();
        if (nextId == null || nextId.isEmpty()) {
            // 这个选项没有后续对话，说明是终点；可以在这里记一笔“已完成”，
            // 然后直接同步现有状态
            TalkNetwork.syncThreadsTo(serverPlayer);
            return;
        }

        TalkConversation nextConv = manager.getConversation(nextId);
        if (nextConv == null) {
            // 脚本错了（找不到对应 id），也先同步一下状态方便 debug
            TalkNetwork.syncThreadsTo(serverPlayer);
            return;
        }

        // 2. 把后续对话接到这个线程上
        thread.appendConversation(nextConv);

        // 3. 同步写入玩家存档
        TalkWorldData data = TalkWorldData.get(serverPlayer.serverLevel());
        data.appendConversation(serverPlayer.getUUID(), threadId, nextId);

        // TODO: 如果你希望在服务端记录“这个线程已经选过哪个选项”，可以在这里给 thread 加字段/标记

        // 4. 同步最新对话给玩家
        TalkNetwork.syncThreadsTo(serverPlayer);
    }
}

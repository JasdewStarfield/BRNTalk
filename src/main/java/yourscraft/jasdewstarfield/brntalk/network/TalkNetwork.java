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
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import yourscraft.jasdewstarfield.brntalk.save.TalkWorldData;

import java.util.List;

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

        var netThreads = manager.getActiveThreads(player.getUUID()).stream() //
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
        List<String> newMsgIds = manager.proceedThread(serverPlayer.getUUID(), threadId, nextMsgId);
        if (!newMsgIds.isEmpty()) {
            // 4. 保存到存档 (批量追加)
            TalkWorldData data = TalkWorldData.get(serverPlayer.serverLevel());
            data.appendMessages(serverPlayer.getUUID(), threadId, newMsgIds);

            // 5. 同步给客户端
            TalkNetwork.syncThreadsTo(serverPlayer);
        }
    }
}

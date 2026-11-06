package yourscraft.jasdewstarfield.brntalk.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.network.TalkTransfer;
import yourscraft.jasdewstarfield.brntalk.client.ClientTalkState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.network.PayloadSync;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.List;

public final class TalkNetworking {
    private TalkNetworking() {
    }

    public static void requestOpenTalk() {
        // 客户端只表达意图，具体网络包格式留在平台层内部。
        PacketDistributor.sendToServer(new TalkNetwork.RequestOpenTalkPayload());
    }

    public static void sendSelectChoice(String threadId, String choiceId) {
        // 连同当前会话和消息位置发送，服务端可以识别重复或过期的选择。
        var thread = ClientTalkState.get().getThread(threadId);
        if (thread != null) sendSelectChoice(threadId, choiceId, thread.getSessionId(), thread.getMessages().size());
    }

    /** 使用按钮显示时捕获的回执；原双参数入口继续为外部调用方保留。 */
    public static void sendSelectChoice(String threadId, String choiceId, String sessionId, int position) {
        PacketDistributor.sendToServer(new TalkNetwork.SelectChoicePayload(threadId, choiceId, sessionId, position));
    }

    public static void sendMarkRead(String threadId) {
        // 已读状态需要落到服务端存档，再由服务端同步回客户端。
        PacketDistributor.sendToServer(new TalkNetwork.MarkThreadReadPayload(threadId));
    }

    public static void sendOpenTalkScreen(ServerPlayer player) {
        // 服务端决定何时打开界面，客户端只响应这个轻量控制包。
        PacketDistributor.sendToPlayer(player, new TalkNetwork.OpenTalkScreenPayload());
    }

    public static void syncThreadsTo(ServerPlayer player) {
        var threads = TalkManager.getInstance().getActiveThreads(player.getUUID());

        List<PayloadSync.NetThread> netThreads = threads.stream()
                .map(PayloadSync.NetThread::fromThread)
                .toList();

        sendTransfer(player, new PayloadSync.SyncThreadsPayload(netThreads));
    }

    public static void sendAddThread(ServerPlayer player, TalkThread thread) {
        PayloadSync.NetThread netThread = PayloadSync.NetThread.fromThread(thread);
        sendTransfer(player, new PayloadSync.AddThreadPayload(netThread));
    }

    public static void sendAppendMessages(ServerPlayer player, String threadId, List<TalkMessage> newMessages) {
        if (newMessages.isEmpty()) return;

        List<PayloadSync.NetMessage> netMsgs = newMessages.stream()
                .map(PayloadSync.NetMessage::fromMessage)
                .toList();

        sendTransfer(player, new PayloadSync.AppendMessagesPayload(threadId, netMsgs));
    }

    public static void sendUpdateState(ServerPlayer player, String threadId, long lastReadTime) {
        PacketDistributor.sendToPlayer(player, new PayloadSync.UpdateStatePayload(threadId, lastReadTime));
    }

    private static void sendTransfer(ServerPlayer player, CustomPacketPayload payload) {
        List<TalkTransfer.Frame> frames;
        try {
            frames = TalkTransfer.split(payload);
        } catch (RuntimeException error) {
            // Encoding failures leave saved history intact and must not disconnect the player.
            Brntalk.LOGGER.error("Cannot encode dialogue history for {}", player.getUUID(), error);
            player.sendSystemMessage(Component.translatable("message.brntalk.sync_failed"));
            return;
        }
        frames.forEach(frame -> PacketDistributor.sendToPlayer(player, frame));
    }
}

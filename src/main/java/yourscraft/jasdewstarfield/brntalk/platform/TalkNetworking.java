package yourscraft.jasdewstarfield.brntalk.platform;

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
        // 选择结果由服务端校验和推进，客户端只发送 thread/choice 标识。
        PacketDistributor.sendToServer(new TalkNetwork.SelectChoicePayload(threadId, choiceId));
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

        PacketDistributor.sendToPlayer(player, new PayloadSync.SyncThreadsPayload(netThreads));
    }

    public static void sendAddThread(ServerPlayer player, TalkThread thread) {
        PayloadSync.NetThread netThread = PayloadSync.NetThread.fromThread(thread);
        PacketDistributor.sendToPlayer(player, new PayloadSync.AddThreadPayload(netThread));
    }

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
}

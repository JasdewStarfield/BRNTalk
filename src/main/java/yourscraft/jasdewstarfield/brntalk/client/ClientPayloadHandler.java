package yourscraft.jasdewstarfield.brntalk.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import yourscraft.jasdewstarfield.brntalk.client.ui.TalkScreen;
import yourscraft.jasdewstarfield.brntalk.client.ui.TalkToast;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.network.PayloadSync;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.List;

public class ClientPayloadHandler {

    private static String lastToastUniqueKey = null;

    public static void handleOpenTalkScreen(final TalkNetwork.OpenTalkScreenPayload payload,
                                            final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new TalkScreen());
        });
    }

    // 处理全量同步
    public static void handleSyncThreads(PayloadSync.SyncThreadsPayload payload,
                                         IPayloadContext context) {
        context.enqueueWork(() -> {
            List<TalkThread> threads = payload.threads().stream()
                    .map(PayloadSync.NetThread::toThread)
                    .toList();
            ClientTalkState.get().setThreads(threads);
        });

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TalkScreen screen) {
            screen.onThreadsSynced();
        }
    }

    // 处理新增线程
    public static void handleAddThread(final PayloadSync.AddThreadPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            TalkThread thread = payload.thread().toThread();
            // 将 NetThread 还原为 TalkThread 并加入状态管理器
            ClientTalkState.get().addThread(payload.thread().toThread());

            List<TalkMessage> msgs = thread.getMessages();
            if (!msgs.isEmpty()) {
                tryShowToast(msgs.getFirst(), thread.getId());
            }
        });
    }

    // 处理附加消息
    public static void handleAppendMessages(final PayloadSync.AppendMessagesPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // 将 NetMessage 转回 TalkMessage
            List<TalkMessage> msgs = payload.newMessages().stream()
                    .map(PayloadSync.NetMessage::toMessage)
                    .toList();
            // 更新客户端状态
            ClientTalkState.get().appendMessages(payload.threadId(), msgs);

            if (!msgs.isEmpty()) {
                TalkMessage newestMsg = msgs.getFirst();
                tryShowToast(newestMsg, payload.threadId());
            }
        });
    }

    // 处理未读状态更新
    public static void handleUpdateState(final PayloadSync.UpdateStatePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientTalkState.get().updateReadTime(payload.threadId(), payload.lastReadTime());
        });
    }

    /**
     * 尝试显示弹窗
     * @param message 消息对象
     * @param threadId 线程ID (用于去重)
     */
    private static void tryShowToast(TalkMessage message, String threadId) {
        if (message == null) return;

        Minecraft mc = Minecraft.getInstance();

        // 1. 如果正在看对话界面，就不弹窗
        if (mc.screen instanceof TalkScreen) return;

        // 2. 检查消息时效性：如果消息产生时间距离现在超过3秒，就不弹了
        long now = System.currentTimeMillis();
        if ((now - message.getTimestamp()) > 3000) {
            return;
        }

        // 3. 去重
        String currentUniqueKey = threadId + ":" + message.getId();
        if (currentUniqueKey.equals(lastToastUniqueKey)) {
            return;
        }
        lastToastUniqueKey = currentUniqueKey;

        // 4. 显示 Toast 并播放音效
        mc.getToasts().addToast(new TalkToast(message));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0F));
    }
}

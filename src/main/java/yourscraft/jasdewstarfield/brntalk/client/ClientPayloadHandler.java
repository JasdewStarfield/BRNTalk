package yourscraft.jasdewstarfield.brntalk.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import yourscraft.jasdewstarfield.brntalk.client.ui.TalkHud;
import yourscraft.jasdewstarfield.brntalk.client.ui.TalkScreen;
import yourscraft.jasdewstarfield.brntalk.client.ui.TalkToast;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.network.PayloadSync;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.List;

public class ClientPayloadHandler {

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
                processIncomingMessages(msgs, thread.getId());
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
                processIncomingMessages(msgs, payload.threadId());
            }
        });
    }

    // 处理未读状态更新
    public static void handleUpdateState(final PayloadSync.UpdateStatePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> ClientTalkState.get().updateReadTime(payload.threadId(), payload.lastReadTime()));
    }

    /**
     * 统一处理接收到的消息列表
     */
    private static void processIncomingMessages(List<TalkMessage> messages, String threadId) {
        if (messages.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        BrntalkConfig.NotificationMode mode = BrntalkConfig.CLIENT.notificationMode.get();
        if (mode == BrntalkConfig.NotificationMode.NONE) {
            return;
        }
        if (mode == BrntalkConfig.NotificationMode.TOAST && mc.screen instanceof TalkScreen) {
            return;
        }

        long now = System.currentTimeMillis();
        List<TalkMessage> recentMessages = messages.stream()
                .filter(msg -> now - msg.getTimestamp() < 5000)
                .toList();

        if (recentMessages.isEmpty()) {
            return;
        }

        boolean shouldPlaySound = false;

        switch (mode) {
            case HUD:
                // HUD 模式：将所有新消息都加入队列
                for (TalkMessage msg : recentMessages) {
                    TalkHud.addMessage(msg, threadId);
                }
                shouldPlaySound = true;
                break;

            case TOAST:
                // Toast 模式：只显示最新的一条，防止刷屏
                TalkMessage latestMsg = recentMessages.getFirst();
                mc.getToasts().addToast(new TalkToast(latestMsg));
                break;

            default:
                break;
        }

        if (shouldPlaySound) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
        }
    }
}

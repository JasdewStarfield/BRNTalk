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
        context.enqueueWork(() -> openTalkScreenIfClosed(null));
    }

    // 处理全量同步
    public static void handleSyncThreads(PayloadSync.SyncThreadsPayload payload,
                                         IPayloadContext context) {
        context.enqueueWork(() -> {
            List<TalkThread> threads = payload.threads().stream()
                    .map(PayloadSync.NetThread::toThread)
                    .toList();
            // 状态写入和监听器通知必须在同一个客户端主线程任务内完成。
            ClientTalkState.get().setThreads(threads);
        });
    }

    // 处理新增线程
    public static void handleAddThread(final PayloadSync.AddThreadPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            TalkThread thread = payload.thread().toThread();
            // 将 NetThread 还原为 TalkThread 并加入状态管理器
            ClientTalkState.get().addThread(thread);

            List<TalkMessage> msgs = thread.getMessages();
            if (!msgs.isEmpty()) {
                processIncomingMessages(msgs, thread.getId());
                maybeAutoOpenTalkScreen(thread.getId());
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
                maybeAutoOpenTalkScreen(payload.threadId());
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
                TalkMessage latestMsg = recentMessages.get(0);
                mc.getToasts().addToast(new TalkToast(latestMsg));
                break;

            default:
                break;
        }

        if (shouldPlaySound) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
        }
    }

    /**
     * 仅对增量消息包执行自动开屏；登录和重连时的全量同步不会调用此方法。
     */
    private static void maybeAutoOpenTalkScreen(String threadId) {
        if (BrntalkConfig.CLIENT.autoOpenOnNewMessage.get()) {
            openTalkScreenIfClosed(threadId);
        }
    }

    /**
     * 幂等地打开对话界面。界面已打开时忽略请求，避免重新创建 Screen 并重播开场动画。
     *
     * @param threadId 自动开屏时需要定位的线程；服务端直接开屏请求可传 null
     */
    private static void openTalkScreenIfClosed(String threadId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TalkScreen) {
            return;
        }

        if (threadId != null) {
            // 开屏前定位到消息所属线程，避免自动打开后仍显示旧对话。
            ClientTalkState.get().selectThread(threadId);
        }
        mc.setScreen(new TalkScreen());
    }
}

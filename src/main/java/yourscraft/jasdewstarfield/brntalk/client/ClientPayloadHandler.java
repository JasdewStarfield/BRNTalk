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

    public static void handleOpenTalkScreen(final TalkNetwork.OpenTalkScreenPayload payload,
                                            final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new TalkScreen());
        });
    }

    public static void handleSyncThreads(PayloadSync.SyncThreadsPayload payload,
                                         IPayloadContext context) {
        context.enqueueWork(() -> {
            List<TalkThread> threads = payload.threads().stream()
                    .map(PayloadSync.NetThread::toThread)
                    .toList();
            ClientTalkState.get().setThreads(threads);
            checkAndShowToast(threads);
        });

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TalkScreen screen) {
            screen.onThreadsSynced();
        }
    }

    private static void checkAndShowToast(List<TalkThread> threads) {
        if (threads.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TalkScreen) return;

        // 1. 找到所有线程中，最新的那个线程
        TalkThread activeThread = null;
        long maxTimestamp = -1;

        for (TalkThread t : threads) {
            TalkMessage last = t.getCurrentMessage();
            if (last != null && last.getTimestamp() > maxTimestamp) {
                maxTimestamp = last.getTimestamp();
                activeThread = t;
            }
        }

        if (activeThread == null) return;

        // 3. 检查是否是"新鲜"的消息
        // 条件B: 消息产生的时间距离现在不超过 3秒 (防止重进存档时把历史消息全弹一遍)
        TalkMessage messageToast = null;
        long now = System.currentTimeMillis();

        for (TalkMessage msg : activeThread.getMessages()) {
            // 如果这条消息是 3秒内 产生的，它就是候选人
            if ((now - msg.getTimestamp()) < 3000) {
                // 选中第一条
                messageToast = msg;
                break;
            }
        }

        if (messageToast == null) return;

        mc.getToasts().addToast(new TalkToast(messageToast));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0F));
    }
}

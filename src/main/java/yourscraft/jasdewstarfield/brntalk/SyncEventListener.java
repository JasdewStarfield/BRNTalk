package yourscraft.jasdewstarfield.brntalk;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;
import yourscraft.jasdewstarfield.brntalk.save.TalkWorldData;

import java.util.List;

@EventBusSubscriber(modid = Brntalk.MODID)
public class SyncEventListener {

    public static void rebuildThreadsForPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        TalkWorldData data = TalkWorldData.get(level);

        PlayerTalkState state = data.get(player.getUUID());
        if (state == null) {
            return;
        }

        TalkManager manager = TalkManager.getInstance();

        for (String threadId : state.getThreadIds()) {
            PlayerTalkState.SavedThread saved = state.getThread(threadId);
            if (saved == null) continue;

            String scriptId = saved.getScriptId();
            TalkConversation conv = manager.getConversation(scriptId);
            if (conv == null) continue;

            long startedAt = saved.getStartTime();
            long lastRead = saved.getLastReadTime();
            TalkThread thread = new TalkThread(threadId, scriptId, startedAt, lastRead);

            // 逐条恢复消息
            List<String> historyIds = saved.getHistory();
            for (String msgId : historyIds) {
                TalkMessage msg = conv.getMessage(msgId);
                if (msg != null) {
                    thread.appendMessage(msg.withTimestamp(0L));
                }
            }

            manager.restoreThread(player.getUUID(), thread);
        }
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        TalkManager manager = TalkManager.getInstance();

        if (event.getPlayer() == null) {
            // 情况 1：/reload，给所有玩家同步
            manager.clearAllThreads();
            // 通过 getRelevantPlayers() 拿到要同步的玩家（/reload 时是所有在线玩家）
            event.getRelevantPlayers().forEach(player -> {
                SyncEventListener.rebuildThreadsForPlayer(player);
                TalkNetwork.syncThreadsTo(player);
            });

        } else {
            // 情况 2：某个玩家加入服务器时（OnDatapackSync 也会触发）
            ServerPlayer player = event.getPlayer();
            manager.clearThreadsForPlayer(player.getUUID());
            SyncEventListener.rebuildThreadsForPlayer(player);
            TalkNetwork.syncThreadsTo(player);
        }
    }
}

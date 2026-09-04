package yourscraft.jasdewstarfield.brntalk;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;
import yourscraft.jasdewstarfield.brntalk.data.ConversationLoadReport;
import yourscraft.jasdewstarfield.brntalk.data.ConversationLoader;
import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.platform.BrntalkPlatform;
import yourscraft.jasdewstarfield.brntalk.platform.TalkNetworking;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = Brntalk.MODID)
public class SyncEventListener {
    public static void rebuildThreadsForPlayer(ServerPlayer player) {
        PlayerTalkState state = BrntalkPlatform.getTalkState(player);

        if (state.getThreadIds().isEmpty()) {
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
            // Older saves did not carry a completion ledger. Rebuild it from a reached
            // terminal text node and emit at most once through the persisted marker.
            BrntalkPlatform.completeConversationIfTerminal(player, thread);
        }
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        TalkManager manager = TalkManager.getInstance();

        if (event.getPlayer() == null) {
            // 情况 1：/reload，给所有玩家同步
            manager.clearAllThreads();
            List<ServerPlayer> relevantPlayers = new ArrayList<>();
            event.getPlayerList().getPlayers().forEach(relevantPlayers::add);
            // Forge 1.20.1 通过 PlayerList 拿到 /reload 后要同步的在线玩家。
            relevantPlayers.forEach(player -> {
                SyncEventListener.rebuildThreadsForPlayer(player);
                TalkNetworking.syncThreadsTo(player);
            });
            // Only the global /reload path should surface validation results.
            notifyPrivilegedPlayersAboutValidationReport(relevantPlayers);

        } else {
            // 情况 2：某个玩家加入服务器时（OnDatapackSync 也会触发）
            ServerPlayer player = event.getPlayer();
            manager.clearThreadsForPlayer(player.getUUID());
            SyncEventListener.rebuildThreadsForPlayer(player);
            TalkNetworking.syncThreadsTo(player);
        }
    }

    // 这个类用来迁移旧版世界数据到玩家数据
    @SubscribeEvent
    @SuppressWarnings("deprecation")
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (BrntalkPlatform.migrateLegacyTalkStateOnLogin(player)) {
            player.sendSystemMessage(Component.translatable("system.brntalk.migration.success").withStyle(ChatFormatting.GREEN));
            Brntalk.LOGGER.info("[BRNTalk] Migrated talk data for player {}", player.getName().getString());
        }

        // 登录后总是从平台状态后端重建并同步一次，确保客户端 UI 拿到最新线程。
        SyncEventListener.rebuildThreadsForPlayer(player);
        TalkNetworking.syncThreadsTo(player);
    }

    private static void notifyPrivilegedPlayersAboutValidationReport(List<ServerPlayer> players) {
        if (!BrntalkConfig.SERVER.sendValidationReportInGame.get()) {
            return;
        }

        ConversationLoadReport report = ConversationLoader.getLastLoadReport();
        if (!report.hasProblems()) {
            return;
        }

        for (ServerPlayer player : players) {
            if (!player.hasPermissions(2)) {
                continue;
            }

            MutableComponent summary = report.failedFileCount() > 0
                    ? Component.translatable("validation.brntalk.reload.summary_with_files",
                    report.loadedConversations(), report.skippedConversations(), report.failedFileCount())
                    : Component.translatable("validation.brntalk.reload.summary",
                    report.loadedConversations(), report.skippedConversations());

            player.sendSystemMessage(summary.withStyle(ChatFormatting.YELLOW));
            // Chat stays short for admins; latest.log carries the structured
            // source/script/message/choice details for author debugging.
            player.sendSystemMessage(Component.translatable("validation.brntalk.reload.details_in_log")
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}

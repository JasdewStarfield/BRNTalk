package yourscraft.jasdewstarfield.brntalk;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;
import yourscraft.jasdewstarfield.brntalk.data.ConversationLoadReport;
import yourscraft.jasdewstarfield.brntalk.data.ConversationLoader;
import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;
import yourscraft.jasdewstarfield.brntalk.save.TalkWorldData;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = Brntalk.MODID)
public class SyncEventListener {
    public static void rebuildThreadsForPlayer(ServerPlayer player) {
        PlayerTalkState state = player.getData(BrntalkRegistries.PLAYER_TALK_STATE);

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
        }
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        TalkManager manager = TalkManager.getInstance();

        if (event.getPlayer() == null) {
            // 情况 1：/reload，给所有玩家同步
            manager.clearAllThreads();
            List<ServerPlayer> relevantPlayers = new ArrayList<>();
            event.getRelevantPlayers().forEach(relevantPlayers::add);
            // 通过 getRelevantPlayers() 拿到要同步的玩家（/reload 时是所有在线玩家）
            relevantPlayers.forEach(player -> {
                SyncEventListener.rebuildThreadsForPlayer(player);
                TalkNetwork.syncThreadsTo(player);
            });
            // Only the global /reload path should surface validation results.
            notifyPrivilegedPlayersAboutValidationReport(relevantPlayers);

        } else {
            // 情况 2：某个玩家加入服务器时（OnDatapackSync 也会触发）
            ServerPlayer player = event.getPlayer();
            manager.clearThreadsForPlayer(player.getUUID());
            SyncEventListener.rebuildThreadsForPlayer(player);
            TalkNetwork.syncThreadsTo(player);
        }
    }

    // 这个类用来迁移旧版世界数据到玩家数据
    @SubscribeEvent
    @SuppressWarnings("deprecation")
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = player.serverLevel();

        // 1. 获取老的全局数据
        TalkWorldData oldGlobalData = TalkWorldData.get(level);

        // 2. 检查该玩家是否有旧数据
        PlayerTalkState oldState = oldGlobalData.get(player.getUUID());

        if (oldState != null) {
            // 3. 将旧数据覆盖到新的 Attachment 中
            // Data Attachments 的 setData 会直接替换对象
            player.setData(BrntalkRegistries.PLAYER_TALK_STATE, oldState);

            // 4. 从旧的全局数据中移除该玩家，防止重复迁移
            oldGlobalData.removeAllThread(player.getUUID());

            player.sendSystemMessage(Component.literal("[BRNTalk] Migrated talk data for you! Your data is now an attachment!").withStyle(ChatFormatting.GREEN));
            Brntalk.LOGGER.info("[BRNTalk] Migrated talk data for player {}", player.getName().getString());
        }

        // 5. 同步
        SyncEventListener.rebuildThreadsForPlayer(player);
        TalkNetwork.syncThreadsTo(player);
    }

    private static void notifyPrivilegedPlayersAboutValidationReport(List<ServerPlayer> players) {
        if (!BrntalkConfig.SERVER.sendValidationReportInGame.get()) {
            return;
        }

        ConversationLoadReport report = ConversationLoader.getLastLoadReport();
        if (!report.hasProblems()) {
            return;
        }

        String summary = "[BRNTalk] Reload completed with validation issues: loaded "
                + report.loadedConversations()
                + " conversation(s), skipped "
                + report.skippedConversations()
                + " invalid conversation(s)";
        if (report.failedFileCount() > 0) {
            summary = summary + ", " + report.failedFileCount() + " file load failure(s)";
        }
        summary = summary + ".";

        List<String> detailLines = new ArrayList<>();
        for (ConversationLoadReport.InvalidConversation invalidConversation : report.invalidConversations()) {
            for (String error : invalidConversation.errors()) {
                detailLines.add("Script '" + invalidConversation.scriptId() + "': " + error);
            }
        }
        for (ConversationLoadReport.FileLoadFailure fileLoadFailure : report.fileLoadFailures()) {
            detailLines.add("File '" + fileLoadFailure.resourceId() + "': " + fileLoadFailure.summary());
        }

        // The full report is still in latest.log; chat only gets the first few
        // detail lines plus a pointer to the log when needed.
        int maxDetailLines = BrntalkConfig.SERVER.validationReportMaxDetailLines.get();
        int remainingLines = Math.max(0, detailLines.size() - maxDetailLines);
        int detailLinesToSend = Math.min(detailLines.size(), maxDetailLines);

        for (ServerPlayer player : players) {
            if (!player.hasPermissions(2)) {
                continue;
            }

            player.sendSystemMessage(Component.literal(summary).withStyle(ChatFormatting.YELLOW));

            for (int i = 0; i < detailLinesToSend; i++) {
                player.sendSystemMessage(Component.literal("[BRNTalk] " + detailLines.get(i)).withStyle(ChatFormatting.RED));
            }

            if (remainingLines > 0) {
                player.sendSystemMessage(Component.literal("[BRNTalk] ...and " + remainingLines + " more issue(s). Check latest.log for full details.")
                        .withStyle(ChatFormatting.RED));
            } else {
                player.sendSystemMessage(Component.literal("[BRNTalk] Full validation details were written to latest.log.")
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }
}

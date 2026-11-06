package yourscraft.jasdewstarfield.brntalk;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import yourscraft.jasdewstarfield.brntalk.runtime.ConversationService;
import yourscraft.jasdewstarfield.brntalk.platform.ServerConversationPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;
import yourscraft.jasdewstarfield.brntalk.data.ConversationLoadReport;
import yourscraft.jasdewstarfield.brntalk.data.ConversationLoader;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = Brntalk.MODID)
public class SyncEventListener {
    public static void rebuildThreadsForPlayer(ServerPlayer player) {
        ConversationService.getInstance().rebuild(new ServerConversationPlayer(player));
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            // NeoForge 21.1.216 的单玩家同步先于 PlayerLoggedIn，统一等迁移后恢复。
            return;
        }
        List<ServerPlayer> players = new ArrayList<>();
        event.getRelevantPlayers().forEach(players::add);
        ConversationService.getInstance().synchronizeReload(
                players.stream().map(ServerConversationPlayer::new).toList(), ConversationLoader.wasLastReloadApplied());
        notifyPrivilegedPlayersAboutValidationReport(players);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ConversationService.getInstance().login(new ServerConversationPlayer(player));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        TalkManager.getInstance().clearThreadsForPlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // 集成服务器可能在同一 JVM 再开世界，剧本和玩家线程一起释放。
        TalkManager.getInstance().clear();
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
            player.sendSystemMessage(Component.translatable("validation.brntalk.reload.retained")
                    .withStyle(ChatFormatting.YELLOW));
            // Chat stays short for admins; latest.log carries the structured
            // source/script/message/choice details for author debugging.
            player.sendSystemMessage(Component.translatable("validation.brntalk.reload.details_in_log")
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}

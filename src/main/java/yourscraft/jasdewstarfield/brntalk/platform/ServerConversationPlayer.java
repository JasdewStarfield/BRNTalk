package yourscraft.jasdewstarfield.brntalk.platform;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.ConversationPlayer;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;

import java.util.List;
import java.util.UUID;

/** 将存档、命令、事件和网络接到 NeoForge；剧情推进由 ConversationService 协调。 */
public record ServerConversationPlayer(ServerPlayer player) implements ConversationPlayer {
    @Override public UUID id() { return player.getUUID(); }
    @Override public PlayerTalkState state() { return BrntalkPlatform.getTalkState(player); }
    @Override public void replaceState(PlayerTalkState state) { BrntalkPlatform.setTalkState(player, state); }
    @Override public void seen(String scriptId, String messageId) { BrntalkPlatform.postPlayerSeenMessage(player, scriptId, messageId); }
    @Override public void completed(TalkThread thread) {
        PlatformEvents.postPlayerCompletedConversation(player, thread.getScriptId(), thread.getId());
    }
    @Override public void added(TalkThread thread) { TalkNetworking.sendAddThread(player, thread); }
    @Override public void appended(String threadId, List<TalkMessage> messages) { TalkNetworking.sendAppendMessages(player, threadId, messages); }
    @Override public void sync() { TalkNetworking.syncThreadsTo(player); }
    @Override public void openScreen() { TalkNetworking.sendOpenTalkScreen(player); }
    @Override public void read(String threadId, long time) { TalkNetworking.sendUpdateState(player, threadId, time); }

    @Override
    public void migrateLegacyState() {
        if (BrntalkPlatform.migrateLegacyTalkStateOnLogin(player)) {
            player.sendSystemMessage(Component.translatable("system.brntalk.migration.success").withStyle(ChatFormatting.GREEN));
            Brntalk.LOGGER.info("[BRNTalk] Migrated talk data for player {}", player.getName().getString());
        }
    }

    @Override
    public void executeAction(TalkThread thread, TalkMessage msg) {
        String action = msg.getAction();
        if (action != null && !action.isBlank()) {
            // 保留原权限、静默输出与回执策略，命令仍在每条消息追加后立即执行。
            CommandSourceStack source = player.createCommandSourceStack()
                    .withPermission(2)
                    .withSuppressedOutput()
                    .withCallback((success, result) -> {
                        if (!success || result == 0) {
                            Brntalk.LOGGER.warn(
                                    "[BRNTalk] Action command returned {} (success={}) for script '{}' thread '{}' message '{}': {}",
                                    result, success, thread.getScriptId(), thread.getId(), msg.getId(), action);
                        }
                    });
            try {
                player.server.getCommands().performPrefixedCommand(source, action);
            } catch (RuntimeException ex) {
                Brntalk.LOGGER.error("[BRNTalk] Action command failed for script '{}' thread '{}' message '{}': {}",
                        thread.getScriptId(), thread.getId(), msg.getId(), action, ex);
            }
        }
    }
}

package yourscraft.jasdewstarfield.brntalk;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

public class BrntalkCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("brntalk")
                        .requires(source -> source.hasPermission(2))
                        // --- start 命令分支 ---
                        .then(Commands.literal("start")
                                // 用法 1: /brntalk start <id> (为自己开启某条指定对话)
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(BrntalkCommands::startForSelf)
                                        // 用法 2: /brntalk start <id> <targets> (为目标开启某条指定对话)
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(BrntalkCommands::startForTargets)
                                        )
                                )
                        )
                        // --- clear 命令分支 ---
                        .then(Commands.literal("clear")
                                // 用法 1: /brntalk clear (清除自己所有)
                                .executes(ctx -> executeClear(
                                        ctx.getSource(),
                                        Collections.singleton(ctx.getSource().getPlayerOrException()),
                                        null
                                ))
                                // 用法 2: /brntalk clear <targets> (清除目标所有)
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> executeClear(
                                                ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"),
                                                null
                                        ))
                                        // 用法 3: /brntalk clear <targets> <id> (清除目标指定ID)
                                        .then(Commands.argument("id", StringArgumentType.string())
                                                .executes(ctx -> executeClear(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayers(ctx, "targets"),
                                                        StringArgumentType.getString(ctx, "id")
                                                ))
                                        )
                                )
                        )
                        // --- has_seen 命令分支 ---
                        // /brntalk has_seen <targets> <scriptId> <messageId>
                        .then(Commands.literal("has_seen")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("scriptId", StringArgumentType.string())
                                                .then(Commands.argument("messageId", StringArgumentType.string())
                                                        .executes(BrntalkCommands::checkSeen)
                                                )
                                        )
                                )
                        )
                        // resume 命令分支
                        .then(Commands.literal("resume")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("scriptId", StringArgumentType.string())
                                                // 用法 1: /brntalk resume <targets> <scriptId> (恢复该脚本所有线程)
                                                .executes(ctx -> resumeConversation(ctx, null))
                                                // 用法 2: /brntalk resume <targets> <scriptId> <messageId> (仅恢复停在该消息ID的线程)
                                                .then(Commands.argument("messageId", StringArgumentType.string())
                                                        .executes(ctx -> resumeConversation(ctx, StringArgumentType.getString(ctx, "messageId")))
                                                )
                                        )
                                )
                        )
        );
    }

    private static int startForSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "id");
        return startConversation(ctx.getSource(), Collections.singleton(player), id);
    }

    private static int startForTargets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        // 获取选择器选中的所有玩家集合
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String id = StringArgumentType.getString(ctx, "id");
        return startConversation(ctx.getSource(), targets, id);
    }

    private static int startConversation(CommandSourceStack source, Collection<ServerPlayer> targets, String id){
        int successCount = 0;

        for (ServerPlayer player : targets) {
            if (BrntalkAPI.startConversation(player, id)) {
                successCount++;
            } else {
                // 如果 API 返回 false，说明 ID 不对，单独给发令者提示
                source.sendFailure(Component.translatable(
                        "command.brntalk.start.not_found",
                        id,
                        player.getName()
                ).withStyle(ChatFormatting.RED));
            }
        }

        if (successCount > 0) {
            final int finalCount = successCount;
            source.sendSuccess(() -> Component.translatable(
                    "command.brntalk.start.success",
                    finalCount,
                    id
            ).withStyle(ChatFormatting.GREEN), true);
        }

        return successCount;
    }

    private static int executeClear(CommandSourceStack source, Collection<ServerPlayer> targets, @Nullable String scriptId) {
        int successCount = 0;

        for (ServerPlayer player : targets) {
            boolean result;
            if (scriptId == null) {
                // 清除所有
                result = BrntalkAPI.clearAllConversation(player);
            } else {
                // 清除指定
                result = BrntalkAPI.clearConversation(player, scriptId);
            }

            if (result) {
                successCount++;
            }
        }

        if (successCount > 0) {
            final int count = successCount;
            source.sendSuccess(() -> {
                if (scriptId == null) {
                    return Component.translatable(
                            "command.brntalk.clear_all.success",
                            count
                    ).withStyle(ChatFormatting.GREEN);
                } else {
                    return Component.translatable(
                            "command.brntalk.clear_script.success",
                            count,
                            scriptId
                    ).withStyle(ChatFormatting.GREEN);
                }
            }, true);
        } else {
            source.sendFailure(Component.translatable(
                    "command.brntalk.clear.failure"
            ).withStyle(ChatFormatting.RED));
        }

        return successCount;
    }

    private static int checkSeen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        String scriptId = StringArgumentType.getString(ctx, "scriptId");
        String messageId = StringArgumentType.getString(ctx, "messageId");

        boolean hasSeen = BrntalkAPI.hasSeen(target, scriptId, messageId);

        if (hasSeen) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable(
                            "command.brntalk.has_seen.true",
                            target.getName(),
                            messageId,
                            scriptId
                    ).withStyle(ChatFormatting.GREEN),
                    false
            );
            return 1;
        } else {
            ctx.getSource().sendFailure(
                    Component.translatable(
                            "command.brntalk.has_seen.false",
                            target.getName(),
                            messageId,
                            scriptId
                    ).withStyle(ChatFormatting.RED)
            );
            return 0;
        }
    }

    private static int resumeConversation(CommandContext<CommandSourceStack> ctx, String matchMessageId) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String scriptId = StringArgumentType.getString(ctx, "scriptId");

        int totalResumed = 0;
        int playersAffected = 0;

        for (ServerPlayer player : targets) {
            int count = BrntalkAPI.resumeConversation(player, scriptId, matchMessageId);
            if (count > 0) {
                totalResumed += count;
                playersAffected++;
            }
        }

        if (totalResumed > 0) {
            final int pCount = playersAffected;
            final int tCount = totalResumed;
            ctx.getSource().sendSuccess(() -> {
                String translationKey = matchMessageId == null
                        ? "command.brntalk.resume.success"
                        : "command.brntalk.resume_filtered.success";
                Object[] arguments = matchMessageId == null
                        ? new Object[]{pCount, scriptId, tCount}
                        : new Object[]{pCount, scriptId, matchMessageId, tCount};
                return Component.translatable(translationKey, arguments).withStyle(ChatFormatting.GREEN);
            }, true);
        } else {
            ctx.getSource().sendFailure(Component.translatable(
                    "command.brntalk.resume.failure"
            ).withStyle(ChatFormatting.RED));
        }

        return totalResumed;
    }
}

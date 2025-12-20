package yourscraft.jasdewstarfield.brntalk;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
                source.sendFailure(Component.literal("§c[BRNTalk] 找不到对话脚本: " + id + " (玩家: " + player.getName().getString() + ")"));
            }
        }

        if (successCount > 0) {
            final int finalCount = successCount;
            source.sendSuccess(() -> Component.literal("§a[BRNTalk] 已为 " + finalCount + " 名玩家触发: " + id), true);
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
                    return Component.literal("§a[BRNTalk] 已清除 " + count + " 名玩家的所有对话进度。");
                } else {
                    return Component.literal("§a[BRNTalk] 已为 " + count + " 名玩家清除对话: " + scriptId);
                }
            }, true);
        } else {
            source.sendFailure(Component.literal("§c[BRNTalk] 未能清除对话 (可能目标当前无对话或指定ID不存在)"));
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
                    Component.literal(
                            "§a[BRNTalk] 玩家 " + target.getName().getString() + " 已达成/阅读: " +
                            messageId + " (剧本: " + scriptId + ")"
                    ),
                    false
            );
            return 1;
        } else {
            ctx.getSource().sendFailure(
                    Component.literal(
                            "§c[BRNTalk] 玩家 " + target.getName().getString() + " 尚未阅读: " + messageId +
                            " (剧本: " + scriptId + ")"
                    )
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
            String msgInfo = (matchMessageId == null) ? "" : " (过滤ID: " + matchMessageId + ")";
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§a[BRNTalk] 已为 " + pCount + " 名玩家继续对话: " + scriptId + msgInfo +
                    "，共恢复 " + tCount + " 个线程。"
            ), true);
        } else {
            ctx.getSource().sendFailure(Component.literal("§c[BRNTalk] 未能继续对话 (可能玩家没有该对话或并未处于等待状态)"));
        }

        return totalResumed;
    }
}

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
                                .executes(BrntalkCommands::clearSelf)
                                // 用法 2: /brntalk clear <targets> (清除目标所有)
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(BrntalkCommands::clearTargets)
                                        // 用法 3: /brntalk clear <targets> <id> (清除目标指定ID)
                                        .then(Commands.argument("id", StringArgumentType.string())
                                                .executes(BrntalkCommands::clearSpecificTarget)
                                        )
                                )
                        )
                        // --- has_seen 命令分支 ---
                        .then(Commands.literal("has_seen")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("scriptId", StringArgumentType.string())
                                                .then(Commands.argument("messageId", StringArgumentType.string())
                                                        .executes(BrntalkCommands::checkSeen)
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
                source.sendFailure(Component.literal("[BRNTalk] 找不到对话脚本: " + id + " (玩家: " + player.getName().getString() + ")"));
            }
        }

        if (successCount > 0) {
            final int finalCount = successCount;
            source.sendSuccess(() -> Component.literal("[BRNTalk] 已为 " + finalCount + " 名玩家触发: " + id), true);
        }

        return successCount;
    }

    private static int clearSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return clearPlayers(ctx.getSource(), Collections.singleton(ctx.getSource().getPlayerOrException()));
    }

    private static int clearTargets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        return clearPlayers(ctx.getSource(), targets);
    }

    private static int clearSpecificTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String id = StringArgumentType.getString(ctx, "id");

        int count = 0;
        for (ServerPlayer player : targets) {
            if (BrntalkAPI.clearConversation(player, id)) {
                count++;
            }
        }

        ctx.getSource().sendSuccess(
                () -> Component.literal("[BRNTalk] 已为 " + targets.size() + " 名玩家清除了对话: " + id),
                true
        );
        return count;
    }

    private static int clearPlayers(CommandSourceStack source, Collection<ServerPlayer> targets){
        for (ServerPlayer player : targets) {
            BrntalkAPI.clearAllConversation(player);
        }

        source.sendSuccess(() -> Component.literal("[BRNTalk] 已清除 " + targets.size() + " 名玩家的对话进度。"), true);

        return targets.size();
    }

    private static int checkSeen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        String scriptId = StringArgumentType.getString(ctx, "scriptId");
        String messageId = StringArgumentType.getString(ctx, "messageId");

        boolean hasSeen = BrntalkAPI.hasSeen(target, scriptId, messageId);

        if (hasSeen) {
            ctx.getSource().sendSuccess(() ->
                    Component.literal("§a[BRNTalk] 玩家 " + target.getName().getString() + " 已达成/阅读: " + messageId + " (剧本: " + scriptId + ")"),
                    false
            );
            return 1;
        } else {
            ctx.getSource().sendFailure(
                    Component.literal("§c[BRNTalk] 玩家 " + target.getName().getString() + " 尚未阅读: " + messageId + " (剧本: " + scriptId + ")")
            );
            return 0;
        }
    }
}

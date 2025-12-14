package yourscraft.jasdewstarfield.brntalk;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import yourscraft.jasdewstarfield.brntalk.save.TalkWorldData;

import java.util.Collection;
import java.util.Collections;

public class BrntalkCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("brntalk")
                        // --- start 命令分支 ---
                        .then(Commands.literal("start")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(BrntalkCommands::startForSelf)
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(BrntalkCommands::startForTargets)
                                        )
                                )
                        )
                        // --- clear 命令分支 ---
                        .then(Commands.literal("clear")
                                .executes(BrntalkCommands::clearSelf)
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(BrntalkCommands::clearTargets)
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

    private static int clearPlayers(CommandSourceStack source, Collection<ServerPlayer> targets){
        for (ServerPlayer player : targets) {
            BrntalkAPI.clearConversation(player);
        }

        source.sendSuccess(() -> Component.literal("[BRNTalk] 已清除 " + targets.size() + " 名玩家的对话进度。"), true);

        return targets.size();
    }
}

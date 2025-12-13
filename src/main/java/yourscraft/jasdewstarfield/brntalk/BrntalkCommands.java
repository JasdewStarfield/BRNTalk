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
        TalkManager manager = TalkManager.getInstance();
        int successCount = 0;

        for (ServerPlayer player : targets) {
            TalkThread thread = manager.startThread(player.getUUID(), id);

            if (thread == null) {
                // 如果脚本不存在，只给命令发送者报错，不打断循环
                source.sendFailure(Component.literal("[BRNTalk] 找不到对话脚本: " + id));
                continue; // 继续处理下一个玩家
            }

            TalkWorldData data = TalkWorldData.get(player.serverLevel());
            data.startThread(player.getUUID(), thread.getId(), id);

            TalkNetwork.syncThreadsTo(player);
            successCount++;
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
        TalkManager manager = TalkManager.getInstance();

        for (ServerPlayer player : targets) {
            // 1. 清除存档
            TalkWorldData data = TalkWorldData.get(player.serverLevel());
            data.clearPlayer(player.getUUID());

            // 2. 清除运行时
            manager.clearThreadsForPlayer(player.getUUID());

            // 3. 同步
            TalkNetwork.syncThreadsTo(player);
        }

        // 4. 给玩家一个提示
        source.sendSuccess(() -> Component.literal("[BRNTalk] 已清除 " + targets.size() + " 名玩家的对话进度。"), true);

        return targets.size();
    }
}

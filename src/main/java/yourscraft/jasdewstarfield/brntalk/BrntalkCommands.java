package yourscraft.jasdewstarfield.brntalk;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import yourscraft.jasdewstarfield.brntalk.save.TalkWorldData;

public class BrntalkCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("brntalk")
                        .then(Commands.literal("start")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(BrntalkCommands::startConversationThread)
                                )
                        )
                        .then(Commands.literal("clear")
                                .executes(BrntalkCommands::clearSelf))
        );
    }

    private static int startConversationThread(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");

        TalkManager manager = TalkManager.getInstance();
        TalkThread thread = manager.startThread(id);
        if (thread == null) {
            source.sendFailure(Component.literal("[BRNTalk] 找不到对话脚本: " + id));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        TalkWorldData data = TalkWorldData.get(player.serverLevel());
        data.startThread(player.getUUID(), thread.getId(), id);

        TalkNetwork.syncThreadsTo(player);

        source.sendSuccess(() -> Component.literal("[BRNTalk] 已触发聊天串: " + id), false);
        return 1;
    }

    private static int clearSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();

        // 1. 清除存档里的对话进度
        TalkWorldData data = TalkWorldData.get(player.serverLevel());
        data.clearPlayer(player.getUUID());

        // 2. 清除运行时的对话线程（全局清空，适合作为调试命令）
        TalkManager manager = TalkManager.getInstance();
        manager.clearAllThreads();

        // 3. 同步一个“空列表”给客户端，让 UI 立刻刷新
        TalkNetwork.syncThreadsTo(player);

        // 4. 给玩家一个提示
        source.sendSuccess(
                () -> Component.literal("[BRNTalk] 已清除当前玩家的所有对话进度。"),
                false
        );

        return 1;
    }
}

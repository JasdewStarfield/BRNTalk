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

public class BrntalkCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("brntalk")
                        .then(Commands.literal("start")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(BrntalkCommands::startConversationThread)
                                )
                        )
        );
    }

    private static int startConversationThread(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");

        TalkManager manager = TalkManager.getInstance();
        TalkThread thread = manager.startThread(id);
        if (thread == null) {
            source.sendFailure(
                    Component.literal("[BRNTalk] 找不到对话脚本: " + id)
            );
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        TalkNetwork.syncThreadsTo(player);

        source.sendSuccess(
                () -> Component.literal("[BRNTalk] 已触发聊天串: " + id),
                false
        );
        return 1;
    }
}

package yourscraft.jasdewstarfield.brntalk;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

public class Commands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                net.minecraft.commands.Commands.literal("brntalk")
                        .then(net.minecraft.commands.Commands.literal("showdemo")
                                .executes(Commands::showDemoConversation)
                        )
                        .then(net.minecraft.commands.Commands.literal("start")
                                .then(net.minecraft.commands.Commands.argument("id", StringArgumentType.string())
                                        .executes(Commands::startConversationThread)
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

    private static int showDemoConversation(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        TalkConversation conv = TalkManager.getInstance().getConversation("demo");

        if (conv == null) {
            source.sendSuccess(() -> Component.literal("[BRNTalk] 找不到 demo 对话"), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== BRNTalk: " + conv.getId() + " ==="), false);
        for (TalkMessage msg : conv.getMessages()) {
            String line = msg.getSpeaker() + ": " + msg.getText();
            source.sendSuccess(() -> Component.literal(line), false);
        }

        return 1; // 表示命令成功
    }
}

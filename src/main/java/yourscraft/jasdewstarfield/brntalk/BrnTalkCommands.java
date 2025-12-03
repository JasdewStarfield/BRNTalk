package yourscraft.jasdewstarfield.brntalk;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class BrnTalkCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("brntalk")
                        .then(Commands.literal("showdemo")
                                .executes(BrnTalkCommands::showDemoConversation)
                        )
                        .then(Commands.literal("start")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(BrnTalkCommands::startConversationThread)
                                )
                        )
        );
    }

    private static int startConversationThread(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        var manager = TalkManager.getInstance();

        var conv = manager.getConversation(id);
        if (conv == null) {
            ctx.getSource().sendFailure(
                    Component.literal("[BRNTalk] 找不到对话脚本: " + id)
            );
            return 0;
        }

        var thread = manager.startThread(id);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[BRNTalk] 已触发聊天串: " + thread.getId()),
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

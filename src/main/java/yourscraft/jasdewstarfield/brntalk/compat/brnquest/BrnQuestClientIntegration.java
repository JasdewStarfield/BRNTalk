package yourscraft.jasdewstarfield.brntalk.compat.brnquest;

import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientRewardPresentation;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientRewardPresentationRegistry;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientTaskPresentation;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientTaskPresentationRegistry;

/** Client-only names, icons and progress text for BRNTalk-owned BRNQuest types. */
public final class BrnQuestClientIntegration {
    private static boolean installed;

    private BrnQuestClientIntegration() {}

    public static synchronized void install() {
        if (installed) return;
        ClientTaskPresentationRegistry.register(BrnQuestIntegration.MESSAGE_SEEN,
                task("screen.brntalk.brnquest.task.message_seen", "✉"));
        ClientTaskPresentationRegistry.register(BrnQuestIntegration.CONVERSATION_COMPLETE,
                task("screen.brntalk.brnquest.task.conversation_complete", "✓"));
        ClientRewardPresentationRegistry.register(BrnQuestIntegration.START_CONVERSATION,
                reward("screen.brntalk.brnquest.reward.start_conversation", "▶"));
        ClientRewardPresentationRegistry.register(BrnQuestIntegration.RESUME_CONVERSATION,
                reward("screen.brntalk.brnquest.reward.resume_conversation", "↻"));
        ClientRewardPresentationRegistry.register(BrnQuestIntegration.OPEN_SCREEN,
                reward("screen.brntalk.brnquest.reward.open_screen", "▣"));
        installed = true;
    }

    private static ClientTaskPresentation task(String typeKey, String symbol) {
        return new ClientTaskPresentation() {
            public NodeStyle nodeStyle(yourscraft.jasdewstarfield.brnquest.api.TaskView task) {
                return NodeStyle.CUSTOM;
            }

            public String symbol(yourscraft.jasdewstarfield.brnquest.api.TaskView task) { return symbol; }

            public Component typeName(yourscraft.jasdewstarfield.brnquest.api.TaskView task) {
                return Component.translatable(typeKey);
            }

            public Component title(yourscraft.jasdewstarfield.brnquest.client.ui.TaskPresentationContext context) {
                String configured = context.task().config().getOrDefault("title", "");
                return configured.isBlank() ? Component.translatable(typeKey) : Component.literal(configured);
            }

            public Component progressText(yourscraft.jasdewstarfield.brnquest.client.ui.TaskPresentationContext context,
                                          boolean satisfied) {
                return Component.translatable(satisfied
                        ? "screen.brntalk.brnquest.task.completed" : "screen.brntalk.brnquest.task.waiting");
            }
        };
    }

    private static ClientRewardPresentation reward(String typeKey, String symbol) {
        return new ClientRewardPresentation() {
            public String symbol(yourscraft.jasdewstarfield.brnquest.api.RewardView reward) { return symbol; }

            public Component typeName(yourscraft.jasdewstarfield.brnquest.api.RewardView reward) {
                return Component.translatable(typeKey);
            }
        };
    }
}

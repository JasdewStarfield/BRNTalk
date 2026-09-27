package yourscraft.jasdewstarfield.brntalk.compat.brnquest;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
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
                task("screen.brntalk.brnquest.task.message_seen", "message_seen"));
        ClientTaskPresentationRegistry.register(BrnQuestIntegration.CONVERSATION_COMPLETE,
                task("screen.brntalk.brnquest.task.conversation_complete", "conversation_complete"));
        ClientRewardPresentationRegistry.register(BrnQuestIntegration.START_CONVERSATION,
                reward("screen.brntalk.brnquest.reward.start_conversation", "start_conversation"));
        ClientRewardPresentationRegistry.register(BrnQuestIntegration.RESUME_CONVERSATION,
                reward("screen.brntalk.brnquest.reward.resume_conversation", "resume_conversation"));
        ClientRewardPresentationRegistry.register(BrnQuestIntegration.OPEN_SCREEN,
                reward("screen.brntalk.brnquest.reward.open_screen", "open_screen"));
        installed = true;
    }

    private static ClientTaskPresentation task(String typeKey, String spriteName) {
        EditorIcon icon = typeSprite(spriteName);
        return new ClientTaskPresentation() {
            public NodeStyle nodeStyle(yourscraft.jasdewstarfield.brnquest.api.TaskView task) {
                return NodeStyle.CUSTOM;
            }

            // The picker and existing objectives share the same resource-pack-replaceable sprite.
            @Override public Optional<EditorIcon> typeIcon() { return Optional.of(icon); }
            @Override public Optional<EditorIcon> icon(TaskView task) { return Optional.of(icon); }

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

    private static ClientRewardPresentation reward(String typeKey, String spriteName) {
        EditorIcon icon = typeSprite(spriteName);
        return new ClientRewardPresentation() {
            // Reward rows and reward-table choices use the same icon as the creation picker.
            @Override public Optional<EditorIcon> typeIcon() { return Optional.of(icon); }
            @Override public Optional<EditorIcon> icon(RewardView reward) { return Optional.of(icon); }

            public Component typeName(yourscraft.jasdewstarfield.brnquest.api.RewardView reward) {
                return Component.translatable(typeKey);
            }
        };
    }

    private static EditorIcon typeSprite(String name) {
        // Use BRNQuest's public sprite renderer for the same pixel scale, tint and shadow as built-ins.
        return EditorIcon.sprite(ResourceLocation.fromNamespaceAndPath("brntalk", "brnquest/type/" + name));
    }
}

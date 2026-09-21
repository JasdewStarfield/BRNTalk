package yourscraft.jasdewstarfield.brntalk.compat.brnquest;

import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.reward.ComposableReward;
import yourscraft.jasdewstarfield.brnquest.reward.RewardClaimResult;
import yourscraft.jasdewstarfield.brnquest.reward.RewardLeafContext;
import yourscraft.jasdewstarfield.brntalk.BrntalkAPI;

/** Reward tables own durable occurrence receipts; standalone reward receipts remain separate. */
final class BrnQuestRewardComposition implements ComposableReward {
    enum Action { START, RESUME, OPEN_SCREEN }

    @FunctionalInterface
    interface Operation {
        boolean run(ServerPlayer player, Map<String, String> config);
    }

    private final Action action;
    private final Operation operation;

    BrnQuestRewardComposition(Action action) {
        this(action, (player, config) -> switch (action) {
            case START -> BrntalkAPI.startConversation(player, config.get("script_id"));
            case RESUME -> BrntalkAPI.resumeConversation(player, config.get("script_id"), config.get("message_id")) > 0;
            case OPEN_SCREEN -> BrntalkAPI.openTalkScreen(player);
        });
    }

    // Keep the side-effect boundary injectable so tests can verify preparation never dispatches actions.
    BrnQuestRewardComposition(Action action, Operation operation) {
        this.action = action;
        this.operation = operation;
    }

    @Override
    public void validateConfig(Map<String, String> config) {
        if (action != Action.OPEN_SCREEN && config.getOrDefault("script_id", "").isBlank()) {
            throw new IllegalArgumentException("BRNTalk script_id must not be blank");
        }
        if (action == Action.RESUME && config.containsKey("message_id") && config.get("message_id").isBlank()) {
            throw new IllegalArgumentException("BRNTalk message_id must not be blank when provided");
        }
        if (action == Action.OPEN_SCREEN && config.containsKey("open")
                && !config.get("open").equals("true") && !config.get("open").equals("false")) {
            throw new IllegalArgumentException("BRNTalk open must be a boolean");
        }
    }

    @Override
    public Map<String, String> prepare(RewardLeafContext context) {
        validateConfig(context.config());
        return Map.copyOf(context.config());
    }

    @Override
    public RewardClaimResult execute(RewardLeafContext context, Map<String, String> prepared) {
        validateConfig(prepared);
        // Each selected occurrence may dispatch, even when another leaf has the same root reward ID.
        // The coordinator persists STARTED before this call and never replays uncertain executions.
        return operation.run(context.root().rewardContext().player(), prepared)
                ? RewardClaimResult.success("BRNTalk reward-table action dispatched")
                : RewardClaimResult.failure("BRNTALK_ACTION_FAILED", "BRNTalk reward-table action could not be dispatched");
    }

    // Default recover returns UNKNOWN: player data alone cannot prove an interrupted dispatch was persisted.
}

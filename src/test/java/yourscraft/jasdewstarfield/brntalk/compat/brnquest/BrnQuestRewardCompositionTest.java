package yourscraft.jasdewstarfield.brntalk.compat.brnquest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import static org.junit.jupiter.api.Assertions.*;

class BrnQuestRewardCompositionTest {
    private static ResourceLocation id(String path) { return ResourceLocation.parse("brntalk:" + path); }

    private static RewardLeafContext leaf(String occurrence, Map<String, String> config) {
        var reward = new RewardView(id("book"), id("table"), ResourceLocation.parse("brnquest:reward_table"), Map.of(), "manual", false);
        var root = new RewardClaimContext(new RewardContext(null, id("book"), id("quest"), reward),
                new ProgressOwnerId(ResourceLocation.parse("brnquest:personal"), new UUID(0, 1)), 1, "generation");
        return new RewardLeafContext(root, occurrence, occurrence, id("start_conversation"), config);
    }

    @Test void preparationAndRecoveryNeverDispatchAnAction() throws Exception {
        for (var action : BrnQuestRewardComposition.Action.values()) {
            var calls = new AtomicInteger();
            var adapter = new BrnQuestRewardComposition(action, (player, config) -> { calls.incrementAndGet(); return true; });
            var leaf = leaf("one", Map.of("script_id", "story", "message_id", "wait"));
            var prepared = adapter.freeze(leaf);
            assertEquals(leaf.config(), prepared);
            assertThrows(UnsupportedOperationException.class, () -> prepared.put("script_id", "changed"));
            assertEquals("UNKNOWN", adapter.recover(leaf, prepared).code());
            assertEquals(0, calls.get(), "Neither selection nor interruption recovery may replay a dialogue");
        }
    }

    @Test void separateOccurrencesSharingOneRootBothDispatchTheirFrozenActions() throws Exception {
        var scripts = new java.util.ArrayList<String>();
        var adapter = new BrnQuestRewardComposition(BrnQuestRewardComposition.Action.START,
                (player, config) -> { scripts.add(config.get("script_id")); return true; });
        var first = leaf("first", Map.of("script_id", "first_story"));
        var second = leaf("second", Map.of("script_id", "second_story"));
        assertEquals(RewardClaimResult.State.SUCCESS, adapter.execute(first, adapter.freeze(first)).state());
        assertEquals(RewardClaimResult.State.SUCCESS, adapter.execute(second, adapter.freeze(second)).state());
        assertEquals(java.util.List.of("first_story", "second_story"), scripts);
    }

    @Test void invalidConfigIsRejectedBeforeDispatch() {
        var adapter = new BrnQuestRewardComposition(BrnQuestRewardComposition.Action.RESUME,
                (player, config) -> { fail("Invalid config dispatched"); return true; });
        assertThrows(IllegalArgumentException.class, () -> adapter.prepare(leaf("one", Map.of())));
        assertThrows(IllegalArgumentException.class, () -> adapter.validateConfig(Map.of("script_id", "story", "message_id", " ")));
        assertDoesNotThrow(() -> adapter.validateConfig(Map.of("script_id", "story")));
    }

    @Test void failedDispatchDoesNotReportSuccess() {
        var adapter = new BrnQuestRewardComposition(BrnQuestRewardComposition.Action.OPEN_SCREEN, (player, config) -> false);
        var result = adapter.execute(leaf("one", Map.of()), Map.of());
        assertEquals(RewardClaimResult.State.FAILURE, result.state());
        assertEquals("BRNTALK_ACTION_FAILED", result.code());
    }

    @Test void allThreeRegisteredRewardImplementationsOptIntoComposition() throws Exception {
        for (String name : java.util.List.of("StartConversationReward", "ResumeConversationReward", "OpenScreenReward")) {
            var ctor = Class.forName(BrnQuestIntegration.class.getName() + "$" + name).getDeclaredConstructor();
            ctor.setAccessible(true);
            assertInstanceOf(BrnQuestRewardComposition.class, ((RewardType<?>) ctor.newInstance()).composition().orElseThrow());
        }
    }

    @Test void realRewardTableCodecAcceptsDialogueLeavesInEveryMode() throws Exception {
        var names = Map.of("start_conversation", "StartConversationReward", "resume_conversation", "ResumeConversationReward",
                "open_screen", "OpenScreenReward");
        for (var entry : names.entrySet()) {
            if (RewardTypeRegistry.get(id(entry.getKey())) != null) continue;
            var ctor = Class.forName(BrnQuestIntegration.class.getName() + "$" + entry.getValue()).getDeclaredConstructor();
            ctor.setAccessible(true);
            RewardTypeRegistry.register(id(entry.getKey()), (RewardType<?>) ctor.newInstance());
        }
        // Exercise the actual table decoder shipped by BRNQuest, including its composition opt-in check.
        var tableType = (RewardType<?>) Class.forName(
                "yourscraft.jasdewstarfield.brnquest.builtin.reward.table.RewardTableReward").getConstructor().newInstance();
        for (String mode : java.util.List.of("all", "random", "choice")) {
            String table = "{\"version\":1,\"mode\":\"" + mode + "\",\"entries\":["
                    + "{\"entry_id\":\"start\",\"type\":\"brntalk:start_conversation\",\"config\":{\"script_id\":\"story\"}},"
                    + "{\"entry_id\":\"resume\",\"type\":\"brntalk:resume_conversation\",\"config\":{\"script_id\":\"story\",\"message_id\":\"wait\"}},"
                    + "{\"entry_id\":\"screen\",\"type\":\"brntalk:open_screen\",\"config\":{}}]}";
            var view = new RewardView(id("book"), id("table"), ResourceLocation.parse("brnquest:reward_table"),
                    Map.of("table", table), "manual", false);
            assertEquals(java.util.Optional.empty(), RewardTypeExecutor.configError(tableType, view), mode);
        }
    }
}

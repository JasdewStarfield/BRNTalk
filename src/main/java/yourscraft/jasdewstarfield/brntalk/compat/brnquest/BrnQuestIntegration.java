package yourscraft.jasdewstarfield.brntalk.compat.brnquest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi;
import yourscraft.jasdewstarfield.brnquest.api.OperationContext;
import yourscraft.jasdewstarfield.brnquest.api.OperationResult;
import yourscraft.jasdewstarfield.brnquest.api.ProgressView;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;
import yourscraft.jasdewstarfield.brnquest.event.BrnQuestEvents;
import yourscraft.jasdewstarfield.brnquest.event.QuestBookReloadedEvent;
import yourscraft.jasdewstarfield.brnquest.extension.BrnQuestExtensionRegistrar;
import yourscraft.jasdewstarfield.brnquest.extension.BrnQuestPlugin;
import yourscraft.jasdewstarfield.brnquest.extension.BrnQuestPlugins;
import yourscraft.jasdewstarfield.brnquest.reward.RewardContext;
import yourscraft.jasdewstarfield.brnquest.reward.RewardResult;
import yourscraft.jasdewstarfield.brnquest.reward.RewardType;
import yourscraft.jasdewstarfield.brnquest.task.TaskContext;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.BrntalkAPI;
import yourscraft.jasdewstarfield.brntalk.event.PlayerCompletedConversationEvent;
import yourscraft.jasdewstarfield.brntalk.event.PlayerSeenMessageEvent;
import yourscraft.jasdewstarfield.brntalk.platform.BrntalkPlatform;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;

import java.util.List;
import java.util.Optional;

/** Optional integration owned by BRNTalk and loaded only when BRNQuest is present. */
public final class BrnQuestIntegration {
    public static final ResourceLocation MESSAGE_SEEN = id("message_seen");
    public static final ResourceLocation CONVERSATION_COMPLETE = id("conversation_complete");
    public static final ResourceLocation START_CONVERSATION = id("start_conversation");
    public static final ResourceLocation RESUME_CONVERSATION = id("resume_conversation");
    public static final ResourceLocation OPEN_SCREEN = id("open_screen");
    private static final ResourceLocation PLUGIN_ID = id("brnquest_integration");
    private static final OperationContext CONTEXT = OperationContext.integration(PLUGIN_ID);
    private static boolean installed;

    private BrnQuestIntegration() {}

    /** Registers task types during mod construction, before BRNQuest freezes its common registry. */
    public static synchronized void install() {
        if (installed) return;
        BrnQuestPlugins.register(new Plugin());
        NeoForge.EVENT_BUS.addListener(BrnQuestIntegration::onMessageSeen);
        NeoForge.EVENT_BUS.addListener(BrnQuestIntegration::onConversationCompleted);
        NeoForge.EVENT_BUS.addListener(BrnQuestIntegration::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(BrnQuestIntegration::onPlayerLoggedIn);
        BrnQuestEvents.subscribe(QuestBookReloadedEvent.class, ignored -> reconcileOnlinePlayers());
        installed = true;
        Brntalk.LOGGER.info("[BRNTalk/BRNQuest] Registered task and reward integration");
    }

    private static void onMessageSeen(PlayerSeenMessageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            advanceMatchingTasks(player, MESSAGE_SEEN, event.getScriptId(), event.getMessageId());
        }
    }

    private static void onConversationCompleted(PlayerCompletedConversationEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            advanceMatchingTasks(player, CONVERSATION_COMPLETE, event.getScriptId(), null);
        }
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        // Queue after all datapack-sync listeners so BRNTalk threads and both active data snapshots are settled.
        event.getRelevantPlayers().forEach(BrnQuestIntegration::scheduleReconciliation);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) scheduleReconciliation(player);
    }

    private static void scheduleReconciliation(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null) server.execute(() -> reconcile(player));
    }

    private static void reconcileOnlinePlayers() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.getPlayerList().getPlayers().forEach(BrnQuestIntegration::scheduleReconciliation);
    }

    private static void reconcile(ServerPlayer player) {
        for (var quest : BrnQuestApi.getQuests()) {
            ProgressView progress = BrnQuestApi.getProgress(player, quest.id().toString()).orElse(null);
            for (TaskView task : quest.tasks()) {
                if (progress != null && progress.taskProgress().getOrDefault(task.id(), 0L) >= 1L) continue;
                if (task.typeId().equals(MESSAGE_SEEN)) {
                    String scriptId = task.config().get("script_id");
                    String messageId = task.config().get("message_id");
                    if (BrntalkAPI.hasSeen(player, scriptId, messageId)) advance(player, quest.id(), task);
                } else if (task.typeId().equals(CONVERSATION_COMPLETE)) {
                    String scriptId = task.config().get("script_id");
                    if (BrntalkAPI.hasCompleted(player, scriptId)) advance(player, quest.id(), task);
                }
            }
            // Also repairs quests left in the former "objective submitted but quest available" state.
            BrnQuestApi.submitQuestCompletionResult(CONTEXT, player, quest.id().toString(), false);
        }
    }

    private static void advanceMatchingTasks(ServerPlayer player, ResourceLocation typeId,
                                             String scriptId, String messageId) {
        // Invalid external event data must never turn into wildcard task progress.
        if (scriptId == null || scriptId.isBlank()) {
            return;
        }
        for (var quest : BrnQuestApi.getQuests()) {
            for (TaskView task : quest.tasks()) {
                if (!task.typeId().equals(typeId) || !scriptId.equals(task.config().get("script_id"))) continue;
                if (messageId != null && !messageId.equals(task.config().get("message_id"))) continue;
                advance(player, quest.id(), task);
            }
        }
    }

    private static void advance(ServerPlayer player, ResourceLocation questId, TaskView task) {
        ProgressView progress = BrnQuestApi.getProgress(player, questId.toString()).orElse(null);
        if (progress != null && progress.taskProgress().getOrDefault(task.id(), 0L) >= 1L) {
            BrnQuestApi.submitQuestCompletionResult(CONTEXT, player, questId.toString(), false);
            return;
        }
        OperationResult result = BrnQuestApi.addTaskProgressResult(CONTEXT, player, task.id().toString(), 1L);
        if (!result.success()) {
            Brntalk.LOGGER.warn("[BRNTalk/BRNQuest] Could not advance task {}: {} ({})",
                    task.id(), result.message(), result.code());
            return;
        }
        // Event-driven objectives have no player submission step. Let BRNQuest atomically finish
        // the quest when this was its final required objective; incomplete siblings remain authoritative.
        BrnQuestApi.submitQuestCompletionResult(CONTEXT, player, questId.toString(), false);
    }

    private static final class Plugin implements BrnQuestPlugin {
        public ResourceLocation id() {
            return PLUGIN_ID;
        }

        public void register(BrnQuestExtensionRegistrar registrar) {
            registrar.task(MESSAGE_SEEN, new MessageSeenTask())
                    .task(CONVERSATION_COMPLETE, new ConversationCompleteTask())
                    .reward(START_CONVERSATION, new StartConversationReward())
                    .reward(RESUME_CONVERSATION, new ResumeConversationReward())
                    .reward(OPEN_SCREEN, new OpenScreenReward());
        }
    }

    private record MessageSeenConfig(String title, String scriptId, String messageId) {}

    private static final class MessageSeenTask implements TaskType<MessageSeenConfig> {
        private static final Codec<MessageSeenConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("title", "").forGetter(MessageSeenConfig::title),
                NON_BLANK.fieldOf("script_id").forGetter(MessageSeenConfig::scriptId),
                NON_BLANK.fieldOf("message_id").forGetter(MessageSeenConfig::messageId)
        ).apply(instance, MessageSeenConfig::new));

        public Codec<MessageSeenConfig> configCodec() {
            return CODEC;
        }

        public boolean satisfied(TaskContext context, MessageSeenConfig config) {
            return context.progress() >= 1L;
        }

        public List<ConfigFieldDescriptor> configFields() {
            return List.of(
                    ConfigFieldDescriptor.field("title", ConfigValueType.TEXT)
                            .withHelp("Optional player-facing objective title"),
                    ConfigFieldDescriptor.field("script_id", ConfigValueType.TEXT).asRequired()
                            .withHelp("BRNTalk dialogue script ID"),
                    ConfigFieldDescriptor.field("message_id", ConfigValueType.TEXT).asRequired()
                            .withHelp("Message node ID reached by the player")
            );
        }

        public Component describe(TaskView task, MessageSeenConfig config) {
            return Component.literal(config.title().isBlank()
                    ? "Reach " + config.scriptId() + "/" + config.messageId() : config.title());
        }
    }

    private record ConversationCompleteConfig(String title, String scriptId) {}

    private static final class ConversationCompleteTask implements TaskType<ConversationCompleteConfig> {
        private static final Codec<ConversationCompleteConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("title", "").forGetter(ConversationCompleteConfig::title),
                NON_BLANK.fieldOf("script_id").forGetter(ConversationCompleteConfig::scriptId)
        ).apply(instance, ConversationCompleteConfig::new));

        public Codec<ConversationCompleteConfig> configCodec() {
            return CODEC;
        }

        public boolean satisfied(TaskContext context, ConversationCompleteConfig config) {
            return context.progress() >= 1L;
        }

        public List<ConfigFieldDescriptor> configFields() {
            return List.of(
                    ConfigFieldDescriptor.field("title", ConfigValueType.TEXT)
                            .withHelp("Optional player-facing objective title"),
                    ConfigFieldDescriptor.field("script_id", ConfigValueType.TEXT).asRequired()
                            .withHelp("BRNTalk dialogue script ID to complete"));
        }

        public Component describe(TaskView task, ConversationCompleteConfig config) {
            return Component.literal(config.title().isBlank()
                    ? "Complete conversation " + config.scriptId() : config.title());
        }
    }

    private record StartConversationConfig(String scriptId) {}

    private static final class StartConversationReward implements RewardType<StartConversationConfig> {
        private static final Codec<StartConversationConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                NON_BLANK.fieldOf("script_id").forGetter(StartConversationConfig::scriptId)
        ).apply(instance, StartConversationConfig::new));

        public Codec<StartConversationConfig> configCodec() {
            return CODEC;
        }

        public List<ConfigFieldDescriptor> configFields() {
            return List.of(
                    ConfigFieldDescriptor.field("title", ConfigValueType.TEXT)
                            .withHelp("Optional player-facing reward title"),
                    ConfigFieldDescriptor.field("script_id", ConfigValueType.TEXT).asRequired()
                            .withHelp("BRNTalk dialogue script ID to start"));
        }

        public RewardResult execute(RewardContext context, StartConversationConfig config) {
            return executeOnce(context, "start:" + config.scriptId(),
                    () -> BrntalkAPI.startConversation(context.player(), config.scriptId()),
                    "BRNTalk conversation started", "BRNTalk dialogue script was not found");
        }
    }

    private record ResumeConversationConfig(String scriptId, Optional<String> messageId) {}

    private static final class ResumeConversationReward implements RewardType<ResumeConversationConfig> {
        private static final Codec<ResumeConversationConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                NON_BLANK.fieldOf("script_id").forGetter(ResumeConversationConfig::scriptId),
                NON_BLANK.optionalFieldOf("message_id").forGetter(ResumeConversationConfig::messageId)
        ).apply(instance, ResumeConversationConfig::new));

        public Codec<ResumeConversationConfig> configCodec() {
            return CODEC;
        }

        public List<ConfigFieldDescriptor> configFields() {
            return List.of(
                    ConfigFieldDescriptor.field("title", ConfigValueType.TEXT)
                            .withHelp("Optional player-facing reward title"),
                    ConfigFieldDescriptor.field("script_id", ConfigValueType.TEXT).asRequired()
                            .withHelp("BRNTalk dialogue script ID to resume"),
                    ConfigFieldDescriptor.field("message_id", ConfigValueType.TEXT)
                            .withHelp("Optional current WAIT message ID used to select the thread")
            );
        }

        public RewardResult execute(RewardContext context, ResumeConversationConfig config) {
            String messageId = config.messageId().orElse(null);
            String action = "resume:" + config.scriptId() + (messageId == null ? "" : ":" + messageId);
            return executeOnce(context, action,
                    () -> BrntalkAPI.resumeConversation(context.player(), config.scriptId(), messageId) > 0,
                    "BRNTalk conversation resumed", "No matching BRNTalk conversation could be resumed");
        }
    }

    private static final class OpenScreenReward implements RewardType<Optional<Boolean>> {
        private static final Codec<Optional<Boolean>> CODEC = Codec.BOOL.optionalFieldOf("open").codec();

        public Codec<Optional<Boolean>> configCodec() {
            return CODEC;
        }

        public List<ConfigFieldDescriptor> configFields() {
            return List.of(ConfigFieldDescriptor.field("title", ConfigValueType.TEXT)
                    .withHelp("Optional player-facing reward title"));
        }

        public RewardResult execute(RewardContext context, Optional<Boolean> ignored) {
            return executeOnce(context, "open_screen", () -> BrntalkAPI.openTalkScreen(context.player()),
                    "BRNTalk screen opened", "BRNTalk screen could not be opened");
        }
    }

    /**
     * Uses the same persisted owner/reward/cycle inputs as BRNQuest's KubeJS bridge. A PENDING receipt is written
     * before the BRNTalk side effect, so a crash can be diagnosed without replaying a conversation on restart.
     */
    private static RewardResult executeOnce(RewardContext context, String action, RewardAction operation,
                                            String successMessage, String failureMessage) {
        var progress = BrnQuestApi.getProgress(context.player(), context.questId().toString()).orElse(null);
        if (progress == null) return RewardResult.failure("Quest progress is unavailable");
        String key = progress.owner().providerId() + "|" + progress.owner().ownerId() + "|"
                + context.reward().id() + "|" + progress.completedAtEpochMillis();
        PlayerTalkState state = BrntalkPlatform.getTalkState(context.player());
        if (!state.beginExternalAction(key, action)) {
            PlayerTalkState.ExternalActionReceipt receipt = state.getExternalActionReceipt(key);
            if (receipt != null && receipt.status() == PlayerTalkState.ExternalActionStatus.FAILED) {
                return RewardResult.failure("Previous BRNTalk reward attempt failed: " + receipt.action());
            }
            return RewardResult.success("BRNTalk reward was already dispatched");
        }
        try {
            boolean succeeded = operation.run();
            state.finishExternalAction(key, succeeded);
            return succeeded ? RewardResult.success(successMessage) : RewardResult.failure(failureMessage);
        } catch (RuntimeException exception) {
            state.finishExternalAction(key, false);
            Brntalk.LOGGER.error("[BRNTalk/BRNQuest] Reward action {} failed for key {}", action, key, exception);
            return RewardResult.failure("BRNTalk reward action failed: " + exception.getMessage());
        }
    }

    @FunctionalInterface
    private interface RewardAction {
        boolean run();
    }

    private static final Codec<String> NON_BLANK = Codec.STRING.comapFlatMap(value -> value == null || value.isBlank()
            ? DataResult.error(() -> "Value must not be blank")
            : DataResult.success(value), value -> value);

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, path);
    }
}

package yourscraft.jasdewstarfield.brntalk.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrnQuestOptionalBoundaryTest {
    private static final Path PROJECT = Path.of(System.getProperty("brntalk.projectDir"));

    @Test
    void foreignTypesStayInsideTheGuardedCompatibilityPackage() throws IOException {
        Path sourceRoot = PROJECT.resolve("src/main/java");
        try (var files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (source.contains("yourscraft.jasdewstarfield.brnquest")) {
                    assertTrue(file.toString().replace('\\', '/').contains("/compat/brnquest/"),
                            () -> "BRNQuest reference escaped optional boundary: " + file);
                }
            }
        }
    }

    @Test
    void commonBootstrapChecksPresenceBeforeResolvingIntegration() throws IOException {
        Path hook = PROJECT.resolve("src/main/java/yourscraft/jasdewstarfield/brntalk/platform/PlatformModHooks.java");
        String source = Files.readString(hook, StandardCharsets.UTF_8);

        int guard = source.indexOf("ModList.get().isLoaded(\"brnquest\")");
        int install = source.indexOf("BrnQuestIntegration.install()");
        assertTrue(guard >= 0 && install > guard);
        assertFalse(source.contains("import yourscraft.jasdewstarfield.brnquest"));
    }

    @Test
    void integrationDeclaresAllDialogueRewardsBehindAStableReceipt() throws IOException {
        Path integration = PROJECT.resolve(
                "src/main/java/yourscraft/jasdewstarfield/brntalk/compat/brnquest/BrnQuestIntegration.java");
        String source = Files.readString(integration, StandardCharsets.UTF_8);

        assertTrue(source.contains(".reward(START_CONVERSATION"));
        assertTrue(source.contains(".reward(RESUME_CONVERSATION"));
        assertTrue(source.contains(".reward(OPEN_SCREEN"));
        assertTrue(source.contains("progress.owner().providerId()"));
        assertTrue(source.contains("progress.owner().ownerId()"));
        assertTrue(source.contains("context.reward().id()"));
        assertTrue(source.contains("progress.completedAtEpochMillis()"));
        assertTrue(source.contains("BrnQuestApi.submitQuestCompletionResult(CONTEXT, player, questId.toString(), false)"),
                "event-driven objectives must attempt normal quest completion after recording progress");

        // The receipt must be acquired before any supplied BRNTalk operation is invoked.
        int beginReceipt = source.indexOf("state.beginExternalAction(key, action)");
        int runOperation = source.indexOf("operation.run()");
        assertTrue(beginReceipt >= 0 && runOperation > beginReceipt);
    }

    @Test
    void clientIntegrationRegistersLocalizedPresentationsBeforeClientSetup() throws IOException {
        Path clientHook = PROJECT.resolve(
                "src/main/java/yourscraft/jasdewstarfield/brntalk/platform/PlatformClientHooks.java");
        Path integration = PROJECT.resolve(
                "src/main/java/yourscraft/jasdewstarfield/brntalk/compat/brnquest/BrnQuestClientIntegration.java");
        String hookSource = Files.readString(clientHook, StandardCharsets.UTF_8);
        String integrationSource = Files.readString(integration, StandardCharsets.UTF_8);

        assertTrue(hookSource.contains("ModList.get().isLoaded(\"brnquest\")"));
        assertTrue(hookSource.contains("BrnQuestClientIntegration.install()"));
        assertTrue(integrationSource.contains("ClientTaskPresentationRegistry.register"));
        assertTrue(integrationSource.contains("ClientRewardPresentationRegistry.register"));
        assertTrue(integrationSource.contains("screen.brntalk.brnquest.task.conversation_complete"));
        assertTrue(integrationSource.contains("Component.translatable(typeKey)"),
                "default task titles must be localized instead of exposing the internal type ID");
    }
}

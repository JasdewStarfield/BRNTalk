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

        // The receipt must be acquired before any supplied BRNTalk operation is invoked.
        int beginReceipt = source.indexOf("state.beginExternalAction(key, action)");
        int runOperation = source.indexOf("operation.run()");
        assertTrue(beginReceipt >= 0 && runOperation > beginReceipt);
    }
}

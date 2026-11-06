package yourscraft.jasdewstarfield.brntalk.data;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 活动版本与失败报告独立：无效候选不能暴露任何一部分。 */
class ConversationRepositoryTest {
    private TalkConversation script(String id, String next) {
        var script = new TalkConversation(id);
        script.addMessage(new TalkMessage("first", TalkMessage.Type.TEXT, TalkMessage.SpeakerType.NPC,
                "NPC", "Text", null, 0, next));
        return script;
    }

    @Test void programmaticReplacementIsValidatedAsAWholeAndCopiesTheCandidateMap() {
        var repository = new ConversationRepository();
        var old = script("old", null);
        Map<String, TalkConversation> candidate = new HashMap<>(Map.of("old", old));
        repository.replace(candidate);
        candidate.clear();
        long version = repository.activeVersion();
        assertSame(old, repository.get("old"));
        assertThrows(IllegalArgumentException.class, () -> repository.replace(Map.of(
                "new", script("new", null), "broken", script("broken", "missing"))));
        assertEquals(version, repository.activeVersion());
        assertTrue(repository.lastReport().hasProblems());
        assertSame(old, repository.get("old"));
        assertNull(repository.get("new"));
        repository.replace(Map.of());
        assertEquals(version + 1, repository.activeVersion());
        assertNull(repository.get("old"));
    }

    @Test void failedPreparationRetainsTheActiveVersionAndPublishesItsFailure() {
        var repository = new ConversationRepository();
        repository.reload(Map.of(ResourceLocation.parse("test:script"), JsonParser.parseString("""
                {"id":"old","messages":[{"id":"first"}]}
                """)));
        long version = repository.activeVersion();
        repository.preparationFailed(ResourceLocation.parse("test:broken"), new IllegalStateException("Malformed JSON"));
        assertFalse(repository.wasLastReloadApplied());
        assertEquals(version, repository.activeVersion());
        assertNotNull(repository.get("old"));
        assertEquals(1, repository.lastReport().failedFileCount());
    }
}

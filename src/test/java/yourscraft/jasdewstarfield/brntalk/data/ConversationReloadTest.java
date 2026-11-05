package yourscraft.jasdewstarfield.brntalk.data;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ConversationReloadTest {
    private final ConversationLoader loader = new ConversationLoader();
    private final TalkManager manager = TalkManager.getInstance();
    private static final ResourceLocation ID = ResourceLocation.parse("test:dialogue");

    @AfterEach void clear() { manager.clear(); }

    private void load(String json) { loader.apply(Map.of(ID, JsonParser.parseString(json)), null, null); }
    private void seed() { load("""
            {"id":"intro","messages":[{"id":"hello","text":"Old script"}]}
            """); }

    @Test void invalidCandidateRetainsOldScriptAndLiveThread() {
        seed();
        var previous = manager.getConversation("intro");
        var player = UUID.randomUUID();
        var thread = new TalkThread("thread", "intro", 0, 0);
        manager.restoreThread(player, thread);
        load("""
                {"conversations":[{"id":"new","messages":[{"id":"a"}]},{"id":"intro","messages":[]}]}
                """);
        assertFalse(ConversationLoader.wasLastReloadApplied());
        assertSame(previous, manager.getConversation("intro"));
        assertNull(manager.getConversation("new"));
        assertSame(thread, manager.getActiveThread(player, "thread"));
    }

    @Test void successfulReplacementAndIntentionalDeletionAreApplied() {
        seed();
        load("""
                {"id":"replacement","messages":[{"id":"hello"}]}
                """);
        assertTrue(ConversationLoader.wasLastReloadApplied());
        assertNull(manager.getConversation("intro"));
        assertNotNull(manager.getConversation("replacement"));
        loader.apply(Map.of(), null, null);
        assertTrue(ConversationLoader.wasLastReloadApplied());
        assertNull(manager.getConversation("replacement"));
    }

    @Test void duplicateIdsAndInvalidRootRejectTheWholeCandidate() {
        seed();
        var previous = manager.getConversation("intro");
        load("""
                {"conversations":[{"id":"intro","messages":[{"id":"a"}]},{"id":"intro","messages":[{"id":"b"}]}]}
                """);
        assertSame(previous, manager.getConversation("intro"));
        load("[]");
        assertSame(previous, manager.getConversation("intro"));
        assertFalse(ConversationLoader.wasLastReloadApplied());
    }

    @Test void malformedJsonCannotMasqueradeAsADeletedResource() {
        seed();
        var previous = manager.getConversation("intro");
        var resource = new Resource(null, () -> new ByteArrayInputStream("{\"id\":".getBytes(StandardCharsets.UTF_8)));
        var resources = (ResourceManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("listResources"))
                        return Map.of(ResourceLocation.parse("test:brntalk/dialogues/intro.json"), resource);
                    throw new UnsupportedOperationException(method.getName());
                });
        assertThrows(IllegalStateException.class, () -> loader.prepare(resources, null));
        assertSame(previous, manager.getConversation("intro"));
    }
}

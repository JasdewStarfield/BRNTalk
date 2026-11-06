package yourscraft.jasdewstarfield.brntalk.runtime;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 恢复使用真实存档与运行存储，验证会话、完成账本及通知边界。 */
class ConversationRecoveryTest {
    private final TalkManager manager = new TalkManager();
    private final ConversationService service = manager.operations();
    private final RecordingConversationPlayer player = new RecordingConversationPlayer();

    private void script() {
        var script = new TalkConversation("script");
        script.addMessage(new TalkMessage("wait", TalkMessage.Type.WAIT, TalkMessage.SpeakerType.NPC, "NPC", "Wait", "reward", 0, "end"));
        script.addMessage(new TalkMessage("end", TalkMessage.Type.TEXT, TalkMessage.SpeakerType.NPC, "NPC", "Done", "reward", 0, null));
        manager.registerConversation(script);
    }

    @Test void loginMigratesOnceBeforeOneFullSyncAndNeverReplaysHistoryActions() {
        script();
        var legacy = new PlayerTalkState();
        legacy.startThread("saved", "script", "wait");
        player.migrate = () -> {
            if (player.state != legacy) { player.replaceState(legacy); player.trace.add("migrated"); }
        };
        service.login(player);
        var first = manager.getActiveThread(player.id, "saved");
        assertNotNull(first);
        assertEquals(0, first.getCurrentMessage().getTimestamp());
        assertEquals(List.of("replace", "migrated", "sync"), player.trace);
        player.trace.clear();
        manager.clearThreadsForPlayer(player.id);
        service.login(player);
        assertEquals(List.of("sync"), player.trace);
        assertNotEquals(first.getSessionId(), manager.getActiveThread(player.id, "saved").getSessionId());
        assertEquals(1, service.resume(player, "script", "wait"));
        assertEquals(List.of("sync", "action:end", "seen:end", "complete", "append"), player.trace);
    }

    @Test void repeatedRestorationCompletesOldTerminalSavesOnceWithoutActionsOrSeenEvents() {
        script();
        player.state.startThread("saved", "script", "end");
        player.onCompleted = () -> service.complete(player, manager.getActiveThread(player.id, "saved"));
        service.rebuild(player);
        service.rebuild(player);
        assertEquals(List.of("complete"), player.trace);
        assertTrue(player.state.hasCompletedConversation("script"));
    }

    @Test void failedReloadKeepsIdentityAndSuccessfulReloadReplacesSessionIncludingDeletion() {
        script();
        var first = service.start(player, "script", null).thread();
        player.trace.clear();
        manager.scripts().reload(Map.of(ResourceLocation.parse("test:bad"), JsonParser.parseString("""
                {"id":"script","messages":[{"id":"broken","nextId":"missing"}]}
                """)));
        service.synchronizeReload(List.of(player), manager.scripts().wasLastReloadApplied());
        assertSame(first, manager.getActiveThread(player.id, first.getId()));
        assertEquals(List.of("sync"), player.trace);
        script();
        service.synchronizeReload(List.of(player), manager.scripts().wasLastReloadApplied());
        var rebuilt = manager.getActiveThread(player.id, first.getId());
        assertNotSame(first, rebuilt);
        assertNotEquals(first.getSessionId(), rebuilt.getSessionId());
        manager.replaceConversations(Map.of());
        service.synchronizeReload(List.of(player), true);
        assertTrue(manager.getActiveThreads(player.id).isEmpty());
        assertTrue(player.state.hasThread(first.getId()));
    }

    @Test void logoutAndIntegratedServerStopReleaseRuntimeStateButPreserveSavedProgress() {
        script();
        service.start(player, "script", null);
        manager.clearThreadsForPlayer(player.id);
        assertTrue(manager.getActiveThreads(player.id).isEmpty());
        service.rebuild(player);
        assertEquals(1, manager.getActiveThreads(player.id).size());
        manager.clear();
        assertNull(manager.getConversation("script"));
        assertTrue(manager.getActiveThreads(player.id).isEmpty());
        assertEquals(1, player.state.getThreadIds().size());
        script();
        service.rebuild(player);
        assertEquals("wait", manager.getActiveThreads(player.id).iterator().next().getCurrentMessage().getId());
    }
}

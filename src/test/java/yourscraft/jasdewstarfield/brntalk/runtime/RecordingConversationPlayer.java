package yourscraft.jasdewstarfield.brntalk.runtime;

import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/** 用真实存档对象记录平台边界，避免为测试复制服务端业务规则。 */
class RecordingConversationPlayer implements ConversationPlayer {
    final UUID id = UUID.randomUUID();
    final List<String> trace = new ArrayList<>();
    PlayerTalkState state = new PlayerTalkState();
    BiConsumer<TalkThread, TalkMessage> action = (thread, message) -> {};
    Runnable onCompleted = () -> {};
    BiConsumer<String, String> onSeen = (script, message) -> {};

    @Override public UUID id() { return id; }
    @Override public PlayerTalkState state() { return state; }
    @Override public void replaceState(PlayerTalkState state) { this.state = state; trace.add("replace"); }
    @Override public void executeAction(TalkThread thread, TalkMessage message) {
        trace.add("action:" + message.getId());
        action.accept(thread, message);
    }
    @Override public void seen(String scriptId, String messageId) {
        trace.add("seen:" + messageId);
        onSeen.accept(scriptId, messageId);
    }
    @Override public void completed(TalkThread thread) { trace.add("complete"); onCompleted.run(); }
    @Override public void added(TalkThread thread) { trace.add("add"); }
    @Override public void appended(String threadId, List<TalkMessage> messages) { trace.add("append"); }
    @Override public void sync() { trace.add("sync"); }
    @Override public void openScreen() { trace.add("open"); }
    @Override public void read(String threadId, long time) { trace.add("read"); }
}

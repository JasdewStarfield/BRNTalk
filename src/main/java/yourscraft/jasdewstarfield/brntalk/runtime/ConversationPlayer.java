package yourscraft.jasdewstarfield.brntalk.runtime;

import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;

import java.util.List;
import java.util.UUID;

/** 对话操作所需的平台端口；业务服务无需解析客户端类或可选模组类型。 */
public interface ConversationPlayer {
    UUID id();
    PlayerTalkState state();
    void replaceState(PlayerTalkState state);
    void executeAction(TalkThread thread, TalkMessage message);
    void seen(String scriptId, String messageId);
    void completed(TalkThread thread);
    void added(TalkThread thread);
    void appended(String threadId, List<TalkMessage> messages);
    void sync();
    void openScreen();
    void read(String threadId, long time);
}

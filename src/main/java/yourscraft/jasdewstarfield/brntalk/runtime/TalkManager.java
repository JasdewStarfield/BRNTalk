package yourscraft.jasdewstarfield.brntalk.runtime;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
import yourscraft.jasdewstarfield.brntalk.data.ConversationRepository;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.platform.ServerConversationPlayer;

import java.util.*;

/** 运行数据的兼容门面；对话操作统一交给 ConversationService。 */
public class TalkManager {
    private static final TalkManager INSTANCE = new TalkManager();
    private final ConversationRepository conversations = new ConversationRepository();
    private final Map<UUID, Map<String, TalkThread>> playerThreads = new HashMap<>();
    private final ConversationService operations = new ConversationService(this);

    TalkManager() {}
    public static TalkManager getInstance() { return INSTANCE; }
    ConversationService operations() { return operations; }

    public void restoreThread(UUID playerUuid, TalkThread thread) {
        playerThreads.computeIfAbsent(playerUuid, k -> new HashMap<>()).put(thread.getId(), thread);
    }
    public void clearAllThreads() { playerThreads.clear(); }
    public void clearThreadsForPlayer(UUID playerUuid) { playerThreads.remove(playerUuid); }
    public TalkConversation getConversation(String id) { return conversations.get(id); }
    public ConversationRepository scripts() { return conversations; }
    public void registerConversation(TalkConversation conversation) { conversations.register(conversation); }
    public void replaceConversations(Map<String, TalkConversation> candidate) { conversations.replace(candidate); }
    public void clear() { conversations.clear(); playerThreads.clear(); }

    /** 保留旧内存操作签名，调用方自行处理存档和通知的约定不变。 */
    public TalkThread startThread(ServerPlayer player, String scriptId, String startMsgId) {
        return operations.startRuntime(new ServerConversationPlayer(player), scriptId, startMsgId);
    }
    public List<TalkMessage> proceedThread(ServerPlayer player, String threadId, String nextMsgId) {
        return operations.proceedRuntime(new ServerConversationPlayer(player), threadId, nextMsgId);
    }
    public List<TalkMessage> resumeThread(ServerPlayer player, String threadId) {
        return operations.resumeRuntime(new ServerConversationPlayer(player), threadId);
    }
    public void removeThread(UUID playerUuid, String threadId) {
        Map<String, TalkThread> threads = playerThreads.get(playerUuid);
        if (threads != null) {
            threads.remove(threadId);
            if (threads.isEmpty()) playerThreads.remove(playerUuid);
        }
    }
    public Collection<TalkThread> getActiveThreads(UUID playerUuid) {
        return playerThreads.getOrDefault(playerUuid, Collections.emptyMap()).values();
    }
    public TalkThread getActiveThread(UUID playerUuid, String threadId) {
        var threads = playerThreads.get(playerUuid);
        return threads == null ? null : threads.get(threadId);
    }
}

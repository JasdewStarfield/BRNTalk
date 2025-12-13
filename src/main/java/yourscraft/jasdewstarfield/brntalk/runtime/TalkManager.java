package yourscraft.jasdewstarfield.brntalk.runtime;

import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;

import java.util.*;

public class TalkManager {

    private static final TalkManager INSTANCE = new TalkManager();

    // 从json加载脚本
    private final Map<String, TalkConversation> conversations = new HashMap<>();

    // 已触发的聊天串
    private final Map<UUID, Map<String, TalkThread>> playerThreads = new HashMap<>();

    private TalkManager() {
    }

    public static TalkManager getInstance() {
        return INSTANCE;
    }

    public void restoreThread(UUID playerUuid,TalkThread thread) {
        playerThreads.computeIfAbsent(playerUuid, k -> new HashMap<>())
                .put(thread.getId(), thread);
    }

    public void clearAllThreads() {
        playerThreads.clear();
    }

    public void clearThreadsForPlayer(UUID playerUuid) {
        playerThreads.remove(playerUuid);
    }

    public TalkConversation getConversation(String id) {
        return conversations.get(id);
    }

    public void registerConversation(TalkConversation conversation) {
        conversations.put(conversation.getId(), conversation);
    }

    public void clear() {
        conversations.clear();
        playerThreads.clear();
    }

    public TalkThread startThread(UUID playerUuid, String conversationId) {
        TalkConversation conv = conversations.get(conversationId);
        if (conv == null) {
            return null;
        }

        long now = System.currentTimeMillis();

        // 创建新线程
        TalkThread thread = new TalkThread(conversationId, now);
        thread.appendConversation(conv);

        // 存入对应玩家的 Map 中
        playerThreads.computeIfAbsent(playerUuid, k -> new HashMap<>())
                .put(conversationId, thread);

        return thread;
    }

    public Collection<TalkThread> getActiveThreads(UUID playerUuid) {
        return playerThreads.getOrDefault(playerUuid, Collections.emptyMap()).values();
    }

    public TalkThread getActiveThread(UUID playerUuid, String threadId) {
        var map = playerThreads.get(playerUuid);
        if (map == null) return null;
        return map.get(threadId);
    }
}
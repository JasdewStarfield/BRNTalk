package yourscraft.jasdewstarfield.brntalk.runtime;

import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;

import java.util.*;

public class TalkManager {

    private static final TalkManager INSTANCE = new TalkManager();

    // 从json加载脚本
    private final Map<String, TalkConversation> conversations = new HashMap<>();

    // 已触发的聊天串
    private final Map<String, TalkThread> activeThreads = new HashMap<>();

    private TalkManager() {
    }

    public static TalkManager getInstance() {
        return INSTANCE;
    }

    public TalkConversation getConversation(String id) {
        return conversations.get(id);
    }

    public void registerConversation(TalkConversation conversation) {
        conversations.put(conversation.getId(), conversation);
    }

    public void clear() {
        conversations.clear();
        activeThreads.clear();
    }

    public TalkThread startThread(String conversationId) {
        TalkConversation conv = conversations.get(conversationId);
        if (conv == null) {
            return null;
        }

        long now = System.currentTimeMillis();

        // 简单处理：同一个脚本 id 只有一个线程，多次 start 会覆盖旧的
        TalkThread thread = new TalkThread(conversationId, now);
        thread.appendConversation(conv);

        activeThreads.put(conversationId, thread);
        return thread;
    }

    public Collection<TalkThread> getActiveThreads() {
        return activeThreads.values();
    }

    public TalkThread getActiveThread(String id) {
        return activeThreads.get(id);
    }
}
package yourscraft.jasdewstarfield.brntalk.client;

import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.*;

public class ClientTalkState {
    private static final ClientTalkState INSTANCE = new ClientTalkState();

    private final List<TalkThread> threads = new ArrayList<>();

    public static ClientTalkState get() {
        return INSTANCE;
    }

    public void setThreads(List<TalkThread> newThreads) {
        threads.clear();
        threads.addAll(newThreads);
    }

    public List<TalkThread> getThreads() {
        return Collections.unmodifiableList(threads);
    }

    public boolean hasUnread(TalkThread thread) {
        if (thread == null) return false;
        long lastActivity = thread.getLastActivityTime();
        long lastRead = thread.getLastReadTime();
        return lastActivity > lastRead;
    }

    public void clear() {
        threads.clear();
    }
}

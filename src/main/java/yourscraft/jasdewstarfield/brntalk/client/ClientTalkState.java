package yourscraft.jasdewstarfield.brntalk.client;

import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
}

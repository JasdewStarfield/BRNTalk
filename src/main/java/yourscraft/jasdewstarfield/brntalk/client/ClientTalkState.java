package yourscraft.jasdewstarfield.brntalk.client;

import net.minecraft.client.Minecraft;
import yourscraft.jasdewstarfield.brntalk.client.ui.TalkScreen;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
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

    public void addThread(TalkThread newThread) {
        // 防止重复添加
        threads.removeIf(t -> t.getId().equals(newThread.getId()));

        threads.add(newThread);

        notifyUI();
    }

    public void appendMessages(String threadId, List<TalkMessage> newMsgs) {
        TalkThread target = null;
        for (TalkThread t : threads) {
            if (t.getId().equals(threadId)) {
                target = t;
                break;
            }
        }

        if (target != null) {
            for (TalkMessage m : newMsgs) {
                target.appendMessage(m);
            }
            notifyUI();
        }
    }

    public void updateReadTime(String threadId, long newTime) {
        for (TalkThread t : threads) {
            if (t.getId().equals(threadId)) {
                t.setLastReadTime(newTime);
                notifyUI();
                return;
            }
        }
    }

    private void notifyUI() {
        if (Minecraft.getInstance().screen instanceof TalkScreen screen) {
            screen.onThreadsSynced();
        }
    }

    public void clear() {
        threads.clear();
    }
}

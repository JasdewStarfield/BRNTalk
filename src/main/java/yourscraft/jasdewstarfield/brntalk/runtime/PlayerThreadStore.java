package yourscraft.jasdewstarfield.brntalk.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 仅持有玩家的运行线程；存档、剧情执行和生命周期时机由调用方负责。 */
final class PlayerThreadStore {
    private final Map<UUID, Map<String, TalkThread>> threads = new HashMap<>();

    void put(UUID player, TalkThread thread) {
        threads.computeIfAbsent(player, ignored -> new HashMap<>()).put(thread.getId(), thread);
    }
    TalkThread get(UUID player, String threadId) {
        var playerThreads = threads.get(player);
        return playerThreads == null ? null : playerThreads.get(threadId);
    }
    Collection<TalkThread> getAll(UUID player) {
        return threads.getOrDefault(player, Collections.emptyMap()).values();
    }
    void remove(UUID player, String threadId) {
        var playerThreads = threads.get(player);
        if (playerThreads != null) {
            playerThreads.remove(threadId);
            if (playerThreads.isEmpty()) threads.remove(player);
        }
    }
    void clearPlayer(UUID player) { threads.remove(player); }
    void clear() { threads.clear(); }
}

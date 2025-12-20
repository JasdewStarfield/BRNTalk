package yourscraft.jasdewstarfield.brntalk.runtime;

import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * 启动线程逻辑
     */
    public TalkThread startThread(UUID playerUuid, String scriptId, String startMsgId) {
        TalkConversation conv = conversations.get(scriptId);
        if (conv == null) {
            return null;
        }

        // 确定起始消息
        TalkMessage firstMsg;
        if (startMsgId == null) {
            firstMsg = conv.getFirstMessage();
        } else {
            firstMsg = conv.getMessage(startMsgId);
        }

        if (firstMsg == null) {
            // 剧本可能是空的，或者找不到指定ID
            return null;
        }

        // 创建新线程
        long now = System.currentTimeMillis();
        String threadId = UUID.randomUUID().toString();
        TalkThread thread = new TalkThread(threadId, scriptId, now, 0L);

        // 存入对应玩家的初始消息
        thread.appendMessage(firstMsg.withTimestamp(now));

        // 尝试自动推进
        autoAdvance(thread, conv);

        playerThreads.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .put(threadId, thread);

        return thread;
    }

    public void removeThread(UUID playerUuid, String threadId) {
        Map<String, TalkThread> threads = playerThreads.get(playerUuid);
        if (threads != null) {
            threads.remove(threadId);
            // 如果玩家没有任何对话了，把玩家的空 Map 也移除
            if (threads.isEmpty()) {
                playerThreads.remove(playerUuid);
            }
        }
    }

    public List<String> proceedThread(UUID playerUuid, String threadId, String nextMsgId) {
        TalkThread thread = getActiveThread(playerUuid, threadId);
        if (thread == null) return Collections.emptyList();

        TalkConversation conv = conversations.get(thread.getScriptId());
        if (conv == null) return Collections.emptyList();

        TalkMessage nextMsg = conv.getMessage(nextMsgId);
        if (nextMsg == null) return Collections.emptyList();

        // 记录添加前的状态，方便计算新增了哪些
        int oldSize = thread.getMessages().size();

        // 1. 添加目标消息
        thread.appendMessage(nextMsg.withTimestamp(System.currentTimeMillis()));

        // 2. 尝试自动推进
        autoAdvance(thread, conv);

        // 3. 收集所有新增的消息 ID
        List<TalkMessage> allMsgs = thread.getMessages();
        List<TalkMessage> newMsgs = allMsgs.subList(oldSize, allMsgs.size());

        return newMsgs.stream().map(TalkMessage::getId).toList();
    }

    private void autoAdvance(TalkThread thread, TalkConversation conv) {
        int safetyLimit = 100; // 防止恶意脚本死循环
        int count = 0;

        while (count < safetyLimit) {
            TalkMessage lastMsg = thread.getCurrentMessage();
            if (lastMsg == null) break;

            // 只有 TEXT 类型且有 nextId 才自动推进
            // CHOICE 类型必须停下来等玩家选
            if (lastMsg.getType() == TalkMessage.Type.TEXT && lastMsg.getNextId() != null) {
                String nextId = lastMsg.getNextId();
                TalkMessage nextMsg = conv.getMessage(nextId);

                if (nextMsg == null) {
                    // 脚本断链了
                    break;
                }

                thread.appendMessage(nextMsg.withTimestamp(System.currentTimeMillis()));
                count++;
            } else {
                // 遇到 Choice 或者 终点(nextId==null)，停止
                break;
            }
        }
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
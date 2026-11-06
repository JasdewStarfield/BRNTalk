package yourscraft.jasdewstarfield.brntalk.runtime;

import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;

import java.util.*;

/** 服务端对话操作的唯一协调入口；每条动作仍与消息追加交错执行。 */
public final class ConversationService {
    private final TalkManager manager;
    private final ConversationRestorer restorer;

    ConversationService(TalkManager manager) {
        this.manager = manager;
        this.restorer = new ConversationRestorer(manager, this);
    }

    public static ConversationService getInstance() { return TalkManager.getInstance().operations(); }

    public enum Status { NO_CHANGE, SUCCESS, REJECTED, FAILURE }

    /** 新增消息为操作完成时的快照，不持有线程列表的可变视图。 */
    public record Result(Status status, TalkThread thread, List<TalkMessage> messages) {
        public Result { messages = List.copyOf(messages); }
        public boolean succeeded() { return status == Status.SUCCESS; }
    }

    public Result start(ConversationPlayer player, String scriptId, String startMessageId) {
        TalkThread thread = startRuntime(player, scriptId, startMessageId);
        if (thread == null) return new Result(Status.FAILURE, null, List.of());
        List<TalkMessage> messages = List.copyOf(thread.getMessages());
        List<String> ids = messages.stream().map(TalkMessage::getId).toList();
        PlayerTalkState state = player.state();
        if (!ids.isEmpty()) {
            state.startThread(thread.getId(), scriptId, ids.getFirst());
            if (ids.size() > 1) state.appendMessages(thread.getId(), ids.subList(1, ids.size()));
            notifySeen(player, scriptId, ids);
            completeIfTerminal(player, thread);
        }
        player.added(thread);
        return new Result(Status.SUCCESS, thread, messages);
    }

    public Result select(ConversationPlayer player, String threadId, String choiceId, String sessionId, int position) {
        TalkThread thread = manager.getActiveThread(player.id(), threadId);
        if (thread == null) return new Result(Status.REJECTED, null, List.of());
        // 必须先在线程上消费显示上下文，命令/事件回调重入也不能重复消费。
        TalkMessage.Choice selected = thread.consumeChoice(sessionId, position, choiceId);
        if (selected == null) return new Result(Status.REJECTED, thread, List.of());
        String next = selected.getNextId();
        if (next == null || next.isEmpty()) {
            complete(player, thread);
            return new Result(Status.SUCCESS, thread, List.of());
        }
        List<TalkMessage> messages = proceedRuntime(player, threadId, next);
        return finishAppend(player, player.state(), thread, messages);
    }

    public int resume(ConversationPlayer player, String scriptId, String matchMessageId) {
        PlayerTalkState state = player.state();
        List<String> targets = new ArrayList<>();
        for (String id : state.getThreadIds()) {
            var saved = state.getThread(id);
            if (!saved.getScriptId().equals(scriptId)) continue;
            TalkThread thread = manager.getActiveThread(player.id(), id);
            if (matchMessageId == null || (thread != null && thread.getCurrentMessage() != null
                    && thread.getCurrentMessage().getId().equals(matchMessageId))) targets.add(id);
        }
        int count = 0;
        // 先固定目标集合，保留回调新增/清除线程时原 API 的遍历边界。
        for (String id : targets) {
            List<TalkMessage> messages = resumeRuntime(player, id);
            if (!messages.isEmpty()) {
                finishAppend(player, state, manager.getActiveThread(player.id(), id), id, scriptId, messages, true);
                count++;
            }
        }
        return count;
    }

    private Result finishAppend(ConversationPlayer player, PlayerTalkState state, TalkThread thread, List<TalkMessage> messages) {
        return finishAppend(player, state, thread, thread.getId(), thread.getScriptId(), messages, false);
    }

    private Result finishAppend(ConversationPlayer player, PlayerTalkState state, TalkThread thread,
                                String threadId, String scriptId, List<TalkMessage> messages, boolean resolveAfterEvents) {
        if (messages.isEmpty()) return new Result(Status.NO_CHANGE, thread, messages);
        List<String> ids = messages.stream().map(TalkMessage::getId).toList();
        state.appendMessages(threadId, ids);
        notifySeen(player, scriptId, ids);
        // WAIT API 原先在事件回调之后查找活动线程；选择路径则保留已消费的线程。
        completeIfTerminal(player, resolveAfterEvents ? manager.getActiveThread(player.id(), threadId) : thread);
        player.appended(threadId, messages);
        return new Result(Status.SUCCESS, thread, messages);
    }

    private void notifySeen(ConversationPlayer player, String scriptId, List<String> ids) {
        for (String id : ids) player.seen(scriptId, id);
    }

    public boolean clearAll(ConversationPlayer player) {
        if (player.state().isEmpty()) return false;
        // 全清仍替换整个状态（包括外部回执），特定脚本清除则保留回执。
        player.replaceState(new PlayerTalkState());
        manager.clearThreadsForPlayer(player.id());
        player.sync();
        return true;
    }

    public boolean clear(ConversationPlayer player, String scriptId) {
        PlayerTalkState state = player.state();
        List<String> targets = state.getThreadIds().stream()
                .filter(id -> state.getThread(id).getScriptId().equals(scriptId)).toList();
        boolean completionRemoved = state.clearCompletedConversation(scriptId);
        if (targets.isEmpty() && !completionRemoved) return false;
        for (String id : targets) {
            state.removeThread(id);
            manager.removeThread(player.id(), id);
        }
        player.sync();
        return true;
    }

    public void open(ConversationPlayer player) {
        player.sync();
        player.openScreen();
    }

    /** 登录迁移先于历史恢复，客户端只接收迁移后的一个全量快照。 */
    public void login(ConversationPlayer player) {
        player.migrateLegacyState();
        rebuild(player);
        player.sync();
    }

    public void rebuild(ConversationPlayer player) { restorer.rebuild(player); }

    public void synchronizeReload(Collection<? extends ConversationPlayer> players, boolean applied) {
        if (applied) manager.clearAllThreads();
        for (var player : players) {
            // 失败重载不重建，保留线程身份和原会话回执。
            if (applied) rebuild(player);
            player.sync();
        }
    }

    public void markRead(ConversationPlayer player, String threadId) {
        long now = System.currentTimeMillis();
        player.state().updateLastReadTime(threadId, now);
        TalkThread thread = manager.getActiveThread(player.id(), threadId);
        if (thread != null) thread.setLastReadTime(now);
        player.read(threadId, now);
    }

    /** TEXT 终点自动完成；WAIT 和 CHOICE 仍需要显式继续/选择。 */
    public void completeIfTerminal(ConversationPlayer player, TalkThread thread) {
        TalkMessage current = thread == null ? null : thread.getCurrentMessage();
        if (current != null && current.getType() == TalkMessage.Type.TEXT && current.getNextId() == null) complete(player, thread);
    }

    public void complete(ConversationPlayer player, TalkThread thread) {
        if (thread != null && player.state().markConversationCompleted(thread.getScriptId())) {
            // 先写完成账本，再通知可重入的联动事件。
            player.completed(thread);
        }
    }

    // 以下底层入口保留 TalkManager 旧公开方法的内存操作语义，不隐式保存或发包。
    public TalkThread startRuntime(ConversationPlayer player, String scriptId, String startMsgId) {
        TalkConversation conv = manager.getConversation(scriptId);
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
        TalkMessage msgWithTime = firstMsg.withTimestamp(now);
        thread.appendMessage(msgWithTime);

        player.executeAction(thread, msgWithTime);

        // 尝试自动推进
        autoAdvance(player, thread, conv);

        manager.restoreThread(player.id(), thread);

        return thread;
    }

    public List<TalkMessage> proceedRuntime(ConversationPlayer player, String threadId, String nextMsgId) {
        TalkThread thread = manager.getActiveThread(player.id(), threadId);
        if (thread == null) return Collections.emptyList();

        TalkConversation conv = manager.getConversation(thread.getScriptId());
        if (conv == null) return Collections.emptyList();

        TalkMessage nextMsg = conv.getMessage(nextMsgId);
        if (nextMsg == null) return Collections.emptyList();

        // 记录添加前的状态，方便计算新增了哪些
        int oldSize = thread.getMessages().size();

        // 1. 添加目标消息
        TalkMessage msgWithTime = nextMsg.withTimestamp(System.currentTimeMillis());
        thread.appendMessage(msgWithTime);
        player.executeAction(thread, msgWithTime);

        // 2. 尝试自动推进
        autoAdvance(player, thread, conv);

        // 3. 收集所有新增的消息 ID
        List<TalkMessage> allMsgs = thread.getMessages();
        if (allMsgs.size() <= oldSize) return Collections.emptyList();

        return List.copyOf(allMsgs.subList(oldSize, allMsgs.size()));
    }

    private void autoAdvance(ConversationPlayer player, TalkThread thread, TalkConversation conv) {
        int safetyLimit = 100; // 防止恶意脚本死循环
        int count = 0;

        while (count < safetyLimit) {
            TalkMessage lastMsg = thread.getCurrentMessage();
            if (lastMsg == null) break;

            // 若为 WAIT，CHOICE 类型，则停止
            if (lastMsg.getType() == TalkMessage.Type.WAIT ||
                    lastMsg.getType() == TalkMessage.Type.CHOICE ||
                    lastMsg.getNextId() == null) {
                break;
            }

            // 只有 TEXT 类型且有 nextId 才自动推进
            if (lastMsg.getType() == TalkMessage.Type.TEXT) {
                String nextId = lastMsg.getNextId();
                TalkMessage nextMsg = conv.getMessage(nextId);

                if (nextMsg == null) {
                    // 脚本断链了
                    break;
                }

                TalkMessage msgWithTime = nextMsg.withTimestamp(System.currentTimeMillis());
                thread.appendMessage(msgWithTime);

                player.executeAction(thread, msgWithTime);

                count++;
            }
        }

        if (count >= safetyLimit) {
            TalkMessage lastMsg = thread.getCurrentMessage();
            String messageId = lastMsg == null ? "<unknown>" : lastMsg.getId();
            Brntalk.LOGGER.warn(
                    "[BRNTalk] Auto-advance stopped after {} steps for script '{}' thread '{}' at message '{}'. " +
                            "This is a runtime safety guard; check the dialogue graph for an unexpected loop.",
                    safetyLimit,
                    thread.getScriptId(),
                    thread.getId(),
                    messageId
            );
        }
    }

    /**
     * 恢复/继续指定线程的对话 (用于从 WAIT 状态解除)
     * @return 新增的消息 ID 列表
     */
    public List<TalkMessage> resumeRuntime(ConversationPlayer player, String threadId) {
        TalkThread thread = manager.getActiveThread(player.id(), threadId);
        if (thread == null) return Collections.emptyList();

        TalkMessage lastMsg = thread.getCurrentMessage();
        if (lastMsg == null || lastMsg.getNextId() == null) {
            return Collections.emptyList();
        }

        return proceedRuntime(player, threadId, lastMsg.getNextId());
    }

}

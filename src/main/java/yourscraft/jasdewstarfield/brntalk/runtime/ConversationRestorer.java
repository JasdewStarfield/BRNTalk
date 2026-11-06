package yourscraft.jasdewstarfield.brntalk.runtime;

import java.util.List;

/** 按存档消息 ID 重建历史；恢复路径从不调用消息动作或增量通知。 */
final class ConversationRestorer {
    private final TalkManager manager;
    private final ConversationService service;

    ConversationRestorer(TalkManager manager, ConversationService service) {
        this.manager = manager;
        this.service = service;
    }

    void rebuild(ConversationPlayer player) {
        var state = player.state();
        manager.clearThreadsForPlayer(player.id());
        // 完成事件可能回调 API 修改存档，遍历恢复开始时的 ID 快照。
        for (String id : List.copyOf(state.getThreadIds())) {
            var saved = state.getThread(id);
            if (saved == null) continue;
            var script = manager.getConversation(saved.getScriptId());
            if (script == null) continue;
            var thread = new TalkThread(id, saved.getScriptId(), saved.getStartTime(), saved.getLastReadTime());
            for (String messageId : saved.getHistory()) {
                var message = script.getMessage(messageId);
                if (message != null) thread.appendMessage(message.withTimestamp(0L));
            }
            manager.restoreThread(player.id(), thread);
            // 旧存档可补记终点；服务先写账本再发完成事件，重复恢复保持幂等。
            service.completeIfTerminal(player, thread);
        }
    }
}

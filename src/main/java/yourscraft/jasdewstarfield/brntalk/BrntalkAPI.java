package yourscraft.jasdewstarfield.brntalk;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.event.PlayerSeenMessageEvent;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;
import yourscraft.jasdewstarfield.brntalk.save.TalkWorldData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 这是一个对外公开的 API 类。
 * 其他模组（如 KubeJS, CC:Tweaked 等）可以通过调用这里的静态方法来控制对话。
 */
public class BrntalkAPI {

    /**
     * 为指定玩家强制开启一段对话。
     *
     * @param player 目标玩家 (ServerPlayer)
     * @param scriptId 对话脚本的 ID (JSON 文件名或内部 ID)
     * @return 如果成功启动返回 true，如果 ID 不存在或玩家无效返回 false
     */
    public static boolean startConversation(ServerPlayer player, String scriptId) {
        if (player == null || scriptId == null) {
            return false;
        }

        TalkManager manager = TalkManager.getInstance();

        // 1. 尝试在内存中启动线程
        TalkThread thread = manager.startThread(player.getUUID(), scriptId, null);
        if (thread == null) {
            // 脚本 ID 不存在
            return false;
        }

        // 2. 写入世界存档 (NBT)
        TalkWorldData data = TalkWorldData.get(player.serverLevel());

        List<String> allMsgIds = thread.getMessages().stream()
                .map(TalkMessage::getId)
                .toList();

        if (!allMsgIds.isEmpty()) {
            // 1. 存第一条 (创建线程结构)
            data.startThread(player.getUUID(), thread.getId(), scriptId, allMsgIds.getFirst());

            // 2. 如果还有后续，批量追加
            if (allMsgIds.size() > 1) {
                List<String> restIds = allMsgIds.subList(1, allMsgIds.size());
                data.appendMessages(player.getUUID(), thread.getId(), restIds);
            }

            // 3. 触发事件
            for (String msgId : allMsgIds) {
                NeoForge.EVENT_BUS.post(new PlayerSeenMessageEvent(player, scriptId, msgId));
            }
        }

        // 4. 同步网络包给客户端
        TalkNetwork.sendAddThread(player, thread);

        return true;
    }

    /**
     * 清除指定玩家的【所有】对话进度。
     *
     * @param player 目标玩家
     * @return 如果找到了对话并成功清除，返回 true；如果没找到，返回 false
     */
    public static boolean clearAllConversation(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        // 1. 清除存档
        TalkWorldData data = TalkWorldData.get(player.serverLevel());
        PlayerTalkState state = data.get(player.getUUID());

        if (state == null || state.getThreadIds().isEmpty()) {
            return false;
        }

        data.removeAllThread(player.getUUID());

        // 2. 清除运行时内存
        TalkManager manager = TalkManager.getInstance();
        manager.clearThreadsForPlayer(player.getUUID());

        // 3. 同步状态
        TalkNetwork.syncThreadsTo(player);

        return true;
    }

    /**
     * 清除指定玩家的【特定】对话进度。
     *
     * @param player 目标玩家
     * @param scriptId 要清除的脚本 ID
     * @return 如果找到了对应的对话并成功清除，返回 true；如果没找到，返回 false
     */
    public static boolean clearConversation(ServerPlayer player, String scriptId) {
        if (player == null || scriptId == null) {
            return false;
        }

        // 1. 查找所有属于该 scriptId 的 threadId
        TalkWorldData data = TalkWorldData.get(player.serverLevel());
        PlayerTalkState state = data.get(player.getUUID());

        if (state == null) return false;

        List<String> threadsToRemove = new ArrayList<>();
        for (String tid : state.getThreadIds()) {
            PlayerTalkState.SavedThread st = state.getThread(tid);
            if (st != null && st.getScriptId().equals(scriptId)) {
                threadsToRemove.add(tid);
            }
        }

        if (threadsToRemove.isEmpty()) {
            return false;
        }

        // 2. 清除存档和运行时内存
        TalkManager manager = TalkManager.getInstance();
        for (String tid : threadsToRemove) {
            data.removeThread(player.getUUID(), tid);      // 移除存档
            manager.removeThread(player.getUUID(), tid);   // 移除内存
        }

        // 3. 同步状态
        TalkNetwork.syncThreadsTo(player);

        return true;
    }

    /**
     * 检查玩家是否已经阅读过某条消息（或到达过某个对话节点）。
     * 这可以用于判断玩家的剧情进度。
     *
     * @param player 目标玩家
     * @param scriptId 对话剧本 ID
     * @param messageId 消息 ID
     * @return 如果玩家的历史记录中包含该消息ID，返回 true
     */
    public static boolean hasSeen(ServerPlayer player, String scriptId, String messageId) {
        if (player == null || scriptId == null || messageId == null) {
            return false;
        }

        TalkWorldData data = TalkWorldData.get(player.serverLevel());
        return data.hasSeenMessage(player.getUUID(), scriptId, messageId);
    }

    /**
     * [新增] 尝试继续指定玩家的某个对话线程（通常用于解除 WAIT 状态）。
     *
     * @param player 目标玩家
     * @param scriptId 剧本 ID (用于查找线程)
     * @param matchMessageId (可选) 只有当线程当前停留的消息 ID 等于此值时才恢复。传 null 则不限制。
     * @return 成功推进返回 true，否则 false
     */
    public static int resumeConversation(ServerPlayer player, String scriptId, @Nullable String matchMessageId) {
        if (player == null || scriptId == null) return 0;

        TalkManager manager = TalkManager.getInstance();

        // 获取玩家存档数据
        TalkWorldData data = TalkWorldData.get(player.serverLevel());
        PlayerTalkState state = data.get(player.getUUID());
        if (state == null) return 0;

        List<String> targetThreadIds = new ArrayList<>();

        // 遍历玩家所有活跃线程，找到匹配 scriptId 的那个
        for (String tid : state.getThreadIds()) {
            PlayerTalkState.SavedThread st = state.getThread(tid);

            // 匹配 Script ID
            if (!st.getScriptId().equals(scriptId)) {
                continue;
            }

            // 匹配 Message ID (如果参数不为空)
            if (matchMessageId != null) {
                // 为了准确，我们检查运行时对象(TalkThread)的当前消息
                TalkThread runtimeThread = manager.getActiveThread(player.getUUID(), tid);

                // 如果运行时线程存在，且当前消息ID匹配
                if (runtimeThread != null) {
                    TalkMessage current = runtimeThread.getCurrentMessage();
                    if (current != null && current.getId().equals(matchMessageId)) {
                        targetThreadIds.add(tid);
                    }
                }
            } else {
                // 没指定 messageId，则匹配所有该脚本的线程
                targetThreadIds.add(tid);
            }
        }

        if (targetThreadIds.isEmpty()) return 0;

        int successCount = 0;

        for (String tid : targetThreadIds) {
            // 调用 TalkManager 的单线程恢复方法
            List<TalkMessage> newMsgs = manager.resumeThread(player.getUUID(), tid);

            if (!newMsgs.isEmpty()) {
                // 存入存档
                List<String> newIds = newMsgs.stream().map(TalkMessage::getId).toList();
                data.appendMessages(player.getUUID(), tid, newIds);

                // 触发事件
                for (String msgId : newIds) {
                    NeoForge.EVENT_BUS.post(new PlayerSeenMessageEvent(player, scriptId, msgId));
                }

                TalkNetwork.sendAppendMessages(player, tid, newMsgs);
                successCount++;
            }
        }

        return successCount;
    }
}

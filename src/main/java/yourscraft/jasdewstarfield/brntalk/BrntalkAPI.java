package yourscraft.jasdewstarfield.brntalk;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import yourscraft.jasdewstarfield.brntalk.save.TalkWorldData;

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
        }

        // 3. 同步网络包给客户端
        TalkNetwork.syncThreadsTo(player);

        return true;
    }

    /**
     * 清除指定玩家的【所有】对话进度。
     *
     * @param player 目标玩家
     * @return 成功执行返回 true
     */
    public static boolean clearAllConversation(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        // 1. 清除存档
        TalkWorldData data = TalkWorldData.get(player.serverLevel());
        data.removeAllThread(player.getUUID());

        // 2. 清除运行时内存
        TalkManager manager = TalkManager.getInstance();
        manager.clearThreadsForPlayer(player.getUUID());

        // 3. 同步空状态给客户端
        TalkNetwork.syncThreadsTo(player);

        return true;
    }

    /**
     * 清除指定玩家的【特定】对话进度。
     *
     * @param player 目标玩家
     * @param threadId 要清除的线程 ID (通常等于 scriptId)
     * @return 成功执行返回 true
     */
    public static boolean clearConversation(ServerPlayer player, String threadId) {
        if (player == null || threadId == null) {
            return false;
        }

        // 1. 清除存档
        TalkWorldData data = TalkWorldData.get(player.serverLevel());
        data.removeThread(player.getUUID(), threadId);

        // 2. 清除运行时内存
        TalkManager manager = TalkManager.getInstance();
        manager.removeThread(player.getUUID(), threadId);

        // 3. 同步最新状态（客户端会自动移除不在列表中的对话）
        TalkNetwork.syncThreadsTo(player);

        return true;
    }

    /**
     * 检查玩家当前是否有正在进行的对话线程。
     * @param player 目标玩家
     * @return 如果有活跃线程返回 true
     */
    public static boolean isTalking(ServerPlayer player) {
        if (player == null) return false;

        TalkManager manager = TalkManager.getInstance();
        var threads = manager.getActiveThreads(player.getUUID());
        return threads != null && !threads.isEmpty();
    }
}

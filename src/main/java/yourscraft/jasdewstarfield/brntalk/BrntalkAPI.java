package yourscraft.jasdewstarfield.brntalk;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;
import yourscraft.jasdewstarfield.brntalk.save.TalkWorldData;

/**
 * 这是一个对外公开的 API 类。
 * 其他模组（如 KubeJS, CC:Tweaked 等）可以通过调用这里的静态方法来控制对话。
 */
public class BrntalkAPI {

    /**
     * 为指定玩家强制开启一段对话。
     *
     * @param player 目标玩家 (ServerPlayer)
     * @param conversationId 对话脚本的 ID (JSON 文件名或内部 ID)
     * @return 如果成功启动返回 true，如果 ID 不存在或玩家无效返回 false
     */
    public static boolean startConversation(ServerPlayer player, String conversationId) {
        if (player == null || conversationId == null) {
            return false;
        }

        TalkManager manager = TalkManager.getInstance();

        // 1. 尝试在内存中启动线程
        TalkThread thread = manager.startThread(player.getUUID(), conversationId);
        if (thread == null) {
            // 脚本 ID 不存在
            return false;
        }

        // 2. 写入世界存档 (NBT)
        TalkWorldData data = TalkWorldData.get(player.serverLevel());
        data.startThread(player.getUUID(), thread.getId(), conversationId);

        // 3. 同步网络包给客户端
        TalkNetwork.syncThreadsTo(player);

        return true;
    }

    /**
     * 清除指定玩家的所有对话进度。
     *
     * @param player 目标玩家
     * @return 除非找不到玩家，总是返回 true (表示操作完成)
     */
    public static boolean clearConversation(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        // 1. 清除存档
        TalkWorldData data = TalkWorldData.get(player.serverLevel());
        data.clearPlayer(player.getUUID());

        // 2. 清除运行时内存
        TalkManager manager = TalkManager.getInstance();
        manager.clearThreadsForPlayer(player.getUUID());

        // 3. 同步空状态给客户端
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

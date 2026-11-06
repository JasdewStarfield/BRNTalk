package yourscraft.jasdewstarfield.brntalk;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brntalk.platform.BrntalkPlatform;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;

import yourscraft.jasdewstarfield.brntalk.runtime.ConversationService;
import yourscraft.jasdewstarfield.brntalk.platform.ServerConversationPlayer;

import javax.annotation.Nullable;

/**
 * 这是一个对外公开的 API 类。
 * 其他模组（如 KubeJS, CC:Tweaked 等）可以通过调用这里的静态方法来控制对话。
 */
public class BrntalkAPI {

    /**
     * 同步指定玩家的对话状态，并要求客户端打开 BRNTalk 对话界面。
     *
     * @param player 目标玩家
     * @return 成功发送打开请求时返回 true；玩家为空时返回 false
     */
    public static boolean openTalkScreen(ServerPlayer player) {
        if (player == null) return false;
        ConversationService.getInstance().open(new ServerConversationPlayer(player));
        return true;
    }

    /**
     * 为指定玩家强制开启一段对话。
     *
     * @param player 目标玩家 (ServerPlayer)
     * @param scriptId 对话脚本的 ID (JSON 文件名或内部 ID)
     * @return 如果成功启动返回 true，如果 ID 不存在或玩家无效返回 false
     */
    public static boolean startConversation(ServerPlayer player, String scriptId) {
        if (player == null || scriptId == null) return false;
        return ConversationService.getInstance().start(new ServerConversationPlayer(player), scriptId, null).succeeded();
    }

    /**
     * 清除指定玩家的【所有】对话进度。
     *
     * @param player 目标玩家
     * @return 如果找到了对话并成功清除，返回 true；如果没找到，返回 false
     */
    public static boolean clearAllConversation(ServerPlayer player) {
        return player != null && ConversationService.getInstance().clearAll(new ServerConversationPlayer(player));
    }

    /**
     * 清除指定玩家的【特定】对话进度。
     *
     * @param player 目标玩家
     * @param scriptId 要清除的脚本 ID
     * @return 如果找到了对应的对话并成功清除，返回 true；如果没找到，返回 false
     */
    public static boolean clearConversation(ServerPlayer player, String scriptId) {
        return player != null && scriptId != null
                && ConversationService.getInstance().clear(new ServerConversationPlayer(player), scriptId);
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

        PlayerTalkState state = BrntalkPlatform.getTalkState(player);
        return state.hasSeenMessage(scriptId, messageId);
    }

    /** Returns the persisted server-side completion state for one dialogue script. */
    public static boolean hasCompleted(ServerPlayer player, String scriptId) {
        if (player == null || scriptId == null) return false;
        return BrntalkPlatform.getTalkState(player).hasCompletedConversation(scriptId);
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
        return ConversationService.getInstance().resume(new ServerConversationPlayer(player), scriptId, matchMessageId);
    }
}

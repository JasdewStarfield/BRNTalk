package yourscraft.jasdewstarfield.brntalk.save;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class TalkWorldData extends SavedData {

    // playerUUID.toString() -> PlayerTalkState
    private final Map<String, PlayerTalkState> players = new HashMap<>();

    public TalkWorldData() {}

    // ----------- 对外操作接口 -----------

    public PlayerTalkState getOrCreate(UUID uuid) {
        return players.computeIfAbsent(uuid.toString(), s -> new PlayerTalkState());
    }

    public PlayerTalkState get(UUID uuid) {
        return players.get(uuid.toString());
    }

    /**
     * 启动一个新的对话线程
     * @param uuid 玩家UUID
     * @param threadId 线程ID (运行时唯一标识)
     * @param scriptId 剧本ID (Conversation ID)
     * @param startMsgId 起始消息 ID
     */
    public void startThread(UUID uuid, String threadId, String scriptId, String startMsgId) {
        PlayerTalkState state = getOrCreate(uuid);
        state.startThread(threadId, scriptId, startMsgId);
        setDirty();
    }

    /**
     * 记录玩家刚刚看到的一条新消息
     */
    public void appendMessage(UUID uuid, String threadId, String messageId) {
        PlayerTalkState state = getOrCreate(uuid);
        state.appendMessage(threadId, messageId);
        setDirty();
    }

    /**
     * 批量记录新消息 (用于自动推进逻辑)
     */
    public void appendMessages(UUID uuid, String threadId, List<String> messageIds) {
        PlayerTalkState state = getOrCreate(uuid);
        state.appendMessages(threadId, messageIds);
        setDirty();
    }

    // ------------ SavedData -------------

    /** 新建一个空实例 */
    public static TalkWorldData create() {
        return new TalkWorldData();
    }

    /** 从 NBT 读取一个实例 */
    public static TalkWorldData load(CompoundTag tag, HolderLookup.Provider lookup) {
        TalkWorldData data = new TalkWorldData();
        if (tag.contains("players")) {
            CompoundTag playersTag = tag.getCompound("players");
            for (String key : playersTag.getAllKeys()) {
                CompoundTag playerTag = playersTag.getCompound(key);
                PlayerTalkState state = PlayerTalkState.fromNbt(playerTag);
                data.players.put(key, state);
            }
        }
        return data;
    }

    /** 把当前实例写入 NBT */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag playersTag = new CompoundTag();
        for (Map.Entry<String, PlayerTalkState> entry : players.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            entry.getValue().saveToNbt(playerTag);
            playersTag.put(entry.getKey(), playerTag);
        }
        tag.put("players", playersTag);
        return tag;
    }

    /** SavedData.Factory */
    public static final SavedData.Factory<TalkWorldData> FACTORY =
            new SavedData.Factory<>(TalkWorldData::create, TalkWorldData::load);

    /** 从某个维度的 dataStorage 获取/创建数据 */
    public static TalkWorldData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "brntalk_talk_data");
    }

    /**
     * 清除玩家特定的对话线程
     */
    public void removeThread(UUID uuid, String threadId) {
        PlayerTalkState state = get(uuid);
        if (state != null) {
            state.removeThread(threadId);
            setDirty();
        }
    }

    /** 清除玩家全部的对话数据 */
    public void removeAllThread(UUID uuid) {
        if (players.remove(uuid.toString()) != null) {
            setDirty();
        }
    }
}

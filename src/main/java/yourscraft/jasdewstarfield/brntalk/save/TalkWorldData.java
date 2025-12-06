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

    // ----------- 对外操作接口（之前设计的那套） -----------

    public PlayerTalkState getOrCreate(UUID uuid) {
        return players.computeIfAbsent(uuid.toString(), s -> new PlayerTalkState());
    }

    public PlayerTalkState get(UUID uuid) {
        return players.get(uuid.toString());
    }

    public void startThread(UUID uuid, String threadId, String firstConversationId) {
        PlayerTalkState state = getOrCreate(uuid);
        state.startThread(threadId, firstConversationId);
        setDirty();
    }

    public void appendConversation(UUID uuid, String threadId, String conversationId) {
        PlayerTalkState state = getOrCreate(uuid);
        state.appendConversation(threadId, conversationId);
        setDirty();
    }

    // ------------- SavedData 必须实现的两个静态方法 + Factory -------------

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
            String key = entry.getKey();
            PlayerTalkState state = entry.getValue();

            CompoundTag playerTag = new CompoundTag();
            state.saveToNbt(playerTag);

            playersTag.put(key, playerTag);
        }

        tag.put("players", playersTag);
        return tag;
    }

    /** 1.21.1 的推荐写法：用 SavedData.Factory 而不是 SavedDataType */
    public static final SavedData.Factory<TalkWorldData> FACTORY =
            new SavedData.Factory<>(TalkWorldData::create, TalkWorldData::load);

    /** 从某个维度的 dataStorage 获取/创建我们的数据（你可以只挂在 Overworld） */
    public static TalkWorldData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "brntalk_talk_data");
    }

    /** 清除玩家全部的对话数据 */
    public void clearPlayer(UUID uuid) {
        String key = uuid.toString();
        if (players.remove(key) != null) {
            setDirty(); // 标记为已修改，让世界存盘时写回文件
        }
    }
}

package yourscraft.jasdewstarfield.brntalk.save;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Deprecated
public class TalkWorldData extends SavedData {

    // playerUUID.toString() -> PlayerTalkState
    private final Map<String, PlayerTalkState> players = new HashMap<>();

    public TalkWorldData() {}

    // ----------- 对外操作接口 -----------

    public PlayerTalkState get(UUID uuid) {
        return players.get(uuid.toString());
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
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
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

    /** 清除玩家全部的对话数据 */
    public void removeAllThread(UUID uuid) {
        if (players.remove(uuid.toString()) != null) {
            setDirty();
        }
    }
}

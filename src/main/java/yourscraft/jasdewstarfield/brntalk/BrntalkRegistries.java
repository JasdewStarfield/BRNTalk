package yourscraft.jasdewstarfield.brntalk;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.save.PlayerTalkState;

import java.util.function.Supplier;

public class BrntalkRegistries {

    // 创建注册器，绑定到 NeoForge 的 Attachment 注册表
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Brntalk.MODID);

    // 注册 PlayerTalkState 为一种 Attachment
    public static final Supplier<AttachmentType<PlayerTalkState>> PLAYER_TALK_STATE = ATTACHMENT_TYPES.register(
            "player_talk_state",
            () -> AttachmentType.builder(PlayerTalkState::new) // 默认工厂：创建空状态
                    .serialize(new IAttachmentSerializer<CompoundTag, PlayerTalkState>() {
                        @Override
                        public @NotNull PlayerTalkState read(@NotNull IAttachmentHolder holder, @NotNull CompoundTag tag,
                                                             HolderLookup.@NotNull Provider provider) {
                            return PlayerTalkState.fromNbt(tag);
                        }

                        @Override
                        public @NotNull CompoundTag write(@NotNull PlayerTalkState attachment,
                                                          HolderLookup.@NotNull Provider provider) {
                            CompoundTag tag = new CompoundTag();
                            attachment.saveToNbt(tag);
                            return tag;
                        }
                    })
                    .copyOnDeath()
                    .build()
    );

    // 在 Brntalk 主类构造函数中调用此方法
    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}

package yourscraft.jasdewstarfield.brntalk;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import yourscraft.jasdewstarfield.brntalk.platform.PlatformModHooks;

@Mod(Brntalk.MODID)
public class Brntalk {

    public static final String MODID = "brntalk";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Brntalk() {
        /*
         * 主类只保留 Forge loader 入口。
         * 实际注册逻辑放在平台层，方便和新版本分支保持共享逻辑一致。
         */
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        PlatformModHooks.register(modEventBus);
        LOGGER.info("[BRNTalk] Mod constructed");
    }
}

package yourscraft.jasdewstarfield.brntalk;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import yourscraft.jasdewstarfield.brntalk.platform.PlatformModHooks;

@Mod(Brntalk.MODID)
public class Brntalk {

    public static final String MODID = "brntalk";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Brntalk(IEventBus modEventBus, ModContainer modContainer) {
        /*
         * 主类只保留 loader 入口
         * 实际注册逻辑放在平台层
         */
        PlatformModHooks.register(modEventBus, modContainer);
        LOGGER.info("[BRNTalk] Mod constructed");
    }
}

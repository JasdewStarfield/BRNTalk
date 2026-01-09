package yourscraft.jasdewstarfield.brntalk;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import yourscraft.jasdewstarfield.brntalk.client.ClientInit;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;
import yourscraft.jasdewstarfield.brntalk.config.ClothConfigIntegration;
import yourscraft.jasdewstarfield.brntalk.data.ConversationLoader;

@Mod(Brntalk.MODID)
public class Brntalk {

    public static final String MODID = "brntalk";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Brntalk(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, BrntalkConfig.CLIENT_SPEC);

        // Common (Client + Server)
        modEventBus.addListener(this::onCommonSetup);
        BrntalkRegistries.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);

        // Server
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        // Client
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.init(modEventBus);
            // 检查 Cloth Config 是否已加载
            if (ModList.get().isLoaded("cloth_config")) {
                // 将注册逻辑委托给一个独立的方法或内部类
                registerConfigScreen(modContainer);
                LOGGER.info("[BRNTalk] Cloth Config integration active");
            }
        }

        LOGGER.info("[BRNTalk] Mod constructed");
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[BRNTalk] HELLO FROM COMMON SETUP");
    }

    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[BRNTalk] HELLO from server starting");
    }

    public void onRegisterCommands(RegisterCommandsEvent event) {
        BrntalkCommands.register(event.getDispatcher());
        LOGGER.info("[BRNTalk] Command registered");
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ConversationLoader());
        LOGGER.info("[BRNTalk] Reloading completed");
    }

    // 只有当 cloth_config 存在时，这个方法才会被调用
    private void registerConfigScreen(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, parent) -> ClothConfigIntegration.createScreen(parent)
        );
        LOGGER.info("[BRNTalk] Cloth Config screen registered");
    }
}

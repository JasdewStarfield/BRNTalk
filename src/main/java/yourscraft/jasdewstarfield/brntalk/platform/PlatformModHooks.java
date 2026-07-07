package yourscraft.jasdewstarfield.brntalk.platform;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.BrntalkCommands;
import yourscraft.jasdewstarfield.brntalk.BrntalkRegistries;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;
import yourscraft.jasdewstarfield.brntalk.config.ClothConfigIntegration;
import yourscraft.jasdewstarfield.brntalk.data.ConversationLoader;

public final class PlatformModHooks {
    private PlatformModHooks() {
    }

    public static void register(IEventBus modEventBus, ModContainer modContainer) {
        /*
         * 集中注册 NeoForge 启动 配置和通用事件
         * Forge 分支替换这个类即可保留共享逻辑
         */
        modContainer.registerConfig(ModConfig.Type.CLIENT, BrntalkConfig.CLIENT_SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, BrntalkConfig.SERVER_SPEC);

        modEventBus.addListener(PlatformModHooks::onCommonSetup);
        BrntalkRegistries.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onServerStarting);
        NeoForge.EVENT_BUS.addListener(PlatformModHooks::onRegisterCommands);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            PlatformClientHooks.register(modEventBus);
            registerOptionalConfigScreen(modContainer);
        }
    }

    private static void onCommonSetup(final FMLCommonSetupEvent event) {
        Brntalk.LOGGER.info("[BRNTalk] HELLO FROM COMMON SETUP");
    }

    private static void onServerStarting(ServerStartingEvent event) {
        Brntalk.LOGGER.info("[BRNTalk] HELLO from server starting");
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        BrntalkCommands.register(event.getDispatcher());
        Brntalk.LOGGER.info("[BRNTalk] Command registered");
    }

    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ConversationLoader());
        Brntalk.LOGGER.info("[BRNTalk] Reloading completed");
    }

    private static void registerOptionalConfigScreen(ModContainer modContainer) {
        if (!ModList.get().isLoaded("cloth_config")) {
            return;
        }

        /*
         * Cloth Config 是可选客户端集成
         * 未安装时不影响服务端和基础功能
         */
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, parent) -> ClothConfigIntegration.createScreen(parent)
        );
        Brntalk.LOGGER.info("[BRNTalk] Cloth Config integration active");
    }
}

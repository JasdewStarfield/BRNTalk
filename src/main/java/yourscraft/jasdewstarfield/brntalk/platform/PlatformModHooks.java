package yourscraft.jasdewstarfield.brntalk.platform;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.BrntalkCommands;
import yourscraft.jasdewstarfield.brntalk.BrntalkRegistries;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;
import yourscraft.jasdewstarfield.brntalk.config.ClothConfigIntegration;
import yourscraft.jasdewstarfield.brntalk.data.ConversationLoader;
import yourscraft.jasdewstarfield.brntalk.network.TalkNetwork;

public final class PlatformModHooks {
    private PlatformModHooks() {
    }

    public static void register(IEventBus modEventBus) {
        /*
         * 集中注册 Forge 启动、配置和通用事件。
         * 共享逻辑只调用 platform 包，不直接依赖 Forge API。
         */
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BrntalkConfig.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, BrntalkConfig.SERVER_SPEC);

        modEventBus.addListener(PlatformModHooks::onCommonSetup);
        BrntalkRegistries.register(modEventBus);
        MinecraftForge.EVENT_BUS.addListener(PlatformModHooks::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(PlatformModHooks::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(PlatformModHooks::onRegisterCommands);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            PlatformClientHooks.register(modEventBus);
            registerOptionalConfigScreen();
        }
    }

    private static void onCommonSetup(final FMLCommonSetupEvent event) {
        // Forge SimpleChannel 注册必须在通用启动阶段完成，避免客户端/服务端协议表不一致。
        TalkNetwork.register();
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

    private static void registerOptionalConfigScreen() {
        if (!ModList.get().isLoaded("cloth_config")) {
            return;
        }

        /*
         * Cloth Config 是可选客户端集成
         * 未安装时不影响服务端和基础功能
         */
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> ClothConfigIntegration.createScreen(parent)
                )
        );
        Brntalk.LOGGER.info("[BRNTalk] Cloth Config integration active");
    }
}

package yourscraft.jasdewstarfield.brntalk.platform;

import com.mojang.brigadier.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.commands.Commands;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.client.ClientKeyRegistry;
import yourscraft.jasdewstarfield.brntalk.client.text.ClientTextFormatter;
import yourscraft.jasdewstarfield.brntalk.client.ui.TalkHud;
import yourscraft.jasdewstarfield.brntalk.client.ui.TalkScreen;
import yourscraft.jasdewstarfield.brntalk.client.ui.button.OpenButton;
import yourscraft.jasdewstarfield.brntalk.compat.brnquest.BrnQuestClientIntegration;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;

public final class PlatformClientHooks {
    private PlatformClientHooks() {
    }

    public static void register(IEventBus modEventBus) {
        /*
         * 集中注册 NeoForge 客户端事件
         * Forge 分支替换这个类即可保留 UI 逻辑
         */
        modEventBus.addListener(PlatformClientHooks::onClientSetup);
        modEventBus.addListener(PlatformClientHooks::onRegisterKeyMappings);
        modEventBus.addListener(PlatformClientHooks::onRegisterClientReloadListeners);
        modEventBus.addListener(PlatformClientHooks::onRegisterGuiLayers);
        NeoForge.EVENT_BUS.addListener(PlatformClientHooks::onClientTick);
        NeoForge.EVENT_BUS.addListener(PlatformClientHooks::onScreenInit);
        NeoForge.EVENT_BUS.addListener(PlatformClientHooks::onRegisterClientCommands);
        // Install before BRNQuest freezes client presentations during client setup.
        if (ModList.get().isLoaded("brnquest")) BrnQuestClientIntegration.install();
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        Brntalk.LOGGER.info("[BRNTalk] HELLO FROM CLIENT SETUP");
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ClientKeyRegistry.createOpenScreenKeyMapping());
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.CHAT,
                TalkHud.LAYER_ID,
                TalkHud::render
        );
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ClientKeyRegistry.onClientTick();
        if (Minecraft.getInstance().player != null) {
            TalkHud.tick();
        }
    }

    private static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            ClientTextFormatter.clearCache();
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof TalkScreen talkScreen) {
                talkScreen.clearRenderCache();
            }
            Brntalk.LOGGER.debug("[BRNTalk] Text cache cleared due to resource reload.");
        });
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (ModList.get().isLoaded("ftblibrary") || !BrntalkConfig.CLIENT.displayOpenButton.get()) {
            return;
        }

        /*
         * 只在容器界面添加按钮
         * 避免普通菜单和全屏 UI 被额外按钮干扰
         */
        if (event.getScreen() instanceof AbstractContainerScreen<?>) {
            int btnX = BrntalkConfig.CLIENT.openButtonX.get();
            int btnY = BrntalkConfig.CLIENT.openButtonY.get();

            OpenButton openButton = new OpenButton(btnX, btnY, button -> TalkNetworking.requestOpenTalk());
            event.addListener(openButton);
        }
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("brntalk")
                        .then(Commands.literal("open_ui")
                                .executes(context -> {
                                    TalkNetworking.requestOpenTalk();
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
        );
    }
}

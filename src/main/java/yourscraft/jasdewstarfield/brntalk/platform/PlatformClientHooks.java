package yourscraft.jasdewstarfield.brntalk.platform;

import com.mojang.brigadier.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.commands.Commands;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.client.ClientKeyRegistry;
import yourscraft.jasdewstarfield.brntalk.client.text.ClientTextFormatter;
import yourscraft.jasdewstarfield.brntalk.client.ui.TalkHud;
import yourscraft.jasdewstarfield.brntalk.client.ui.TalkScreen;
import yourscraft.jasdewstarfield.brntalk.client.ui.button.OpenButton;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;

public final class PlatformClientHooks {
    private PlatformClientHooks() {
    }

    public static void register(IEventBus modEventBus) {
        /*
         * 集中注册 Forge 客户端事件。
         * UI 类只暴露渲染和状态方法，具体事件签名留在平台层适配。
         */
        modEventBus.addListener(PlatformClientHooks::onClientSetup);
        modEventBus.addListener(PlatformClientHooks::onRegisterKeyMappings);
        modEventBus.addListener(PlatformClientHooks::onRegisterClientReloadListeners);
        modEventBus.addListener(PlatformClientHooks::onRegisterGuiOverlays);
        MinecraftForge.EVENT_BUS.addListener(PlatformClientHooks::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(PlatformClientHooks::onScreenInit);
        MinecraftForge.EVENT_BUS.addListener(PlatformClientHooks::onRegisterClientCommands);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        Brntalk.LOGGER.info("[BRNTalk] HELLO FROM CLIENT SETUP");
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ClientKeyRegistry.createOpenScreenKeyMapping());
    }

    private static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(
                VanillaGuiOverlay.CHAT_PANEL.id(),
                "brntalk_hud",
                (gui, gfx, partialTick, screenWidth, screenHeight) -> TalkHud.render(gfx, partialTick)
        );
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
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

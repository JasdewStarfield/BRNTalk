package yourscraft.jasdewstarfield.brntalk.client;

import com.mojang.brigadier.Command;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.commands.Commands;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.client.ui.TalkHud;
import yourscraft.jasdewstarfield.brntalk.client.ui.button.OpenButton;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;

public class ClientInit {

    private ClientInit() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ClientInit::onClientSetup);
        modEventBus.addListener(ClientInit::onRegisterKeyMappings);
        modEventBus.addListener(ClientInit::onRegisterClientReloadListeners);
        modEventBus.addListener(ClientInit::onRegisterGuiLayers);
        NeoForge.EVENT_BUS.addListener(ClientInit::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientInit::onScreenInit);
        NeoForge.EVENT_BUS.addListener(ClientInit::onRegisterClientCommands);
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        Brntalk.LOGGER.info("[BRNTalk] HELLO FROM CLIENT SETUP");
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        ClientKeyRegistry.registerKeyMappings(event);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.CHAT,
                TalkHud.LAYER_ID,
                TalkHud::render
        );
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ClientKeyRegistry.onClientTick(event);
    }

    private static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            ClientTalkUtils.clearCache();
            Brntalk.LOGGER.debug("[BRNTalk] Text cache cleared due to resource reload.");
        });
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        // FTB Library Integration
        // 当 FTB Library 加载时，使用它的 Sidebar Buttons 注册
        if (ModList.get().isLoaded("ftblibrary") || !BrntalkConfig.CLIENT.displayOpenButton.get()) {
            return;
        }

        // 检查当前屏幕是否为容器类屏幕
        if (event.getScreen() instanceof AbstractContainerScreen<?>) {
            int btnX = BrntalkConfig.CLIENT.openButtonX.get();
            int btnY = BrntalkConfig.CLIENT.openButtonY.get();

            OpenButton openButton = new OpenButton(btnX, btnY, button -> ClientPayloadSender.requestOpenTalk());
            event.addListener(openButton);
        }
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("brntalk")
                        .then(Commands.literal("open_ui")
                            .executes(context -> {
                                ClientPayloadSender.requestOpenTalk();
                                return Command.SINGLE_SUCCESS;
                            })
                        )
        );
    }
}

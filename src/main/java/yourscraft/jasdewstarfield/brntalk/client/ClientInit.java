package yourscraft.jasdewstarfield.brntalk.client;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import yourscraft.jasdewstarfield.brntalk.Brntalk;

public class ClientInit {

    private ClientInit() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ClientInit::onClientSetup);
        modEventBus.addListener(ClientInit::onRegisterKeyMappings);
        modEventBus.addListener(ClientInit::onRegisterClientReloadListeners);
        NeoForge.EVENT_BUS.addListener(ClientInit::onClientTick);
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        Brntalk.LOGGER.info("[BRNTalk] HELLO FROM CLIENT SETUP");
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        ClientKeyRegistry.registerKeyMappings(event);
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
}

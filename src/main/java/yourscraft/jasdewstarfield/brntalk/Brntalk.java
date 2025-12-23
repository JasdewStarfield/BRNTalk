package yourscraft.jasdewstarfield.brntalk;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import yourscraft.jasdewstarfield.brntalk.client.ClientInit;
import yourscraft.jasdewstarfield.brntalk.data.ConversationLoader;

@Mod(Brntalk.MODID)
public class Brntalk {

    public static final String MODID = "brntalk";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Brntalk(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, BrntalkConfig.CLIENT_SPEC);

        // Common (Client + Server)
        modEventBus.addListener(this::onCommonSetup);
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);

        // Server
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        // Client
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.init(modEventBus);
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
    }
}

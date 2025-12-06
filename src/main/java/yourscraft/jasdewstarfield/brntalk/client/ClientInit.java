package yourscraft.jasdewstarfield.brntalk.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

public class ClientInit {
    private ClientInit() {}

    public static void init(IEventBus modEventBus) {
        // 键位：注册到 MOD bus 上
        modEventBus.addListener(ClientInit::onRegisterKeyMappings);

        // Tick：注册到 FORGE/NeoForge 总线
        NeoForge.EVENT_BUS.addListener(ClientInit::onClientTick);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        ClientKeyRegistry.registerKeyMappings(event);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ClientKeyRegistry.onClientTick(event);
    }
}

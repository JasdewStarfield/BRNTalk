package yourscraft.jasdewstarfield.brntalk.client;

import yourscraft.jasdewstarfield.brntalk.client.ui.TalkScreen;
import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;


public class TalkClientRegistry {

    private static KeyMapping OPEN_DEMO_KEY;

    public static void initClient(IEventBus modEventBus) {
        modEventBus.addListener(TalkClientRegistry::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(TalkClientRegistry::onClientTick);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        OPEN_DEMO_KEY = new KeyMapping(
                "key.brntalk.open_demo",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                KeyMapping.CATEGORY_MISC
        );
        event.register(OPEN_DEMO_KEY);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (OPEN_DEMO_KEY == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return; // 未进入世界时不处理

        while (OPEN_DEMO_KEY.consumeClick()) {
            mc.setScreen(new TalkScreen());
        }
    }
}

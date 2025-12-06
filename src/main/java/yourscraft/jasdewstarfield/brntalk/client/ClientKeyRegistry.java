package yourscraft.jasdewstarfield.brntalk.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;


public class ClientKeyRegistry {

    static KeyMapping OPEN_SCREEN_KEY;

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        OPEN_SCREEN_KEY = new KeyMapping(
                "key.brntalk.open_screen",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                KeyMapping.CATEGORY_MISC
        );
        event.register(OPEN_SCREEN_KEY);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (OPEN_SCREEN_KEY == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return; // 未进入世界时不处理

        while (OPEN_SCREEN_KEY.consumeClick()) {
            ClientPayloadSender.requestOpenTalk();
        }
    }
}

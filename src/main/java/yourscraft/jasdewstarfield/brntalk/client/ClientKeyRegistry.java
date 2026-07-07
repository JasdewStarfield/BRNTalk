package yourscraft.jasdewstarfield.brntalk.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import yourscraft.jasdewstarfield.brntalk.platform.TalkNetworking;


public class ClientKeyRegistry {

    static KeyMapping OPEN_SCREEN_KEY;

    public static KeyMapping createOpenScreenKeyMapping() {
        // 按键对象只创建一次，事件注册由平台 hook 层负责。
        OPEN_SCREEN_KEY = new KeyMapping(
                "key.brntalk.open_screen",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                KeyMapping.CATEGORY_MISC
        );
        return OPEN_SCREEN_KEY;
    }

    public static void onClientTick() {
        if (OPEN_SCREEN_KEY == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return; // 未进入世界时不处理

        while (OPEN_SCREEN_KEY.consumeClick()) {
            TalkNetworking.requestOpenTalk();
        }
    }
}

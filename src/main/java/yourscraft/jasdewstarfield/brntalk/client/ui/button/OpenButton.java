package yourscraft.jasdewstarfield.brntalk.client.ui.button;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brntalk.Brntalk;

public class OpenButton extends AbstractTalkTextureButton {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Brntalk.MODID, "textures/gui/button/button_open.png");

    private static final int BUTTON_SIZE = 16;

    public OpenButton(int x, int y, OnPress onPress) {
        super(x, y, BUTTON_SIZE, onPress, TEXTURE, 0.8f, 1.0f);
        this.setTooltip(Tooltip.create(Component.translatable("sidebar_button.brntalk.brntalk_open_ui")));
    }
}

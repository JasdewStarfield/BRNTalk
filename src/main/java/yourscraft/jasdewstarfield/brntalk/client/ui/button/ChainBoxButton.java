package yourscraft.jasdewstarfield.brntalk.client.ui.button;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brntalk.Brntalk;

public class ChainBoxButton extends AbstractTalkTextureButton {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "textures/gui/button/button_chain_box.png");

    private static final int BUTTON_SIZE = 19;

    public ChainBoxButton(int x, int y, OnPress onPress) {
        super(x, y, BUTTON_SIZE, onPress, TEXTURE, 1.0f, 1.0f);
    }
}

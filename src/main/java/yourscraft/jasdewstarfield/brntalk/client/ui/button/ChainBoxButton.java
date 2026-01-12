package yourscraft.jasdewstarfield.brntalk.client.ui.button;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.Brntalk;

public class ChainBoxButton extends Button {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "textures/gui/button/button_chain_box.png");

    private static final int BUTTON_SIZE = 19;

    public ChainBoxButton(int x, int y, OnPress onPress) {
        super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.empty(), onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // 占位，仅绘制
        gfx.blit(TEXTURE, this.getX(), this.getY(), 0, 0, this.width, this.height, BUTTON_SIZE, BUTTON_SIZE);
    }
}

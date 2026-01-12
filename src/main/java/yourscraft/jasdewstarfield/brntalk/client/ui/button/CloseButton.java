package yourscraft.jasdewstarfield.brntalk.client.ui.button;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.Brntalk;

public class CloseButton extends Button {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "textures/gui/button/button_close.png");

    private static final int BUTTON_SIZE = 28;

    public CloseButton(int x, int y, OnPress onPress) {
        super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.empty(), onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        if (this.isHoveredOrFocused()) {
            gfx.setColor(1.2f, 1.2f, 1.2f, 1.2f);
        } else {
            gfx.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        // 绘制图片
        gfx.blit(TEXTURE, this.getX(), this.getY(), 0, 0, this.width, this.height, BUTTON_SIZE, BUTTON_SIZE);

        // 重置颜色
        gfx.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}

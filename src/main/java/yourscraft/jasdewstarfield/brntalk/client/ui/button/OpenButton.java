package yourscraft.jasdewstarfield.brntalk.client.ui.button;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.components.Button;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.Brntalk;

public class OpenButton extends Button {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Brntalk.MODID, "textures/gui/button/button_open.png");

    private static final int BUTTON_SIZE = 16;

    public OpenButton(int x, int y, OnPress onPress) {
        super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.empty(), onPress, DEFAULT_NARRATION);
        this.setTooltip(Tooltip.create(Component.translatable("sidebar_button.brntalk.brntalk_open_ui")));
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        if (this.isHoveredOrFocused()) {
            gfx.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            //以此提示可点击，非悬停时：稍微变暗 (0.8)
            gfx.setColor(0.8f, 0.8f, 0.8f, 1.0f);
        }

        // 绘制图片
        gfx.blit(TEXTURE, this.getX(), this.getY(), 0, 0, this.width, this.height, BUTTON_SIZE, BUTTON_SIZE);

        // 重置颜色
        gfx.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}

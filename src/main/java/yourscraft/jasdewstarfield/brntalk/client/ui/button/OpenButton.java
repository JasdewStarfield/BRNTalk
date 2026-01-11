package yourscraft.jasdewstarfield.brntalk.client.ui.button;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.components.Button;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.Brntalk;

public class OpenButton extends Button {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "textures/gui/button/button_open.png");

    public OpenButton(int x, int y, OnPress onPress) {
        super(x, y, 16, 16, Component.empty(), onPress, DEFAULT_NARRATION);
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
        gfx.blit(TEXTURE, this.getX(), this.getY(), 0, 0, this.width, this.height, 16, 16);

        // 重置颜色
        gfx.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}

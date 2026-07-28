package yourscraft.jasdewstarfield.brntalk.client.ui.button;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * BRNTalk 方形纹理按钮的公共模板，统一悬停着色、纹理绘制和颜色恢复。
 */
public abstract class AbstractTalkTextureButton extends Button {
    private final ResourceLocation texture;
    private final float idleTint;
    private final float hoveredTint;

    protected AbstractTalkTextureButton(int x, int y, int size, OnPress onPress,
                                        ResourceLocation texture, float idleTint, float hoveredTint) {
        super(x, y, size, size, Component.empty(), onPress, DEFAULT_NARRATION);
        this.texture = texture;
        this.idleTint = idleTint;
        this.hoveredTint = hoveredTint;
    }

    @Override
    public final void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float tint = this.isHoveredOrFocused() ? hoveredTint : idleTint;
        graphics.setColor(tint, tint, tint, tint);
        graphics.blit(texture, this.getX(), this.getY(), 0, 0,
                this.width, this.height, this.width, this.height);
        // GuiGraphics 的颜色状态是共享的，组件绘制结束后必须恢复。
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}

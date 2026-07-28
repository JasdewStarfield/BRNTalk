package yourscraft.jasdewstarfield.brntalk.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import static yourscraft.jasdewstarfield.brntalk.client.ui.TalkUIStyles.*;

/**
 * BRNTalk 客户端共用的底层绘制原语。
 */
public final class TalkRenderUtils {
    private TalkRenderUtils() {
    }

    public static void drawTiledTexture(GuiGraphics graphics, ResourceLocation texture,
                                        int x, int y, int width, int height,
                                        int u, int v, int tileWidth, int tileHeight,
                                        int uOffset, int vOffset,
                                        int textureWidth, int textureHeight) {
        RenderSystem.setShaderTexture(0, texture);
        graphics.enableScissor(x, y, x + width, y + height);

        int startX = -(uOffset % tileWidth);
        int startY = -(vOffset % tileHeight);
        if (startX > 0) {
            startX -= tileWidth;
        }
        if (startY > 0) {
            startY -= tileHeight;
        }

        for (int dx = startX; dx < width; dx += tileWidth) {
            for (int dy = startY; dy < height; dy += tileHeight) {
                graphics.blit(texture, x + dx, y + dy, u, v,
                        tileWidth, tileHeight, textureWidth, textureHeight);
            }
        }
        graphics.disableScissor();
    }

    public static void drawRepeatedTexture(GuiGraphics graphics, ResourceLocation texture,
                                           int x, int y, int width, int height,
                                           int textureWidth, int textureHeight) {
        drawTiledTexture(graphics, texture, x, y, width, height,
                0, 0, textureWidth, textureHeight, 0, 0, textureWidth, textureHeight);
    }

    public static void drawTextureFrame(GuiGraphics graphics, ResourceLocation texture,
                                        int x, int y, int width, int height,
                                        int borderWidth, int borderHeight,
                                        int textureWidth, int textureHeight) {
        RenderSystem.setShaderTexture(0, texture);

        int innerWidth = width - borderWidth * 2;
        int innerHeight = height - borderHeight * 2;
        int textureInnerWidth = textureWidth - borderWidth * 2;
        int textureInnerHeight = textureHeight - borderHeight * 2;

        graphics.blit(texture, x, y, 0, 0, borderWidth, borderHeight, textureWidth, textureHeight);
        graphics.blit(texture, x + width - borderWidth, y, textureWidth - borderWidth, 0,
                borderWidth, borderHeight, textureWidth, textureHeight);
        graphics.blit(texture, x, y + height - borderHeight, 0, textureHeight - borderHeight,
                borderWidth, borderHeight, textureWidth, textureHeight);
        graphics.blit(texture, x + width - borderWidth, y + height - borderHeight,
                textureWidth - borderWidth, textureHeight - borderHeight,
                borderWidth, borderHeight, textureWidth, textureHeight);

        for (int dx = 0; dx < innerWidth; dx += textureInnerWidth) {
            int partWidth = Math.min(textureInnerWidth, innerWidth - dx);
            graphics.blit(texture, x + borderWidth + dx, y, borderWidth, 0,
                    partWidth, borderHeight, textureWidth, textureHeight);
            graphics.blit(texture, x + borderWidth + dx, y + height - borderHeight,
                    borderWidth, textureHeight - borderHeight,
                    partWidth, borderHeight, textureWidth, textureHeight);
        }

        for (int dy = 0; dy < innerHeight; dy += textureInnerHeight) {
            int partHeight = Math.min(textureInnerHeight, innerHeight - dy);
            graphics.blit(texture, x, y + borderHeight + dy, 0, borderHeight,
                    borderWidth, partHeight, textureWidth, textureHeight);
            graphics.blit(texture, x + width - borderWidth, y + borderHeight + dy,
                    textureWidth - borderWidth, borderHeight,
                    borderWidth, partHeight, textureWidth, textureHeight);
        }
    }

    public static void drawCustomScrollbar(GuiGraphics graphics, int x, int y, int viewHeight,
                                           int totalHeight, double scrollAmount, int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }

        int barHeight = (int) ((float) (viewHeight * viewHeight) / (float) totalHeight);
        barHeight = Mth.clamp(barHeight, DECO_SCROLL_BAR_H, viewHeight);
        int barY = y + (int) ((scrollAmount / (float) maxScroll) * (viewHeight - barHeight));

        ResourceLocation texture = TEX_PARTS;
        int width = DECO_SCROLL_BAR_W;
        graphics.blit(texture, x, barY, DECO_SCROLL_BAR_U, DECO_SCROLL_BAR_V,
                width, 5, DECO_W, DECO_H);
        graphics.blit(texture, x, barY + barHeight - 5, DECO_SCROLL_BAR_U, DECO_SCROLL_BAR_V + 9,
                width, 5, DECO_W, DECO_H);

        int remaining = barHeight - 10;
        int currentY = barY + 5;
        while (remaining > 0) {
            int partHeight = Math.min(remaining, 4);
            graphics.blit(texture, x, currentY, DECO_SCROLL_BAR_U, DECO_SCROLL_BAR_V + 5,
                    width, partHeight, DECO_W, DECO_H);
            currentY += partHeight;
            remaining -= partHeight;
        }
    }
}

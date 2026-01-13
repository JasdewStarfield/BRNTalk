package yourscraft.jasdewstarfield.brntalk.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.client.ClientTalkUtils;

import java.util.List;



public class TalkToast implements Toast{
    private static final ResourceLocation BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("toast/advancement");

    private final Component title;
    private final Component subtitle;

    public TalkToast(TalkMessage message) {
        this.title = Component.translatable("gui.brntalk.toast_new_message");
        String preview = ClientTalkUtils.getSingleLinePreview(message, 120);
        this.subtitle = Component.literal(preview);
    }

    @Override
    public @NotNull Visibility render(GuiGraphics gfx, ToastComponent toastComponent, long timeSinceLastVisible) {
        // 1. 绘制背景
        gfx.blitSprite(BACKGROUND_SPRITE, 0, 0, 160, 32);

        // 2. 绘制文字
        Font font = toastComponent.getMinecraft().font;
        // 标题 (黄色 0xFF500050 是阴影色, 0xFFFFFF00 是字色)
        gfx.drawString(font, this.title, 30, 7, 0xFFFFFF00, false);
        // 副标题 (截断过长的文本)
        List<FormattedCharSequence> lines = font.split(this.subtitle, 125); // 125 是文字最大宽度
        if (!lines.isEmpty()) {
            gfx.drawString(font, lines.getFirst(), 30, 18, 0xFFFFFFFF, false);
        }

        gfx.renderFakeItem(new ItemStack(Items.PAPER), 8, 8);

        // 5000ms (5秒) 后消失
        return timeSinceLastVisible >= 5000L ? Visibility.HIDE : Visibility.SHOW;
    }
}

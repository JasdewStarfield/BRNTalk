package yourscraft.jasdewstarfield.brntalk;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.config.BrntalkConfig;

import java.util.Collections;
import java.util.List;

@mezz.jei.api.JeiPlugin
public class BrntalkJeiPlugin implements IModPlugin {

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // 注册一个通用的处理器给所有容器界面
        registration.addGenericGuiContainerHandler(AbstractContainerScreen.class, new IGuiContainerHandler<>() {
            @Override
            public @NotNull List<Rect2i> getGuiExtraAreas(@NotNull AbstractContainerScreen<?> containerScreen) {
                // 1. 检查是否安装了 FTB Library
                // 如果安装了 FTB，我们的按钮会被隐藏，所以不需要通知 JEI 避让
                if (ModList.get().isLoaded("ftblibrary")) {
                    return Collections.emptyList();
                }

                int buttonX = BrntalkConfig.CLIENT.openButtonX.get();
                int buttonY = BrntalkConfig.CLIENT.openButtonY.get();

                // 2. 返回按钮的坐标区域
                return List.of(new Rect2i(buttonX, 0,  16, buttonY+ 16));
            }
        });
    }
}

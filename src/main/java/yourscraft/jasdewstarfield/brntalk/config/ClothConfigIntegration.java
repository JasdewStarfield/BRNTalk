package yourscraft.jasdewstarfield.brntalk.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClothConfigIntegration {

    public static Screen createScreen(Screen parent) {
        // 1. 创建构建器
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("title.brntalk.config"));

        // 2. 获取条目构建器
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // 3. 创建分类
        ConfigCategory visualCategory = builder.getOrCreateCategory(Component.translatable("config.category.brntalk.visual"));
        ConfigCategory scrollingCategory = builder.getOrCreateCategory(Component.translatable("config.category.brntalk.scrolling"));
        ConfigCategory openButtonCategory = builder.getOrCreateCategory(Component.translatable("config.category.brntalk.open_button"));

        // 4. 添加配置项
        visualCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.option.brntalk.use_vanilla_style_ui"),
                        BrntalkConfig.CLIENT.useVanillaStyleUI.get()
                )
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.option.brntalk.use_vanilla_style_ui.tooltip"))
                .setSaveConsumer(BrntalkConfig.CLIENT.useVanillaStyleUI::set)
                .build());

        visualCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.option.brntalk.char_delay"),
                        BrntalkConfig.CLIENT.charDelay.get()
                )
                .setDefaultValue(20)
                .setMin(0)
                .setMax(200)
                .setTooltip(Component.translatable("config.option.brntalk.char_delay.tooltip"))
                .setSaveConsumer(BrntalkConfig.CLIENT.charDelay::set)
                .build());

        visualCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.option.brntalk.msg_pause"),
                        BrntalkConfig.CLIENT.msgPause.get()
                )
                .setDefaultValue(1000)
                .setMin(0)
                .setMax(10000)
                .setTooltip(Component.translatable("config.option.brntalk.msg_pause.tooltip"))
                .setSaveConsumer(BrntalkConfig.CLIENT.msgPause::set)
                .build());

        scrollingCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.option.brntalk.scroll_rate"),
                        BrntalkConfig.CLIENT.scrollRate.get()
                )
                .setDefaultValue(25)
                .setMin(1)
                .setMax(10000)
                .setTooltip(Component.translatable("config.option.brntalk.scroll_rate.tooltip"))
                .setSaveConsumer(BrntalkConfig.CLIENT.scrollRate::set)
                .build());

        scrollingCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.option.brntalk.smooth_factor"),
                        BrntalkConfig.CLIENT.smoothFactor.get()
                )
                .setDefaultValue(0.3f)
                .setMin(0f)
                .setMax(1f)
                .setTooltip(Component.translatable("config.option.brntalk.smooth_factor.tooltip"))
                .setSaveConsumer(BrntalkConfig.CLIENT.smoothFactor::set)
                .build());

        openButtonCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.option.brntalk.openButtonX"),
                        BrntalkConfig.CLIENT.openButtonX.get()
                )
                .setDefaultValue(5)
                .setMin(0)
                .setMax(1000)
                .setTooltip(Component.translatable("config.option.brntalk.openButtonX.tooltip"))
                .setSaveConsumer(BrntalkConfig.CLIENT.openButtonX::set)
                .build());

        openButtonCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.option.brntalk.openButtonY"),
                        BrntalkConfig.CLIENT.openButtonY.get()
                )
                .setDefaultValue(5)
                .setMin(0)
                .setMax(1000)
                .setTooltip(Component.translatable("config.option.brntalk.openButtonY.tooltip"))
                .setSaveConsumer(BrntalkConfig.CLIENT.openButtonY::set)
                .build());

        // 5. 保存逻辑
        builder.setSavingRunnable(BrntalkConfig.CLIENT_SPEC::save);

        return builder.build();
    }
}

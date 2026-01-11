package yourscraft.jasdewstarfield.brntalk.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class BrntalkConfig {
    public static final ClientConfig CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        // 构建配置规格
        final Pair<ClientConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT_SPEC = specPair.getRight();
        CLIENT = specPair.getLeft();
    }

    public enum NotificationMode {
        HUD,    // 新版沉浸式 HUD
        TOAST,  // 旧版 Toast 弹窗
        NONE    // 不提醒
    }

    public static class ClientConfig {
        public final ModConfigSpec.BooleanValue useVanillaStyleUI;
        public final ModConfigSpec.IntValue charDelay;
        public final ModConfigSpec.IntValue msgPause;
        public final ModConfigSpec.DoubleValue scrollRate;
        public final ModConfigSpec.DoubleValue smoothFactor;
        public final ModConfigSpec.BooleanValue displayOpenButton;
        public final ModConfigSpec.IntValue openButtonX;
        public final ModConfigSpec.IntValue openButtonY;
        public final ModConfigSpec.EnumValue<NotificationMode> notificationMode;
        public final ModConfigSpec.DoubleValue hudScale;
        public final ModConfigSpec.IntValue hudOffsetY;
        public final ModConfigSpec.IntValue hudTopMargin;

        public ClientConfig(ModConfigSpec.Builder builder) {
            builder.comment("Visual settings").push("visual");

            useVanillaStyleUI = builder
                    .comment("Use Vanilla Style GUI and Background")
                    .comment("If set to true, the custom GUI and background will be replaced by the standard vanilla dark background.")
                    .define("useVanillaStyleUI", false);

            charDelay = builder
                    .comment("Typing Speed (ms/char)")
                    .comment("Lower value means faster typing speed. Set to 0 to display immediately.")
                    .defineInRange("charDelay", 20, 0, 200);

            msgPause = builder
                    .comment("Message Pause Interval (ms)")
                    .comment("Pause Time between continuous messages. Set to 0 to disable pausing.")
                    .defineInRange("msgPause", 1000, 0, 10000);

            builder.pop();

            builder.comment("Scrolling settings").push("scrolling");

            scrollRate = builder
                    .comment("Rate of Scrolling")
                    .comment("Higher value means faster scrolling.")
                    .defineInRange("scrollRate", 25.0, 1, 100);

            smoothFactor = builder
                    .comment("Scrolling Drag")
                    .comment("Smooth factor of scrolling. Lower value means smoother scrolling.")
                    .defineInRange("smoothFactor", 0.3f, 0f, 1f);

            builder.pop();

            builder.comment("Open button settings").push("button");

            displayOpenButton = builder
                    .comment("Whether to display the 'Open Screen' button or not")
                    .comment("If true, the button will be displayed on every container screens.")
                    .comment("This won't work if you have installed FTB Library.")
                    .define("displayOpenButton", true);

            openButtonX = builder
                    .comment("X position of the 'Open Screen' button (pixel)")
                    .comment("The distance between button and the left edge of screen.")
                    .comment("This won't work if you have installed FTB Library.")
                    .defineInRange("openButtonX", 5, 0, 1000);

            openButtonY = builder
                    .comment("Y position of the 'Open Screen' button")
                    .comment("The distance between button and the top edge of screen.")
                    .comment("This won't work if you have installed FTB Library.")
                    .defineInRange("openButtonY", 5, 0, 1000);

            builder.pop();

            builder.comment("HUD settings").push("hud");

            notificationMode = builder
                    .comment("Notification Mode")
                    .comment("How new messages are displayed.")
                    .comment("Other HUD settings are only available when this is set to 'HUD'.")
                    .defineEnum("notificationMode", NotificationMode.HUD);

            hudScale = builder
                    .comment("Scale of the HUD")
                    .comment("The total size multiplier of HUD. 1.0 means original size.")
                    .defineInRange("hudScale", 0.75, 0.1, 1.0);

            hudOffsetY = builder
                    .comment("Vertical Offset from screen center (pixel)")
                    .comment("Relative to the center. Positive value moves HUD down, negative moves up.")
                    .defineInRange("hudOffsetY", 60, -1000, 1000);

            hudTopMargin = builder
                    .comment("Top Margin (Safety Line) (pixel)")
                    .comment("Messages won't be rendered if they go above this Y position to prevent overlapping.")
                    .defineInRange("hudTopMargin", 20, 0, 1000);

            builder.pop();
        }
    }
}

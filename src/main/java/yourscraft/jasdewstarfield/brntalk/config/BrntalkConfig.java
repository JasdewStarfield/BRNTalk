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

    public static class ClientConfig {
        public final ModConfigSpec.BooleanValue useVanillaStyleUI;
        public final ModConfigSpec.IntValue charDelay;
        public final ModConfigSpec.IntValue msgPause;
        public final ModConfigSpec.DoubleValue scrollRate;
        public final ModConfigSpec.DoubleValue smoothFactor;
        public final ModConfigSpec.IntValue openButtonX;
        public final ModConfigSpec.IntValue openButtonY;

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

            openButtonX = builder
                    .comment("X position of the 'Open Screen' button (pixel)")
                    .comment("The distance between button and the left edge of screen.")
                    .defineInRange("openButtonX", 5, 0, 1000);

            openButtonY = builder
                    .comment("Y position of the 'Open Screen' button")
                    .comment("The distance between button and the top edge of screen.")
                    .defineInRange("openButtonY", 5, 0, 1000);

            builder.pop();
        }
    }
}

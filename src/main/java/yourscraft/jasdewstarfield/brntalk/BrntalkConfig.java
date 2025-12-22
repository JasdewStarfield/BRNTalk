package yourscraft.jasdewstarfield.brntalk;

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
        public final ModConfigSpec.IntValue charDelay;
        public final ModConfigSpec.IntValue msgPause;

        public ClientConfig(ModConfigSpec.Builder builder) {
            builder.comment("Visual settings for BRNTalk").push("visual");

            charDelay = builder
                    .comment("Typing Speed (ms per char). Lower value means faster typing speed")
                    .defineInRange("charDelay", 20, 0, 200);

            msgPause = builder
                    .comment("Pause Time between continuous messages (ms)")
                    .defineInRange("msgPause", 1000, 0, 10000);

            builder.pop();
        }
    }
}

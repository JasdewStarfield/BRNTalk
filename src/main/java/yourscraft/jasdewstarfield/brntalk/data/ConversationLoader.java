package yourscraft.jasdewstarfield.brntalk.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.*;

public class ConversationLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public ConversationLoader() {
        super(GSON, "brntalk/dialogues");
    }

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager resources, ProfilerFiller profiler) {
        // The vanilla JSON listener skips malformed files. Here a missing entry could look
        // like an intentional deletion, so abort preparation and retain the active scripts.
        Map<ResourceLocation, JsonElement> prepared = new LinkedHashMap<>();
        String prefix = "brntalk/dialogues/";
        resources.listResources("brntalk/dialogues", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (var reader = resource.openAsReader()) {
                String path = id.getPath();
                var key = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path.substring(prefix.length(), path.length() - 5));
                prepared.put(key, com.google.gson.JsonParser.parseReader(reader));
            } catch (Exception error) {
                repository().preparationFailed(id, error);
                throw new IllegalStateException("BRNTalk reload rejected; retaining active scripts. Invalid resource: " + id, error);
            }
        });
        return prepared;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, @NotNull ResourceManager resources,
                         @NotNull ProfilerFiller profiler) {
        repository().reload(jsons);
    }

    private static ConversationRepository repository() { return TalkManager.getInstance().scripts(); }
    public static ConversationLoadReport getLastLoadReport() { return repository().lastReport(); }
    public static boolean wasLastReloadApplied() { return repository().wasLastReloadApplied(); }
}

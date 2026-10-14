package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Existing fragments/ingots retain exact source item material susceptibility. @author NingMeng123 */
public final class MaterialContentRules {
    public record Profile(int legacyId, int maximumStack, float componentDifficulty, boolean fire, boolean lava,
            boolean acid, boolean pepsin) {}
    private static final Map<ResourceLocation, Profile> PROFILES = load();
    private MaterialContentRules() {}
    public static Map<ResourceLocation, Profile> profiles() { return PROFILES; }
    public static Profile get(ResourceLocation id) { return PROFILES.get(id); }
    private static Map<ResourceLocation, Profile> load() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(MaterialContentRules.class.getResourceAsStream(
                "/mite/materials-028.json")), StandardCharsets.UTF_8)) {
            Map<ResourceLocation, Profile> result = new LinkedHashMap<>();
            JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("items").forEach(element -> {
                var data = element.getAsJsonObject();
                result.put(ResourceLocation.parse(data.get("id").getAsString()), new Profile(data.get("legacy_id").getAsInt(),
                        data.get("maximum_stack").getAsInt(), data.get("component_difficulty").getAsFloat(),
                        data.get("harmed_by_fire").getAsBoolean(), data.get("harmed_by_lava").getAsBoolean(),
                        data.get("harmed_by_acid").getAsBoolean(), data.get("harmed_by_pepsin").getAsBoolean()));
            });
            return Map.copyOf(result);
        } catch (Exception error) { throw new IllegalStateException("Cannot load verified material profiles", error); }
    }
}

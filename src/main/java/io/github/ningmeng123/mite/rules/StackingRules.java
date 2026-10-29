package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Confirmed carried identities and explicit modern family adaptations. @author NingMeng123 */
public final class StackingRules {
    // User balance adjustment on top of the frozen R196 evidence table. Nuggets keep their existing limits.
    private static final Set<String> FOUR_PER_STACK_MINERALS = Set.of(
            "minecraft:copper_ingot", "minecraft:gold_ingot", "minecraft:iron_ingot", "minecraft:netherite_ingot",
            "minecraft:raw_copper", "minecraft:raw_gold", "minecraft:raw_iron",
            "mite:silver_ingot", "mite:ancient_metal_ingot", "mite:mithril_ingot", "mite:adamantium_ingot");
    private static final Map<String, Integer> LIMITS = load();
    private StackingRules() {}

    public static Map<String, Integer> limits() { return LIMITS; }
    public static int limit(String id) { return LIMITS.getOrDefault(id, 0); }

    /** zc#getItemStackLimit: subtype zero is one layer; all other valid layers have limit eight. */
    public static int snowLimit(int layers) { return layers >= 2 && layers <= 8 ? 8 : 32; }

    private static Map<String, Integer> load() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(StackingRules.class.getResourceAsStream(
                "/mite/stacking-030.json")), StandardCharsets.UTF_8)) {
            var data = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, Integer> limits = new LinkedHashMap<>();
            for (var value : data.getAsJsonArray("mappedItems")) {
                var row = value.getAsJsonObject();
                String id = row.get("modern_id").getAsString();
                int limit = FOUR_PER_STACK_MINERALS.contains(id) ? 4 : row.get("max_stack_size").getAsInt();
                if (!id.matches("(minecraft|mite):[a-z0-9_/.-]+") || limit < 1 || limit > 64
                        || limits.putIfAbsent(id, limit) != null) throw new IllegalArgumentException("Invalid stacking identity: " + id);
            }
            if (limits.isEmpty()) throw new IllegalArgumentException("Empty source stacking table");
            if (limits.getOrDefault("minecraft:snow", 0) != 32) throw new IllegalArgumentException("Invalid source single-layer snow limit");
            return Collections.unmodifiableMap(limits);
        } catch (Exception failure) { throw new IllegalStateException("Cannot load R196 stacking identities", failure); }
    }
}

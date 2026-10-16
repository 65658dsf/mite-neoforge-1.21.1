package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** The four MITE slimes join ordinary overworld hostiles independently of invasion progress. @author NingMeng123 */
public final class OverworldSlimeSpawns {
    public static final int WEIGHT = 100;
    private static final List<String> SPECIES = List.of("EntityJelly", "EntityBlob", "EntityOoze", "EntityPudding");

    private OverworldSlimeSpawns() {}

    public static boolean species(String sourceClass) { return sourceClass != null && SPECIES.contains(sourceClass); }

    public static boolean applies(ServerLevel level, String sourceClass) {
        return level.dimension().equals(Level.OVERWORLD) && sourceClass != null && species(sourceClass);
    }

    /** Adapt the runtime pool only, retaining archival tables and every other dimension verbatim. */
    public static List<R196MobSpawning.Entry> pool(ServerLevel level, Category category, List<R196MobSpawning.Entry> original) {
        if (!level.dimension().equals(Level.OVERWORLD) || category != Category.MONSTER || original.isEmpty()) return original;
        List<R196MobSpawning.Entry> result = new ArrayList<>(original.size() + SPECIES.size());
        Set<String> present = new HashSet<>();
        for (var entry : original) {
            if (entry.sourceClass() != null && species(entry.sourceClass())) {
                result.add(weighted(entry));
                present.add(entry.sourceClass());
            } else result.add(entry);
        }
        for (String species : SPECIES) if (!present.contains(species)) {
            var template = R196MobSpawning.sourceProfile(26, Category.MONSTER).stream()
                    .filter(entry -> species.equals(entry.sourceClass())).findFirst().orElseThrow();
            result.add(weighted(template));
        }
        return List.copyOf(result);
    }

    private static R196MobSpawning.Entry weighted(R196MobSpawning.Entry entry) {
        return new R196MobSpawning.Entry(entry.type(), entry.sourceClass(), entry.category(), WEIGHT,
                entry.minimum(), entry.maximum(), entry.pack(), entry.modern());
    }
}

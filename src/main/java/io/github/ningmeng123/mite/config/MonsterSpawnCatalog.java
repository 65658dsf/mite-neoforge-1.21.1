package io.github.ningmeng123.mite.config;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entity identities and baseline pool metadata come from the current registries and generator. @author NingMeng123 */
public final class MonsterSpawnCatalog {
    public record Entry(String id, Component name, EntityType<?> type, List<Integer> baseWeights) {
        public Entry { baseWeights = List.copyOf(baseWeights); }
    }

    private MonsterSpawnCatalog() {}

    /** Addons own their registered identities; MITE also edits other, unclaimed monster namespaces. */
    public static boolean ownedBy(String ownerModId, String entityId) {
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        if (id == null) return false;
        return ownerModId.equals(Mite.MOD_ID) ? !MiteExtensions.policy().ownsMonsterSpawnSettings(entityId)
                : id.getNamespace().equals(ownerModId);
    }

    public static List<Entry> entries(String ownerModId, RegistryAccess access) {
        Set<EntityType<?>> sourceMonsters = new HashSet<>();
        // Source bats and dire wolves have ambient/creature native categories, but are still configurable enemies.
        for (var kind : MobKind.values()) sourceMonsters.add(MiteMobs.type(kind));
        Map<EntityType<?>, Set<Integer>> weights = new HashMap<>();
        for (var entry : R196MobSpawning.sourceEntries())
            weights.computeIfAbsent(entry.type(), ignored -> new TreeSet<>()).add(entry.weight());
        access.registry(Registries.BIOME).ifPresent(biomes -> biomes.forEach(biome -> {
            for (var data : biome.getMobSettings().getMobs(MobCategory.MONSTER).unwrap()) {
                // The source scheduler replaces the native pool for source species.
                if (!R196MobSpawning.sourceType(data.type))
                    weights.computeIfAbsent(data.type, ignored -> new TreeSet<>()).add(data.getWeight().asInt());
            }
        }));
        var result = new ArrayList<Entry>();
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            if ((type.getCategory() == MobCategory.MONSTER || sourceMonsters.contains(type)) && ownedBy(ownerModId, id))
                result.add(new Entry(id, type.getDescription(), type,
                        new ArrayList<>(weights.getOrDefault(type, Set.of()))));
        }
        result.sort(Comparator.comparing(Entry::id));
        return List.copyOf(result);
    }
}

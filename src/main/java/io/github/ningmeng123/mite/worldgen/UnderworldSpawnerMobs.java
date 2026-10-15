package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.rules.UnderworldSpawnerRules;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;

/** Copies only changed spawn identities; all authored NBT, weights, equipment and trial rewards survive. @author NingMeng123 */
public final class UnderworldSpawnerMobs {
    private UnderworldSpawnerMobs() {}

    public static boolean applies(@Nullable Level level) {
        return level != null && level.dimension().equals(MiteWorldDimensions.UNDERWORLD);
    }

    public static SpawnData remap(SpawnData source) {
        CompoundTag entity = remapEntity(source.getEntityToSpawn());
        return entity == source.getEntityToSpawn() ? source
                : new SpawnData(entity, source.getCustomSpawnRules(), source.getEquipment());
    }

    /** Includes passengers so a data pack cannot cage a lord as another creature's rider. */
    private static CompoundTag remapEntity(CompoundTag source) {
        String original = source.getString("id");
        String replacement = UnderworldSpawnerRules.replacement(original);
        CompoundTag result = source;
        if (!original.equals(replacement)) {
            result = source.copy();
            result.putString("id", replacement);
        }
        ListTag passengers = source.getList("Passengers", Tag.TAG_COMPOUND);
        ListTag remappedPassengers = null;
        for (int index = 0; index < passengers.size(); index++) {
            CompoundTag originalPassenger = passengers.getCompound(index);
            CompoundTag passenger = remapEntity(originalPassenger);
            if (passenger != originalPassenger) {
                if (remappedPassengers == null) remappedPassengers = passengers.copy();
                remappedPassengers.set(index, passenger);
            }
        }
        if (remappedPassengers != null) {
            if (result == source) result = source.copy();
            result.put("Passengers", remappedPassengers);
        }
        return result;
    }

    public static SimpleWeightedRandomList<SpawnData> remap(SimpleWeightedRandomList<SpawnData> source) {
        var builder = SimpleWeightedRandomList.<SpawnData>builder();
        boolean changed = false;
        for (var entry : source.unwrap()) {
            SpawnData replacement = remap(entry.data());
            changed |= replacement != entry.data();
            builder.add(replacement, entry.getWeight().asInt());
        }
        return changed ? builder.build() : source;
    }

    public static TrialSpawnerConfig remap(TrialSpawnerConfig source) {
        var potentials = remap(source.spawnPotentialsDefinition());
        return potentials == source.spawnPotentialsDefinition() ? source : new TrialSpawnerConfig(
                source.spawnRange(), source.totalMobs(), source.simultaneousMobs(), source.totalMobsAddedPerPlayer(),
                source.simultaneousMobsAddedPerPlayer(), source.ticksBetweenSpawn(), potentials,
                source.lootTablesToEject(), source.itemsToDropWhenOminous());
    }
}

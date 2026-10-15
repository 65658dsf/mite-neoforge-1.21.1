package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Append-only source-pool invasion, using the existing natural-spawn scheduler and its qualifications. @author NingMeng123 */
public final class BloodMoonInvasion {
    private record Snapshot(boolean unlocked, long highestDay) {}
    private static final Map<MinecraftServer, Snapshot> SNAPSHOTS = Collections.synchronizedMap(new WeakHashMap<>());

    private BloodMoonInvasion() {}

    static void publish(ServerLevel level, boolean unlocked, long highestDay) {
        SNAPSHOTS.put(level.getServer(), new Snapshot(unlocked, highestDay));
    }

    private static BloodMoonInvasionData observe(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        BloodMoonInvasionData data = BloodMoonInvasionData.get(overworld);
        data.observeDay(Math.max(0L, overworld.getDayTime()) / 24_000L + 1L);
        return data;
    }

    /** Other dimensions and equipment read the same maximum calendar day reached by the primary world. */
    public static long highestDay(ServerLevel level) {
        if (!level.getServer().isSameThread()) return SNAPSHOTS.getOrDefault(level.getServer(), new Snapshot(false, 0L)).highestDay();
        return observe(level).highestDay();
    }

    /** Tick eagerly, and also migrate an already-late save when its first natural-spawn query arrives. */
    public static boolean unlocked(ServerLevel level) {
        if (!level.dimension().equals(Level.OVERWORLD)) return false;
        if (!level.getServer().isSameThread()) return SNAPSHOTS.getOrDefault(level.getServer(), new Snapshot(false, 0L)).unlocked();
        return observe(level).unlocked();
    }

    /** Keep all original entries and their weights; append only types not already present. */
    public static List<R196MobSpawning.Entry> merge(List<R196MobSpawning.Entry> original,
            List<R196MobSpawning.Entry> underworld) {
        List<R196MobSpawning.Entry> result = new ArrayList<>(original);
        Set<EntityType<?>> present = new HashSet<>();
        for (var entry : original) present.add(entry.type());
        for (var entry : underworld) if (present.add(entry.type())) result.add(entry);
        return List.copyOf(result);
    }

    public static List<R196MobSpawning.Entry> pool(ServerLevel level, Category category, List<R196MobSpawning.Entry> original) {
        if ((category != Category.MONSTER && category != Category.AMBIENT) || !unlocked(level)) return original;
        return merge(original, R196MobSpawning.sourceProfile(26, category));
    }
}

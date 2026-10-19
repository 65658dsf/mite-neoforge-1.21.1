package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Spider;
import io.github.ningmeng123.mite.entity.SourceSpiderFinalization;
import io.github.ningmeng123.mite.rules.UnderworldDungeonMobRules;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.SpawnData;

/** Stable per-cage dungeon selection and fully initialized underground spider riders. @author NingMeng123 */
public final class UnderworldDungeonSpawns {
    public static final String CONFIGURED_TAG = "mite:underworld_dungeon_configured";
    public static final String JOCKEY_TAG = "mite:underworld_spider_jockey";
    private static final long ROSTER_SALT = 0x554E44455244554EL;
    private static final Set<String> LEGACY_IDS = Set.of(
            "minecraft:zombie", "minecraft:skeleton", "minecraft:spider",
            "mite:revenant", "mite:longdead", "mite:demon_spider");

    private UnderworldDungeonSpawns() {}

    /** Authored equipment, rules, weights, NBT and multiple candidates are never treated as legacy defaults. */
    public static boolean canUpgrade(@Nullable SpawnData current, SimpleWeightedRandomList<SpawnData> potentials) {
        if (!legacyDefault(current)) return false;
        var entries = potentials.unwrap();
        if (entries.isEmpty()) return true;
        if (entries.size() != 1) return false;
        var entry = entries.getFirst();
        return entry.getWeight().asInt() == 1 && legacyDefault(entry.data())
                && entry.data().getEntityToSpawn().equals(current.getEntityToSpawn());
    }

    private static boolean legacyDefault(@Nullable SpawnData data) {
        if (data == null || data.getCustomSpawnRules().isPresent() || data.getEquipment().isPresent()) return false;
        CompoundTag entity = data.getEntityToSpawn();
        return entity.size() == 1 && entity.contains("id", Tag.TAG_STRING) && LEGACY_IDS.contains(entity.getString("id"));
    }

    /** Uses a local random source so loading a cage cannot consume world random state or reroll its identity. */
    public static SpawnData select(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) throw new IllegalArgumentException("Dungeon mob selection requires a server level");
        RandomSource random = RandomSource.create(server.getSeed() ^ Mth.getSeed(pos) ^ ROSTER_SALT);
        var selected = UnderworldDungeonMobRules.select(random.nextInt(UnderworldDungeonMobRules.totalWeight()));
        CompoundTag entity = new CompoundTag();
        entity.putString("id", selected.entityId());
        if (selected.spiderJockey()) {
            entity.putBoolean(JOCKEY_TAG, true);
            CompoundTag rider = new CompoundTag();
            rider.putString("id", "mite:longdead");
            ListTag passengers = new ListTag();
            passengers.add(rider);
            entity.put("Passengers", passengers);
        }
        return new SpawnData(entity, Optional.empty(), Optional.empty());
    }

    /** Only the exact project-owned preview template can request special passenger initialization. */
    public static boolean isJockey(SpawnData data) {
        if (data.getCustomSpawnRules().isPresent() || data.getEquipment().isPresent()) return false;
        CompoundTag entity = data.getEntityToSpawn();
        if (entity.size() != 3 || !entity.getString("id").equals("mite:demon_spider")
                || !entity.contains(JOCKEY_TAG, Tag.TAG_BYTE) || !entity.getBoolean(JOCKEY_TAG)
                || !entity.contains("Passengers", Tag.TAG_LIST)) return false;
        ListTag passengers = entity.getList("Passengers", Tag.TAG_COMPOUND);
        if (passengers.size() != 1) return false;
        CompoundTag rider = passengers.getCompound(0);
        return rider.size() == 1 && rider.getString("id").equals("mite:longdead");
    }

    /** Called only for {@link #isJockey} templates, after the vehicle's formal spawn callback. */
    public static void finalizeJockey(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason) {
        if (!(mob instanceof R196Spider spider) || spider.miteKind() != MobKind.DEMON_SPIDER) return;
        // Loaded preview riders have no equipment or finalized spawn state. They must never reach the world.
        for (var preview : spider.getPassengers()) {
            preview.stopRiding();
            preview.discard();
        }
        if (mob.isSpawnCancelled()) return;
        if (mob.getSpawnType() == null) {
            mob.setSpawnCancelled(true);
            return;
        }
        var rider = SourceSpiderFinalization.createRider(spider, level, difficulty, reason);
        if (rider == null) {
            mob.setSpawnCancelled(true);
            return;
        }
        if (mob.isSpawnCancelled()) {
            rider.discard();
            return;
        }
        boolean mounted = rider.startRiding(spider);
        if (!mounted || mob.isSpawnCancelled()) {
            rider.stopRiding();
            rider.discard();
            mob.setSpawnCancelled(true);
        }
    }
}

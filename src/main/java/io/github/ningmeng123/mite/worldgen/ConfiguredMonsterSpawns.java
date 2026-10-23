package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.MonsterSpawnRules;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Applies saved rates to the actual natural-spawn pool, including source replacement families. @author NingMeng123 */
public final class ConfiguredMonsterSpawns {
    public static final String ADMITTED = "mite:natural_spawn_admitted";
    private static final Set<EntityJoinLevelEvent> PROVISIONAL_ADMISSIONS = ConcurrentHashMap.newKeySet();
    private ConfiguredMonsterSpawns() {}

    public static void register() {
        // Run before addons' LOWEST cancellation cleanup so their population reservations are released.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ConfiguredMonsterSpawns::naturalJoin);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ConfiguredMonsterSpawns::finishNaturalJoin);
    }

    public static double multiplier(EntityType<?> type) {
        double base = MiteConfig.monsterSpawnMultiplier(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        double result = MiteExtensions.policy().monsterSpawnMultiplier(type, base);
        return Double.isFinite(result) && result >= 0D && result <= 100D ? result : base;
    }

    public static double weight(EntityType<?> type, double fallback) {
        var policy = MiteExtensions.policy();
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        double base = policy.ownsMonsterSpawnSettings(id) ? fallback : MiteConfig.monsterSpawnWeight(id, fallback);
        double result = policy.monsterSpawnWeight(type, base);
        return Double.isFinite(result) && result >= 0D && result <= 1000000D && result == Math.rint(result) ? result : base;
    }

    public static double replacementProbability(double probability, double parentRate, double replacementRate) {
        return MonsterSpawnRules.replacementProbability(probability, parentRate, replacementRate);
    }

    /** Retains the original lottery and its random stream whenever the two rates are equal. */
    public static boolean replacement(RandomSource random, double probability, EntityType<?> parent,
            EntityType<?> replacement, BooleanSupplier originalLottery) {
        double parentRate = multiplier(parent), replacementRate = multiplier(replacement);
        if (parentRate == replacementRate) return originalLottery.getAsBoolean();
        double adjusted = replacementProbability(probability, parentRate, replacementRate);
        return adjusted > 0D && (adjusted >= 1D || random.nextDouble() < adjusted);
    }

    public static double familyMultiplier(ServerLevel level, BlockPos pos, R196MobSpawning.Entry entry,
            boolean finalizationTransforms) {
        double rate = multiplier(entry.type());
        if (!entry.modern()) {
            if ("tf".equals(entry.sourceClass())) {
                int sourceY = R196SpawnRules.sourceY(pos.getY(), R196MobSpawning.underworld(level));
                rate = mixture(entry.type(), MiteMobs.type(MobKind.INFERNAL_CREEPER), MonsterSpawnRules.infernalCreeperProbability(sourceY));
            } else if (finalizationTransforms && "EntityVampireBat".equals(entry.sourceClass())
                    && (R196MobSpawning.underworld(level) || !R196MobSpawning.referenceSpawning(level) && BloodMoonInvasion.unlocked(level))) {
                rate = mixture(entry.type(), MiteMobs.type(MobKind.GIANT_VAMPIRE_BAT), 1D / 6D);
            } else if (finalizationTransforms && "EntityLongdead".equals(entry.sourceClass())) {
                rate = mixture(entry.type(), MiteMobs.type(MobKind.LONGDEAD_GUARDIAN), 1D / 6D);
            }
            if (finalizationTransforms && entry.type() == EntityType.SKELETON && level.dimension().equals(Level.NETHER))
                rate = multiplier(EntityType.WITHER_SKELETON);
            else if (finalizationTransforms && entry.type() == EntityType.ZOMBIE)
                rate = mixture(entry.type(), EntityType.ZOMBIE_VILLAGER, .05D);
        }
        return rate;
    }

    private static double mixture(EntityType<?> parent, EntityType<?> replacement, double probability) {
        return MonsterSpawnRules.familyMultiplier(probability, multiplier(parent), multiplier(replacement));
    }

    /** Called only after all native and addon pool additions and unlock checks. */
    public static R196MobSpawning.Entry choose(ServerLevel level, BlockPos pos, R196SpawnRules.Category category,
            List<R196MobSpawning.Entry> entries, RandomSource random, boolean finalizationTransforms) {
        if (entries.isEmpty()) return null;
        double[] weights = new double[entries.size()];
        boolean nativeWeights = true;
        long nativeTotal = 0L;
        var context = new MiteExtensions.SpawnContext(level, category, null);
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            double rate = familyMultiplier(level, pos, entry, finalizationTransforms);
            if (finalizationTransforms) {
                double extended = MiteExtensions.policy().monsterSpawnWeightMultiplier(context, pos, entry.type(), rate);
                if (Double.isFinite(extended) && extended >= 0D && extended <= 100D) rate = extended;
            }
            double baseWeight = weight(entry.type(), Math.max(0, entry.weight()));
            weights[index] = baseWeight * rate;
            nativeWeights &= rate == 1D && baseWeight == Math.max(0, entry.weight());
            nativeTotal += Math.max(0, entry.weight());
        }
        if (nativeWeights && nativeTotal > 0L && nativeTotal <= Integer.MAX_VALUE) {
            int draw = random.nextInt((int) nativeTotal);
            for (var entry : entries) {
                draw -= Math.max(0, entry.weight());
                if (draw < 0) return entry;
            }
        }
        boolean any = false;
        for (double weight : weights) any |= weight > 0D;
        if (!any) return null;
        int index = MonsterSpawnRules.weightedIndex(weights, random.nextDouble());
        return index < 0 ? null : entries.get(index);
    }

    private static void naturalJoin(EntityJoinLevelEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof Mob mob) || mob.getSpawnType() != MobSpawnType.NATURAL) return;
        if (event.loadedFromDisk() || mob.getPersistentData().getBoolean(ADMITTED)) {
            // NeoForge persists NATURAL as the mob's original reason. It is also restored on a
            // dimension-transfer copy, which must not turn an existing inhabitant into a new spawn.
            mob.getPersistentData().putBoolean(ADMITTED, true);
            return;
        }
        // Check the final identity here; a disabled parent must not block its enabled replacement before finalization.
        if (multiplier(mob.getType()) == 0D || !R196MobSpawning.naturalDaylightAllows(mob, level)) {
            event.setCanceled(true);
        } else {
            mob.getPersistentData().putBoolean(ADMITTED, true);
            PROVISIONAL_ADMISSIONS.add(event);
        }
    }

    private static void finishNaturalJoin(EntityJoinLevelEvent event) {
        if (PROVISIONAL_ADMISSIONS.remove(event) && event.isCanceled() && event.getEntity() instanceof Mob mob)
            mob.getPersistentData().remove(ADMITTED);
    }
}

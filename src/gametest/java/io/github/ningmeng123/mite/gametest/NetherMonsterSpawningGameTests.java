package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual selection, qualification and finalization in the Nether. @author NingMeng123 */
@GameTestHolder("mite_spawning")
@PrefixGameTestTemplate(false)
public final class NetherMonsterSpawningGameTests {
    private NetherMonsterSpawningGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void sourceMonstersCanNaturallySpawnOnNetherGroundInBothPresets(GameTestHelper helper) {
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "The real Nether dimension is available");
        var site = new DaylightUndeadSpawnGameTests.Site(nether, true, 88);
        site.set(site.pos.below(), Blocks.NETHERRACK.defaultBlockState());
        site.keepLoaded(helper);
        helper.startSequence().thenWaitUntil(() -> site.requireLightingReady(helper)).thenExecute(() -> {
            try (site) {
                var chunk = nether.getChunkAt(site.pos);
                Map<Structure, LongSet> references = new HashMap<>();
                chunk.getAllReferences().forEach((structure, locations) -> references.put(structure, new LongOpenHashSet(locations)));
                chunk.setAllReferences(Map.of());
                try {
                    for (boolean reference : new boolean[]{false, true}) {
                        try (var preset = new SpawnPreset(reference)) {
                            assertNetherCandidates(helper, site);
                        }
                    }
                    assertNaturalEntry(helper, site);
                } finally {
                    chunk.setAllReferences(references);
                }
            }
        }).thenSucceed();
    }

    private static void assertNetherCandidates(GameTestHelper helper, DaylightUndeadSpawnGameTests.Site site) {
        Set<EntityType<?>> remaining = new HashSet<>();
        for (MobKind kind : List.of(MobKind.GHOUL, MobKind.WIGHT, MobKind.INVISIBLE_STALKER, MobKind.DEMON_SPIDER,
                MobKind.HELLHOUND, MobKind.INFERNAL_CREEPER, MobKind.SHADOW, MobKind.FIRE_ELEMENTAL,
                MobKind.BLACK_WIDOW_SPIDER, MobKind.REVENANT, MobKind.LONGDEAD, MobKind.NETHERSPAWN,
                MobKind.CLAY_GOLEM, MobKind.BONE_LORD, MobKind.ANCIENT_BONE_LORD, MobKind.PHASE_SPIDER)) {
            remaining.add(MiteMobs.type(kind));
        }
        remaining.add(EntityType.ZOMBIFIED_PIGLIN);
        RandomSource random = RandomSource.create(604_732L);
        for (int draw = 0; draw < 4_096 && !remaining.isEmpty(); draw++) {
            var entry = R196MobSpawning.select(site.level, site.pos, Category.MONSTER, random, false, true);
            if (entry != null) remaining.remove(entry.type());
        }
        helper.assertTrue(remaining.isEmpty(), "Nether selection admits the added source species and the original piglin: " + remaining);
    }

    private static void assertNaturalEntry(GameTestHelper helper, DaylightUndeadSpawnGameTests.Site site) {
        var worldData = site.level.getServer().getWorldData();
        Difficulty previousDifficulty = site.level.getDifficulty();
        try {
            for (MobKind kind : List.of(MobKind.GHOUL, MobKind.FIRE_ELEMENTAL, MobKind.NETHERSPAWN)) {
                var mob = site.mob(MiteMobs.type(kind));
                try {
                    worldData.setDifficulty(Difficulty.PEACEFUL);
                    helper.assertFalse(R196MobSpawning.qualifiesWithEvent(mob, site.level, MobSpawnType.NATURAL, true, false),
                            "Peaceful rejects the Nether candidate " + kind);
                    worldData.setDifficulty(Difficulty.HARD);
                    site.set(site.pos.below(), Blocks.AIR.defaultBlockState());
                    helper.assertFalse(R196MobSpawning.qualifiesWithEvent(mob, site.level, MobSpawnType.NATURAL, true, false),
                            "Natural Nether spawns require a supporting surface for " + kind);
                    site.set(site.pos.below(), Blocks.NETHERRACK.defaultBlockState());
                    helper.assertTrue(R196MobSpawning.qualifiesWithEvent(mob, site.level, MobSpawnType.NATURAL, true, false),
                            "The dark netherrack habitat qualifies for " + kind);
                    var result = R196MobSpawning.finalizeNaturalMonster(site.level, mob, null, 1);
                    helper.assertTrue(result.monsters() == 1 && mob.isAddedToLevel() && mob.getSpawnType() == MobSpawnType.NATURAL,
                            "One candidate naturally enters the Nether: " + kind);
                } finally {
                    mob.discard();
                }
            }
        } finally {
            worldData.setDifficulty(previousDifficulty);
        }
    }
}

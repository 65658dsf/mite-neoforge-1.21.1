package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Cavern vegetation must not silently replace the ordinary hostile roster with addon entries. @author NingMeng123 */
@GameTestHolder("mite_underworld_natural")
@PrefixGameTestTemplate(false)
public final class UnderworldNaturalSpawnGameTests {
    private UnderworldNaturalSpawnGameTests() {}

    @GameTest(template = "empty")
    public static void underworldMyceliumKeepsTheOrdinarySourceCandidatePool(GameTestHelper helper) {
        try (var preset = new SpawnPreset(true); Site site = new Site(helper, 0)) {
            var stonePool = R196MobSpawning.sourcePool(site.level, site.pos, Category.MONSTER);
            Set<EntityType<?>> stoneSpecialists = Set.of(MiteMobs.type(MobKind.OOZE), MiteMobs.type(MobKind.PUDDING));
            List<EntityType<?>> stoneChoices = new ArrayList<>();
            for (int seed = 0; seed < 512; seed++) stoneChoices.add(select(site, seed));
            site.set(site.pos.below(), Blocks.MYCELIUM.defaultBlockState());
            helper.assertTrue(R196MobSpawning.positionY(site.level, site.pos, Category.MONSTER) != null,
                    "A natural underworld mycelium patch retains the source-ground spawn path");
            helper.assertTrue(stonePool.equals(R196MobSpawning.sourcePool(site.level, site.pos, Category.MONSTER)),
                    "Changing the floor preserves every source candidate and its original weight; species habitat checks remain separate");
            Set<EntityType<?>> selected = new HashSet<>();
            int specialistDraws = 0;
            for (int seed = 0; seed < 512; seed++) {
                EntityType<?> choice = select(site, seed);
                EntityType<?> stoneChoice = stoneChoices.get(seed);
                helper.assertTrue(choice != null && !stoneSpecialists.contains(choice),
                        "Mycelium keeps an ordinary candidate without admitting the two stone-floor specialists: " + seed);
                if (stoneSpecialists.contains(stoneChoice)) {
                    specialistDraws++;
                    helper.assertTrue(choice != stoneChoice,
                            "The original ooze/pudding stone-floor restriction still rerolls a mycelium candidate: " + seed);
                } else {
                    helper.assertTrue(choice == stoneChoice,
                            "Every non-specialist draw keeps exactly the same candidate on stone and mycelium: " + seed
                                    + " stone=" + stoneChoice + " mycelium=" + choice);
                }
                selected.add(choice);
            }
            helper.assertTrue(specialistDraws > 0, "The fixed seeds actually exercise the preserved stone-only habitat branch");
            for (EntityType<?> expected : List.of(EntityType.SPIDER, EntityType.CREEPER, EntityType.CAVE_SPIDER,
                    MiteMobs.type(MobKind.WIGHT), MiteMobs.type(MobKind.LONGDEAD)))
                helper.assertTrue(selected.contains(expected), "The restored pool includes " + expected);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryHostileSpeciesQualifyOnUnderworldMycelium(GameTestHelper helper) {
        try (Site site = new Site(helper, 1)) {
            site.set(site.pos.below(), Blocks.MYCELIUM.defaultBlockState());
            for (EntityType<? extends Mob> type : List.of(EntityType.SPIDER, EntityType.CREEPER, EntityType.CAVE_SPIDER,
                    MiteMobs.type(MobKind.WIGHT), MiteMobs.type(MobKind.LONGDEAD), MiteMobs.type(MobKind.SHADOW))) {
                Mob mob = type.create(site.level);
                helper.assertTrue(mob != null, "Registered ordinary hostile creates: " + type);
                try {
                    Double y = R196MobSpawning.positionY(site.level, site.pos, Category.MONSTER);
                    helper.assertTrue(y != null, "The scheduler obtains a supported position for " + type);
                    mob.moveTo(site.pos.getX() + .5, y, site.pos.getZ() + .5, 0, 0);
                    helper.assertTrue(R196MobSpawning.qualifiesWithEvent(mob, site.level, MobSpawnType.NATURAL, false, false),
                            "Actual source geometry and species checks permit the mycelium candidate: " + type);
                } finally { mob.discard(); }
            }
            helper.assertFalse(R196MobSpawning.groundAllows(site.level, site.pos.below(), Category.ANIMAL),
                    "Hostile ground adaptation does not turn fungus patches into animal grass");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void overworldMyceliumAndUnsafeGroundKeepTheirExistingExclusions(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 0, 2));
        BlockState previous = helper.getLevel().getBlockState(pos);
        try {
            helper.getLevel().setBlock(pos, Blocks.MYCELIUM.defaultBlockState(), 2);
            helper.assertFalse(R196MobSpawning.groundAllows(helper.getLevel(), pos, Category.MONSTER),
                    "Overworld mycelium retains its original hostile-ground exclusion");
        } finally { helper.getLevel().setBlock(pos, previous, 2); }
        try (Site site = new Site(helper, 2)) {
            for (var block : List.of(Blocks.BEDROCK, Blocks.OAK_FENCE, Blocks.AIR)) {
                site.set(site.pos.below(), block.defaultBlockState());
                helper.assertTrue(R196MobSpawning.positionY(site.level, site.pos, Category.MONSTER) == null,
                        "Underworld fungus adaptation cannot admit unsafe ground: " + block);
            }
        }
        helper.succeed();
    }

    private static EntityType<?> select(Site site, int seed) {
        // This is the same modern-only switch used by the real natural spawning pass.
        boolean modernOnly = R196MobSpawning.positionY(site.level, site.pos, Category.MONSTER) == null;
        var entry = R196MobSpawning.select(site.level, site.pos, Category.MONSTER, RandomSource.create(seed), modernOnly, true);
        return entry == null ? null : entry.type();
    }

    private static final class Site implements AutoCloseable {
        final ServerLevel level;
        final BlockPos pos;
        final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        Site(GameTestHelper helper, int index) {
            level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            helper.assertTrue(level != null, "The real underworld is loaded");
            pos = new BlockPos(-9_800 + index * 32, 180, 9_600);
            for (BlockPos point : BlockPos.betweenClosed(pos.offset(-3, -1, -3), pos.offset(3, 5, 3))) {
                boolean shell = point.getY() == pos.getY() - 1 || point.getY() == pos.getY() + 5
                        || Math.abs(point.getX() - pos.getX()) == 3 || Math.abs(point.getZ() - pos.getZ()) == 3;
                set(point, (shell ? Blocks.STONE : Blocks.AIR).defaultBlockState());
            }
        }
        void set(BlockPos point, BlockState state) {
            previous.putIfAbsent(point.immutable(), level.getBlockState(point));
            level.setBlock(point, state, 2);
        }
        @Override public void close() { previous.forEach((point, state) -> level.setBlock(point, state, 2)); }
    }
}

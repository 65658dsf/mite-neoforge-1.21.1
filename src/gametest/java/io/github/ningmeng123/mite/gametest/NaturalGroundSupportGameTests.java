package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.worldgen.R196SpawnQualification;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The positioned natural candidate needs immediate support even when an addon supplied its source identity. @author NingMeng123 */
@GameTestHolder("mite_ground_spawns")
@PrefixGameTestTemplate(false)
public final class NaturalGroundSupportGameTests {
    private NaturalGroundSupportGameTests() {}

    @GameTest(template = "empty")
    public static void hellhoundCannotAppearTwentyFourBlocksAboveARealCavernFloor(GameTestHelper helper) {
        try (Site site = new Site(helper, 0)) {
            Mob hellhound = site.mob(MiteMobs.type(MobKind.HELLHOUND));
            try {
                helper.assertTrue(site.level.getBlockState(site.pos.below(24)).is(Blocks.STONE),
                        "This regression has a real distant floor, rather than an empty void");
                helper.assertTrue(R196MobSpawning.positionY(site.level, site.pos, Category.MONSTER) == null,
                        "The sampled air cell has no immediate support");
                helper.assertTrue(R196SpawnQualification.qualifies(hellhound, site.level, false, false),
                        "The old collision and species predicate alone admits the injected hellhound in midair");
                helper.assertFalse(qualifies(hellhound, site),
                        "The actual natural-spawn qualification rejects the unsupported first injected candidate");
                // This is also the scheduler's wide-body collision retry offset. It must not find a floor far below.
                for (double[] offset : new double[][]{{-.5, 0}, {.5, 0}, {0, -.5}, {0, .5}}) {
                    hellhound.setPos(site.pos.getX() + .5 + offset[0], site.pos.getY() + .005,
                            site.pos.getZ() + .5 + offset[1]);
                    helper.assertFalse(qualifies(hellhound, site), "A horizontal retry cannot turn an air spawn into supported ground");
                }
                BlockPos landing = site.pos.below(23);
                hellhound.moveTo(landing.getX() + .5, landing.getY() + .005, landing.getZ() + .5, 0, 0);
                helper.assertTrue(qualifies(hellhound, site), "The same hellhound qualifies when sampled directly on the real floor");
            } finally { hellhound.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void groundSpeciesNeedSupportButKeepStoneMyceliumAndSlabSurfaces(GameTestHelper helper) {
        try (Site site = new Site(helper, 1)) {
            for (EntityType<? extends Mob> type : List.of(MiteMobs.type(MobKind.HELLHOUND), EntityType.ZOMBIE,
                    EntityType.CREEPER, EntityType.SPIDER, MiteMobs.type(MobKind.LONGDEAD), MiteMobs.type(MobKind.FIRE_ELEMENTAL))) {
                Mob mob = site.mob(type);
                try {
                    helper.assertFalse(qualifies(mob, site), "Ground species cannot use unsupported air: " + type);
                    for (var floor : List.of(Blocks.STONE, Blocks.MYCELIUM)) {
                        site.set(site.pos.below(), floor.defaultBlockState());
                        helper.assertTrue(qualifies(mob, site), "A supported underworld surface remains valid: " + floor + " / " + type);
                        mob.setPos(mob.getX(), site.pos.getY() + .5, mob.getZ());
                        helper.assertFalse(qualifies(mob, site), "A floor in the correct block column cannot support a floating half-block gap: " + type);
                        mob.setPos(mob.getX(), site.pos.getY() + .005, mob.getZ());
                    }
                    site.set(site.pos.below(), Blocks.AIR.defaultBlockState());
                    site.set(site.pos, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                    Double y = R196MobSpawning.positionY(site.level, site.pos, Category.MONSTER);
                    helper.assertTrue(y != null, "The existing partial-block positioning still obtains its surface");
                    mob.setPos(mob.getX(), y, mob.getZ());
                    helper.assertTrue(qualifies(mob, site), "A real bottom-slab surface retains the source placement rules: " + type);
                } finally {
                    mob.discard();
                    site.set(site.pos, Blocks.AIR.defaultBlockState());
                    site.set(site.pos.below(), Blocks.AIR.defaultBlockState());
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realFlyingSpeciesKeepUnsupportedAirAndSpawnerReasonsKeepTheirRules(GameTestHelper helper) {
        try (Site site = new Site(helper, 2)) {
            var overworld = helper.getLevel().getServer().overworld();
            var progress = BloodMoonInvasionData.get(overworld);
            long previousDay = progress.highestDay();
            boolean previousDirty = progress.isDirty();
            try {
                progress.restoreProgress(Math.max(128L, previousDay));
                for (EntityType<? extends Mob> type : List.of(EntityType.BLAZE, EntityType.GHAST, EntityType.BAT,
                        MiteMobs.type(MobKind.VAMPIRE_BAT))) {
                    Mob mob = site.mob(type);
                    try {
                        boolean allowed = false;
                        for (int seed = 0; seed < 128 && !allowed; seed++) {
                            mob.getRandom().setSeed(4096L + seed);
                            allowed = qualifies(mob, site);
                        }
                        helper.assertTrue(allowed, "A genuine flying identity still qualifies in open air: " + type);
                    } finally { mob.discard(); }
                }
                Mob hellhound = site.mob(MiteMobs.type(MobKind.HELLHOUND));
                try {
                    helper.assertTrue(R196MobSpawning.qualifiesWithEvent(hellhound, site.level, MobSpawnType.SPAWNER, false, false),
                            "The natural-spawn fix does not replace scripted spawner placement rules");
                } finally { hellhound.discard(); }
            } finally {
                progress.restoreProgress(previousDay);
                progress.setDirty(previousDirty);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void addonSuccessCannotOverrideTheActualNaturalGroundRequirement(GameTestHelper helper) {
        try (Site site = new Site(helper, 3)) {
            Mob hellhound = site.mob(MiteMobs.type(MobKind.HELLHOUND));
            int[] calls = {0};
            Consumer<MobSpawnEvent.PositionCheck> override = event -> {
                if (event.getEntity() != hellhound) return;
                calls[0]++;
                event.setResult(MobSpawnEvent.PositionCheck.Result.SUCCEED);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobSpawnEvent.PositionCheck.class, override);
            try {
                helper.assertFalse(qualifies(hellhound, site),
                        "Even an unconditional addon-success listener cannot admit an unsupported natural ground mob");
                helper.assertTrue(calls[0] == 0, "Geometry is resolved before an addon can override species predicates");
                site.set(site.pos.below(), Blocks.STONE.defaultBlockState());
                helper.assertTrue(qualifies(hellhound, site) && calls[0] == 1,
                        "The same real listener is invoked and accepted once the candidate has immediate support");
            } finally {
                NeoForge.EVENT_BUS.unregister(override);
                hellhound.discard();
            }
        }
        helper.succeed();
    }

    private static boolean qualifies(Mob mob, Site site) {
        return R196MobSpawning.qualifiesWithEvent(mob, site.level, MobSpawnType.NATURAL, false, false);
    }

    private static final class Site implements AutoCloseable {
        final ServerLevel level;
        final BlockPos pos;
        final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        Site(GameTestHelper helper, int index) {
            level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            helper.assertTrue(level != null, "The real underworld is loaded");
            pos = new BlockPos(-9_500 + index * 32, 180, 9_600);
            for (BlockPos point : BlockPos.betweenClosed(pos.offset(-6, -24, -6), pos.offset(6, 6, 6))) {
                boolean shell = point.getY() == pos.getY() - 24 || point.getY() == pos.getY() + 6
                        || Math.abs(point.getX() - pos.getX()) == 6 || Math.abs(point.getZ() - pos.getZ()) == 6;
                set(point, (shell ? Blocks.STONE : Blocks.AIR).defaultBlockState());
            }
        }
        Mob mob(EntityType<? extends Mob> type) {
            Mob mob = type.create(level);
            if (mob == null) throw new AssertionError("Registered natural-spawn identity creates: " + type);
            mob.moveTo(pos.getX() + .5, pos.getY() + .005, pos.getZ() + .5, 0, 0);
            mob.setNoAi(true);
            return mob;
        }
        void set(BlockPos point, BlockState state) {
            previous.putIfAbsent(point.immutable(), level.getBlockState(point));
            level.setBlock(point, state, 2);
        }
        @Override public void close() { previous.forEach((point, state) -> level.setBlock(point, state, 2)); }
    }
}

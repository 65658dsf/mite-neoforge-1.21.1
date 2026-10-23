package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.gametest.DaylightUndeadSpawnGameTests.Progress;
import io.github.ningmeng123.mite.gametest.DaylightUndeadSpawnGameTests.Site;
import io.github.ningmeng123.mite.mixin.MobSpawnTypeAccessor;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.ConfiguredMonsterSpawns;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.worldgen.R196OutdoorChecks;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real surface-cave geometry and admission regression coverage. @author NingMeng123 */
@GameTestHolder("mite_surface_caves")
@PrefixGameTestTemplate(false)
public final class SurfaceCaveSpawnGameTests {
    private SurfaceCaveSpawnGameTests() {}

    @GameTest(template = "empty")
    public static void allHostilesAndBloodDates(GameTestHelper helper) {
        DaylightUndeadSpawnGameTests.sourceAndModernHostilesShareTheDaylightRestriction(helper);
    }

    @GameTest(template = "empty")
    public static void currentDatesCannotReleaseDaylight(GameTestHelper helper) {
        DaylightUndeadSpawnGameTests.bloodDateEndBlueMoonAndPermanentInvasionUnlockCannotKeepDaylightUndeadEnabled(helper);
    }

    @GameTest(template = "empty")
    public static void enclosedAndOtherDimensions(GameTestHelper helper) {
        DaylightUndeadSpawnGameTests.enclosedHabitatsAndOtherDimensionsRemainEligibleForTheirOwnSpawnChecks(helper);
    }

    @GameTest(template = "empty")
    public static void positionSuccessCannotBypassDaylight(GameTestHelper helper) {
        DaylightUndeadSpawnGameTests.naturalPositionSuccessCannotBypassDaylightButOtherSpawnReasonsKeepTheirEventContract(helper);
    }

    @GameTest(template = "empty")
    public static void treeCanopiesAndBranches(GameTestHelper helper) {
        DaylightUndeadSpawnGameTests.denseTreeCanopiesAndOverheadBranchesCannotHideSurfaceBoggedAtEitherElevation(helper);
    }

    @GameTest(template = "empty")
    public static void surfaceWater(GameTestHelper helper) {
        DaylightUndeadSpawnGameTests.surfaceWaterDoesNotCountAsAnUndergroundRoofForTheDaylightGate(helper);
    }

    @GameTest(template = "empty")
    public static void sideOpeningUnderRockRoof(GameTestHelper helper) {
        DaylightUndeadSpawnGameTests.stoneRoofCannotHideTheLowerSurfaceCaveBesideAnEntranceAtBothElevations(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void solidBodyCollisionStillRejects(GameTestHelper helper) {
        DaylightUndeadSpawnGameTests.modernSpawnCandidatesCannotStartInsideSolidGrassStoneOrTreeTrunks(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void blockedHeadroomStillRejects(GameTestHelper helper) {
        DaylightUndeadSpawnGameTests.modernSpawnCandidatesNeedClearHeadroomEvenWhenTheirFeetAreInAir(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void validDarkCaveStillQualifies(GameTestHelper helper) {
        DaylightUndeadSpawnGameTests.modernBoggedStillQualifiesInRealDarkCaveAirWithValidGroundDuringOrdinaryDaylight(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void ordinaryAndBloodDayJockeysCannotBypassFinalGate(GameTestHelper helper) {
        HostilePopulationGameTests.ordinaryAndBloodDayBothRejectTheFinalizedOutdoorJockey(helper);
    }

    @GameTest(template = "empty")
    public static void stairsAndCornerReachSurfaceAtDifferentElevations(GameTestHelper helper) {
        try (Progress progress = new Progress(helper.getLevel())) {
            progress.time(6_000L);
            for (int height : List.of(74, 200)) try (Site site = steppedCave(helper, height)) {
                helper.assertFalse(R196OutdoorChecks.surface(site.level, site.pos), "The candidate has a real stone roof at Y=" + height);
                helper.assertFalse(R196OutdoorChecks.outdoors(site.level, site.pos), "The old flat short-path outdoor test cannot follow this raised corner");
                helper.assertTrue(R196OutdoorChecks.daytimeSurfaceHabitat(site.level, site.pos),
                        "An air path up two steps and around the corner still reaches the surface at Y=" + height);
                for (EntityType<? extends Mob> type : List.of(EntityType.CREEPER, EntityType.SPIDER, EntityType.ZOMBIE,
                        MiteMobs.type(MobKind.DIRE_WOLF), MiteMobs.type(MobKind.VAMPIRE_BAT))) {
                    Mob mob = site.mob(type);
                    helper.assertFalse(R196MobSpawning.naturalDaylightAllows(mob, site.level),
                            "The daytime shallow-cave gate covers " + type + " at Y=" + height);
                }
                for (EntityType<? extends Mob> type : List.of(EntityType.BAT, EntityType.COW))
                    helper.assertTrue(R196MobSpawning.naturalDaylightAllows(site.mob(type), site.level),
                            "Ordinary animals retain their own spawn rules: " + type);
                progress.time(18_000L);
                helper.assertTrue(R196MobSpawning.naturalDaylightAllows(site.mob(EntityType.CREEPER), site.level),
                        "The same habitat is eligible for the original nighttime spawn checks");
                progress.time(6_000L);
                site.fill(site.pos.offset(4, 2, 3), site.pos.offset(4, 6, 3), Blocks.STONE.defaultBlockState());
                helper.assertFalse(R196OutdoorChecks.daytimeSurfaceHabitat(site.level, site.pos),
                        "Filling the only entrance makes the room truly enclosed");
                helper.assertTrue(R196MobSpawning.naturalDaylightAllows(site.mob(EntityType.CREEPER), site.level),
                        "A sealed underground room retains its original daytime eligibility");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deepTunnelDoesNotBecomeSurfaceBecauseOfDistantEntrance(GameTestHelper helper) {
        try (Progress progress = new Progress(helper.getLevel()); Site site = new Site(helper.getLevel(), true, 74)) {
            site.fill(site.pos.offset(-4, -1, -4), site.pos.offset(22, 5, 4), Blocks.STONE.defaultBlockState());
            site.fill(site.pos.offset(-2, 0, -2), site.pos.offset(2, 2, 2), Blocks.AIR.defaultBlockState());
            site.fill(site.pos.offset(3, 0, 0), site.pos.offset(16, 2, 0), Blocks.AIR.defaultBlockState());
            site.openColumn(site.pos.offset(16, 0, 0));
            progress.time(6_000L);
            helper.assertTrue(R196OutdoorChecks.surface(site.level, site.pos.offset(16, 0, 0)), "The tunnel has a real open-air end");
            helper.assertFalse(R196OutdoorChecks.daytimeSurfaceHabitat(site.level, site.pos),
                    "An entrance sixteen air-path steps away does not classify the inner cave as surface");
            helper.assertTrue(R196MobSpawning.naturalDaylightAllows(site.mob(EntityType.SPIDER), site.level),
                    "Deep daytime cave spawning remains eligible for native light and geometry checks");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creeperPositionOverrideAndDirectJoinCannotBypassSurfaceGate(GameTestHelper helper) {
        try (Progress progress = new Progress(helper.getLevel()); Site site = steppedCave(helper, 74)) {
            progress.time(6_000L);
            Mob creeper = site.mob(EntityType.CREEPER);
            AtomicInteger overrides = new AtomicInteger();
            Consumer<MobSpawnEvent.PositionCheck> allow = event -> {
                if (event.getEntity() == creeper) {
                    overrides.incrementAndGet();
                    event.setResult(MobSpawnEvent.PositionCheck.Result.SUCCEED);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobSpawnEvent.PositionCheck.class, allow);
            try {
                helper.assertFalse(R196MobSpawning.qualifiesWithEvent(creeper, site.level, MobSpawnType.NATURAL, false, false),
                        "A success override cannot bypass the shallow-cave gate for a non-undead hostile");
                helper.assertValueEqual(overrides.get(), 0, "The surface gate runs before any override is dispatched");
            } finally { NeoForge.EVENT_BUS.unregister(allow); }
            for (EntityType<? extends Mob> type : List.of(EntityType.CREEPER, EntityType.SPIDER)) {
                Mob direct = site.mob(type);
                ((MobSpawnTypeAccessor) direct).mite$setSpawnType(MobSpawnType.NATURAL);
                helper.assertFalse(site.level.addFreshEntity(direct), "A direct NATURAL join cannot skip qualification: " + type);
                helper.assertFalse(direct.getPersistentData().getBoolean(ConfiguredMonsterSpawns.ADMITTED),
                        "A rejected daytime natural is not stamped as admitted");
            }
            Mob finalized = site.mob(EntityType.CREEPER);
            helper.assertValueEqual(R196MobSpawning.finalizeNaturalMonster(site.level, finalized, null, 1).monsters(), 0,
                    "The final natural-spawn transaction rechecks the exposed cave habitat");
            helper.assertTrue(finalized.isRemoved(), "A rejected natural finalization cleans up its candidate");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedAndExplicitMobsAreNotRemovedByDaylight(GameTestHelper helper) {
        try (Progress progress = new Progress(helper.getLevel()); Site site = steppedCave(helper, 74)) {
            progress.time(6_000L);
            Mob transferred = site.mob(EntityType.CREEPER);
            ((MobSpawnTypeAccessor) transferred).mite$setSpawnType(MobSpawnType.NATURAL);
            transferred.getPersistentData().putBoolean(ConfiguredMonsterSpawns.ADMITTED, true);
            helper.assertTrue(site.level.addFreshEntity(transferred), "An already-admitted inhabitant can transfer into daylight");
            helper.assertFalse(transferred.isRemoved(), "The daylight gate does not remove existing inhabitants");
            Mob loaded = site.mob(EntityType.SPIDER);
            ((MobSpawnTypeAccessor) loaded).mite$setSpawnType(MobSpawnType.NATURAL);
            var reload = new EntityJoinLevelEvent(loaded, site.level, true);
            NeoForge.EVENT_BUS.post(reload);
            helper.assertFalse(reload.isCanceled(), "An older save without the admitted marker still loads in daytime");
            helper.assertTrue(loaded.getPersistentData().getBoolean(ConfiguredMonsterSpawns.ADMITTED),
                    "Disk-loaded inhabitants receive their compatibility admission marker");
            for (MobSpawnType reason : List.of(MobSpawnType.SPAWN_EGG, MobSpawnType.COMMAND, MobSpawnType.SPAWNER, MobSpawnType.TRIGGERED)) {
                Mob explicit = site.mob(EntityType.CREEPER);
                ((MobSpawnTypeAccessor) explicit).mite$setSpawnType(reason);
                helper.assertTrue(site.level.addFreshEntity(explicit), "The natural gate does not block " + reason);
                helper.assertFalse(explicit.isRemoved(), "The explicit spawn remains alive: " + reason);
            }
        }
        helper.succeed();
    }

    private static Site steppedCave(GameTestHelper helper, int height) {
        Site site = new Site(helper.getLevel(), true, height);
        site.fill(site.pos.offset(-4, -1, -4), site.pos.offset(7, 6, 6), Blocks.STONE.defaultBlockState());
        site.fill(site.pos.offset(-2, 0, -2), site.pos.offset(2, 2, 2), Blocks.AIR.defaultBlockState());
        for (BlockPos foot : List.of(site.pos.offset(3, 1, 0), site.pos.offset(4, 2, 0),
                site.pos.offset(4, 2, 1), site.pos.offset(4, 2, 2), site.pos.offset(4, 2, 3))) {
            site.set(foot, Blocks.AIR.defaultBlockState());
            site.set(foot.above(), Blocks.AIR.defaultBlockState());
        }
        site.openColumn(site.pos.offset(4, 2, 3));
        return site;
    }
}

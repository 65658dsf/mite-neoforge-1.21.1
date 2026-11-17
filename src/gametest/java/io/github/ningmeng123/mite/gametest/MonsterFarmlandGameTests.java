package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.R196DiggerGoal;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196MonsterAi;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.ai.MonsterFarmland;
import io.github.ningmeng123.mite.entity.ai.MonsterFarmlandGoal;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.survival.FoodAgriculture;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Separate source pursuit digging from user-requested idle raiding, through real paths/blocks/vetoes. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class MonsterFarmlandGameTests {
    private static final long BLOOD_NIGHT = 31L * 24_000L + 18_000L;
    private MonsterFarmlandGameTests() {}

    @GameTest(template = "empty")
    public static void vanillaGroundMonstersReceiveOneIdleGoalWhileModdedAndFlyingMonstersDoNot(GameTestHelper helper) {
        for (var type : List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.HUSK,
                EntityType.PIGLIN, EntityType.ENDERMAN, EntityType.SILVERFISH, EntityType.WITCH)) {
            Mob mob = type.create(helper.getLevel()); helper.getLevel().addFreshEntity(mob);
            try {
                MonsterFarmlandGoal.install((PathfinderMob) mob);
                helper.assertValueEqual(mob.goalSelector.getAvailableGoals().stream()
                        .filter(wrapped -> wrapped.getGoal() instanceof MonsterFarmlandGoal).count(), 1L,
                        "Real join installs one bounded idle goal for the ground monster " + type);
            } finally { mob.discard(); }
        }
        for (var type : List.of(EntityType.COW, EntityType.WOLF, EntityType.BAT, EntityType.BLAZE,
                EntityType.VEX, EntityType.GHAST, EntityType.PHANTOM, EntityType.SHULKER,
                MiteMobs.type(MobKind.REVENANT), MiteMobs.type(MobKind.GHOUL))) {
            Mob mob = type.create(helper.getLevel()); helper.getLevel().addFreshEntity(mob);
            try {
                helper.assertTrue(mob.goalSelector.getAvailableGoals().stream()
                        .noneMatch(wrapped -> wrapped.getGoal() instanceof MonsterFarmlandGoal),
                        "Modded mobs, animals and floating/stationary monsters have no walking farm goal " + type);
            } finally { mob.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyTheCurrentOverworldBloodMoonNightAllowsRaidingAndDawnCancelsWork(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            fixture.field(true, false); var goal = fixture.ready();
            for (long time : new long[]{18_000L, 31L * 24_000L + 6_000L, 127L * 24_000L + 18_000L}) {
                fixture.level.setDayTime(time);
                helper.assertFalse(goal.canUse(), "Ordinary nights, blood-date daylight and blue moons do not start raids");
                helper.assertFalse(MonsterFarmland.flatten(fixture.mob, fixture.farm), "Direct flattening respects the current blood night");
                fixture.assertIntact("An inactive blood moon");
            }
            fixture.level.setDayTime(BLOOD_NIGHT);
            helper.assertTrue(goal.canUse(), "The actual current blood moon night enables the reachable raid");
            goal.start(); fixture.mob.moveTo(fixture.farm.getX() + .5, fixture.farm.getY() + 1, fixture.farm.getZ() + .5);
            for (int tick = 0; tick < 39; tick++) goal.tick();
            fixture.level.setDayTime(32L * 24_000L + 6_000L); goal.tick();
            helper.assertTrue(goal.targetBlock() == null && fixture.mob.getNavigation().isDone(), "Dawn cancels movement and clears work progress");
            fixture.assertIntact("Dawn on the last work tick");
            var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            var netherZombie = EntityType.ZOMBIE.create(nether);
            try { helper.assertFalse(MonsterFarmland.allowed(netherZombie), "Other dimensions never inherit the overworld blood moon"); }
            finally { netherZombie.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pursuitDiggingAndFallTramplingCannotBypassTheBloodNightOrVanillaRestriction(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            fixture.field(true, false);
            var digger = R196DiggerGoal.first(fixture.mob);
            fixture.level.setDayTime(18_000L);
            helper.assertFalse(digger.canDestroyMaterial(fixture.farm), "Source pursuit cannot dig farmland outside a blood moon");
            fixture.set(fixture.farm.east(), Blocks.DIRT.defaultBlockState());
            helper.assertTrue(digger.canDestroyMaterial(fixture.farm.east()), "Ordinary pursuit still permits its existing non-farmland materials");
            Blocks.FARMLAND.fallOn(fixture.level, fixture.level.getBlockState(fixture.farm), fixture.farm, fixture.mob, 2F);
            fixture.assertIntact("Ordinary-night landing");
            fixture.level.setDayTime(BLOOD_NIGHT);
            helper.assertTrue(digger.canDestroyMaterial(fixture.farm), "A vanilla zombie may dig farmland during the blood moon");
            var revenant = (PathfinderMob) MiteMobs.type(MobKind.REVENANT).create(fixture.level);
            revenant.setNoAi(true); fixture.level.addFreshEntity(revenant);
            try {
                revenant.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                helper.assertFalse(MonsterFarmland.flatten(revenant, fixture.farm), "Modded zombie subclasses cannot directly flatten farms");
                helper.assertFalse(R196DiggerGoal.first(revenant).canDestroyMaterial(fixture.farm), "Source inheritance cannot grant modded mobs farmland digging");
                Blocks.FARMLAND.fallOn(fixture.level, fixture.level.getBlockState(fixture.farm), fixture.farm, revenant, 2F);
                fixture.assertIntact("A modded monster landing during blood moon");
            } finally { revenant.discard(); }
            var hostileBat = MiteMobs.type(MobKind.VAMPIRE_BAT).create(fixture.level);
            try {
                Blocks.FARMLAND.fallOn(fixture.level, fixture.level.getBlockState(fixture.farm), fixture.farm, hostileBat, 2F);
                fixture.assertIntact("An ambient-category MITE hostile bat landing during blood moon");
            } finally { hostileBat.discard(); }
            fixture.grief(false);
            Blocks.FARMLAND.fallOn(fixture.level, fixture.level.getBlockState(fixture.farm), fixture.farm, fixture.mob, 2F);
            fixture.assertIntact("Disabled mobGriefing during blood moon landing");
            fixture.grief(true);
            Blocks.FARMLAND.fallOn(fixture.level, fixture.level.getBlockState(fixture.farm), fixture.farm, fixture.mob, 2F);
            helper.assertTrue(fixture.level.getBlockState(fixture.farm).is(Blocks.DIRT)
                    && fixture.level.getBlockState(fixture.farm.above()).isAir(), "Vanilla monster landing works on a permitted blood night");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reachableCropAndEmptyFieldsNeedFortyWorkTicksAndDoNotDropSoilItems(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            for (boolean crop : new boolean[]{false, true}) for (boolean enriched : new boolean[]{false, true}) {
                fixture.field(crop, enriched); var goal = fixture.ready();
                helper.assertTrue(goal.canUse(), "A real reachable path exists to the selected soil; crop=" + crop
                        + ", enriched=" + enriched + "; " + fixture.diagnostic());
                helper.assertValueEqual(goal.targetBlock(), fixture.farm, "Nearest reachable farm is selected");
                goal.start();
                helper.assertTrue(!fixture.mob.getNavigation().isDone(), "Actual ground navigation receives the verified path");
                fixture.mob.moveTo(fixture.farm.getX() + .5, fixture.farm.getY() + 1, fixture.farm.getZ() + .5);
                for (int tick = 0; tick < 39; tick++) goal.tick();
                helper.assertTrue(fixture.level.getBlockState(fixture.farm).is(Blocks.FARMLAND), "Thirty-nine ticks cannot flatten a field");
                goal.tick();
                helper.assertTrue(fixture.level.getBlockState(fixture.farm).is(Blocks.DIRT)
                        && fixture.level.getBlockState(fixture.farm.above()).isAir(),
                        "Deliberate forty-tick raiding works on bare/cropped and wet/fertilized fields");
                helper.assertTrue(fixture.drops().isEmpty(), "Raiding yields neither farm soil nor mature wheat/seeds");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "monster_farmland_live", timeoutTicks = 220)
    public static void actualIdleZombieWalksItsRealPathAndFlattensOneField(GameTestHelper helper) {
        var fixture = new Fixture(helper); fixture.field(true, false);
        // The walking area extends beyond the empty template. Its own batch and native tickets prevent another
        // fixture from clearing its fields or leaving its entity in a loaded but non-ticking chunk.
        fixture.mob.targetSelector.removeAllGoals(goal -> true);
        fixture.mob.goalSelector.removeAllGoals(goal -> !(goal instanceof MonsterFarmlandGoal));
        double initialX = fixture.mob.getX();
        var chunks = java.util.stream.Stream.of(new ChunkPos(fixture.origin), new ChunkPos(fixture.farm)).distinct().toList();
        var ticket = TicketType.<Integer>create("mite_live_farmland_test", java.util.Comparator.naturalOrder(), 240);
        int ticketKey = fixture.mob.getId(); boolean[] cleaned = {false};
        Runnable cleanup = () -> {
            if (cleaned[0]) return;
            cleaned[0] = true;
            try { fixture.close(); }
            finally { for (var chunk : chunks) fixture.level.getChunkSource().removeRegionTicket(ticket, chunk, 2, ticketKey); }
        };
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup.run(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) {
                com.mojang.logging.LogUtils.getLogger().error("Live farmland fixture: {}", fixture.diagnostic()); cleanup.run();
            }
        });
        try {
            for (var chunk : chunks) fixture.level.getChunkSource().addRegionTicket(ticket, chunk, 2, ticketKey);
            helper.startSequence().thenWaitUntil(() -> {
                helper.assertTrue(fixture.level.isPositionEntityTicking(fixture.origin)
                                && fixture.level.isPositionEntityTicking(fixture.farm),
                        "The real movement area reaches native ENTITY_TICKING status; " + fixture.diagnostic());
                helper.assertTrue(fixture.level.getEntity(fixture.mob.getUUID()) == fixture.mob,
                        "The real walking zombie is indexed in its ticking chunk; " + fixture.diagnostic());
            }).thenExecute(() -> {
                fixture.mob.setNoGravity(false); fixture.mob.setOnGround(true);
                var goal = fixture.ready();
                helper.assertTrue(goal.canUse() && fixture.farm.equals(goal.targetBlock()),
                        "The installed live goal initially finds the real reachable crop field; " + fixture.diagnostic());
                // Probe through the actual entrypoint, then reset only the scan clock so the selector starts it itself.
                goal.stop(); fixture.ready();
                // NoAI freezes travel too, so it cannot settle a zero-velocity fixture before AI starts. Supply
                // the normal downward gravity velocity: the first real Entity.move collides with the floor
                // instead of clearing onGround before the following full selector update can find its path.
                fixture.mob.setDeltaMovement(0D, -.08D, 0D);
                // The next native entity tick increments tickCount; exercise its running-goals-only branch first.
                fixture.mob.tickCount += Math.floorMod(fixture.mob.tickCount + fixture.mob.getId(), 2);
                fixture.mob.setNoAi(false);
            }).thenIdle(160).thenExecute(() -> {
                helper.assertTrue(fixture.mob.getX() > initialX + 1D,
                        "The real movement controller advances toward the farm; " + fixture.diagnostic());
                helper.assertTrue(fixture.level.getBlockState(fixture.farm).is(Blocks.DIRT),
                        "Normal entity ticks and installed GoalSelector complete the one-cell raid; " + fixture.diagnostic());
                helper.assertTrue(fixture.drops().isEmpty(), "The live walking raid does not create free crop or soil loot");
            }).thenSucceed();
        } catch (RuntimeException | Error failure) { cleanup.run(); throw failure; }
    }

    @GameTest(template = "empty")
    public static void liveAttackTargetPreventsSelectionAndInterruptsAnExistingRaid(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            fixture.field(true, false); var goal = fixture.ready();
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.moveTo(fixture.origin.getX(), fixture.origin.getY(), fixture.origin.getZ() + 3);
            fixture.mob.setTarget(player);
            helper.assertTrue(!goal.canUse(), "Living prey takes priority over looking for farmland");
            fixture.mob.setTarget(null); helper.assertTrue(goal.canUse(), "The idle zombie may select its real farm path");
            goal.start(); fixture.mob.moveTo(fixture.farm.getX() + .5, fixture.farm.getY() + 1, fixture.farm.getZ() + .5);
            for (int tick = 0; tick < 20; tick++) goal.tick();
            fixture.mob.setTarget(player); goal.tick();
            helper.assertTrue(goal.targetBlock() == null && fixture.level.getBlockState(fixture.farm).is(Blocks.FARMLAND)
                    && fixture.level.getBlockState(fixture.farm.above()).is(Blocks.WHEAT),
                    "Acquiring a living target clears progress before either crop or soil changes");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedFieldAndOutOfRangeFieldDoNotStartOrForcePathMovement(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            fixture.field(true, false);
            for (Direction direction : Direction.Plane.HORIZONTAL) for (int height = 1; height <= 2; height++)
                fixture.set(fixture.farm.relative(direction).above(height), Blocks.STONE.defaultBlockState());
            fixture.set(fixture.farm.above(3), Blocks.STONE.defaultBlockState());
            helper.assertTrue(!fixture.ready().canUse(), "An actually enclosed field has no reachable destination path");
            helper.assertTrue(fixture.mob.getNavigation().isDone(), "Path probing cannot start movement through a wall");
        }
        try (var fixture = new Fixture(helper)) {
            fixture.set(fixture.origin.east(9).below(), soil(false));
            helper.assertTrue(!fixture.ready().canUse(), "A field beyond the eight-block horizontal scan is not targeted");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void disablingMobGriefingProtectsBothIdleSelectionAndInProgressWork(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            fixture.field(true, false); var goal = fixture.ready();
            fixture.grief(false); helper.assertTrue(!goal.canUse(), "Disabled mobGriefing blocks the real goal entrypoint");
            fixture.grief(true); helper.assertTrue(goal.canUse(), "Restored griefing permits the verified path");
            goal.start(); fixture.mob.moveTo(fixture.farm.getX() + .5, fixture.farm.getY() + 1, fixture.farm.getZ() + .5);
            for (int tick = 0; tick < 20; tick++) goal.tick(); fixture.grief(false); goal.tick();
            helper.assertTrue(goal.targetBlock() == null && fixture.level.getBlockState(fixture.farm).is(Blocks.FARMLAND)
                    && fixture.level.getBlockState(fixture.farm.above()).is(Blocks.WHEAT),
                    "Changing the rule mid-raid cancels before the final mutation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void soilCropAndFarmlandTrampleVetoesEachProtectTheEntireRaidTransaction(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            for (boolean protectCrop : new boolean[]{false, true}) {
                fixture.field(true, false);
                Consumer<LivingDestroyBlockEvent> cancel = event -> {
                    if (event.getEntity() == fixture.mob && event.getPos().equals(protectCrop ? fixture.farm.above() : fixture.farm))
                        event.setCanceled(true);
                };
                NeoForge.EVENT_BUS.addListener(LivingDestroyBlockEvent.class, cancel);
                try { fixture.work(); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
                fixture.assertIntact("LivingDestroyBlockEvent veto for " + (protectCrop ? "crop" : "soil"));
            }
            fixture.field(true, true);
            Consumer<BlockEvent.FarmlandTrampleEvent> cancel = event -> { if (event.getPos().equals(fixture.farm)) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(BlockEvent.FarmlandTrampleEvent.class, cancel);
            try { fixture.work(); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
            fixture.assertIntact("FarmlandTrampleEvent veto");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void existingSourceDiggerStillNeedsPreyAndActuallyDigsThePreysFarmlandSupport(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            BlockPos obstacle = fixture.origin.east(); fixture.set(obstacle, soil(false));
            fixture.set(obstacle.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
            var digger = R196DiggerGoal.first(fixture.mob);
            helper.assertTrue(digger != null, "Actual source watcher goal was installed by the entity join");
            fixture.mob.getPersistentData().putBoolean(R196MonsterAi.SMART, true);
            fixture.mob.setTarget(null); fixture.mob.getRandom().setSeed(0L);
            helper.assertTrue(!digger.canUse(), "R196 pursuit digging itself never scans fields without prey");
            // Non-player source pursuit retains the original support-digging branch.
            var prey = EntityType.COW.create(fixture.level);
            prey.moveTo(obstacle.getX() + .5, obstacle.getY() + 1, obstacle.getZ() + .5);
            fixture.mob.setTarget(prey); fixture.mob.getRandom().setSeed(0L);
            helper.assertTrue(digger.canUse(), "Source pursuit selects the real reachable support below elevated prey");
            helper.assertValueEqual(digger.targetBlock(), obstacle, "The selected pursuit obstacle is farmland, not a general raid target");
            int breakTicks = 40 + 10 * digger.stageDelay(obstacle);
            digger.start(); for (int tick = 0; tick < breakTicks - 1; tick++) digger.tick();
            helper.assertTrue(fixture.level.getBlockState(obstacle).is(Blocks.FARMLAND), "Blood-moon pursuit preserves the source staged break delay");
            digger.tick();
            helper.assertTrue(fixture.level.getBlockState(obstacle).isAir() && fixture.level.getBlockState(obstacle.above()).isAir(),
                    "Actual source pursuit digging removes farmland and updates its supported crop");
            helper.assertTrue(fixture.drops().stream().allMatch(item -> item.getItem().is(Items.DIRT)),
                    "Source support loss cannot generate wheat or seeds");
            helper.assertValueEqual(fixture.drops().stream().mapToInt(item -> item.getItem().getCount()).sum(), 1,
                    "Pursuit harvesting drops one dirt, distinct from deliberate flattening");
            digger.stop();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void playerOnAdjacentFarmlandUsesFasterActuallyAttackableStep(GameTestHelper helper) {
        try (var settings = PathfindingTestSettings.synchronous(); var fixture = new Fixture(helper)) {
            BlockPos obstacle=fixture.origin.east();fixture.set(obstacle,soil(false));
            fixture.set(obstacle.above(),Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7));
            var player=helper.makeMockPlayer(GameType.SURVIVAL);
            player.moveTo(obstacle.getX()+.5,obstacle.getY()+1,obstacle.getZ()+.5);fixture.mob.setTarget(player);
            var digger=R196DiggerGoal.first(fixture.mob);var plan=digger.pursuitPlan();
            helper.assertTrue(plan.completeWalkingRoute()&&!plan.choosesBreaking()&&plan.walkingTicks()<1_840D,
                    "The actual step onto adjacent farmland is quicker than forty plus ten times 180 digging ticks");
            var endpoint=plan.path().getEntityPosAtNode(fixture.mob,plan.path().getNodeCount()-1);
            fixture.mob.moveTo(endpoint);
            helper.assertTrue(SourceMeleeGoal.canStrike(fixture.mob,player),
                    "The real navigation endpoint passes source melee distance and physical collision rays");
            fixture.mob.moveTo(fixture.origin.getX()+.25,fixture.origin.getY(),fixture.origin.getZ()+.5);
            helper.assertFalse(digger.canUse(),"Faster player pursuit does not dig away the reachable farmland support");
            helper.assertTrue(fixture.level.getBlockState(obstacle).is(Blocks.FARMLAND)
                    &&fixture.level.getBlockState(obstacle.above()).is(Blocks.WHEAT),"Walking keeps both support and crop intact");
        }
        helper.succeed();
    }

    private static BlockState soil(boolean enriched) {
        return Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, enriched ? 7 : 0)
                .setValue(FoodAgriculture.FERTILIZED, enriched);
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper; final ServerLevel level; final BlockPos origin, farm;
        final Zombie mob; final long oldTime, oldHighestDay; final boolean oldGrief;
        final Map<BlockPos, BlockState> oldBlocks = new LinkedHashMap<>();
        final List<ItemEntity> before;
        Fixture(GameTestHelper helper) {
            this.helper = helper; level = helper.getLevel(); origin = helper.absolutePos(new BlockPos(3, 4, 3)); farm = origin.east(4).below();
            oldTime = level.getDayTime(); oldGrief = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
            oldHighestDay = BloodMoonInvasionData.get(level).highestDay();
            level.setDayTime(BLOOD_NIGHT); grief(true);
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, -1, -3), origin.offset(9, -1, 3))) set(pos, Blocks.STONE.defaultBlockState());
            // The walking fixture extends beyond the tiny template; do not inherit a neighbouring test's blocks.
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, 0, -3), origin.offset(9, 3, 3))) set(pos, Blocks.AIR.defaultBlockState());
            before = new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(12)));
            mob = EntityType.ZOMBIE.create(level); mob.moveTo(origin.getX() + .25, origin.getY(), origin.getZ() + .5);
            mob.setNoAi(true); mob.setNoGravity(true); mob.setOnGround(true); mob.setPersistenceRequired();
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET)); level.addFreshEntity(mob);
            mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        void set(BlockPos pos, BlockState state) {
            oldBlocks.putIfAbsent(pos.immutable(), level.getBlockState(pos)); level.setBlock(pos, state, 2);
        }
        void field(boolean crop, boolean enriched) {
            mob.setTarget(null); mob.moveTo(origin.getX() + .25, origin.getY(), origin.getZ() + .5);
            set(farm, soil(enriched)); set(farm.above(), crop ? Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7) : Blocks.AIR.defaultBlockState());
        }
        MonsterFarmlandGoal ready() {
            mob.tickCount += 1_000;
            return mob.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                    .filter(MonsterFarmlandGoal.class::isInstance).map(MonsterFarmlandGoal.class::cast).findFirst().orElseThrow();
        }
        void work() {
            var goal = ready(); helper.assertTrue(goal.canUse(), "Protected transaction still begins with a real valid path"); goal.start();
            mob.moveTo(farm.getX() + .5, farm.getY() + 1, farm.getZ() + .5);
            for (int tick = 0; tick < 40; tick++) goal.tick();
        }
        void assertIntact(String message) {
            helper.assertTrue(level.getBlockState(farm).is(Blocks.FARMLAND) && level.getBlockState(farm.above()).is(Blocks.WHEAT)
                    && drops().isEmpty(), message + " leaves crop, soil and drops untouched");
        }
        List<ItemEntity> drops() { return level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(12), item -> !before.contains(item)); }
        String diagnostic() {
            return "position=" + mob.position() + ", ticks=" + mob.tickCount + ", alive=" + mob.isAlive()
                    + ", id=" + mob.getId() + ", velocity=" + mob.getDeltaMovement()
                    + ", removed=" + mob.isRemoved() + ", NoAI=" + mob.isNoAi() + ", effectiveAI=" + mob.isEffectiveAi()
                    + ", onGround=" + mob.onGround() + ", ticking=" + level.isPositionEntityTicking(mob.blockPosition())
                    + ", grief=" + level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                    + ", soil=" + level.getBlockState(farm) + ", plant=" + level.getBlockState(farm.above())
                    + ", navigationDone=" + mob.getNavigation().isDone() + ", idleAllowed=" + MonsterFarmland.idleAndAllowed(mob)
                    + ", disabled=" + field(mob.goalSelector, "disabledFlags") + ", locked=" + field(mob.goalSelector, "lockedFlags")
                    + ", goal=" + mob.goalSelector.getAvailableGoals().stream()
                            .filter(wrapped -> wrapped.getGoal() instanceof MonsterFarmlandGoal)
                            .map(wrapped -> wrapped.isRunning() + ":" + ((MonsterFarmlandGoal)wrapped.getGoal()).targetBlock()
                                    + ":" + ((MonsterFarmlandGoal)wrapped.getGoal()).workTicks()
                                    + ":scans=" + field(wrapped.getGoal(), "scanSequence")
                                    + ":next=" + field(wrapped.getGoal(), "nextScanTick")).toList();
        }
        private static Object field(Object owner, String name) {
            try { var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
            catch (ReflectiveOperationException failure) { throw new AssertionError("Missing diagnostic field " + name, failure); }
        }
        void grief(boolean value) { level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(value, level.getServer()); }
        @Override public void close() {
            var goal = mob.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                    .filter(MonsterFarmlandGoal.class::isInstance).map(MonsterFarmlandGoal.class::cast).findFirst().orElse(null);
            if (goal != null) goal.stop(); var digger = R196DiggerGoal.first(mob); if (digger != null) digger.stop();
            mob.discard(); drops().forEach(ItemEntity::discard);
            oldBlocks.forEach((pos, state) -> level.setBlock(pos, state, 2)); level.setDayTime(oldTime); grief(oldGrief);
            BloodMoonInvasionData.get(level).restoreProgress(oldHighestDay);
        }
    }
}

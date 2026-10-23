package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.MonsterHunting;
import io.github.ningmeng123.mite.entity.NativeArachnidEvents;
import io.github.ningmeng123.mite.entity.R196Bat;
import io.github.ningmeng123.mite.entity.R196Cube;
import io.github.ningmeng123.mite.entity.SourceMonsterTargetMemory;
import io.github.ningmeng123.mite.entity.SourceMonsterTargeting;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual goal, tick, contact and save paths for the user-selected blood-night hunting policy. @author NingMeng123 */
@GameTestHolder("mite_hunting")
@PrefixGameTestTemplate(false)
public final class MonsterHuntingGameTests {
    private static final long ORDINARY_NIGHT = 18_000L;
    private static final long BLOOD_DAY = 31L * 24_000L + 6_000L;
    private static final long BLUE_NIGHT = 127L * 24_000L + 18_000L;

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final BloodMoonHuntingTestClock clock;
        final List<Mob> entities = new ArrayList<>();
        Fixture(GameTestHelper h) { this.h = h; clock = new BloodMoonHuntingTestClock(h.getLevel()); }
        <T extends Mob> T mob(EntityType<T> type, double x) {
            T mob = type.create(h.getLevel());
            var origin = h.absolutePos(new BlockPos(1, 5, 1));
            mob.moveTo(origin.getX() + .5D + x, origin.getY(), origin.getZ() + .5D, 0, 0);
            mob.setNoAi(true); mob.setNoGravity(true);
            h.getLevel().addFreshEntity(mob); entities.add(mob); return mob;
        }
        void time(long time) { h.getLevel().setDayTime(time); }
        @Override public void close() { entities.forEach(Mob::discard); clock.close(); }
    }

    @GameTest(template = "empty")
    public static void sourceAnimalAndVillagerGoalsOnlyAcquireOnActualBloodNights(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var zombie = f.mob(EntityType.ZOMBIE, 0);
            var cow = f.mob(EntityType.COW, 2);
            var villager = f.mob(EntityType.VILLAGER, 3);
            var animalGoal = new SourceMonsterTargeting.Nearest(zombie, Animal.class, 0, false);
            var villagerGoal = new SourceMonsterTargeting.Nearest(zombie, Villager.class, 0, false);
            for (long time : new long[]{ORDINARY_NIGHT, BLOOD_DAY, BLUE_NIGHT}) {
                f.time(time);
                h.assertFalse(animalGoal.canUse(), "Passive animals are not hunted outside an actual blood night: " + time);
                h.assertFalse(villagerGoal.canUse(), "Villagers are not hunted outside an actual blood night: " + time);
            }
            f.time(BloodMoonHuntingTestClock.BLOOD_NIGHT);
            h.assertTrue(animalGoal.canUse() && villagerGoal.canUse(), "Both original prey categories activate during a blood night");
            animalGoal.start(); h.assertTrue(zombie.getTarget() == cow, "The actual animal target is selected");
            animalGoal.stop(); villagerGoal.start(); h.assertTrue(zombie.getTarget() == villager, "The actual villager target is selected");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bloodNightEndingClearsHuntBeforeNextAiAndCannotRequeueIt(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var zombie = f.mob(EntityType.ZOMBIE, 0); f.mob(EntityType.VILLAGER, 2);
            var goal = new SourceMonsterTargeting.Nearest(zombie, Villager.class, 0, false);
            h.assertTrue(goal.canUse(), "Blood-night hunt starts"); goal.start();
            h.assertTrue(zombie.getTarget() != null, "Fixture has a real target before the transition");
            f.time(32L * 24_000L + 1L);
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(zombie));
            h.assertTrue(zombie.getTarget() == null && ((SourceMonsterTargetMemory)zombie).mite$retarget() == null,
                    "The pre-AI event clears both active and queued hunting targets");
            h.assertFalse(goal.canContinueToUse(), "The old source goal cannot continue across the transition");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyPendingPassiveHuntDoesNotRestoreOutsideBloodNight(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var zombie = f.mob(EntityType.ZOMBIE, 0); var cow = f.mob(EntityType.COW, 2);
            var memory = (SourceMonsterTargetMemory)zombie;
            // A version-one pre-update save has only a UUID, with no new PREY marker.
            memory.mite$pendingTarget(cow.getUUID()); zombie.tickCount = 10; f.time(ORDINARY_NIGHT);
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(zombie));
            h.assertTrue(zombie.getTarget() == null && memory.mite$pendingTarget() == null,
                    "A legacy animal hunt is discarded during an ordinary night");
            f.time(BloodMoonHuntingTestClock.BLOOD_NIGHT); memory.mite$pendingTarget(cow.getUUID());
            SourceMonsterTargeting.restorePendingTarget(zombie);
            h.assertTrue(zombie.getTarget() == cow, "The same legacy UUID can restore during a blood night");
            f.time(ORDINARY_NIGHT); NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(zombie));
            h.assertTrue(zombie.getTarget() == null, "A restored legacy hunt is remembered and ends with the blood night");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void savedBloodHuntDoesNotResumeAfterTimeChanges(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var zombie = f.mob(EntityType.ZOMBIE, 0); f.mob(EntityType.VILLAGER, 2);
            var goal = new SourceMonsterTargeting.Nearest(zombie, Villager.class, 0, false);
            h.assertTrue(goal.canUse(), "Save fixture starts a real hunt"); goal.start();
            var saved = new CompoundTag(); zombie.saveWithoutId(saved);
            var restored = EntityType.ZOMBIE.create(h.getLevel()); restored.load(saved); restored.setUUID(java.util.UUID.randomUUID());
            restored.setNoAi(true); restored.tickCount = 10; f.entities.add(restored); zombie.discard();
            h.getLevel().addFreshEntity(restored); f.time(ORDINARY_NIGHT);
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(restored));
            h.assertTrue(restored.getTarget() == null && ((SourceMonsterTargetMemory)restored).mite$pendingTarget() == null,
                    "Saved hunting provenance prevents a stale target from returning after a reload");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryVanillaMonsterNearestGoalsUseTheSameBloodNightGate(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var prey = f.mob(EntityType.VILLAGER, 2);
            for (EntityType<? extends PathfinderMob> type : List.of(EntityType.HUSK, EntityType.DROWNED, EntityType.STRAY,
                    EntityType.PILLAGER, EntityType.VINDICATOR)) {
                var owner = f.mob(type, 0);
                var goal = new NearestAttackableTargetGoal<>(owner, Villager.class, 0, false, false, entity -> entity == prey);
                f.time(ORDINARY_NIGHT); h.assertFalse(goal.canUse(), "Shared vanilla selection rejects ordinary-night prey: " + type);
                f.time(BloodMoonHuntingTestClock.BLOOD_NIGHT); h.assertTrue(goal.canUse(), "Shared vanilla selection permits blood-night prey: " + type);
                goal.start(); f.time(ORDINARY_NIGHT); NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(owner));
                h.assertTrue(owner.getTarget() == null && !goal.canContinueToUse(), "Neither the active target nor its fallback can survive the end: " + type);
                goal.stop(); owner.discard();
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void retaliationAndSourceCooperationStillWorkOnOrdinaryNights(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.time(ORDINARY_NIGHT); var attacker = f.mob(EntityType.VILLAGER, 2);
            var husk = f.mob(EntityType.HUSK, 0); husk.tickCount = 20; husk.setLastHurtByMob(attacker);
            var revenge = new HurtByTargetGoal(husk);
            h.assertTrue(revenge.canUse(), "Vanilla retaliation remains available"); revenge.start();
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(husk));
            h.assertTrue(husk.getTarget() == attacker, "Ordinary-night retaliation retains its target");
            var ghoul = (PathfinderMob)f.mob(MiteMobs.type(MobKind.GHOUL), 0);
            var peer = (PathfinderMob)f.mob(MiteMobs.type(MobKind.GHOUL), 1);
            ghoul.tickCount = 20; ghoul.setLastHurtByMob(attacker);
            var sourceRevenge = new SourceMonsterTargeting.Revenge(ghoul, true);
            h.assertTrue(sourceRevenge.canUse(), "Source retaliation remains available"); sourceRevenge.start();
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(peer));
            h.assertTrue(ghoul.getTarget() == attacker && peer.getTarget() == attacker,
                    "Same-source cooperation remains active outside blood nights");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void batsAndSlimeContactCannotKillPassiveMobsOutsideBloodNights(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var bat = (R196Bat)f.mob(MiteMobs.type(MobKind.VAMPIRE_BAT), 0);
            var cow = f.mob(EntityType.COW, 0); bat.setTarget(cow);
            f.time(ORDINARY_NIGHT); float health = cow.getHealth();
            h.assertTrue(!bat.preysUpon(cow) && !bat.tryContactAttack(cow) && cow.getHealth() == health,
                    "Bat scanning and contact attacks both reject passive ordinary-night prey");
            var slime = f.mob(EntityType.SLIME, 0); slime.setSize(1, true);
            R196Cube.vanillaContact(slime, cow); h.assertTrue(cow.getHealth() == health, "Native slime incidental contact cannot bypass hunting rules");
            f.time(BloodMoonHuntingTestClock.BLOOD_NIGHT);
            h.assertTrue(bat.preysUpon(cow) && bat.tryContactAttack(cow), "Bat blood-night feeding still deals real contact damage");
            cow.invulnerableTime = 0; health = cow.getHealth(); R196Cube.vanillaContact(slime, cow);
            h.assertTrue(cow.getHealth() < health, "Slime blood-night contact retains the original real damage path");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void spiderChickenHuntIsGatedAndPlayersRemainEligible(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var spider = f.mob(EntityType.CAVE_SPIDER, 0); var chicken = f.mob(EntityType.CHICKEN, 2);
            long seed = 0; while (new Random(seed).nextFloat() >= .01F) seed++;
            spider.tickCount = 1 - spider.getId() * 47;
            f.time(ORDINARY_NIGHT); spider.getRandom().setSeed(seed); NativeArachnidEvents.acquireTarget(spider);
            h.assertTrue(spider.getTarget() == null, "Even a successful chicken draw cannot hunt on an ordinary night");
            f.time(BloodMoonHuntingTestClock.BLOOD_NIGHT); spider.getRandom().setSeed(seed); NativeArachnidEvents.acquireTarget(spider);
            h.assertTrue(spider.getTarget() == chicken, "Blood-night chicken hunting retains the literal one-percent draw");
            f.time(ORDINARY_NIGHT); NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(spider));
            h.assertTrue(spider.getTarget() == null, "The spider drops its old chicken as soon as the blood night ends");
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            h.assertTrue(MonsterHunting.mayAttackPrey(spider, player), "Player hostility does not depend on the blood-night gate");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wardenDirectBrainAttackIsGatedAndEndsWithBloodNight(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var warden = f.mob(EntityType.WARDEN, 0); var prey = f.mob(EntityType.COW, 2);
            f.time(ORDINARY_NIGHT); warden.setAttackTarget(prey);
            h.assertFalse(warden.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET),
                    "The roar-to-attack direct brain path cannot start an ordinary-night hunt");
            f.time(BloodMoonHuntingTestClock.BLOOD_NIGHT); warden.setAttackTarget(prey);
            h.assertTrue(warden.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == prey,
                    "Blood-night roaring can assign the original real brain target");
            f.time(ORDINARY_NIGHT); NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(warden));
            h.assertFalse(warden.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET),
                    "A warden cannot retain its independent brain hunt after the blood night");
            warden.setLastHurtByMob(prey); warden.setAttackTarget(prey);
            h.assertTrue(warden.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == prey,
                    "A directly attacked warden can still retaliate during an ordinary night");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void vexOwnerCopyOnlyHuntsDuringBloodNightAndStillAssistsRetaliation(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var owner = f.mob(EntityType.EVOKER, 0); var vex = f.mob(EntityType.VEX, 1);
            var prey = f.mob(EntityType.VILLAGER, 3); vex.setOwner(owner); owner.setTarget(prey);
            var copy = vex.targetSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                    .filter(goal -> goal.getClass().getSimpleName().equals("VexCopyOwnerTargetGoal")).findFirst().orElseThrow();
            f.time(ORDINARY_NIGHT); h.assertFalse(copy.canUse(), "An idle vex cannot copy an ordinary-night passive victim");
            f.time(BloodMoonHuntingTestClock.BLOOD_NIGHT); h.assertTrue(copy.canUse(), "The actual owner-copy goal activates on blood nights");
            copy.start(); h.assertTrue(vex.getTarget() == prey, "The summoned vex has the actual selected victim");
            f.time(ORDINARY_NIGHT); NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(vex));
            h.assertTrue(vex.getTarget() == null && !copy.canContinueToUse(), "The independent vex hunt ends before its next AI update");
            owner.setLastHurtByMob(prey); owner.setTarget(prey);
            h.assertTrue(copy.canUse(), "Vexes can copy the summoner's genuine retaliation target during an ordinary night");
            copy.start(); NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(vex));
            h.assertTrue(vex.getTarget() == prey, "The summoner's defensive assistance is retained");
        }
        h.succeed();
    }
}

package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.SourceMonsterTargetMemory;
import io.github.ningmeng123.mite.entity.SourceMonsterTargeting;
import io.github.ningmeng123.mite.registry.MiteMobs;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Literal qy/re/qx/qa boundaries exercised through actual source entities and native save/load. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class MonsterTargetMeleeGameTests {
    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, double x, double y, double z) {
        T entity = type.create(h.getLevel());
        var origin = h.absolutePos(new BlockPos(1, 10, 1));
        entity.moveTo(origin.getX() + .5 + x, origin.getY() + y, origin.getZ() + .5 + z, 0, 0);
        entity.setNoAi(true); entity.setNoGravity(true);
        h.getLevel().addFreshEntity(entity);
        return entity;
    }
    private static PathfinderMob source(GameTestHelper h, MobKind kind) {
        return (PathfinderMob)mob(h, MiteMobs.type(kind), 0, 0, 0);
    }
    private static void wall(GameTestHelper h, Mob owner, net.minecraft.world.level.block.Block block) {
        var origin = owner.blockPosition();
        for (int y = 0; y < 4; y++) for (int z = -1; z <= 1; z++)
            h.getLevel().setBlock(origin.offset(1, y, z), block.defaultBlockState(), 3);
    }
    private static SourceMonsterTargeting.Nearest nearest(PathfinderMob owner, Class<? extends LivingEntity> type) {
        return owner.targetSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                .filter(SourceMonsterTargeting.Nearest.class::isInstance)
                .map(SourceMonsterTargeting.Nearest.class::cast).filter(goal -> goal.targetType() == type).findFirst().orElseThrow();
    }

    @GameTest(template = "empty")
    public static void sourceTargetFamiliesRetainDistinctPreyAndRevengePriorities(GameTestHelper h) {
        var ghoul = source(h, MobKind.GHOUL);
        var wight = source(h, MobKind.WIGHT);
        var elemental = source(h, MobKind.EARTH_ELEMENTAL);
        var skeleton = source(h, MobKind.LONGDEAD);
        SourceMonsterTargeting.install(ghoul); SourceMonsterTargeting.install(wight);
        SourceMonsterTargeting.install(elemental); SourceMonsterTargeting.install(skeleton);
        h.assertTrue(ghoul.targetSelector.getAvailableGoals().stream().filter(w -> w.getGoal() instanceof SourceMonsterTargeting.Nearest).count() == 3,
                "Ghoul source constructor targets players, villagers and animals");
        h.assertTrue(wight.targetSelector.getAvailableGoals().stream().filter(w -> w.getGoal() instanceof SourceMonsterTargeting.Nearest).count() == 1,
                "Wight has player seeking only; its villager melee task does not imply a villager target selector");
        h.assertTrue(elemental.targetSelector.getAvailableGoals().stream().filter(w -> w.getGoal() instanceof SourceMonsterTargeting.Nearest).count() == 2
                        && skeleton.targetSelector.getAvailableGoals().stream().filter(w -> w.getGoal() instanceof SourceMonsterTargeting.Nearest).count() == 1,
                "Elementals keep two target categories while source skeletons seek players only");
        ghoul.discard(); wight.discard(); elemental.discard(); skeleton.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void enabledDiggerCanAcquireThroughWallButSwordHoldingZombieCannot(GameTestHelper h) {
        var zombie = mob(h, EntityType.ZOMBIE, 0, 0, 0);
        zombie.getPersistentData().putBoolean("mite_zombie_smart_035", true);
        var target = mob(h, EntityType.VILLAGER, 3, 0, 0);
        wall(h, zombie, Blocks.STONE);
        var goal = new SourceMonsterTargeting.Nearest(zombie, Villager.class, 0, true);
        h.assertTrue(goal.canUse(), "re skips sight while the watcher is allowed to dig");
        zombie.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        h.assertFalse(goal.canUse(), "Holding a source digging-blocking sword restores the actual sight requirement");
        zombie.discard(); target.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinarySightTargetPersistsForSixtyUnseenUpdatesThenStops(GameTestHelper h) {
        var wight = source(h, MobKind.WIGHT);
        var target = mob(h, EntityType.VILLAGER, 3, 0, 0);
        var goal = new SourceMonsterTargeting.Nearest(wight, Villager.class, 0, true);
        h.assertTrue(goal.canUse(), "Unobstructed source target is initially visible"); goal.start();
        wall(h, wight, Blocks.STONE);
        for (int i = 0; i < 60; i++) h.assertTrue(goal.canContinueToUse(), "The first sixty source unseen updates retain the target");
        h.assertFalse(goal.canContinueToUse(), "Only the sixty-first unseen update ends tracking");
        goal.stop(); wight.discard(); target.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void renewedSightResetsTheUnseenCounter(GameTestHelper h) {
        var wight = source(h, MobKind.WIGHT);
        var target = mob(h, EntityType.VILLAGER, 3, 0, 0);
        var goal = new SourceMonsterTargeting.Nearest(wight, Villager.class, 0, true);
        h.assertTrue(goal.canUse(), "Fixture starts visible"); goal.start(); wall(h, wight, Blocks.STONE);
        for (int i = 0; i < 60; i++) h.assertTrue(goal.canContinueToUse(), "First sight loss remains inside source grace");
        wall(h, wight, Blocks.AIR); h.assertTrue(goal.canContinueToUse(), "Sight returns for one update"); wall(h, wight, Blocks.STONE);
        for (int i = 0; i < 60; i++) h.assertTrue(goal.canContinueToUse(), "Restored sight grants a fresh sixty updates");
        h.assertFalse(goal.canContinueToUse(), "Fresh counter also expires at sixty-one");
        goal.stop(); wight.discard(); target.discard(); h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void currentRangeIncludesBoundaryAndRejectsTheNextPosition(GameTestHelper h) {
        var wight = source(h, MobKind.WIGHT);
        var target = mob(h, EntityType.VILLAGER, 40, 0, 0);
        var goal = new SourceMonsterTargeting.Nearest(wight, Villager.class, 0, false);
        var level = h.getLevel();
        var chunks = java.util.stream.Stream.of(wight.chunkPosition(), target.chunkPosition()).distinct().toList();
        var ticket = net.minecraft.server.level.TicketType.<Integer>create(
                "mite_range_boundary_test", java.util.Comparator.naturalOrder(), 200);
        int ticketKey = wight.getId();
        Runnable cleanup = () -> {
            goal.stop(); wight.discard(); target.discard();
            for (var chunk : chunks) level.getChunkSource().removeRegionTicket(ticket, chunk, 2, ticketKey);
        };
        try {
            for (var chunk : chunks) level.getChunkSource().addRegionTicket(ticket, chunk, 2, ticketKey);
            // The forty-block target can lie outside the template's ticking chunks.
            // Wait for the native query used by Nearest, rather than changing its range.
            h.runAfterDelay(199, cleanup);
            h.startSequence().thenWaitUntil(() -> {
                h.assertTrue(level.isPositionEntityTicking(wight.blockPosition())
                        && level.isPositionEntityTicking(target.blockPosition()), "Both range fixture chunks reach entity-ticking status");
                h.assertTrue(level.getEntity(wight.getUUID()) == wight && level.getEntity(target.getUUID()) == target,
                        "Both live range fixtures are indexed by their actual UUIDs");
                h.assertTrue(level.getEntitiesOfClass(Villager.class, wight.getBoundingBox().inflate(40, 6, 40),
                        entity -> entity == target).contains(target), "The original nearest-target query can observe the forty-block fixture");
            }).thenExecute(() -> {
                try {
                    h.assertTrue(goal.canUse(), "R196 wight follow range is forty"); goal.start();
                    h.assertTrue(wight.getTarget() == target, "Boundary assertions use the actual forty-block target");
                    h.assertTrue(goal.canContinueToUse(), "Exactly forty blocks is within re range");
                    target.setPos(wight.getX() + 40.01, target.getY(), target.getZ());
                    h.assertFalse(goal.canContinueToUse(), "Any true distance above forty ends target tracking");
                } finally { cleanup.run(); }
            }).thenSucceed();
        } catch (RuntimeException | Error failure) { cleanup.run(); throw failure; }
    }

    @GameTest(template = "empty")
    public static void playerHitDoublesRangeAfterHurtAnimationHasEnded(GameTestHelper h) {
        var zombie = mob(h, EntityType.ZOMBIE, 0, 0, 0);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        h.assertTrue(SourceMonsterTargeting.effectiveRange(zombie) == 40D, "Literal source zombie follow range before a player hit");
        zombie.hurt(zombie.damageSources().playerAttack(player), 1F);
        zombie.hurtTime = 0;
        h.assertTrue(SourceMonsterTargeting.effectiveRange(zombie) == 80D, "100-tick recently-hit memory remains independent of hurt animation");
        zombie.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void archerHighPlayerFallbackUsesTwentyLayerCeiling(GameTestHelper h) {
        var skeleton = mob(h, EntityType.SKELETON, 0, 0, 0);
        skeleton.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        // GameTestHelper's deprecated server mock hard-overrides isCreative() to true even after setGameMode.
        // A real registered survival ServerPlayer is required to test hostile acquisition without weakening creative exclusion.
        try (var fixture = new SurvivalPlayer(h)) {
            var player = fixture.player;
            player.moveTo(skeleton.getX() + 2, skeleton.getY() + 20, skeleton.getZ());
            h.assertTrue(!player.isCreative() && !player.isSpectator(), "Actual survival player is an attackable fixture");
            var goal = new SourceMonsterTargeting.Nearest(skeleton, Player.class, 0, true);
            h.assertTrue(goal.canUse(), "Source bow skeleton fallback can find a visible player twenty levels above");
            player.moveTo(skeleton.getX() + 2, skeleton.getY() + 20.01, skeleton.getZ());
            h.assertFalse(goal.canUse(), "The fallback does not search above twenty");
        }
        skeleton.discard(); h.succeed();
    }

    private static final class SurvivalPlayer implements AutoCloseable {
        final ServerPlayer player;
        final EmbeddedChannel channel;
        SurvivalPlayer(GameTestHelper h) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-archer-035"), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
        }
        @Override public void close() { player.server.getPlayerList().remove(player); channel.finishAndReleaseAll(); }
    }

    @GameTest(template = "empty")
    public static void sourceClassCooperationDoesNotConfuseWightsWithGhouls(GameTestHelper h) {
        var ghoul = source(h, MobKind.GHOUL);
        var peer = source(h, MobKind.GHOUL);
        var other = source(h, MobKind.WIGHT);
        var attacker = mob(h, EntityType.VILLAGER, 3, 0, 0);
        ghoul.tickCount = 17; ghoul.setLastHurtByMob(attacker);
        var revenge = new SourceMonsterTargeting.Revenge(ghoul, true);
        h.assertTrue(revenge.canUse(), "New source revenge timestamp starts a target task"); revenge.start();
        h.assertTrue(peer.getTarget() == attacker && other.getTarget() == null,
                "qx cooperation follows original runtime class rather than shared modern R196Undead Java class");
        ghoul.discard(); peer.discard(); other.discard(); attacker.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceRevengeDoesNotReplacePeersExistingTarget(GameTestHelper h) {
        var ghoul = source(h, MobKind.GHOUL);
        var peer = source(h, MobKind.GHOUL);
        var attacker = mob(h, EntityType.VILLAGER, 3, 0, 0);
        var original = mob(h, EntityType.COW, 2, 0, 0);
        peer.setTarget(original); ghoul.tickCount = 19; ghoul.setLastHurtByMob(attacker);
        var revenge = new SourceMonsterTargeting.Revenge(ghoul, true);
        h.assertTrue(revenge.canUse(), "New attacker is suitable"); revenge.start();
        h.assertTrue(peer.getTarget() == original, "Source qx only recruits idle peers");
        ghoul.discard(); peer.discard(); attacker.discard(); original.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void externallyAssignedTargetSurvivesNearestGoalClassAndRandomGate(GameTestHelper h) {
        var wight = source(h, MobKind.WIGHT);
        var target = mob(h, EntityType.VILLAGER, 3, 0, 0);
        wight.setTarget(target);
        var goal = new SourceMonsterTargeting.Nearest(wight, Player.class, 100000, true);
        h.assertTrue(goal.canUse(), "Source og.AI_retarget is consumed before class selection and random roll"); goal.start();
        h.assertTrue(wight.getTarget() == target && ((SourceMonsterTargetMemory)wight).mite$retarget() == null,
                "The explicit externally assigned target remains active and queued reference is consumed once");
        goal.stop(); wight.discard(); target.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledNeoForgeTargetDoesNotLeaveAnAiRetargetReference(GameTestHelper h) {
        var wight = source(h, MobKind.WIGHT);
        var target = mob(h, EntityType.VILLAGER, 3, 0, 0);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent> cancel = event -> {
            if (event.getEntity() == wight) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            wight.setTarget(target);
            h.assertTrue(wight.getTarget() == null && ((SourceMonsterTargetMemory)wight).mite$retarget() == null,
                    "Target cancellation occurs before both actual assignment and the source AI_retarget bridge");
        } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        wight.discard(); target.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void wholeNbtTargetRestoresOnlyAtRawTenTickBoundary(GameTestHelper h) {
        var zombie = mob(h, EntityType.ZOMBIE, 0, 0, 0);
        var target = mob(h, EntityType.VILLAGER, 3, 0, 0); zombie.setTarget(target);
        var tag = new CompoundTag(); zombie.saveWithoutId(tag);
        h.assertTrue(tag.getCompound(SourceMonsterTargeting.SAVE).getUUID("target").equals(target.getUUID()), "Real Mob save serializes active target UUID");
        var restored = EntityType.ZOMBIE.create(h.getLevel()); restored.load(tag); restored.setUUID(UUID.randomUUID());
        h.getLevel().addFreshEntity(restored); restored.tickCount = 9;
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(restored));
        h.assertTrue(restored.getTarget() == null, "No lookup before raw tick ten");
        restored.tickCount = 10; NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(restored));
        h.assertTrue(restored.getTarget() == target && ((SourceMonsterTargetMemory)restored).mite$pendingTarget() == null,
                "Nearby living target resolves through the actual registered tick subscriber");
        zombie.discard(); restored.discard(); target.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void targetUuidDoesNotResolveOutsideSourceNearbyBounds(GameTestHelper h) {
        var zombie = mob(h, EntityType.ZOMBIE, 0, 0, 0);
        var target = mob(h, EntityType.VILLAGER, 33, 0, 0);
        try {
            var bounds = zombie.getBoundingBox().inflate(32, 8, 32);
            var memory = (SourceMonsterTargetMemory)zombie; memory.mite$pendingTarget(target.getUUID()); zombie.tickCount = 10;
            h.assertFalse(bounds.intersects(target.getBoundingBox()), "The horizontal fixture is geometrically outside the source nearby box");
            SourceMonsterTargeting.restorePendingTarget(zombie);
            h.assertTrue(zombie.getTarget() == null && memory.mite$pendingTarget() != null, "UUID lookup is restricted to bbox+32,+8,+32, not all loaded world entities");
            target.setPos(zombie.getX(), zombie.getY() + 11, zombie.getZ()); zombie.tickCount = 20;
            h.assertFalse(bounds.intersects(target.getBoundingBox()), "The vertical fixture is geometrically outside the source nearby box");
            SourceMonsterTargeting.restorePendingTarget(zombie);
            h.assertTrue(zombie.getTarget() == null, "A target beyond the vertical nearby box also stays unresolved");
            // The test grid may put the owner at local X15.5. A fixed +3 crosses into a freshly
            // loaded chunk whose entity section is still hidden from native nearby queries.
            double nearbyX = (zombie.blockPosition().getX() & ~15) + 8.5D;
            target.setPos(nearbyX, zombie.getY(), zombie.getZ()); zombie.tickCount = 30;
            h.assertTrue(target.chunkPosition().equals(zombie.chunkPosition()), "The positive fixture stays inside its already accessible owner chunk");
            h.assertTrue(h.getLevel().getEntity(target.getUUID()) == target && bounds.intersects(target.getBoundingBox()),
                    "The live pending UUID has returned to a visible entity section inside the source nearby bounds");
            h.assertTrue(h.getLevel().getEntitiesOfClass(LivingEntity.class, bounds, entity -> entity == target).contains(target),
                    "The native nearby entity query can actually observe the positive-control target before AI restoration");
            SourceMonsterTargeting.restorePendingTarget(zombie);
            h.assertTrue(zombie.getTarget() == target && memory.mite$pendingTarget() == null,
                    "A still-pending target can later reenter the nearby bounds");
        } finally { zombie.discard(); target.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unresolvedTargetExpiresOnRawOneThousandBoundary(GameTestHelper h) {
        var zombie = mob(h, EntityType.ZOMBIE, 0, 0, 0);
        var memory = (SourceMonsterTargetMemory)zombie; memory.mite$pendingTarget(UUID.randomUUID());
        zombie.tickCount = 990; SourceMonsterTargeting.restorePendingTarget(zombie);
        h.assertTrue(memory.mite$pendingTarget() != null, "Missing target remains pending before one thousand");
        zombie.tickCount = 1000; SourceMonsterTargeting.restorePendingTarget(zombie);
        h.assertTrue(memory.mite$pendingTarget() == null, "Source og drops unresolved target after the thousand-tick lookup");
        zombie.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceMeleeUsesOnePointFiveAndHeldToolReach(GameTestHelper h) {
        var zombie = mob(h, EntityType.ZOMBIE, 0, 0, 0);
        var target = mob(h, EntityType.VILLAGER, 1.5, 0, 0);
        h.assertTrue(SourceMeleeGoal.canStrike(zombie, target), "Unarmed source reach includes exactly 1.5");
        target.setPos(zombie.getX() + 1.51, target.getY(), target.getZ());
        h.assertFalse(SourceMeleeGoal.canStrike(zombie, target), "Unarmed source reach rejects 1.51");
        zombie.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        target.setPos(zombie.getX() + 1.94, target.getY(), target.getZ());
        h.assertTrue(SourceMeleeGoal.canStrike(zombie, target), "Source sword +.75 reach contributes only .75*.6 to mob reach");
        target.setPos(zombie.getX() + 1.96, target.getY(), target.getZ());
        h.assertFalse(SourceMeleeGoal.canStrike(zombie, target), "Sword mob reach is 1.95, not full player tool reach");
        zombie.discard(); target.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceMeleeChecksTrueDistanceToTargetFeet(GameTestHelper h) {
        var zombie = mob(h, EntityType.ZOMBIE, 0, 0, 0);
        var target = mob(h, EntityType.VILLAGER, 0, 1.6, 0);
        h.assertFalse(SourceMeleeGoal.canStrike(zombie, target), "Overlapping body height does not bypass source 1.5 true foot distance");
        zombie.discard(); target.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void transparentVisionDoesNotPermitPhysicalMeleeThroughGlass(GameTestHelper h) {
        var zombie = mob(h, EntityType.ZOMBIE, 0, 0, 0);
        var target = mob(h, EntityType.VILLAGER, 1.5, 0, 0);
        wall(h, zombie, Blocks.GLASS);
        h.assertTrue(SourceMonsterTargeting.canSee(zombie, target, false), "R196 vision ignores ordinary glass");
        h.assertFalse(SourceMeleeGoal.canStrike(zombie, target), "All three physical attack rays remain blocked by glass");
        zombie.discard(); target.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void tintedGlassRetainsItsModernSightBlockingBehavior(GameTestHelper h) {
        var wight = source(h, MobKind.WIGHT);
        var target = mob(h, EntityType.VILLAGER, 3, 0, 0); wall(h, wight, Blocks.TINTED_GLASS);
        h.assertFalse(SourceMonsterTargeting.canSee(wight, target, false), "Modern opaque tinted glass is not included in R196 plain-glass policy");
        wight.discard(); target.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void twoLeafLayersAccumulateFiftyImpedanceInsteadOfRandomChance(GameTestHelper h) {
        var wight = source(h, MobKind.WIGHT);
        var target = mob(h, EntityType.VILLAGER, 4, 0, 0); wall(h, wight, Blocks.OAK_LEAVES);
        h.assertTrue(SourceMonsterTargeting.canSee(wight, target, false), "One foliage block adds fifty of one hundred source impedance");
        var origin = wight.blockPosition();
        for (int y = 0; y < 4; y++) for (int z = -1; z <= 1; z++)
            h.getLevel().setBlock(origin.offset(2, y, z), Blocks.OAK_LEAVES.defaultBlockState(), 3);
        h.assertFalse(SourceMonsterTargeting.canSee(wight, target, false), "A second leaf block brings the ray to one hundred impedance");
        h.assertTrue(SourceMonsterTargeting.canSee(wight, target, true), "Source previous-target policy can explicitly ignore both leaf layers");
        wight.discard(); target.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void physicalMeleeAppliesOnlyOncePerTwentyGoalTicks(GameTestHelper h) {
        var zombie = mob(h, EntityType.ZOMBIE, 0, 0, 0);
        var target = mob(h, EntityType.COW, 1.2, 0, 0); zombie.setTarget(target);
        var goal = new SourceMeleeGoal(zombie, 1, false);
        float health = target.getHealth(); goal.tick(); float first = target.getHealth();
        h.assertTrue(first < health && goal.attackCooldown() == 20, "Actual first source melee hit starts literal twenty cooldown");
        for (int i = 0; i < 19; i++) { target.invulnerableTime = 0; goal.tick(); }
        h.assertTrue(target.getHealth() == first && goal.attackCooldown() == 1, "Nineteen updates do not attack a second time");
        target.invulnerableTime = 0; goal.tick();
        h.assertTrue(target.getHealth() < first && goal.attackCooldown() == 20, "Twentieth update permits the next actual hit");
        zombie.discard(); target.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void meleeClassFilterAndDiggingTakeoverUseSourceEntryPoints(GameTestHelper h) {
        var zombie = mob(h, EntityType.ZOMBIE, 0, 0, 0);
        var cow = mob(h, EntityType.COW, 1.2, 0, 0); zombie.setTarget(cow);
        var playerOnly = new SourceMeleeGoal(zombie, Player.class, 1, false);
        h.assertFalse(playerOnly.canUse(), "Source qa player task does not activate for an animal");
        var animals = new SourceMeleeGoal(zombie, Animal.class, 1, true);
        float before = cow.getHealth();
        h.assertTrue(animals.tickWhenDigging() && cow.getHealth() < before,
                "An immediately available strike interrupts higher-priority digging through the actual qa bridge");
        h.assertFalse(animals.tickWhenDigging(), "The same digging check respects the new attack cooldown");
        zombie.discard(); cow.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void nearbyFallingBlockSuppressesMeleeWithoutInventedDamage(GameTestHelper h) {
        var zombie = mob(h, EntityType.ZOMBIE, 0, 0, 0);
        var cow = mob(h, EntityType.COW, 1.2, 0, 0); zombie.setTarget(cow);
        BlockPos above = zombie.blockPosition().above(2); h.getLevel().setBlock(above, Blocks.SAND.defaultBlockState(), 3);
        var falling = FallingBlockEntity.fall(h.getLevel(), above, Blocks.SAND.defaultBlockState()); falling.setNoGravity(true);
        var goal = new SourceMeleeGoal(zombie, 1, false);
        h.assertFalse(goal.canUse(), "Source qa avoids attack activation under a nearby falling block");
        h.assertTrue(goal.suppressionTicks() == 10 && goal.attackCooldown() == 10, "Both source attack and suppression timers are at least ten");
        float health = cow.getHealth(); for (int i = 0; i < 10; i++) goal.tick();
        h.assertTrue(cow.getHealth() == health, "Suppression does not accidentally execute melee damage");
        falling.discard(); zombie.discard(); cow.discard(); h.succeed();
    }
}

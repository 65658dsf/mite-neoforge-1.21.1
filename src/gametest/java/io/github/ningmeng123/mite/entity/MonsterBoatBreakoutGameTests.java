package io.github.ningmeng123.mite.entity;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real passenger ticks, staged escape and native boat protections/drops. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class MonsterBoatBreakoutGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_boat_breakout_test", Integer::compareTo);
    private MonsterBoatBreakoutGameTests() {}

    @GameTest(template="empty", batch="boat_breakout_live", timeoutTicks=650)
    public static void aTargetlessZombieBreaksItsBoatAfterRealPassengerWorkThenNaturallyAttacks(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.ZOMBIE, false);
        f.begin().thenWaitUntil(() -> h.assertTrue(f.progress() != null, "Real passenger Post events start the work"))
                .thenIdle(30).thenExecute(() -> {
                    h.assertTrue(f.mob.tickCount > f.mountedAt + 20 && f.mob.getTarget() == null,
                            "A targetless monster receives real native passenger ticks");
                    h.assertTrue(f.progress().ridingTicks() < MonsterBoatBreakout.WAIT_TICKS && f.progress().stages() == 0,
                            "The initial forty-tick wait has not been replaced by immediate damage");
                    h.assertTrue(f.boat.isAlive() && f.boat.getDamage() == 0F && f.damageAttempts == 0,
                            "The boat stays undamaged throughout the initial wait");
                }).thenWaitUntil(() -> h.assertTrue(f.progress() != null && f.progress().stages() == 10,
                        "Ten real swing stages complete before the final native hit"))
                .thenExecute(() -> h.assertTrue(f.boat.isAlive() && f.boat.getDamage() == 0F && f.damageAttempts == 0,
                        "Intermediate work stages cannot prematurely destroy the boat"))
                .thenWaitUntil(() -> h.assertTrue(f.boat.isRemoved(), "The eleventh work stage destroys the actual boat"))
                .thenExecute(() -> {
                    h.assertTrue(f.mob.tickCount - f.mountedAt >= MonsterBoatBreakout.WAIT_TICKS
                                    + (MonsterBoatBreakout.BREAK_STAGES - 1) * MonsterBoatBreakout.STAGE_INTERVAL_TICKS,
                            "Destruction follows the full native-tick work schedule");
                    h.assertTrue(!f.mob.isPassenger() && f.damageAttempts == 1 && f.mobAttackSource,
                            "One ordinary mobAttack hit destroys the boat and native removal releases its passenger");
                    h.assertValueEqual(f.items(Items.OAK_BOAT), 1, "The normal boat item drops exactly once");
                    f.player.setGameMode(GameType.SURVIVAL);
                }).thenWaitUntil(() -> h.assertTrue(f.hits > 0,
                        "The released monster resumes natural AI and physically reaches and damages its survival player"))
                .thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="empty", batch="boat_breakout_live", timeoutTicks=350)
    public static void aChestBoatDropsItsOwnItemAndInventoryExactlyOnce(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.VINDICATOR, true);
        ChestBoat chest = (ChestBoat)f.boat;
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        chest.setItem(1, new ItemStack(Items.DIRT, 3));
        f.begin().thenWaitUntil(() -> h.assertTrue(chest.isRemoved(), "Real passenger work destroys the chest boat"))
                .thenExecute(() -> {
                    h.assertTrue(!f.mob.isPassenger() && f.damageAttempts == 1 && f.mobAttackSource,
                            "Native chest-boat damage releases its monster without a synthetic discard");
                    h.assertValueEqual(f.items(Items.OAK_CHEST_BOAT), 1, "One chest-boat item drops");
                    h.assertValueEqual(f.items(Items.DIAMOND), 3, "The original diamond count drops exactly once");
                    h.assertValueEqual(f.items(Items.DIRT), 3, "The original legal dirt stack drops exactly once");
                    h.assertTrue(chest.isEmpty(), "Normal container destruction consumes its stored inventory");
                    f.close();
                }).thenSucceed();
    }

    @GameTest(template="empty", batch="boat_breakout_reset", timeoutTicks=550)
    public static void dismountingRemountingAndChangingBoatsResetAllWork(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.VINDICATOR, false);
        Boat second = f.extraBoat(f.origin.east(4));
        f.begin().thenWaitUntil(() -> h.assertTrue(f.progress() != null && f.progress().stages() >= 3,
                        "The first boat accumulates real partial work"))
                .thenExecute(() -> {
                    f.mob.stopRiding();
                    h.assertTrue(f.progress() == null, "Accepted dismount clears progress immediately");
                    h.assertTrue(f.mob.startRiding(f.boat, true), "The monster can normally remount the same boat");
                    h.assertTrue(f.progress() == null, "A same-tick remount does not revive old work");
                }).thenIdle(30).thenExecute(() -> h.assertTrue(f.progress() != null && f.progress().stages() == 0,
                        "Remounting repeats the initial wait rather than resuming old stages"))
                .thenWaitUntil(() -> h.assertTrue(f.progress() != null && f.progress().stages() >= 2,
                        "The remounted boat begins a fresh work cycle"))
                .thenExecute(() -> {
                    h.assertTrue(f.mob.startRiding(second, true), "The passenger switches through the normal mounting API");
                    h.assertTrue(f.progress() == null && f.boat.getDamage() == 0F,
                            "Changing boat discards previous work without damaging the old boat");
                }).thenIdle(30).thenExecute(() -> h.assertTrue(second.isAlive() && f.progress() != null && f.progress().stages() == 0,
                        "The replacement boat also gets its full initial wait"))
                .thenWaitUntil(() -> h.assertTrue(second.isRemoved(), "Fresh completed work can break the replacement boat"))
                .thenExecute(() -> { h.assertTrue(f.boat.isAlive(), "The previous boat remains intact"); f.close(); }).thenSucceed();
    }

    @GameTest(template="empty", batch="boat_breakout_griefing", timeoutTicks=600)
    public static void theMobGriefingRuleAndItsProtectionHookPauseAndResetDestruction(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.ZOMBIE, false);
        Consumer<EntityMobGriefingEvent> deny = event -> { if (event.getEntity() == f.mob) event.setCanGrief(false); };
        f.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(deny));
        f.begin().thenWaitUntil(() -> h.assertTrue(f.progress() != null && f.progress().stages() >= 2,
                        "The monster first demonstrates real partial work"))
                .thenExecute(() -> f.griefing(false)).thenIdle(160).thenExecute(() -> {
                    h.assertTrue(f.boat.isAlive() && f.progress() == null && f.damageAttempts == 0,
                            "mobGriefing=false stops and resets the work without applying boat damage");
                    NeoForge.EVENT_BUS.addListener(EntityMobGriefingEvent.class, deny); f.griefing(true);
                }).thenIdle(160).thenExecute(() -> {
                    h.assertTrue(f.boat.isAlive() && f.progress() == null && f.damageAttempts == 0,
                            "The standard entity griefing protection hook can also deny destruction");
                    NeoForge.EVENT_BUS.unregister(deny);
                }).thenWaitUntil(() -> h.assertTrue(f.boat.isRemoved(), "Removing protection permits a fresh normal break cycle"))
                .thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="empty", batch="boat_breakout_protection", timeoutTicks=650)
    public static void invulnerableAndHookProtectedBoatsSurviveNormalMobDamageAttempts(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.VINDICATOR, false);
        Consumer<EntityInvulnerabilityCheckEvent> deny = event -> {
            if (event.getEntity() == f.boat && event.getSource().getEntity() == f.mob) event.setInvulnerable(true);
        };
        f.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(deny));
        f.boat.setInvulnerable(true);
        f.begin().thenIdle(160).thenExecute(() -> {
            h.assertTrue(f.boat.isAlive() && f.progress() == null && f.damageAttempts == 0,
                    "A genuinely invulnerable boat is never damaged or worked through");
            f.boat.setInvulnerable(false); NeoForge.EVENT_BUS.addListener(EntityInvulnerabilityCheckEvent.class, deny);
        }).thenIdle(160).thenExecute(() -> {
            h.assertTrue(f.boat.isAlive() && f.boat.getDamage() == 0F && f.damageAttempts >= 1,
                    "Normal Boat.hurt reaches the protection event, whose refusal leaves the boat intact");
            NeoForge.EVENT_BUS.unregister(deny);
        }).thenWaitUntil(() -> h.assertTrue(f.boat.isRemoved(), "Allowed native damage can eventually release the passenger"))
                .thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="empty", batch="boat_breakout_ignored", timeoutTicks=300)
    public static void animalsPlayersAndExplicitNoAiMonstersDoNotBreakTheirBoats(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.ZOMBIE, false);
        Boat animalBoat = f.extraBoat(f.origin.offset(0, 0, 3));
        Boat playerBoat = f.extraBoat(f.origin.offset(4, 0, 3));
        Mob cow = f.extraMob(EntityType.COW, f.origin.offset(0, 0, 3));
        f.begin().thenExecute(() -> {
            f.mob.setNoAi(true);
            h.assertTrue(cow.getVehicle() == animalBoat || cow.startRiding(animalBoat, true), "The actual animal boards its boat");
            h.assertTrue(f.player.startRiding(playerBoat, true), "The actual player boards through normal controls");
        }).thenIdle(180).thenExecute(() -> {
            h.assertTrue(f.boat.isAlive() && animalBoat.isAlive() && playerBoat.isAlive(),
                    "Animals, players and NoAI entities keep their boats intact");
            h.assertTrue(cow.tickCount > 100 && f.player.tickCount > 100 && f.damageAttempts == 0,
                    "This is real passenger ticking, not a frozen test world");
            h.assertTrue(f.progress() == null && MonsterBoatBreakout.progress(cow) == null,
                    "Ignored passengers never accumulate destruction work");
            f.close();
        }).thenSucceed();
    }

    @GameTest(template="empty", batch="boat_breakout_drop_rule", timeoutTicks=350)
    public static void nativeEntityDropRulesStillControlTheDestroyedOrdinaryBoat(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.VINDICATOR, false);
        f.begin().thenExecute(() -> f.drops(false))
                .thenWaitUntil(() -> h.assertTrue(f.boat.isRemoved(), "The boat still uses its normal destruction path"))
                .thenExecute(() -> {
                    h.assertTrue(!f.mob.isPassenger() && f.damageAttempts == 1, "Destruction releases the passenger normally");
                    h.assertValueEqual(f.items(Items.OAK_BOAT), 0, "doEntityDrops=false suppresses the native ordinary boat item");
                    f.close();
                }).thenSucceed();
    }

    @GameTest(template="empty", batch="boat_road_live", timeoutTicks=750)
    public static void aWalkingSourceZombieWorksThroughTheOnlyBoatRoadblockThenActuallyAttacks(GameTestHelper h) {
        new RoadFixture(h, EntityType.ZOMBIE, true).breakAndAttack();
    }

    @GameTest(template="empty", batch="boat_road_live", timeoutTicks=750)
    public static void aWalkingNativeVindicatorWorksThroughTheOnlyBoatRoadblockThenActuallyAttacks(GameTestHelper h) {
        new RoadFixture(h, EntityType.VINDICATOR, true).breakAndAttack();
    }

    @GameTest(template="empty", batch="boat_road_bypass", timeoutTicks=500)
    public static void aVisibleSafeRouteMakesTheNativeMonsterWalkAroundTheUndamagedBoat(GameTestHelper h) {
        RoadFixture r = new RoadFixture(h, EntityType.VINDICATOR, false);
        r.begin().thenWaitUntil(() -> h.assertTrue(r.f.hits > 0,
                        "Full native ticks physically walk around the boat and damage the naturally acquired player: " + r.diagnostics()))
                .thenExecute(() -> {
                    h.assertTrue(r.f.boat.isAlive() && r.f.boat.getDamage() == 0F && r.f.damageAttempts == 0 && r.maxStages == 0,
                            "A complete safe side route never triggers destructive road work: " + r.diagnostics());
                    h.assertTrue(r.furthestSide > .6D && !r.rodeBoat && r.f.boat.tickCount > r.boatStartedAt + 10,
                            "A real moving monster bypasses a normally ticking boat without mounting it");
                    r.close();
                }).thenSucceed();
    }

    @GameTest(template="empty", batch="boat_road_bypass", timeoutTicks=750)
    public static void openingASafeSideRouteDuringSourceWorkPreservesTheBoatAndRestoresActualPursuit(GameTestHelper h) {
        RoadFixture r = new RoadFixture(h, EntityType.ZOMBIE, true);
        r.begin().thenWaitUntil(() -> r.assertPartialWork(2)).thenExecute(() -> {
            h.assertTrue(r.f.boat.isAlive() && r.f.damageAttempts == 0,
                    "The side route opens during real intermediate work before any boat damage");
            r.openSideRoute();
        }).thenWaitUntil(() -> h.assertTrue(r.f.hits > 0,
                "The newly opened route must lead to real player damage rather than waiting for cached failure to expire: " + r.diagnostics()))
                .thenExecute(() -> {
                    h.assertTrue(r.f.boat.isAlive() && r.f.boat.getDamage() == 0F && r.f.damageAttempts == 0 && r.progress() == null,
                            "Fresh safe-route verification cancels partial destructive work and preserves the real boat: " + r.diagnostics());
                    h.assertTrue(r.furthestSide > .6D && !r.rodeBoat, "Actual walking uses the new side opening without boarding");
                    r.close();
                }).thenSucceed();
    }

    @GameTest(template="empty", batch="boat_road_target", timeoutTicks=750)
    public static void anInterveningWallAndThenAnInvalidTargetCannotReceiveRemoteOrStaleSourceBoatDamage(GameTestHelper h) {
        RoadFixture r = new RoadFixture(h, EntityType.ZOMBIE, true);
        r.wall(true);
        r.begin().thenIdle(170).thenExecute(() -> {
            h.assertTrue(r.f.mob.getTarget() == r.f.player,
                    "Source glass visibility still acquires the real player, so the protection is not an absent-target shortcut");
            h.assertTrue(r.f.boat.isAlive() && r.f.damageAttempts == 0 && r.progress() == null,
                    "A real intervening collision wall cannot be bypassed by damaging a nearby irrelevant boat: " + r.diagnostics());
            r.wall(false);
        }).thenWaitUntil(() -> r.assertPartialWork(2)).thenExecute(() -> {
            h.assertTrue(!r.rodeBoat && r.f.damageAttempts == 0, "Real road work began after removing the physical wall");
            r.f.player.setGameMode(GameType.CREATIVE);
        }).thenWaitUntil(() -> h.assertTrue(r.progress() == null, "Losing the legal player target immediately clears pending road work"))
                .thenIdle(25).thenExecute(() -> {
                    h.assertTrue(r.f.boat.isAlive() && r.f.boat.getDamage() == 0F && r.f.damageAttempts == 0 && r.progress() == null,
                            "An invalidated target cannot receive later stale stage damage: " + r.diagnostics());
                    r.close();
                }).thenSucceed();
    }

    @GameTest(template="empty", batch="boat_road_protection", timeoutTicks=900)
    public static void roadDestructionHonorsPerMonsterGriefingAndNativeBoatDamageProtection(GameTestHelper h) {
        RoadFixture r = new RoadFixture(h, EntityType.VINDICATOR, true);
        Consumer<EntityMobGriefingEvent> denyGrief = event -> { if (event.getEntity() == r.f.mob) event.setCanGrief(false); };
        Consumer<EntityInvulnerabilityCheckEvent> denyDamage = event -> {
            if (event.getEntity() == r.f.boat && event.getSource().getEntity() == r.f.mob) event.setInvulnerable(true);
        };
        r.f.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(denyGrief));
        r.f.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(denyDamage));
        NeoForge.EVENT_BUS.addListener(EntityMobGriefingEvent.class, denyGrief);
        r.begin().thenIdle(170).thenExecute(() -> {
            h.assertTrue(!EventHooks.canEntityGrief(h.getLevel(), r.f.mob) && r.f.boat.isAlive()
                            && r.f.damageAttempts == 0 && r.progress() == null,
                    "The actual per-monster griefing hook stops road destruction without altering parallel test game rules");
            NeoForge.EVENT_BUS.addListener(EntityInvulnerabilityCheckEvent.class, denyDamage);
            NeoForge.EVENT_BUS.unregister(denyGrief);
        }).thenWaitUntil(() -> h.assertTrue(r.f.damageAttempts > 0, "Completed road work reaches the normal boat damage protection hook"))
                .thenExecute(() -> {
                    h.assertTrue(r.f.boat.isAlive() && r.f.boat.getDamage() == 0F && !r.rodeBoat,
                            "Rejected ordinary mob damage preserves the road boat with no passenger-breakout shortcut");
                    NeoForge.EVENT_BUS.unregister(denyDamage);
                }).thenWaitUntil(() -> h.assertTrue(r.f.boat.isRemoved(), "Removing protection permits a fresh normal destruction cycle"))
                .thenWaitUntil(() -> h.assertTrue(r.f.hits > 0, "The unblocked native monster physically resumes attack after destruction"))
                .thenExecute(() -> {
                    h.assertTrue(!r.rodeBoat && r.f.mobAttackSource, "Both the protected and accepted attempts use actual walking-monster road work");
                    h.assertValueEqual(r.f.items(Items.OAK_BOAT), 1, "The protected retry sequence still drops one original boat item");
                    r.close();
                }).thenSucceed();
    }

    @GameTest(template="empty", batch="boat_road_multiple", timeoutTicks=900)
    public static void twoSeparatedBoatRoadblocksReceiveIndependentNearbyWorkAndNormalDropsBeforeAttack(GameTestHelper h) {
        RoadFixture r = new RoadFixture(h, EntityType.ZOMBIE, true);
        Boat second = r.f.extraBoat(r.f.origin.east(10));
        second.moveTo(Vec3.atBottomCenterOf(r.f.origin.east(10)).add(0D, 0D, .5D));
        int[] firstAttempt = {-1}, secondAttempt = {-1}, firstCount = {0}, secondCount = {0};
        boolean[] remoteDamage = {false}, damagedSecondBeforeFirstRemoval = {false};
        Consumer<EntityInvulnerabilityCheckEvent> observe = event -> {
            if (event.getSource().getEntity() != r.f.mob || event.getEntity() != r.f.boat && event.getEntity() != second) return;
            if (r.f.mob.distanceToSqr(event.getEntity()) > 9D) remoteDamage[0] = true;
            if (event.getEntity() == r.f.boat) { firstAttempt[0] = r.f.mob.tickCount; firstCount[0]++; }
            else {
                secondAttempt[0] = r.f.mob.tickCount; secondCount[0]++;
                if (r.f.boat.isAlive()) damagedSecondBeforeFirstRemoval[0] = true;
            }
        };
        NeoForge.EVENT_BUS.addListener(EntityInvulnerabilityCheckEvent.class, observe);
        r.f.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(observe));
        r.begin().thenWaitUntil(() -> {
            r.assertPartialWork(2);
            h.assertTrue(r.progress().boat().equals(r.f.boat.getUUID()), "The first real work cycle targets the nearer road boat");
        }).thenWaitUntil(() -> h.assertTrue(r.f.boat.isRemoved(), "The first ordinary boat is normally destroyed"))
                .thenExecute(() -> {
                    h.assertTrue(firstCount[0] == 1 && secondCount[0] == 0 && second.isAlive() && second.getDamage() == 0F
                                    && r.f.hits == 0 && !r.rodeBoat,
                            "Only the nearer obstacle receives one normal hit while the second still blocks the player");
                    h.assertValueEqual(r.f.items(Items.OAK_BOAT), 1, "The first boat drops exactly one ordinary item");
                }).thenWaitUntil(() -> {
                    r.assertPartialWork(2);
                    h.assertTrue(r.progress().boat().equals(second.getUUID()) && r.f.mob.getX() > r.f.origin.getX() + 7D,
                            "Actual walking reaches the separated second boat and starts a fresh nearby work cycle: " + r.diagnostics());
                }).thenWaitUntil(() -> h.assertTrue(second.isRemoved(), "The second obstacle completes its own normal destruction cycle"))
                .thenExecute(() -> {
                    h.assertTrue(firstCount[0] == 1 && secondCount[0] == 1 && !remoteDamage[0] && !damagedSecondBeforeFirstRemoval[0]
                                    && secondAttempt[0] - firstAttempt[0] >= MonsterBoatBreakout.WAIT_TICKS
                                    + (MonsterBoatBreakout.BREAK_STAGES - 1) * MonsterBoatBreakout.STAGE_INTERVAL_TICKS - 1,
                            "Separated boats cannot be removed by one remote, simultaneous or inherited-stage attack");
                    h.assertValueEqual(r.f.items(Items.OAK_BOAT), 2, "Both real boats drop their original item exactly once");
                }).thenWaitUntil(() -> h.assertTrue(r.f.hits > 0, "Removing both real roadblocks permits an actual attack on the player"))
                .thenExecute(() -> {
                    h.assertTrue(!r.rodeBoat && r.f.damageAttempts == 2 && r.f.mobAttackSource && r.furthestX > 8D,
                            "Two complete unmounted road cycles, not passenger escape, restore real pursuit");
                    r.close();
                }).thenSucceed();
    }

    @GameTest(template="empty", batch="boat_road_contention", timeoutTicks=1000)
    public static void fourRealWalkingMonstersShareTheBudgetAndAllFinishBoatRoadWorkAndAttack(GameTestHelper h) {
        List<RoadFixture> roads = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            RoadFixture r = new RoadFixture(h, EntityType.ZOMBIE, true);
            r.f.mob.getRandom().setSeed(31_1031L);
            r.f.player.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("mite", "boat_road_contention_health"), 1000D, AttributeModifier.Operation.ADD_VALUE));
            r.f.player.setHealth(r.f.player.getMaxHealth());
            roads.add(r);
        }
        long[] deferredBefore = {0L};
        h.startSequence().thenWaitUntil(() -> {
            for (int work = 0; work < 64 && h.getLevel().getChunkSource().pollTask(); work++) {}
            for (RoadFixture r : roads) {
                for (ChunkPos chunk : r.f.chunks) h.assertTrue(h.getLevel().isPositionEntityTicking(chunk.getWorldPosition()),
                        "All four independent native road fixtures are genuinely entity-ticking before simultaneous activation");
                h.assertTrue(h.getLevel().getEntity(r.f.mob.getUUID()) == r.f.mob && h.getLevel().getEntity(r.f.boat.getUUID()) == r.f.boat,
                        "Each independent real monster and real boat entered the level");
            }
        }).thenExecute(() -> {
            deferredBefore[0] = PathSearchBudget.stats(h.getLevel().getServer()).deferred();
            for (RoadFixture r : roads) {
                h.assertTrue(!r.f.mob.isPassenger() && h.getLevel().noCollision(r.f.boat, r.f.boat.getBoundingBox().deflate(.001D)),
                        "Each normal boat roadblock fits the corridor and starts without a trapped passenger");
                r.startedAt = r.f.mob.tickCount; r.boatStartedAt = r.f.boat.tickCount;
                r.f.player.setGameMode(GameType.SURVIVAL); r.f.mob.setNoAi(false);
            }
        }).thenWaitUntil(() -> {
            for (RoadFixture r : roads) h.assertTrue(r.f.boat.isRemoved() && r.f.hits > 0,
                    "Every naturally acquired player must be reached after its own real walking road work, without budget starvation: " + r.diagnostics());
        }).thenExecute(() -> {
            var stats = PathSearchBudget.stats(h.getLevel().getServer());
            if (MiteConfig.pursuitSearchBudgetEnabled()) h.assertTrue(stats.deferred() > deferredBefore[0],
                    "The enabled shared budget must witness actual deferral while all four real road workloads still complete");
            System.out.println("BOAT ROAD CONTENTION budgetEnabled=" + MiteConfig.pursuitSearchBudgetEnabled()
                    + ", deferredBefore=" + deferredBefore[0] + ", deferredAfter=" + stats.deferred() + ", completed=" + roads.size());
            for (RoadFixture r : roads) {
                h.assertTrue(!r.rodeBoat && r.f.damageAttempts == 1 && r.f.mobAttackSource && r.furthestX > 8D
                                && r.maxStages >= MonsterBoatBreakout.BREAK_STAGES - 1 && r.sawRoadSwing,
                        "Each real monster independently approaches, swings through its stages, damages one boat and resumes unmounted attack");
                h.assertValueEqual(r.f.items(Items.OAK_BOAT), 1, "Each independent native road boat drops exactly one item");
            }
            for (RoadFixture r : roads) r.close();
        }).thenSucceed();
    }

    /** Reuses the normal-boat fixture without its passenger mounting sequence. */
    private static final class RoadFixture implements AutoCloseable {
        final Fixture f;
        final PathfinderMob actor;
        final GameTestHelper h;
        int startedAt, boatStartedAt, firstWorkAt = -1, maxStages;
        double furthestX, furthestSide;
        boolean rodeBoat, sawRoadSwing;

        RoadFixture(GameTestHelper h, EntityType<? extends Mob> type, boolean sealedCorridor) {
            this.h = h; f = new Fixture(h, type, false); actor = (PathfinderMob)f.mob;
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(f.mob instanceof Zombie ? Items.IRON_SWORD : Items.IRON_AXE));
            f.mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            f.boat.moveTo(Vec3.atBottomCenterOf(f.origin.east(6)));
            if (sealedCorridor) {
                // A boat fits this two-block width, but leaves less than a monster body's
                // width on either side. The low ceiling also rules out climbing over it.
                f.boat.moveTo(Vec3.atBottomCenterOf(f.origin.east(6)).add(0D, 0D, .5D));
                for (int x = -4; x <= 18; x++) {
                    for (int z = 0; z <= 1; z++) h.getLevel().setBlock(f.origin.offset(x, 2, z), Blocks.BEDROCK.defaultBlockState(), 2);
                    for (int y = 0; y <= 4; y++) for (int z : new int[]{-1, 2})
                        h.getLevel().setBlock(f.origin.offset(x, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
                }
                for (int x : new int[]{-4, 18}) for (int y = 0; y <= 4; y++) for (int z = 0; z <= 1; z++)
                    h.getLevel().setBlock(f.origin.offset(x, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
            h.onEachTick(() -> {
                if (f.closed || f.mob.isNoAi()) return;
                furthestX = Math.max(furthestX, f.mob.getX() - f.origin.getX());
                furthestSide = Math.max(furthestSide, Math.abs(f.mob.getZ() - f.origin.getZ() - .5D));
                if (f.mob.isPassenger()) rodeBoat = true;
                var work = progress();
                if (work != null) {
                    if (firstWorkAt < 0) firstWorkAt = f.mob.tickCount;
                    maxStages = Math.max(maxStages, work.stages());
                    if (f.mob.swinging) sawRoadSwing = true;
                }
            });
        }

        GameTestSequence begin() {
            return h.startSequence().thenWaitUntil(() -> {
                for (int work = 0; work < 64 && h.getLevel().getChunkSource().pollTask(); work++) {}
                for (ChunkPos chunk : f.chunks) h.assertTrue(h.getLevel().isPositionEntityTicking(chunk.getWorldPosition()),
                        "The road obstacle fixture reaches real entity-ticking chunk status");
                h.assertTrue(h.getLevel().getEntity(f.mob.getUUID()) == f.mob && h.getLevel().getEntity(f.boat.getUUID()) == f.boat,
                        "The actual walking monster and unoccupied road boat are present");
            }).thenExecute(() -> {
                h.assertTrue(!f.mob.isPassenger() && !f.boat.isVehicle(), "The road scenario begins without a trapped passenger");
                for (Boat boat : f.boats) h.assertTrue(h.getLevel().noCollision(boat, boat.getBoundingBox().deflate(.001D)),
                        "Every naturally ticking road boat initially fits the actual world rather than intersecting corridor walls");
                startedAt = f.mob.tickCount; boatStartedAt = f.boat.tickCount;
                f.player.setGameMode(GameType.SURVIVAL); f.mob.setNoAi(false);
            }).thenWaitUntil(() -> h.assertTrue(f.mob.getTarget() == f.player,
                    "Complete native ticks naturally discover the registered survival player without assigning a target: " + diagnostics()));
        }

        void assertPartialWork(int stages) {
            var work = progress();
            h.assertTrue(work != null && work.stages() >= stages,
                    "Walking toward the real blocking boat produces actual staged road work: " + diagnostics());
            h.assertTrue(furthestX > 2D && f.mob.tickCount > startedAt + 10 && f.boat.tickCount > boatStartedAt + 10 && !rodeBoat,
                    "Both the monster and boat genuinely tick; road work follows physical approach without boarding");
        }

        void breakAndAttack() {
            begin().thenWaitUntil(() -> assertPartialWork(2)).thenExecute(() -> h.assertTrue(f.boat.isAlive()
                            && f.boat.getDamage() == 0F && f.damageAttempts == 0,
                    "Intermediate walking work does not apply premature boat damage"))
                    .thenWaitUntil(() -> assertPartialWork(MonsterBoatBreakout.BREAK_STAGES - 1))
                    .thenExecute(() -> h.assertTrue(sawRoadSwing && f.boat.isAlive() && f.damageAttempts == 0,
                            "The real monster visibly swings through ten work stages while the normally ticking boat remains intact"))
                    .thenWaitUntil(() -> h.assertTrue(f.boat.isRemoved(), "Completed road work destroys the real unoccupied boat"))
                    .thenExecute(() -> {
                        h.assertTrue(!rodeBoat && f.damageAttempts == 1 && f.mobAttackSource,
                                "A single ordinary walking mobAttack hit, not passenger escape or discard, removes the obstruction");
                        h.assertTrue(f.mob.tickCount - firstWorkAt >= MonsterBoatBreakout.WAIT_TICKS
                                        + (MonsterBoatBreakout.BREAK_STAGES - 1) * MonsterBoatBreakout.STAGE_INTERVAL_TICKS - 1,
                                "Full wait and stage timing cannot be bypassed by duplicate events or local goal ticks");
                        h.assertValueEqual(f.items(Items.OAK_BOAT), 1, "Native boat destruction drops the original boat item exactly once");
                    }).thenWaitUntil(() -> h.assertTrue(f.hits > 0,
                            "After removal the actual monster resumes navigation and damages the player: " + diagnostics()))
                    .thenExecute(() -> { h.assertTrue(!rodeBoat && furthestX > 8D, "The successful road attack requires real unmounted movement"); close(); })
                    .thenSucceed();
        }

        void openSideRoute() {
            for (int x = 3; x <= 10; x++) for (int y = 0; y <= 3; y++)
                h.getLevel().setBlock(f.origin.offset(x, y, 2), Blocks.AIR.defaultBlockState(), 3);
        }

        void wall(boolean present) {
            for (int y = 0; y <= 1; y++) for (int z = 0; z <= 1; z++) h.getLevel().setBlock(f.origin.offset(5, y, z),
                    (present ? Blocks.GLASS_PANE : Blocks.AIR).defaultBlockState(), 3);
        }

        MonsterBoatBreakout.RoadProgress progress() { return MonsterBoatBreakout.roadProgress(actor); }
        String diagnostics() {
            return "kind=" + f.mob.getType() + ", tick=" + f.mob.tickCount + ", pos=" + f.mob.position().subtract(Vec3.atBottomCenterOf(f.origin))
                    + ", target=" + f.mob.getTarget() + ", road=" + progress() + ", passenger=" + f.mob.isPassenger() + ", rode=" + rodeBoat
                    + ", boatTick=" + f.boat.tickCount + ", boatPos=" + f.boat.position().subtract(Vec3.atBottomCenterOf(f.origin))
                    + ", damage=" + f.boat.getDamage() + ", attempts=" + f.damageAttempts + ", hits=" + f.hits;
        }
        @Override public void close() { f.close(); }
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final int key = IDS.getAndIncrement();
        final BlockPos origin;
        final List<ChunkPos> chunks = new ArrayList<>();
        final List<Boat> boats = new ArrayList<>();
        final List<Mob> mobs = new ArrayList<>();
        final List<Runnable> cleanup = new ArrayList<>();
        final boolean oldGriefing, oldDrops;
        final Mob mob;
        final Boat boat;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final Consumer<EntityInvulnerabilityCheckEvent> damage;
        final Consumer<LivingDamageEvent.Post> hurt;
        int mountedAt, damageAttempts, hits;
        boolean mobAttackSource, closed;

        Fixture(GameTestHelper h, EntityType<? extends Mob> type, boolean chest) {
            this.h = h;
            oldGriefing = h.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
            oldDrops = h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS);
            griefing(true); drops(true);
            origin = new BlockPos(915_000 + key * 96, h.absolutePos(BlockPos.ZERO).getY() + 12, 915_000);
            for (int x = -6; x <= 20; x++) for (int z = -6; z <= 6; z++) for (int y = -1; y <= 5; y++)
                h.getLevel().setBlock(origin.offset(x, y, z), (y == -1 || y == 5 ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            ChunkPos low = new ChunkPos(origin.offset(-6, 0, -6)), high = new ChunkPos(origin.offset(20, 0, 6));
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z); chunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, key);
                for (int cx = x - 2; cx <= x + 2; cx++) for (int cz = z - 2; cz <= z + 2; cz++)
                    h.getLevel().getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
            }
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "boat-exit-" + key), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.CREATIVE);
            player.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("mite", "boat_breakout_test_health"), 100D, AttributeModifier.Operation.ADD_VALUE));
            player.setHealth(player.getMaxHealth()); player.moveTo(Vec3.atBottomCenterOf(origin.east(12)));
            mob = extraMob(type, origin); mob.setNoAi(true);
            boat = chest ? new ChestBoat(h.getLevel(), origin.getX() + .5D, origin.getY(), origin.getZ() + .5D) :
                    new Boat(h.getLevel(), origin.getX() + .5D, origin.getY(), origin.getZ() + .5D);
            boat.setVariant(Boat.Type.OAK); boat.setNoGravity(true); h.getLevel().addFreshEntity(boat); boats.add(boat);
            damage = event -> {
                if (boats.contains(event.getEntity()) && event.getSource().getEntity() == mob) {
                    damageAttempts++; mobAttackSource |= event.getSource().is(DamageTypes.MOB_ATTACK);
                }
            };
            hurt = event -> { if (event.getEntity() == player && event.getSource().getEntity() == mob && event.getNewDamage() > 0F) hits++; };
            NeoForge.EVENT_BUS.addListener(EntityInvulnerabilityCheckEvent.class, damage);
            NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, hurt);
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
        }

        Mob extraMob(EntityType<? extends Mob> type, BlockPos at) {
            Mob actor = type.create(h.getLevel()); actor.moveTo(Vec3.atBottomCenterOf(at));
            EventHooks.finalizeMobSpawn(actor, h.getLevel(), h.getLevel().getCurrentDifficultyAt(at), MobSpawnType.SPAWN_EGG, null);
            if (actor instanceof Zombie zombie) zombie.setBaby(false);
            actor.setPersistenceRequired(); actor.setCanPickUpLoot(false); h.getLevel().addFreshEntity(actor); mobs.add(actor);
            return actor;
        }

        Boat extraBoat(BlockPos at) {
            Boat extra = new Boat(h.getLevel(), at.getX() + .5D, at.getY(), at.getZ() + .5D);
            extra.setVariant(Boat.Type.OAK); extra.setNoGravity(true); h.getLevel().addFreshEntity(extra); boats.add(extra); return extra;
        }

        GameTestSequence begin() {
            return h.startSequence().thenWaitUntil(() -> {
                for (int work = 0; work < 64 && h.getLevel().getChunkSource().pollTask(); work++) {}
                for (ChunkPos chunk : chunks) h.assertTrue(h.getLevel().isPositionEntityTicking(chunk.getWorldPosition()),
                        "The real passenger fixture reaches entity-ticking chunk status");
                h.assertTrue(h.getLevel().getEntity(mob.getUUID()) == mob && h.getLevel().getEntity(boat.getUUID()) == boat,
                        "Both the real monster and boat entered the server level");
            }).thenExecute(() -> {
                mob.setNoAi(false);
                h.assertTrue(mob.getVehicle() == boat || mob.startRiding(boat, true),
                        "The monster boards naturally or mounts through the standard passenger API");
                mountedAt = mob.tickCount;
            });
        }

        MonsterBoatBreakout.Progress progress() { return MonsterBoatBreakout.progress(mob); }
        void griefing(boolean enabled) { h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(enabled, h.getLevel().getServer()); }
        void drops(boolean enabled) { h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(enabled, h.getLevel().getServer()); }
        int items(Item item) {
            return h.getLevel().getEntitiesOfClass(ItemEntity.class, area()).stream()
                    .map(ItemEntity::getItem).filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
        }
        AABB area() { return new AABB(Vec3.atLowerCornerOf(origin.offset(-8, -3, -8)), Vec3.atLowerCornerOf(origin.offset(24, 8, 8))); }

        @Override public void close() {
            if (closed) return; closed = true;
            for (Runnable action : cleanup) action.run();
            NeoForge.EVENT_BUS.unregister(damage); NeoForge.EVENT_BUS.unregister(hurt);
            for (Mob actor : mobs) actor.discard();
            for (Boat vehicle : boats) vehicle.discard();
            h.getLevel().getEntitiesOfClass(ItemEntity.class, area()).forEach(ItemEntity::discard);
            player.server.getPlayerList().remove(player); channel.finishAndReleaseAll(); griefing(oldGriefing); drops(oldDrops);
            for (ChunkPos chunk : chunks) h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, key);
        }
    }
}

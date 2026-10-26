package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.phantom.PhantomBladeProjectile;
import io.github.ningmeng123.mite.phantom.PhantomBladeSwarm;
import io.github.ningmeng123.mite.phantom.PhantomBladeState;
import io.github.ningmeng123.mite.phantom.PhantomBladeSystem;
import io.github.ningmeng123.mite.registry.MitePhantomBlades;
import io.github.ningmeng123.mite.rules.PhantomBladeRules;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real online owners and a single persistent sword exercise combat, return and cooldown. @author NingMeng123 */
@GameTestHolder("mite_phantom")
@PrefixGameTestTemplate(false)
public final class PhantomBladeGameTests {
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void onlyTheStrongestMainInventoryCoreOwnsExactlyOneSword(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.player.getInventory().offhand.set(0, new ItemStack(MitePhantomBlades.get("vibranium")));
        f.player.getInventory().armor.set(0, new ItemStack(MitePhantomBlades.get("adamantium")));
        PhantomBladeSystem.refresh(f.player);
        h.assertTrue(PhantomBladeSystem.swarm(f.player) == null, "Offhand and armor slots cannot activate a sword");
        f.core("copper", 0); f.core("silver", 1); PhantomBladeSystem.refresh(f.player);
        PhantomBladeSwarm first = PhantomBladeSystem.swarm(f.player);
        h.assertTrue(first != null && first.isAddedToLevel(), "The synchronized controller belongs to the real online player");
        h.assertValueEqual(first.material(), "copper", "Equal-rank copper and silver select the earliest main-inventory slot");
        for (int slot = 2; slot < 36; slot++) f.core("copper", slot);
        PhantomBladeSystem.refresh(f.player);
        h.assertTrue(PhantomBladeSystem.swarm(f.player) == first, "Thirty-six cores share the same controller");
        h.runAtTickTime(3, () -> {
            h.assertValueEqual(f.swords.size(), 1, "The entire inventory creates exactly one physical sword");
            PhantomBladeProjectile original = f.swords.getFirst();
            f.core("vibranium", 35); PhantomBladeSystem.refresh(f.player);
            var upgraded = PhantomBladeSystem.swarm(f.player);
            h.assertTrue(first.isRemoved() && original.isRemoved() && upgraded != first && upgraded.material().equals("vibranium"),
                    "Changing the strongest material replaces its controller and removes the former sword");
        });
        h.runAtTickTime(6, () -> {
            var upgraded = PhantomBladeSystem.swarm(f.player);
            h.assertTrue(f.liveSwords().size() == 1 && f.liveSwords().getFirst().isDocked(), "Only one replacement sword rests behind the owner");
            h.assertValueEqual(upgraded.orbitBladeMask(), 1, "The waiting sword occupies the only visible slot");
            h.assertFalse(upgraded.save(new CompoundTag()), "The controller is recreated from the player's inventory after loading");
            h.assertFalse(f.liveSwords().getFirst().save(new CompoundTag()), "The physical sword is recreated rather than duplicated by world entity saves");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void everyMaterialStartsWithOneDockedSwordAndItsOwnCooldown(GameTestHelper h) {
        List<Fixture> fixtures = new ArrayList<>();
        for (var profile : PhantomBladeRules.profiles()) {
            Fixture f = new Fixture(h); fixtures.add(f); f.core(profile.material(), 0); PhantomBladeSystem.refresh(f.player);
            h.assertValueEqual(PhantomBladeSystem.swarm(f.player).bladeCount(), 1, "Every material has one sword");
            h.assertValueEqual(PhantomBladeState.current(f.player, profile).nextAttack(), PhantomBladeState.now(f.player)+profile.intervalTicks(),
                    "Initial readiness uses the material's short cooldown");
        }
        h.runAtTickTime(3, () -> {
            for (Fixture f : fixtures) {
                h.assertTrue(f.liveSwords().size() == 1 && f.liveSwords().getFirst().isDocked(), "Each material creates its actual permanent waiting sword");
                h.assertValueEqual(PhantomBladeSystem.swarm(f.player).orbitBladeMask(), 1, "Cooldown never hides a docked sword");
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void oldMagazineSavesMigrateOnceToAShortSharedCooldownWithoutRefillingExploits(GameTestHelper h) {
        Fixture f = new Fixture(h); var copper = PhantomBladeRules.profile("copper");
        long now = PhantomBladeState.now(f.player);
        for (int version : new int[] {0, 2}) for (int charges : new int[] {0, 1, 4, 8}) {
            CompoundTag legacy = new CompoundTag(); legacy.putInt("Version", version); legacy.putInt("Charges", charges);
            legacy.putInt("Spent", 8-charges); legacy.putLong("NextAttack", now+800L); legacy.putLong("RechargeEnd", now+600L);
            f.player.getPersistentData().put(PhantomBladeState.KEY, legacy);
            var migrated = PhantomBladeState.current(f.player, copper);
            h.assertValueEqual(migrated.nextAttack(), now+60L, "An old long magazine deadline becomes at most one normal copper cooldown");
            h.assertValueEqual(f.player.getPersistentData().getCompound(PhantomBladeState.KEY).getInt("Version"), 3, "The conversion saves the single-sword schema");
            h.assertValueEqual(PhantomBladeState.current(f.player, PhantomBladeRules.profile("vibranium")), migrated,
                    "Switching after migration preserves the already committed deadline");
        }
        CompoundTag partial = new CompoundTag(); partial.putLong("NextAttack", now+17L); partial.putLong("RechargeEnd", now+9L);
        f.player.getPersistentData().put(PhantomBladeState.KEY, partial);
        var migrated = PhantomBladeState.current(f.player, copper);
        h.assertValueEqual(migrated.nextAttack(), now+17L, "An already short legacy deadline is preserved");
        f.core("copper", 0); f.relog();
        h.assertValueEqual(PhantomBladeState.current(f.player, copper), migrated, "Real player NBT and relog cannot reset the migrated timer");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void copperHitsReturnsAndReusesTheSameSwordAfterItsSixtyTickCooldown(GameTestHelper h) {
        attackCycle(h, "copper", .5D, 60);
    }
    @GameTest(template = "empty", timeoutTicks = 220)
    public static void rustedIronHasItsOwnSeventyTickCooldown(GameTestHelper h) { attackCycle(h, "rusted_iron", .4D, 70); }
    @GameTest(template = "empty", timeoutTicks = 220)
    public static void goldHasItsOwnSixtyFiveTickCooldown(GameTestHelper h) { attackCycle(h, "gold", .45D, 65); }
    @GameTest(template = "empty", timeoutTicks = 220)
    public static void silverHasTheCopperRankSixtyTickCooldown(GameTestHelper h) { attackCycle(h, "silver", .5D, 60); }
    @GameTest(template = "empty", timeoutTicks = 180)
    public static void ironHasAFiftyTickCooldown(GameTestHelper h) { attackCycle(h, "iron", 1D, 50); }
    @GameTest(template = "empty", timeoutTicks = 180)
    public static void ancientMetalHasAFortyFiveTickCooldown(GameTestHelper h) { attackCycle(h, "ancient_metal", 1.25D, 45); }
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void mithrilHasAFortyTickCooldown(GameTestHelper h) { attackCycle(h, "mithril", 1.5D, 40); }
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void adamantiumHasAThirtyTickCooldown(GameTestHelper h) { attackCycle(h, "adamantium", 2D, 30); }
    @GameTest(template = "empty", timeoutTicks = 140)
    public static void vibraniumHasATwentyTickCooldown(GameTestHelper h) { attackCycle(h, "vibranium", 3D, 20); }

    private static void attackCycle(GameTestHelper h, String material, double damage, int cooldown) {
        Fixture f = new Fixture(h); var target = f.enemy(9, 3, 4); float initial = target.getHealth();
        f.core(material, 0); PhantomBladeSystem.refresh(f.player);
        f.damageListener(event -> {
            close(h, event.getAmount(), damage, "The ordinary incoming-damage event receives the material's exact base damage");
            h.assertTrue(event.getSource().getEntity() == f.player && event.getSource().getDirectEntity() == f.swords.getFirst(),
                    "The permanent physical sword retains real player damage ownership");
            h.assertFalse(event.getSource().is(DamageTypeTags.BYPASSES_ARMOR), "The sword uses native armor mitigation");
            if (f.hits != 1) return;
            long firstHit = PhantomBladeState.now(f.player);
            PhantomBladeProjectile sword = f.swords.getFirst();
            h.runAfterDelay(1, () -> {
                h.assertTrue(!sword.isRemoved() && sword.isReturning(), "A physical hit starts a visible return without discarding the sword");
                h.assertValueEqual(PhantomBladeState.current(f.player, PhantomBladeRules.profile(material)).nextAttack(), firstHit+cooldown,
                        "Cooldown begins when the attack finishes rather than when the outbound flight launches");
            });
            h.runAfterDelay(cooldown-1, () -> {
                h.assertValueEqual(f.launches.size(), 1, "No second attack starts one tick before cooldown ends");
                h.assertTrue(sword.isDocked() && !sword.isRemoved(), "The very same sword waits visibly behind its owner");
                h.assertValueEqual(PhantomBladeSystem.swarm(f.player).orbitBladeMask(), 1, "A waiting sword stays visible during cooldown");
                h.assertTrue(sword.position().distanceTo(PhantomBladeSwarm.restingPosition(f.player)) < .01D, "The docked sword uses the player's actual back position");
                h.assertTrue(!PhantomBladeState.ready(f.player, PhantomBladeState.current(f.player, PhantomBladeRules.profile(material))), "The persisted deadline remains authoritative");
            });
            h.runAfterDelay(cooldown+15, () -> {
                h.assertValueEqual(f.swords.size(), 1, "Repeated attacks never create a replacement sword entity");
                h.assertValueEqual(f.launches.size(), 2, "The same sword makes exactly one next attack after cooldown");
                h.assertTrue(f.launches.get(0) == sword && f.launches.get(1) == sword && !sword.isRemoved(), "Both physical flights use identical entity identity");
                h.assertValueEqual(f.hits, 2, "Both real flights hit once and return flights do no damage");
                close(h, target.getHealth(), initial-2D*damage, "Two native transactions apply exactly twice the unarmored base damage");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void manyLowAndHighTierCoresStillReuseOneVibraniumSword(GameTestHelper h) {
        Fixture f = new Fixture(h); f.enemy(11, 3, 4);
        for (int slot = 0; slot < 36; slot++) f.core(slot%2 == 0 ? "vibranium" : "copper", slot);
        PhantomBladeSystem.refresh(f.player);
        h.runAtTickTime(110, () -> {
            h.assertValueEqual(f.swords.size(), 1, "Thirty-six cores create one sword throughout several attack cycles");
            h.assertTrue(f.launches.size() >= 3 && f.launches.stream().allMatch(sword -> sword == f.swords.getFirst()), "Every repeated attack reuses that one sword");
            h.assertValueEqual(f.swords.getFirst().material(), "vibranium", "The strongest carried material determines the sword");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void neutralMobsPetsAlliesOutOfRangeEnemiesAndOpaqueWallsAreNotAutomaticTargets(GameTestHelper h) {
        Fixture f = new Fixture(h); f.core("vibranium", 0);
        var enderman = f.mob(EntityType.ENDERMAN, 7, 3, 7);
        var pig = f.mob(EntityType.PIG, 7, 3, 8);
        var wolf = f.mob(EntityType.WOLF, 7, 3, 9); wolf.tame(f.player);
        var far = f.enemy(15, 3, 4); var ally = f.enemy(8, 3, 2);
        var scoreboard = h.getLevel().getScoreboard(); var team = scoreboard.addPlayerTeam("phantom_"+f.player.getId());
        scoreboard.addPlayerToTeam(f.player.getScoreboardName(), team); scoreboard.addPlayerToTeam(ally.getScoreboardName(), team);
        f.cleanup.add(() -> scoreboard.removePlayerTeam(team));
        h.assertFalse(PhantomBladeSystem.validTarget(f.player, enderman), "Neutral enemies require actual anger at the owner");
        h.assertFalse(PhantomBladeSystem.validTarget(f.player, pig), "Livestock is excluded");
        h.assertFalse(PhantomBladeSystem.validTarget(f.player, wolf), "Pets are excluded");
        h.assertFalse(PhantomBladeSystem.validTarget(f.player, ally), "Allies are excluded");
        h.assertFalse(PhantomBladeSystem.validTarget(f.player, far), "The range remains ten blocks");
        var blocked = f.enemy(10, 3, 4); f.wall(7);
        h.assertFalse(f.player.hasLineOfSight(blocked), "A real wall blocks target visibility");
        PhantomBladeSystem.refresh(f.player);
        h.runAtTickTime(45, () -> {
            h.assertTrue(f.launches.isEmpty() && f.swords.size() == 1 && f.swords.getFirst().isDocked(), "With no valid visible enemy the sword stays behind the owner");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void aWallPlacedAfterLaunchReturnsTheSameSwordWithoutThroughWallDamage(GameTestHelper h) {
        Fixture f = new Fixture(h); var target = f.enemy(13, 3, 4); float initial = target.getHealth();
        f.onLaunch = sword -> {
            f.wall(9);
            h.runAfterDelay(24, () -> {
                h.assertValueEqual(f.launches.size(), 1, "The new wall prevents subsequent automatic launches");
                h.assertTrue(sword.isDocked() && !sword.isRemoved(), "Collision returns the original sword to the player's back");
                h.assertValueEqual(f.hits, 0, "A wall collision creates no damage transaction");
                close(h, target.getHealth(), initial, "The hidden enemy is unharmed");
                h.succeed();
            });
        };
        f.core("vibranium", 0); PhantomBladeSystem.refresh(f.player);
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void incomingDamageAndProjectileImpactCancellationStillReturnThePermanentSword(GameTestHelper h) {
        Fixture f = new Fixture(h); var target = f.enemy(10, 3, 4); float initial = target.getHealth();
        f.damageListener(event -> event.setCanceled(true));
        Consumer<ProjectileImpactEvent> impact = event -> {
            if (event.getProjectile() instanceof PhantomBladeProjectile blade && blade.getOwner() == f.player && f.hits > 0) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(ProjectileImpactEvent.class, impact); f.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(impact));
        f.core("vibranium", 0); PhantomBladeSystem.refresh(f.player);
        h.runAtTickTime(110, () -> {
            h.assertValueEqual(f.swords.size(), 1, "Both cancellation paths retain the original sword");
            h.assertTrue(f.launches.size() >= 2 && !f.swords.getFirst().isRemoved(), "Protected impacts still complete return and cooldown cycles");
            h.assertValueEqual(f.hits, 1, "Impact cancellation suppresses every later incoming-damage event");
            close(h, target.getHealth(), initial, "Neither native protection path loses health"); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void aFriendlyBodyBlocksTheSwordAndReturnFlightNeverCausesCollateralDamage(GameTestHelper h) {
        Fixture f = new Fixture(h); var target = f.enemy(13, 3, 4); var cow = f.mob(EntityType.COW, 8, 3, 4);
        float enemyHealth = target.getHealth(), animalHealth = cow.getHealth();
        f.core("vibranium", 0); PhantomBladeSystem.refresh(f.player);
        h.runAtTickTime(65, () -> {
            h.assertTrue(!f.launches.isEmpty() && f.swords.size() == 1 && !f.swords.getFirst().isRemoved(), "Interception sends the existing sword back instead of consuming it");
            h.assertValueEqual(f.hits, 0, "Outbound and return collisions with a friendly body never submit damage");
            close(h, target.getHealth(), enemyHealth, "The blade does not tunnel through livestock");
            close(h, cow.getHealth(), animalHealth, "The intercepting animal remains unharmed"); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void losingAnOutboundTargetReturnsTheSameSwordWithoutDamage(GameTestHelper h) {
        Fixture f = new Fixture(h); var target = f.enemy(13, 3, 4);
        f.onLaunch = sword -> {
            target.discard();
            h.runAfterDelay(20, () -> {
                h.assertTrue(sword.isDocked() && !sword.isRemoved(), "A target that disappears midflight sends the sword back");
                h.assertValueEqual(f.swords.size(), 1, "Invalidating the target does not replace the sword");
                h.assertValueEqual(f.launches.size(), 1, "No target remains for another attack");
                h.assertValueEqual(f.hits, 0, "The abandoned outbound flight and return cause no damage"); h.succeed();
            });
        };
        f.core("vibranium", 0); PhantomBladeSystem.refresh(f.player);
    }

    @GameTest(template = "empty", timeoutTicks = 260)
    public static void waitingSwordNeverTimesOutAndFollowsTheOwnersBackWhenTurning(GameTestHelper h) {
        Fixture f = new Fixture(h); f.core("vibranium", 0); PhantomBladeSystem.refresh(f.player);
        h.runAtTickTime(90, () -> { f.player.setYRot(90F); f.player.yBodyRot = 90F; });
        h.runAtTickTime(150, () -> { f.player.setYRot(210F); f.player.yBodyRot = 210F; });
        h.runAtTickTime(240, () -> {
            h.assertTrue(f.swords.size() == 1 && f.swords.getFirst().isDocked() && !f.swords.getFirst().isRemoved(), "Idle lifetime never removes or replaces the sword");
            Vec3 back = PhantomBladeSwarm.restingPosition(f.player);
            h.assertTrue(f.swords.getFirst().position().distanceTo(back) < .01D, "The docked sword follows the rotated back position");
            h.assertTrue(back.subtract(f.player.position()).multiply(1D, 0D, 1D).dot(f.player.getLookAngle().multiply(1D, 0D, 1D)) < 0D,
                    "The resting position is physically behind the player's facing direction");
            h.assertTrue(f.launches.isEmpty(), "Waiting without enemies never creates an attack"); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void removingTheCoreIntoAContainerRemovesItsOutboundSword(GameTestHelper h) {
        Fixture f = new Fixture(h); var target = f.enemy(13, 3, 4); float initial = target.getHealth();
        f.onLaunch = sword -> {
            var swarm = PhantomBladeSystem.swarm(f.player);
            h.setBlock(3, 3, 3, Blocks.CHEST); var chest = (ChestBlockEntity)h.getBlockEntity(new BlockPos(3, 3, 3));
            chest.setItem(0, f.player.getInventory().removeItemNoUpdate(0)); PhantomBladeSystem.refresh(f.player);
            h.assertTrue(swarm.isRemoved() && sword.isRemoved() && PhantomBladeSystem.swarm(f.player) == null, "Removing the carried core cleans both controller and physical sword");
            f.player.getInventory().offhand.set(0, chest.removeItemNoUpdate(0)); PhantomBladeSystem.refresh(f.player);
            h.runAfterDelay(30, () -> {
                h.assertTrue(f.liveSwords().isEmpty() && PhantomBladeSystem.swarm(f.player) == null, "Offhand and container inventory do not keep an automatic weapon active");
                close(h, target.getHealth(), initial, "The removed core cannot finish an outbound hit"); h.succeed();
            });
        };
        f.core("vibranium", 0); PhantomBladeSystem.refresh(f.player);
    }

    @GameTest(template = "empty")
    public static void spectatorLogoutDeathCloneAndDimensionChangesCleanTheirOldController(GameTestHelper h) {
        Fixture f = new Fixture(h); f.core("vibranium", 0); PhantomBladeSystem.refresh(f.player);
        var first = PhantomBladeSystem.swarm(f.player);
        f.player.setGameMode(GameType.SPECTATOR); PhantomBladeSystem.refresh(f.player);
        h.assertTrue(first.isRemoved() && PhantomBladeSystem.swarm(f.player) == null, "Spectators cannot keep the weapon active");
        f.player.setGameMode(GameType.SURVIVAL); PhantomBladeSystem.refresh(f.player);
        var logout = PhantomBladeSystem.swarm(f.player); NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.player));
        h.assertTrue(logout.isRemoved(), "Logout cleans the transient controller");
        PhantomBladeSystem.refresh(f.player); var death = PhantomBladeSystem.swarm(f.player);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(f.player, f.player.damageSources().generic()));
        h.assertTrue(death.isRemoved(), "Player death cleans the transient controller");
        PhantomBladeSystem.refresh(f.player); var dimension = PhantomBladeSystem.swarm(f.player);
        var underworld = f.player.server.getLevel(MiteWorldDimensions.UNDERWORLD);
        f.player.teleportTo(underworld, .5D, 250D, .5D, 0F, 0F);
        h.assertTrue(dimension.isRemoved(), "A real dimension transfer cleans the departed world's controller");
        var home = h.absolutePos(new BlockPos(4, 3, 4)).getCenter(); f.player.teleportTo(h.getLevel(), home.x, home.y, home.z, 0F, 0F);
        PhantomBladeSystem.refresh(f.player); var cloned = PhantomBladeSystem.swarm(f.player);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(f.player, f.player, false));
        h.assertTrue(cloned.isRemoved(), "Cloning removes the previous controller"); h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void cooldownSurvivesCoreSwitchNbtRelogDeathCloneAndRealDimensionTransfers(GameTestHelper h) {
        Fixture f = new Fixture(h); f.enemy(9, 3, 4); f.core("copper", 0); PhantomBladeSystem.refresh(f.player);
        f.damageListener(event -> {
            if (f.hits != 1) return;
            h.runAfterDelay(2, () -> {
                var copper = PhantomBladeRules.profile("copper"); var before = PhantomBladeState.current(f.player, copper);
                h.assertTrue(before.nextAttack() > PhantomBladeState.now(f.player), "The real first hit has an active cooldown");
                f.player.getInventory().clearContent(); PhantomBladeSystem.refresh(f.player);
                f.core("vibranium", 0); PhantomBladeSystem.refresh(f.player);
                h.assertValueEqual(PhantomBladeState.current(f.player, PhantomBladeRules.profile("vibranium")), before, "Removing and changing cores cannot reset a committed cooldown");
                f.relog(); PhantomBladeSystem.refresh(f.player);
                h.assertValueEqual(PhantomBladeState.current(f.player, copper), before, "Real player save/load and a new online player preserve the exact timer");
                f.player.getPersistentData().putString("other_mod:must_not_copy", "unrelated");
                Fixture replacement = new Fixture(h); replacement.player.moveTo(h.absolutePos(new BlockPos(25, 3, 25)).getCenter());
                NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement.player, f.player, true));
                h.assertValueEqual(PhantomBladeState.current(replacement.player, copper), before, "Death cloning preserves only the weapon's cooldown record");
                h.assertFalse(replacement.player.getPersistentData().contains("other_mod:must_not_copy"), "Unrelated player data is not copied");
                NeoForge.EVENT_BUS.post(new LivingDeathEvent(f.player, f.player.damageSources().generic())); PhantomBladeSystem.refresh(f.player);
                var underworld = f.player.server.getLevel(MiteWorldDimensions.UNDERWORLD);
                f.player.teleportTo(underworld, .5D, 250D, .5D, 0F, 0F); PhantomBladeSystem.refresh(f.player);
                h.assertValueEqual(PhantomBladeState.current(f.player, copper), before, "Dimensions share the same absolute cooldown clock");
                var home = h.absolutePos(new BlockPos(4, 3, 4)).getCenter(); f.player.teleportTo(h.getLevel(), home.x, home.y, home.z, 0F, 0F);
                f.core("copper", 0); PhantomBladeSystem.refresh(f.player);
                h.assertValueEqual(PhantomBladeState.current(f.player, copper), before, "Returning to the original material and dimension retains the deadline");
                h.assertFalse(PhantomBladeState.ready(f.player, before), "None of the lifecycle operations makes an unfinished cooldown ready");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void nativeArmorMitigatesHitsAndRealKillsKeepPlayerCreditAndExperience(GameTestHelper h) {
        Fixture f = new Fixture(h); var target = f.enemy(9, 3, 4); target.getAttribute(Attributes.ARMOR).setBaseValue(20D);
        float initial = target.getHealth();
        boolean loot = h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(true, h.getLevel().getServer());
        f.cleanup.add(() -> h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(loot, h.getLevel().getServer()));
        int[] deaths = {0}, xp = {0};
        Consumer<LivingDeathEvent> death = event -> {
            if (event.getEntity() != target) return;
            h.assertTrue(event.getSource().getEntity() == f.player && target.getLastHurtByMob() == f.player, "Native lethal damage and retaliation remember the real player owner"); deaths[0]++;
        };
        Consumer<EntityJoinLevelEvent> xpJoin = event -> {
            if (event.getLevel() == h.getLevel() && event.getEntity() instanceof ExperienceOrb orb && orb.distanceToSqr(target) < 16D) xp[0] += orb.value;
        };
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, death); f.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(death));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, xpJoin); f.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(xpJoin));
        f.damageListener(event -> {
            if (f.hits == 1) h.runAfterDelay(2, () -> {
                float lost = initial-target.getHealth(); h.assertTrue(lost > 0F && lost < 3F, "Native armor reduces the genuine three-point hit");
                target.getAttribute(Attributes.ARMOR).setBaseValue(0D); target.setHealth(1F); target.invulnerableTime = 0;
            });
            if (f.hits == 2) h.runAfterDelay(15, () -> {
                h.assertValueEqual(deaths[0], 1, "The next outbound hit commits exactly one native death");
                h.assertValueEqual(xp[0], 5, "An unarmed adult source zombie awards its native five XP exactly once");
                h.assertTrue(f.swords.size() == 1 && !f.swords.getFirst().isRemoved(), "A lethal hit also preserves the returning sword"); h.succeed();
            });
        });
        f.core("vibranium", 0); PhantomBladeSystem.refresh(f.player);
    }

    private static void close(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual-expected) < .00001D, message+": actual="+actual+", expected="+expected);
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        ServerPlayer player;
        EmbeddedChannel channel;
        final List<Entity> mobs = new ArrayList<>();
        final List<Runnable> cleanup = new ArrayList<>();
        final List<PhantomBladeProjectile> swords = new ArrayList<>();
        final List<PhantomBladeProjectile> launches = new ArrayList<>();
        Consumer<PhantomBladeProjectile> onLaunch = sword -> {};
        Consumer<LivingIncomingDamageEvent> incoming = event -> {};
        PhantomBladeProjectile lastOutbound;
        int hits;
        boolean closed;

        Fixture(GameTestHelper h) {
            this.h = h;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-phantom"), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true); player.setInvulnerable(true);
            player.moveTo(h.absolutePos(new BlockPos(4, 3, 4)).getCenter()); player.getInventory().clearContent(); player.removeAllEffects();
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (event.getEntity() instanceof PhantomBladeProjectile sword && sword.getOwner() == player) swords.add(sword);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, join); cleanup.add(() -> NeoForge.EVENT_BUS.unregister(join));
            Consumer<LivingIncomingDamageEvent> damage = event -> {
                if (event.getSource().getDirectEntity() instanceof PhantomBladeProjectile sword && sword.getOwner() == player) { ++hits; incoming.accept(event); }
            };
            NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, damage); cleanup.add(() -> NeoForge.EVENT_BUS.unregister(damage));
            h.onEachTick(() -> {
                if (closed) return;
                var live = liveSwords(); h.assertTrue(live.size() <= 1, "At every tick the owner has at most one physical sword");
                PhantomBladeProjectile outbound = null;
                if (!live.isEmpty()) {
                    var sword = live.getFirst(); h.assertValueEqual(sword.bladeIndex(), 0, "The only sword always uses slot zero");
                    if (!sword.isDocked() && !sword.isReturning()) outbound = sword;
                }
                if (outbound != null && lastOutbound != outbound) { launches.add(outbound); onLaunch.accept(outbound); }
                lastOutbound = outbound;
            });
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testAddedForRerun(GameTestInfo old, GameTestInfo next, GameTestRunner runner) { close(); }
            });
        }
        List<PhantomBladeProjectile> liveSwords() { return swords.stream().filter(sword -> !sword.isRemoved() && sword.getOwner() == player && sword.level() == player.level()).toList(); }
        void core(String material, int slot) { player.getInventory().items.set(slot, new ItemStack(MitePhantomBlades.get(material))); }
        void relog() {
            ServerPlayer original = player; CompoundTag saved = original.saveWithoutId(new CompoundTag());
            original.server.getPlayerList().remove(original); channel.finishAndReleaseAll();
            var cookie = CommonListenerCookie.createInitial(original.getGameProfile(), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.load(saved); player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true); player.setInvulnerable(true);
            h.assertTrue(player != original && PhantomBladeSystem.active(player), "Relog uses a distinct genuinely online ServerPlayer");
        }
        <T extends Mob> T mob(EntityType<T> type, int x, int y, int z) {
            T mob = type.create(h.getLevel()); mob.setNoAi(true); mob.setNoGravity(true); mob.moveTo(h.absolutePos(new BlockPos(x, y, z)).getCenter());
            mob.getPersistentData().putBoolean("modern_ite:explicit_command_spawn", true);
            h.assertTrue(h.getLevel().addFreshEntity(mob) && mob.isAddedToLevel(), "The target fixture is a real inserted living mob"); mobs.add(mob); return mob;
        }
        net.minecraft.world.entity.monster.Zombie enemy(int x, int y, int z) {
            var zombie = mob(EntityType.ZOMBIE, x, y, z); zombie.setBaby(false);
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) zombie.setItemSlot(slot, ItemStack.EMPTY);
            zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D); zombie.getAttribute(Attributes.ARMOR).setBaseValue(0D);
            zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1D); zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0D);
            for (int roofX = x-1; roofX <= x+1; roofX++) for (int roofZ = z-1; roofZ <= z+1; roofZ++) h.setBlock(roofX, y+4, roofZ, Blocks.STONE);
            io.github.ningmeng123.mite.entity.MonsterDayScaling.refresh(zombie); zombie.setHealth(zombie.getMaxHealth()); return zombie;
        }
        void damageListener(Consumer<LivingIncomingDamageEvent> listener) { incoming = listener; }
        void wall(int x) { for (int y = 2; y <= 8; y++) for (int z = 1; z <= 10; z++) h.setBlock(x, y, z, Blocks.STONE); }
        @Override public void close() {
            if (closed) return; closed = true; PhantomBladeSystem.clear(player); mobs.forEach(Entity::discard);
            for (int index = cleanup.size()-1; index >= 0; --index) cleanup.get(index).run();
            player.server.getPlayerList().remove(player); channel.finishAndReleaseAll();
        }
    }
}

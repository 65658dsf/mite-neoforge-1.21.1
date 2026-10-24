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

/** Real online players, ticking formations, physical projectiles and native damage transactions. @author NingMeng123 */
@GameTestHolder("mite_phantom")
@PrefixGameTestTemplate(false)
public final class PhantomBladeGameTests {
    @GameTest(template = "empty")
    public static void onlyTheStrongestMainInventoryCoreOwnsOneNonpersistentFormation(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.player.getInventory().offhand.set(0, new ItemStack(MitePhantomBlades.get("vibranium")));
        f.player.getInventory().armor.set(0, new ItemStack(MitePhantomBlades.get("adamantium")));
        PhantomBladeSystem.refresh(f.player);
        h.assertTrue(PhantomBladeSystem.swarm(f.player) == null, "Offhand and armor slots cannot activate a formation");
        f.core("copper", 0); f.core("silver", 1);
        PhantomBladeSystem.refresh(f.player);
        PhantomBladeSwarm first = PhantomBladeSystem.swarm(f.player);
        h.assertTrue(first != null && first.isAddedToLevel(), "A real synchronized formation is added to the online player's level");
        h.assertValueEqual(first.material(), "copper", "Equal-strength copper and silver use the earliest occupied main-inventory slot");
        h.assertValueEqual(first.ownerId(), f.player.getId(), "The client receives the real owner entity ID");
        h.assertValueEqual(first.orbitBladeMask(), 255, "All eight orbit swords are initially visible");
        for (int slot = 2; slot < 36; slot++) f.core("copper", slot);
        PhantomBladeSystem.refresh(f.player);
        h.assertTrue(PhantomBladeSystem.swarm(f.player) == first, "Filling the main inventory never creates another controller");
        f.core("vibranium", 35); PhantomBladeSystem.refresh(f.player);
        PhantomBladeSwarm upgraded = PhantomBladeSystem.swarm(f.player);
        h.assertTrue(first.isRemoved() && upgraded != first && upgraded.material().equals("vibranium"), "The single strongest core replaces the former formation");
        h.assertFalse(upgraded.save(new CompoundTag()), "Formation entities are excluded from normal world entity saves");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 155)
    public static void copperLaunchesOneActualBladePerSixtyTicksAndKeepsItsHalfPointIncomingDamage(GameTestHelper h) {
        Fixture f = new Fixture(h); var target = f.enemy(8, 3, 4);
        f.core("copper", 0); PhantomBladeSystem.refresh(f.player);
        float initial = target.getHealth();
        f.damageListener(event -> {
            h.assertTrue(event.getSource().getEntity() == f.player && event.getSource().getDirectEntity() instanceof PhantomBladeProjectile,
                    "Ordinary projectile damage retains player ownership");
            close(h, event.getAmount(), .5D, "Copper submits the configured half point to the real damage pipeline");
            h.assertFalse(event.getSource().is(DamageTypeTags.BYPASSES_ARMOR), "The attack has no armor-bypass tag");
        });
        h.runAtTickTime(62, () -> h.assertTrue(f.shots.size() == 1 && f.shots.getFirst().isAddedToLevel(),
                "The outbound blade is a real inserted entity before its physical collision"));
        h.runAtTickTime(135, () -> {
            h.assertValueEqual(f.launchedAt, List.of(60, 120), "Real formation ticks launch exactly one blade at60 and120, never eight simultaneous hits");
            h.assertValueEqual(f.hits, 2, "Both real flights reach the target");
            close(h, target.getHealth(), initial-1D, "An unarmored modern enemy receives two genuine half-point hits");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void manyLowAndHighTierCoresStillUseOnlyTheTwentyTickVibraniumCadence(GameTestHelper h) {
        Fixture f = new Fixture(h); f.enemy(11, 3, 4);
        for (int slot = 0; slot < 36; slot++) f.core(slot % 2 == 0 ? "vibranium" : "copper", slot);
        PhantomBladeSystem.refresh(f.player);
        h.runAtTickTime(66, () -> {
            h.assertValueEqual(f.launchedAt, List.of(20, 40, 60), "Thirty-six cores produce exactly the single strongest formation's cadence");
            h.assertTrue(f.shots.stream().allMatch(shot -> shot.material().equals("vibranium") && shot.owner() == f.player),
                    "Every attack synchronizes the strongest material and real owner");
            h.assertTrue(f.shots.stream().noneMatch(shot -> shot.save(new CompoundTag())), "In-flight swords cannot survive a world save");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 55)
    public static void neutralMobsPetsAlliesOutOfRangeEnemiesAndOpaqueWallsAreNotAutomaticTargets(GameTestHelper h) {
        Fixture f = new Fixture(h); f.core("vibranium", 0);
        var enderman = f.mob(EntityType.ENDERMAN, 7, 3, 7);
        var pig = f.mob(EntityType.PIG, 7, 3, 8);
        var wolf = f.mob(EntityType.WOLF, 7, 3, 9); wolf.tame(f.player);
        var far = f.enemy(15, 3, 4);
        var ally = f.enemy(8, 3, 2);
        var scoreboard = h.getLevel().getScoreboard();
        var team = scoreboard.addPlayerTeam("phantom_"+f.player.getId());
        scoreboard.addPlayerToTeam(f.player.getScoreboardName(), team); scoreboard.addPlayerToTeam(ally.getScoreboardName(), team);
        f.cleanup.add(() -> scoreboard.removePlayerTeam(team));
        h.assertFalse(PhantomBladeSystem.validTarget(f.player, enderman), "A neutral Enemy without anger at the owner is excluded");
        h.assertFalse(PhantomBladeSystem.validTarget(f.player, pig), "Neutral livestock is excluded");
        h.assertFalse(PhantomBladeSystem.validTarget(f.player, wolf), "Tamed animals are excluded");
        h.assertFalse(PhantomBladeSystem.validTarget(f.player, ally), "The owner's scoreboard allies are excluded");
        h.assertFalse(PhantomBladeSystem.validTarget(f.player, far), "The radius uses a strict ten-block distance");
        var blocked = f.enemy(10, 3, 4);
        f.wall(7);
        h.assertFalse(f.player.hasLineOfSight(blocked), "The real opaque wall blocks the owner's target visibility");
        PhantomBladeSystem.refresh(f.player);
        h.runAtTickTime(40, () -> {
            h.assertTrue(f.shots.isEmpty(), "No physical sword is launched at any excluded or hidden entity");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 55)
    public static void aWallPlacedAfterLaunchStopsTheRealProjectileBeforeTheEnemy(GameTestHelper h) {
        Fixture f = new Fixture(h); var target = f.enemy(13, 3, 4); float initial = target.getHealth();
        f.onLaunch = shot -> {
            h.assertValueEqual(PhantomBladeSystem.swarm(f.player).orbitBladeMask(), 255, "The first launch leaves the formation from an available orbit slot");
            f.wall(9);
        };
        f.core("vibranium", 0); PhantomBladeSystem.refresh(f.player);
        h.runAtTickTime(35, () -> {
            h.assertValueEqual(f.shots.size(), 1, "Visibility was clear at launch before the wall was inserted");
            h.assertTrue(f.shots.getFirst().isRemoved(), "The real sword collides with the newly placed wall");
            h.assertValueEqual(f.hits, 0, "No damage transaction reaches an enemy through the wall");
            close(h, target.getHealth(), initial, "Collision cannot damage the hidden target");
            h.assertValueEqual(PhantomBladeSystem.swarm(f.player).orbitBladeMask(), 254, "A blocked sword remains consumed until the whole formation recharges");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 55)
    public static void incomingDamageProtectionAndProjectileImpactCancellationRemainAuthoritative(GameTestHelper h) {
        Fixture f = new Fixture(h); var target = f.enemy(10, 3, 4); float initial = target.getHealth();
        f.damageListener(event -> event.setCanceled(true));
        f.core("vibranium", 0); PhantomBladeSystem.refresh(f.player);
        h.runAtTickTime(35, () -> {
            h.assertValueEqual(f.hits, 1, "The actual physical hit invokes the cancellable native incoming-damage event");
            close(h, target.getHealth(), initial, "A protection listener cancels all health loss");
            Consumer<ProjectileImpactEvent> impact = event -> {
                if (event.getProjectile() instanceof PhantomBladeProjectile blade && blade.getOwner() == f.player) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(ProjectileImpactEvent.class, impact); f.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(impact));
        });
        h.runAtTickTime(52, () -> {
            h.assertValueEqual(f.shots.size(), 2, "The next normal cooldown still creates one real blade");
            h.assertValueEqual(f.hits, 1, "Cancelling the second projectile impact prevents a second damage transaction");
            close(h, target.getHealth(), initial, "Both types of protection leave the target unchanged"); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 55)
    public static void aFriendlyLivingBodyBlocksTheBladeWithoutReceivingCollateralDamage(GameTestHelper h) {
        Fixture f = new Fixture(h); var target = f.enemy(13, 3, 4); var cow = f.mob(EntityType.COW, 8, 3, 4);
        float enemyHealth = target.getHealth(), animalHealth = cow.getHealth();
        f.core("vibranium", 0); PhantomBladeSystem.refresh(f.player);
        h.runAtTickTime(35, () -> {
            h.assertValueEqual(f.shots.size(), 1, "A visible enemy causes the one scheduled launch");
            h.assertTrue(f.shots.getFirst().isRemoved(), "The neutral animal physically intercepts and stops the projectile");
            close(h, target.getHealth(), enemyHealth, "The sword does not tunnel through a neutral body");
            close(h, cow.getHealth(), animalHealth, "Interception never hurts neutral livestock"); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void removingTheCoreIntoAContainerDestroysTheFormationAndEveryInFlightSword(GameTestHelper h) {
        Fixture f = new Fixture(h); var target = f.enemy(13, 3, 4); float initial = target.getHealth();
        f.core("vibranium", 0); PhantomBladeSystem.refresh(f.player);
        h.runAtTickTime(22, () -> {
            PhantomBladeSwarm swarm = PhantomBladeSystem.swarm(f.player);
            h.assertTrue(f.shots.size() == 1 && !f.shots.getFirst().isRemoved() && swarm.orbitBladeMask() != 255,
                    "Cleanup begins during an actual outbound flight with one hidden orbit sword");
            h.setBlock(3, 3, 3, Blocks.CHEST);
            ChestBlockEntity chest = (ChestBlockEntity)h.getBlockEntity(new BlockPos(3, 3, 3));
            chest.setItem(0, f.player.getInventory().removeItemNoUpdate(0)); PhantomBladeSystem.refresh(f.player);
            h.assertTrue(swarm.isRemoved() && f.shots.getFirst().isRemoved() && PhantomBladeSystem.swarm(f.player) == null,
                    "Moving the core into a real container immediately removes both controller and in-flight attack");
            f.player.getInventory().offhand.set(0, chest.removeItemNoUpdate(0)); PhantomBladeSystem.refresh(f.player);
            h.assertTrue(PhantomBladeSystem.swarm(f.player) == null, "Moving it onward to the offhand cannot restore the controller");
        });
        h.runAtTickTime(55, () -> {
            h.assertValueEqual(f.shots.size(), 1, "No attacks continue from offhand or container inventory");
            close(h, target.getHealth(), initial, "A removed core cannot finish an already launched hit"); h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void spectatorLogoutDeathCloneAndDimensionChangesRemoveTheTemporaryFormation(GameTestHelper h) {
        Fixture f = new Fixture(h); f.core("vibranium", 0); PhantomBladeSystem.refresh(f.player);
        var first = PhantomBladeSystem.swarm(f.player);
        f.player.setGameMode(GameType.SPECTATOR); PhantomBladeSystem.refresh(f.player);
        h.assertTrue(first.isRemoved() && PhantomBladeSystem.swarm(f.player) == null, "Spectators cannot retain an automatic attack");
        f.player.setGameMode(GameType.SURVIVAL); PhantomBladeSystem.refresh(f.player);
        var logout = PhantomBladeSystem.swarm(f.player);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.player));
        h.assertTrue(logout.isRemoved(), "The logout lifecycle cleans the transient formation");
        PhantomBladeSystem.refresh(f.player); var death = PhantomBladeSystem.swarm(f.player);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(f.player, f.player.damageSources().generic()));
        h.assertTrue(death.isRemoved(), "A completed player-death event cleans the transient formation");
        PhantomBladeSystem.refresh(f.player); var dimension = PhantomBladeSystem.swarm(f.player);
        var underworld = f.player.server.getLevel(MiteWorldDimensions.UNDERWORLD);
        f.player.teleportTo(underworld, .5D, 250D, .5D, 0F, 0F);
        h.assertTrue(dimension.isRemoved(), "A real cross-dimension teleport cleans the original world's formation");
        f.player.teleportTo(h.getLevel(), h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5D, 3.5D, 4.5D)).x,
                h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5D, 3.5D, 4.5D)).y,
                h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5D, 3.5D, 4.5D)).z, 0F, 0F);
        PhantomBladeSystem.refresh(f.player); var cloned = PhantomBladeSystem.swarm(f.player);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(f.player, f.player, false));
        h.assertTrue(cloned.isRemoved(), "Cloning cannot leave an old controller attached to the departing instance");
        f.player.getInventory().clearContent(); PhantomBladeSystem.refresh(f.player);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 85)
    public static void nativeArmorStillMitigatesHitsAndRealKillsKeepPlayerCreditAndNativeExperience(GameTestHelper h) {
        Fixture f = new Fixture(h); var target = f.enemy(8, 3, 4);
        target.getAttribute(Attributes.ARMOR).setBaseValue(20D);
        float initial = target.getHealth();
        f.core("vibranium", 0); PhantomBladeSystem.refresh(f.player);
        h.runAtTickTime(34, () -> {
            float lost = initial-target.getHealth();
            h.assertTrue(lost > 0F && lost < 3F, "Actual armor reduces the real hit without making a low-base blade unable to hurt");
            target.getAttribute(Attributes.ARMOR).setBaseValue(0D); target.setHealth(1F); target.invulnerableTime = 0;
        });
        boolean loot = h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(true, h.getLevel().getServer());
        f.cleanup.add(() -> h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(loot, h.getLevel().getServer()));
        int[] deaths = {0}, xp = {0};
        Consumer<LivingDeathEvent> death = event -> {
            if (event.getEntity() != target) return;
            h.assertTrue(event.getSource().getEntity() == f.player && target.getLastHurtByMob() == f.player,
                    "Native lethal damage and retaliation remember the real player owner"); deaths[0]++;
        };
        Consumer<EntityJoinLevelEvent> xpJoin = event -> {
            if (event.getLevel() == h.getLevel() && event.getEntity() instanceof ExperienceOrb orb
                    && orb.distanceToSqr(target) < 16D) xp[0] += orb.value;
        };
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, death); f.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(death));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, xpJoin); f.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(xpJoin));
        h.runAtTickTime(75, () -> {
            h.assertValueEqual(deaths[0], 1, "The real second hit commits exactly one native death transaction");
            // mobs-031.json source tw declares experience5; R196VanillaLivingMixin preserves that exact native reward.
            h.assertValueEqual(xp[0], 5, "The unarmed adult source zombie creates its native five experience points exactly once");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1180)
    public static void copperSpendsEightSequentialSwordsThenRechargesForExactlyThirtySeconds(GameTestHelper h) {
        rechargeCycle(h, "copper", 60, 600, false);
    }

    @GameTest(template = "empty", timeoutTicks = 1180)
    public static void silverSpendsEightSequentialSwordsThenRechargesForExactlyThirtySeconds(GameTestHelper h) {
        rechargeCycle(h, "silver", 60, 600, false);
    }

    @GameTest(template = "empty", timeoutTicks = 530)
    public static void vibraniumRechargesForFifteenSecondsAcrossCoreSwitchesNbtRelogDeathCloneAndDimensions(GameTestHelper h) {
        rechargeCycle(h, "vibranium", 20, 300, true);
    }

    private static void rechargeCycle(GameTestHelper h, String material, int interval, int recharge, boolean persistence) {
        Fixture f = new Fixture(h); f.enemy(9, 3, 4);
        f.core(material, 0); PhantomBladeSystem.refresh(f.player);
        f.onLaunch = shot -> {
            if (f.shots.size() != 8) return;
            long eighthTime = PhantomBladeState.now(f.player);
            h.runAfterDelay(2, () -> {
                var spent = PhantomBladeState.current(f.player, PhantomBladeRules.profile(material));
                h.assertValueEqual(spent.charges(), 0, "The eighth accepted real launch consumes the final sword");
                h.assertValueEqual(spent.rechargeEnd(), eighthTime+recharge, "Recharge begins at the eighth real launch using the shared overworld clock");
                h.assertValueEqual(spent.nextAttack(), eighthTime+recharge+interval, "The next cycle waits the standard interval after all eight refill");
                h.assertValueEqual(PhantomBladeSystem.swarm(f.player).orbitBladeMask(), 0, "An exhausted formation displays no remaining swords");
                if (persistence) {
                    var before = spent;
                    f.player.getInventory().clearContent(); PhantomBladeSystem.refresh(f.player);
                    f.core("copper", 0); PhantomBladeSystem.refresh(f.player);
                    h.assertValueEqual(PhantomBladeState.current(f.player, PhantomBladeRules.profile("copper")), before,
                            "Removing the core and selecting a different tier cannot reset charges or either absolute timer");
                    h.assertValueEqual(PhantomBladeSystem.swarm(f.player).orbitBladeMask(), 0, "A replacement formation restores the empty synchronized mask");
                    f.relog(); PhantomBladeSystem.refresh(f.player);
                    h.assertValueEqual(PhantomBladeState.current(f.player, PhantomBladeRules.profile("copper")), before,
                            "A real player NBT save and logout/login into a new ServerPlayer retain the exact exhausted state");
                    f.player.getPersistentData().putString("other_mod:must_not_copy", "unrelated");
                    Fixture replacement = new Fixture(h);
                    replacement.player.moveTo(h.absolutePos(new BlockPos(25, 3, 25)).getCenter());
                    NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement.player, f.player, true));
                    h.assertValueEqual(PhantomBladeState.current(replacement.player, PhantomBladeRules.profile("vibranium")), before,
                            "The death-clone event preserves only the existing charge and timing record");
                    h.assertFalse(replacement.player.getPersistentData().contains("other_mod:must_not_copy"), "Cloning does not copy unrelated persistent keys");
                    NeoForge.EVENT_BUS.post(new LivingDeathEvent(f.player, f.player.damageSources().generic()));
                    PhantomBladeSystem.refresh(f.player);
                    var underworld = f.player.server.getLevel(MiteWorldDimensions.UNDERWORLD);
                    f.player.teleportTo(underworld, .5D, 250D, .5D, 0F, 0F); PhantomBladeSystem.refresh(f.player);
                    h.assertValueEqual(PhantomBladeState.current(f.player, PhantomBladeRules.profile("copper")), before,
                            "A real dimension transfer uses the same absolute overworld clock and cannot replenish swords");
                    var home = h.absolutePos(new BlockPos(4, 3, 4)).getCenter();
                    f.player.teleportTo(h.getLevel(), home.x, home.y, home.z, 0F, 0F);
                    f.core(material, 0); PhantomBladeSystem.refresh(f.player);
                    h.assertValueEqual(PhantomBladeState.current(f.player, PhantomBladeRules.profile(material)), before,
                            "Returning to the original tier and dimension retains the remaining recharge");
                }
            });
            h.runAfterDelay(recharge-1, () -> {
                h.assertValueEqual(PhantomBladeState.current(f.player, PhantomBladeRules.profile(material)).charges(), 0,
                        "One tick before the deadline, all eight remain spent");
                h.assertValueEqual(f.shots.size(), 8, "No ninth launch occurs during recharge");
                h.assertValueEqual(PhantomBladeSystem.swarm(f.player).orbitBladeMask(), 0, "The synchronized orbit remains empty until the full deadline");
            });
            h.runAfterDelay(recharge+1, () -> {
                h.assertValueEqual(PhantomBladeState.current(f.player, PhantomBladeRules.profile(material)).charges(), 8,
                        "Passing the real deadline restores all eight swords together");
                h.assertValueEqual(PhantomBladeSystem.swarm(f.player).orbitBladeMask(), 255, "The real formation synchronizes eight refilled orbit swords");
                h.assertValueEqual(f.shots.size(), 8, "Refilling does not simultaneously fire the entire formation");
            });
            h.runAfterDelay(recharge+interval-1, () -> h.assertValueEqual(f.shots.size(), 8,
                    "After refilling, the normal per-sword interval must still finish"));
            h.runAfterDelay(recharge+interval+2, () -> {
                h.assertValueEqual(f.shots.size(), 9, "Only the first sword of the refilled cycle launches after its normal interval");
                h.assertValueEqual(f.launchTimes.get(8), eighthTime+recharge+interval, "The ninth real projectile obeys the recharge plus normal interval exactly");
                for (int index = 1; index < 8; index++) h.assertValueEqual(f.launchTimes.get(index)-f.launchTimes.get(index-1), (long)interval,
                        "Each of the first eight physical swords launches individually at the material's fixed interval");
                h.assertValueEqual(PhantomBladeState.current(f.player, PhantomBladeRules.profile(material)).charges(), 7,
                        "The refilled cycle consumes exactly one charge");
                h.succeed();
            });
        };
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
        final List<PhantomBladeProjectile> shots = new ArrayList<>();
        final List<Integer> launchedAt = new ArrayList<>();
        final List<Long> launchTimes = new ArrayList<>();
        Consumer<PhantomBladeProjectile> onLaunch = shot -> {};
        Consumer<LivingIncomingDamageEvent> incoming = event -> {};
        int hits;
        boolean closed;

        Fixture(GameTestHelper h) {
            this.h = h;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-phantom"), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true); player.setInvulnerable(true);
            player.moveTo(h.absolutePos(new BlockPos(4, 3, 4)).getCenter());
            player.getInventory().clearContent(); player.removeAllEffects();
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (event.getEntity() instanceof PhantomBladeProjectile shot && shot.getOwner() == player) {
                    shots.add(shot); var swarm = PhantomBladeSystem.swarm(player);
                    launchedAt.add(swarm.tickCount); launchTimes.add(PhantomBladeState.now(player)); onLaunch.accept(shot);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, join);
            cleanup.add(() -> NeoForge.EVENT_BUS.unregister(join));
            Consumer<LivingIncomingDamageEvent> damage = event -> {
                if (event.getSource().getDirectEntity() instanceof PhantomBladeProjectile shot && shot.getOwner() == player) { ++hits; incoming.accept(event); }
            };
            NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, damage); cleanup.add(() -> NeoForge.EVENT_BUS.unregister(damage));
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testAddedForRerun(GameTestInfo old, GameTestInfo next, GameTestRunner runner) { close(); }
            });
        }
        void core(String material, int slot) { player.getInventory().items.set(slot, new ItemStack(MitePhantomBlades.get(material))); }
        void relog() {
            ServerPlayer original = player;
            CompoundTag saved = original.saveWithoutId(new CompoundTag());
            original.server.getPlayerList().remove(original); channel.finishAndReleaseAll();
            var cookie = CommonListenerCookie.createInitial(original.getGameProfile(), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.load(saved); player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true); player.setInvulnerable(true);
            h.assertTrue(player != original && PhantomBladeSystem.active(player), "The relog fixture uses a distinct, genuinely online ServerPlayer");
        }
        <T extends Mob> T mob(EntityType<T> type, int x, int y, int z) {
            T mob = type.create(h.getLevel()); mob.setNoAi(true); mob.setNoGravity(true);
            mob.moveTo(h.absolutePos(new BlockPos(x, y, z)).getCenter());
            // The fixture explicitly creates this mob; ITE's first-day natural-spawn grace must not classify it as automatic.
            mob.getPersistentData().putBoolean("modern_ite:explicit_command_spawn", true);
            h.assertTrue(h.getLevel().addFreshEntity(mob) && mob.isAddedToLevel(), "The target fixture is a real inserted living mob");
            mobs.add(mob); return mob;
        }
        net.minecraft.world.entity.monster.Zombie enemy(int x, int y, int z) {
            // Both the base and ITE admit source zombies; addon policies may intentionally remove modern raid mobs.
            var zombie = mob(EntityType.ZOMBIE, x, y, z);
            zombie.setBaby(false);
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) zombie.setItemSlot(slot, ItemStack.EMPTY);
            zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
            zombie.getAttribute(Attributes.ARMOR).setBaseValue(0D);
            zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1D);
            zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0D);
            for (int roofX = x-1; roofX <= x+1; roofX++) for (int roofZ = z-1; roofZ <= z+1; roofZ++)
                h.setBlock(roofX, y+4, roofZ, Blocks.STONE);
            io.github.ningmeng123.mite.entity.MonsterDayScaling.refresh(zombie);
            zombie.setHealth(zombie.getMaxHealth());
            return zombie;
        }
        void damageListener(Consumer<LivingIncomingDamageEvent> listener) { incoming = listener; }
        void wall(int x) { for (int y = 2; y <= 8; y++) for (int z = 1; z <= 10; z++) h.setBlock(x, y, z, Blocks.STONE); }
        @Override public void close() {
            if (closed) return; closed = true;
            PhantomBladeSystem.clear(player); mobs.forEach(Entity::discard);
            for (int index = cleanup.size()-1; index >= 0; --index) cleanup.get(index).run();
            player.server.getPlayerList().remove(player); channel.finishAndReleaseAll();
        }
    }
}

package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196MonsterAi;
import io.github.ningmeng123.mite.entity.SourceMonsterTargetMemory;
import io.github.ningmeng123.mite.entity.SourceMonsterTargeting;
import io.github.ningmeng123.mite.entity.SourceRangedAttackGoal;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real accepted damage interrupts ongoing revenge without replacing species-owned AI. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class MonsterRetaliationGameTests {
    private MonsterRetaliationGameTests() {}

    @GameTest(template="empty")
    public static void runningSourceAndVindicatorRevengeFollowTheNewAcceptedPlayerHit(GameTestHelper h) {
        withFixture(h, f -> {
            ServerPlayer third = f.player(2D, 1D);
            for (var type : List.of(MiteMobs.type(MobKind.GHOUL), EntityType.ZOMBIE, EntityType.VINDICATOR)) {
                PathfinderMob mob = f.mob(type);
                f.startRevenge(mob);
                f.hit(mob, f.attacker);
                h.assertTrue(mob.getTarget() == f.attacker, "A valid new player hit interrupts an already running revenge goal: " + type);
                f.selectorsKeep(mob, f.attacker, 12);
                f.hit(mob, third);
                h.assertTrue(mob.getTarget() == third, "A fresh accepted player C hit can interrupt B during its protection interval");
                f.selectorsKeep(mob, third, 8);
                mob.discard();
            }
        });
    }

    @GameTest(template="empty")
    public static void anOwnedArrowSwitchesTheRangedSkeletonWithoutReplacingItsAttackGoal(GameTestHelper h) {
        withFixture(h, f -> {
            var skeleton = f.mob(EntityType.SKELETON);
            skeleton.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            ((AbstractSkeleton)skeleton).reassessWeaponGoal();
            f.startRevenge(skeleton);
            var arrow = new Arrow(EntityType.ARROW, h.getLevel()); arrow.setOwner(f.attacker);
            skeleton.invulnerableTime = 0;
            float before = skeleton.getHealth();
            skeleton.hurt(h.getLevel().damageSources().arrow(arrow, f.attacker), 1F);
            h.assertTrue(skeleton.getHealth() < before && skeleton.getTarget() == f.attacker,
                    "Actual projectile damage identifies its player owner as the new threat");
            f.selectorsKeep(skeleton, f.attacker, 8);
            h.assertTrue(skeleton.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof SourceRangedAttackGoal),
                    "Retaliation preserves the skeleton's existing ranged attack implementation");
        });
    }

    @GameTest(template="empty")
    public static void creeperNearestRefreshCannotImmediatelyUndoAHitButItsProtectionExpires(GameTestHelper h) {
        withFixture(h, f -> {
            var creeper = f.mob(EntityType.CREEPER);
            creeper.setTarget(f.original);
            creeper.getRandom().setSeed(1L); creeper.targetSelector.tick();
            h.assertTrue(creeper.getTarget() == f.original, "The source nearest goal starts with player A");
            f.hit(creeper, f.attacker);
            h.assertTrue(creeper.getTarget() == f.attacker, "A visible player B hit interrupts the creeper's higher-priority nearest goal");
            f.original.setPos(creeper.position().add(.8D, 0D, 0D));
            long refreshSeed = 0L;
            while (RandomSource.create(refreshSeed).nextInt(40) != 0) refreshSeed++;
            for (int tick = 0; tick < 12; tick++) {
                creeper.getRandom().setSeed(refreshSeed); creeper.targetSelector.tick();
                h.assertTrue(creeper.getTarget() == f.attacker, "Even a forced nearest-refresh roll cannot undo a recent accepted hit");
            }
            var memory = (SourceMonsterTargetMemory)creeper;
            memory.mite$recentPlayerThreat(f.attacker.getUUID(), h.getLevel().getGameTime());
            creeper.getRandom().setSeed(refreshSeed); creeper.targetSelector.tick();
            h.assertTrue(creeper.getTarget() == f.original, "After expiry the original bounded nearest-refresh policy may select the closer visible player again");
        });
    }

    @GameTest(template="empty")
    public static void nearbyPlayersAndCanceledZeroOrAbsorbedDamageDoNotStealRevenge(GameTestHelper h) {
        withFixture(h, f -> {
            var mob = f.mob(EntityType.VINDICATOR); f.startRevenge(mob);
            f.selectorsKeep(mob, f.original, 8);
            Consumer<LivingIncomingDamageEvent> cancel = event -> { if (event.getEntity() == mob) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, cancel);
            try { f.attempt(mob, f.attacker); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
            f.selectorsKeep(mob, f.original, 4);
            Consumer<LivingDamageEvent.Pre> zero = event -> { if (event.getEntity() == mob) event.setNewDamage(0F); };
            NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Pre.class, zero);
            float before = mob.getHealth();
            try { f.attempt(mob, f.attacker); } finally { NeoForge.EVENT_BUS.unregister(zero); }
            h.assertTrue(mob.getHealth() == before, "The damage listener really reduced the second attack to zero");
            f.selectorsKeep(mob, f.original, 4);
            mob.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(10D);
            mob.setAbsorptionAmount(10F);
            h.assertValueEqual(mob.getAbsorptionAmount(), 10F, "The fixture raises the native absorption cap before filling its buffer");
            float[] absorbedHit = {Float.NaN, Float.NaN, Float.NaN};
            Consumer<LivingDamageEvent.Pre> absorbedPre = event -> { if (event.getEntity() == mob) absorbedHit[0] = event.getNewDamage(); };
            Consumer<LivingDamageEvent.Post> absorbedPost = event -> {
                if (event.getEntity() == mob) {
                    absorbedHit[1] = event.getNewDamage();
                    absorbedHit[2] = event.getReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ABSORPTION);
                }
            };
            NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Pre.class, absorbedPre);
            NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, absorbedPost);
            try { f.attempt(mob, f.attacker); }
            finally { NeoForge.EVENT_BUS.unregister(absorbedPre); NeoForge.EVENT_BUS.unregister(absorbedPost); }
            h.assertTrue(absorbedHit[0] > 0F && absorbedHit[1] == 0F && absorbedHit[2] > 0F,
                    "The real damage sequence reaches Pre with positive damage, then reports full absorption and zero Post health damage");
            h.assertTrue(mob.getHealth() == before && mob.getAbsorptionAmount() < 10F, "The third attack consumes absorption without losing health");
            f.selectorsKeep(mob, f.original, 4);
        });
    }

    @GameTest(template="empty")
    public static void creativeSpectatorAlliedAndHiddenPlayersCannotForceAnActiveTargetSwitch(GameTestHelper h) {
        withFixture(h, f -> {
            var mob = f.mob(EntityType.ZOMBIE); f.startRevenge(mob);
            f.attacker.setGameMode(GameType.CREATIVE); f.attempt(mob, f.attacker);
            f.selectorsKeep(mob, f.original, 3);
            f.attacker.setGameMode(GameType.SPECTATOR); f.attempt(mob, f.attacker);
            f.selectorsKeep(mob, f.original, 3);
            f.attacker.setGameMode(GameType.SURVIVAL);
            var scoreboard = h.getLevel().getScoreboard();
            var team = scoreboard.addPlayerTeam("retal-" + UUID.randomUUID().toString().substring(0, 6));
            scoreboard.addPlayerToTeam(mob.getScoreboardName(), team); scoreboard.addPlayerToTeam(f.attacker.getScoreboardName(), team);
            try { f.attempt(mob, f.attacker); f.selectorsKeep(mob, f.original, 3); }
            finally { scoreboard.removePlayerTeam(team); }
            f.attacker.setPos(mob.position().add(3D, 0D, 0D));
            BlockPos origin = mob.blockPosition();
            for (int y = 0; y < 4; y++) for (int z = -2; z <= 2; z++)
                h.getLevel().setBlock(origin.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            h.assertFalse(SourceMonsterTargeting.canSee(mob, f.attacker, false), "The new attacker is genuinely hidden by a solid wall");
            f.hit(mob, f.attacker);
            f.selectorsKeep(mob, f.original, 4);
        });
    }

    @GameTest(template="empty")
    public static void targetListenersCanCancelOrReplaceRetaliationWithoutGhostThreatMemory(GameTestHelper h) {
        withFixture(h, f -> {
            var mob = f.mob(EntityType.ZOMBIE); f.startRevenge(mob);
            Consumer<LivingChangeTargetEvent> cancel = event -> {
                if (event.getEntity() == mob && event.getNewAboutToBeSetTarget() == f.attacker) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(LivingChangeTargetEvent.class, cancel);
            try { f.hit(mob, f.attacker); f.selectorsKeep(mob, f.original, 3); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            h.assertTrue(!f.attacker.getUUID().equals(((SourceMonsterTargetMemory)mob).mite$recentPlayerThreat()),
                    "A canceled assignment cannot establish a protected player B target");
            ServerPlayer replacement = f.player(2D, 2D);
            Consumer<LivingChangeTargetEvent> redirect = event -> {
                if (event.getEntity() == mob && event.getNewAboutToBeSetTarget() == f.attacker) event.setNewAboutToBeSetTarget(replacement);
            };
            NeoForge.EVENT_BUS.addListener(LivingChangeTargetEvent.class, redirect);
            try {
                f.hit(mob, f.attacker);
                h.assertTrue(mob.getTarget() == replacement, "A listener's actual player C assignment is respected");
                f.selectorsKeep(mob, replacement, 5);
                h.assertTrue(!f.attacker.getUUID().equals(((SourceMonsterTargetMemory)mob).mite$recentPlayerThreat()),
                        "Redirecting to C cannot leave B in protected threat memory");
            } finally { NeoForge.EVENT_BUS.unregister(redirect); }
        });
    }

    @GameTest(template="empty")
    public static void acceptedNewHitCancelsTheOldWeaponWindupWithoutResettingRecovery(GameTestHelper h) {
        if (!BetterCombatBridge.enabled()) { h.succeed(); return; }
        withFixture(h, f -> {
            try (var compatibility = new BetterCombatMobSettings(true)) {
                var mob = f.mob(EntityType.VINDICATOR);
                mob.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                mob.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
                f.startRevenge(mob);
                f.original.setPos(mob.position().add(0D, 0D, 1.25D));
                f.attacker.setPos(mob.position().add(.8D, 0D, .8D));
                mob.setYRot(0F); mob.setYHeadRot(0F); mob.yBodyRot = 0F;
                var profile = BetterCombatBridge.profile(mob, 0);
                h.assertTrue(profile != null && profile.windupTicks() > 0 && profile.cooldownTicks() >= profile.windupTicks(),
                        "The actual installed weapon profile provides its windup and recovery boundaries");
                float originalHealth = f.original.getHealth(), attackerHealth = f.attacker.getHealth();
                try {
                    h.assertTrue(BetterMobCombat.request(mob, f.original), "The armed monster begins a real pending swing against A");
                    h.assertValueEqual(f.original.getHealth(), originalHealth, "Player A is still unharmed during weapon windup");
                    f.hit(mob, f.attacker);
                    h.assertTrue(mob.getTarget() == f.attacker, "Player B's actual accepted hit interrupts the windup target");
                    h.assertFalse(BetterMobCombat.request(mob, f.attacker), "Retargeting cannot start B's swing in the same tick");
                    for (int tick = 1; tick < profile.cooldownTicks(); tick++) {
                        ++mob.tickCount;
                        f.original.invulnerableTime = 0; f.attacker.invulnerableTime = 0;
                        BetterMobCombat.tick(mob);
                        h.assertFalse(BetterMobCombat.request(mob, f.attacker), "The original weapon recovery remains spent before tick " + tick);
                    }
                    h.assertValueEqual(f.original.getHealth(), originalHealth, "No old impact damages A after its windup was canceled");
                    h.assertValueEqual(f.attacker.getHealth(), attackerHealth, "B receives no replacement instant hit during the old recovery");
                    ++mob.tickCount; BetterMobCombat.tick(mob);
                    h.assertTrue(BetterMobCombat.request(mob, f.attacker), "A new swing may start exactly at the original recovery boundary");
                    h.assertValueEqual(f.attacker.getHealth(), attackerHealth, "The legal new swing still starts with its own windup");
                } finally { BetterMobCombat.cancel(mob); }
            }
        });
    }

    private static void withFixture(GameTestHelper h, Consumer<Fixture> assertions) {
        var fixture = new Fixture(h);
        h.runAfterDelay(2, () -> { try (fixture) { assertions.accept(fixture); h.succeed(); } });
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final List<ServerPlayer> players = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>();
        final List<PathfinderMob> mobs = new ArrayList<>();
        final ServerPlayer original;
        final ServerPlayer attacker;
        final net.minecraft.world.phys.Vec3 origin;
        Fixture(GameTestHelper h) {
            this.h = h; origin = h.absolutePos(new BlockPos(2, 3, 2)).getCenter();
            original = player(-5D, 0D); attacker = player(1.5D, 0D);
        }
        ServerPlayer player(double x, double z) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "retal-" + UUID.randomUUID().toString().substring(0, 6)), false);
            var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); var channel = new EmbeddedChannel(connection);
            players.add(player); channels.add(channel);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true); player.setPos(origin.add(x, 0D, z));
            return player;
        }
        PathfinderMob mob(EntityType<? extends Mob> type) {
            PathfinderMob mob = (PathfinderMob)type.create(h.getLevel()); mobs.add(mob);
            mob.setNoAi(true); mob.setNoGravity(true); mob.moveTo(origin); mob.tickCount = 10;
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80D); mob.setHealth(80F);
            h.getLevel().addFreshEntity(mob);
            if (R196MonsterAi.managed(mob)) SourceMonsterTargeting.install(mob);
            original.setPos(origin.add(-5D, 0D, 0D)); attacker.setPos(origin.add(1.5D, 0D, 0D));
            return mob;
        }
        void attempt(PathfinderMob mob, Player player) {
            mob.invulnerableTime = 0;
            mob.hurt(h.getLevel().damageSources().playerAttack(player), 1F);
        }
        void hit(PathfinderMob mob, Player player) {
            float before = mob.getHealth(); attempt(mob, player);
            h.assertTrue(mob.getHealth() < before, "The real damage path must accept positive health damage before retaliation");
        }
        void startRevenge(PathfinderMob mob) {
            hit(mob, original); mob.targetSelector.tick(); mob.targetSelector.tick();
            h.assertTrue(mob.getTarget() == original, "Player A owns the running retaliation goal before player B attacks");
        }
        void selectorsKeep(PathfinderMob mob, Player expected, int count) {
            for (int tick = 0; tick < count; tick++) {
                mob.targetSelector.tick();
                h.assertTrue(mob.getTarget() == expected, "Full target selector updates retain the actual accepted target on tick " + tick);
            }
        }
        @Override public void close() {
            mobs.forEach(PathfinderMob::discard);
            for (var player : players) player.server.getPlayerList().remove(player);
            channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
}

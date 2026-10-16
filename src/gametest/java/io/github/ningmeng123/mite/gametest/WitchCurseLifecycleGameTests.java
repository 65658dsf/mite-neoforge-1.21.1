package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.curse.CurseState;
import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import io.github.ningmeng123.mite.curse.WitchCurseData;
import io.github.ningmeng123.mite.registry.MiteAttachments;
import io.github.ningmeng123.mite.registry.MiteUtilityContent;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** World-ledger lifecycle using real server players without modifying the shared game clock. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class WitchCurseLifecycleGameTests {
    private static Witch witch(GameTestHelper helper) {
        Witch witch = EntityType.WITCH.create(helper.getLevel());
        BlockPos pos = helper.absolutePos(new BlockPos(1, 10, 1));
        witch.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        witch.setNoAi(true);
        witch.setNoGravity(true);
        helper.getLevel().addFreshEntity(witch);
        return witch;
    }

    private static WitchCurseData data(ServerPlayer player) { return WitchCurseData.get(player.serverLevel()); }

    private static void replace(ServerPlayer player, long deadline, boolean realized, boolean known) {
        var previous = data(player).entry(player.getUUID());
        data(player).replace(new WitchCurseData.Entry(previous.player(), previous.username(), previous.witch(), previous.type(),
                deadline, realized, known));
    }

    private static void clear(Witch... witches) {
        for (Witch witch : witches) { PlayerCurses.removeForWitch(witch); if (!witch.isRemoved()) witch.discard(); }
    }

    @GameTest(template = "empty")
    public static void pendingCurseIsHiddenAndRealizesAtTheExactGameTimeBoundary(GameTestHelper helper) {
        Witch witch = witch(helper);
        try (var fixture = new PlayerFixture(helper)) {
            ServerPlayer player = fixture.player;
            long now = player.server.overworld().getGameTime();
            helper.assertTrue(PlayerCurses.schedule(player, witch, R196Curse.EQUIPMENT_DECAY, 6000), "Source delay is schedulable");
            helper.assertTrue(data(player).entry(player.getUUID()).realizationTime() == now + 6000,
                    "The source 6000-tick delay is stored against absolute overworld gameTime");
            PlayerCurses.reconcile(player);
            helper.assertTrue(PlayerCurses.state(player).equals(CurseState.NONE), "A pending curse exposes no type or known flag to the client attachment");
            helper.assertFalse(PlayerCurses.has(player, R196Curse.EQUIPMENT_DECAY), "Pending curses do not apply an effect");
            replace(player, now + 1, false, false);
            PlayerCurses.reconcile(player);
            helper.assertFalse(data(player).entry(player.getUUID()).realized(), "One tick before the deadline remains pending");
            replace(player, now, false, false);
            PlayerCurses.reconcile(player);
            helper.assertTrue(data(player).entry(player.getUUID()).realized(), "Equality with the deadline realizes the curse");
            helper.assertTrue(PlayerCurses.has(player, R196Curse.EQUIPMENT_DECAY) && !PlayerCurses.state(player).known(),
                    "The realized effect is initially unknown");
        } finally { clear(witch); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedLedgerRoundTripsPendingRealizedAndKnownIdentities(GameTestHelper helper) {
        var source = new WitchCurseData();
        var pending = new WitchCurseData.Entry(UUID.randomUUID(), "pending-owner", UUID.randomUUID(), R196Curse.CANNOT_RUN,
                1234567890123L, false, false);
        var unknown = new WitchCurseData.Entry(UUID.randomUUID(), "unknown-owner", UUID.randomUUID(), R196Curse.CANNOT_DRINK,
                -1L, true, false);
        var known = new WitchCurseData.Entry(UUID.randomUUID(), "known-owner", pending.witch(), R196Curse.FEAR_OF_UNDEAD,
                999L, true, true);
        source.add(pending); source.add(unknown); source.add(known);
        CompoundTag saved = source.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertValueEqual(saved.getInt("version"), WitchCurseData.VERSION, "The persistent ledger records its schema version");
        var loaded = WitchCurseData.load(saved, helper.getLevel().registryAccess());
        for (var entry : new WitchCurseData.Entry[]{pending, unknown, known})
            helper.assertTrue(entry.equals(loaded.entry(entry.player())), "Every identity, username, caster, type, deadline and discovery flag round-trips exactly");
        helper.assertFalse(loaded.add(pending), "Reloaded pending entries still reject duplicate scheduling");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dimensionsShareTheLedgerAndAReconciledPlayerMirror(GameTestHelper helper) {
        Witch witch = witch(helper);
        try (var fixture = new PlayerFixture(helper)) {
            ServerPlayer player = fixture.player;
            ServerLevel original = player.serverLevel();
            var ledger = data(player);
            helper.assertTrue(PlayerCurses.schedule(player, witch, R196Curse.EQUIPMENT_DECAY, 0), "A cross-dimension fixture is scheduled");
            PlayerCurses.reconcile(player);
            try {
                for (var dimension : java.util.List.of(Level.OVERWORLD, MiteWorldDimensions.UNDERWORLD, Level.NETHER, Level.END)) {
                    ServerLevel level = player.server.getLevel(dimension);
                    helper.assertTrue(level != null, "The registered dimension exists: " + dimension.location());
                    helper.assertTrue(WitchCurseData.get(level) == ledger, "Every dimension resolves the identical overworld SavedData instance");
                    player.setServerLevel(level);
                    player.setData(MiteAttachments.CURSE_STATE, CurseState.NONE);
                    PlayerCurses.reconcile(player);
                    helper.assertTrue(PlayerCurses.has(player, R196Curse.EQUIPMENT_DECAY), "Reconciliation restores the same curse after a dimension change");
                }
            } finally { player.setServerLevel(original); }
        } finally { clear(witch); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realDeathAndRespawnRebuildTheMirrorFromTheSharedCurse(GameTestHelper helper) {
        Witch witch = witch(helper);
        try (var fixture = new PlayerFixture(helper)) {
            ServerPlayer original = fixture.player;
            helper.assertTrue(PlayerCurses.schedule(original, witch, R196Curse.EQUIPMENT_DECAY, 0), "The player is cursed before death");
            PlayerCurses.reconcile(original);
            PlayerCurses.reveal(original);
            var before = data(original).entry(original.getUUID());
            original.kill();
            helper.assertTrue(original.isDeadOrDying(), "The native player death path executes");
            helper.assertTrue(before.equals(data(original).entry(original.getUUID())), "Player death does not mutate or erase the world curse");
            original.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            fixture.player = original.connection.player;
            helper.assertTrue(fixture.player != original && fixture.player.isAlive(), "The native respawn packet creates a living replacement player");
            PlayerCurses.reconcile(fixture.player);
            helper.assertTrue(PlayerCurses.state(fixture.player).equals(new CurseState(R196Curse.EQUIPMENT_DECAY.id(), true)),
                    "The replacement regains both effect and discovery from the same UUID-owned world entry");
            helper.assertTrue(before.equals(data(fixture.player).entry(fixture.player.getUUID())), "The original caster and realization deadline survive respawn");
        } finally { clear(witch); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void logoutAndCasterChunkUnloadPreserveAnOfflinePendingCurse(GameTestHelper helper) {
        Witch witch = witch(helper);
        try (var original = new PlayerFixture(helper)) {
            ServerPlayer player = original.player;
            helper.assertTrue(PlayerCurses.schedule(player, witch, R196Curse.EQUIPMENT_DECAY, 6000), "An offline pending curse is scheduled");
            replace(player, player.server.overworld().getGameTime(), false, false);
            var pending = data(player).entry(player.getUUID());
            player.server.getPlayerList().remove(player);
            witch.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(pending.equals(data(player).entry(player.getUUID())), "Logout and UNLOADED_TO_CHUNK leave the saved curse intact");
            helper.assertFalse(pending.realized(), "The offline record is still pending until the player is reconciled");
            try (var rejoined = new PlayerFixture(helper, player.getGameProfile())) {
                PlayerCurses.reconcile(rejoined.player);
                helper.assertTrue(PlayerCurses.has(rejoined.player, R196Curse.EQUIPMENT_DECAY), "An elapsed pending curse realizes after the same UUID rejoins");
                helper.assertTrue(data(rejoined.player).entry(player.getUUID()).witch().equals(witch.getUUID()),
                        "The unloaded original caster remains the owner");
            }
        } finally { clear(witch); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void milkKeepsTheCurseButTheRealDisenchantingBottleClearsPendingAndActiveEntries(GameTestHelper helper) {
        Witch witch = witch(helper);
        try (var fixture = new PlayerFixture(helper)) {
            ServerPlayer player = fixture.player;
            helper.assertTrue(PlayerCurses.schedule(player, witch, R196Curse.EQUIPMENT_DECAY, 0), "A non-drink curse permits actual milk consumption");
            PlayerCurses.reconcile(player);
            var milkBefore = data(player).entry(player.getUUID());
            player.getFoodData().setFoodLevel(0);
            ((DualFoodData)player.getFoodData()).mite$setNutrition(0);
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
            ItemStack emptyBucket = new ItemStack(Items.MILK_BUCKET).finishUsingItem(player.serverLevel(), player);
            helper.assertTrue(emptyBucket.is(Items.BUCKET) && emptyBucket.getCount() == 1,
                    "Allowed native milk consumption actually completes and returns one empty bucket");
            helper.assertFalse(player.hasEffect(MobEffects.POISON), "Completed milk consumption clears an ordinary potion effect");
            helper.assertTrue(milkBefore.equals(data(player).entry(player.getUUID())),
                    "Clearing ordinary potion effects by real milk consumption leaves the independent curse intact");
            helper.assertTrue(PlayerCurses.cure(player), "Reset the allowed-milk fixture before testing the drink restriction");
            for (boolean active : new boolean[]{false, true}) {
                helper.assertTrue(PlayerCurses.schedule(player, witch, R196Curse.CANNOT_DRINK, active ? 0 : 6000), "Both pending and active drink curses are scheduled");
                PlayerCurses.reconcile(player);
                var before = data(player).entry(player.getUUID());
                player.getFoodData().setFoodLevel(0);
                ((DualFoodData)player.getFoodData()).mite$setNutrition(0);
                new ItemStack(Items.MILK_BUCKET).finishUsingItem(player.serverLevel(), player);
                var after = data(player).entry(player.getUUID());
                helper.assertTrue(after != null && before.player().equals(after.player()) && before.username().equals(after.username())
                        && before.witch().equals(after.witch()) && before.type() == after.type()
                        && before.realizationTime() == after.realizationTime() && before.realized() == after.realized(),
                        "A milk attempt preserves the curse identity, caster, type, deadline and activation even if it reveals the restriction");
                helper.assertTrue(!active || after.known(), "Attempting to drink milk correctly discovers an active drink restriction");
                ItemStack bottle = new ItemStack(MiteUtilityContent.BOTTLE_OF_DISENCHANTING.get());
                ItemStack result = bottle.finishUsingItem(player.serverLevel(), player);
                helper.assertTrue(result.is(Items.GLASS_BOTTLE) && result.getCount() == 1, "The actual drink returns exactly one empty bottle in survival");
                helper.assertTrue(data(player).entry(player.getUUID()) == null, "The real bottle's finish event removes pending and active curses");
                helper.assertTrue(PlayerCurses.state(player).equals(CurseState.NONE), "The attachment is cleared in the same cure transaction");
                helper.assertFalse(PlayerCurses.cure(player), "A repeated cure cannot remove or refund the same curse twice");
            }
        } finally { clear(witch); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyTheOwningCasterDeathClearsAllOfHerPlayers(GameTestHelper helper) {
        Witch caster = witch(helper);
        Witch unrelated = witch(helper);
        try (var first = new PlayerFixture(helper); var second = new PlayerFixture(helper)) {
            helper.assertTrue(PlayerCurses.schedule(first.player, caster, R196Curse.EQUIPMENT_DECAY, 0), "The first player has an active curse");
            PlayerCurses.reconcile(first.player);
            helper.assertTrue(PlayerCurses.schedule(second.player, caster, R196Curse.CANNOT_RUN, 6000), "The same witch also has a pending curse on another player");
            unrelated.kill();
            helper.assertTrue(data(first.player).entry(first.player.getUUID()) != null
                    && data(second.player).entry(second.player.getUUID()) != null, "A different witch's accepted death cannot lift these curses");
            caster.kill();
            helper.assertTrue(data(first.player).entry(first.player.getUUID()) == null
                    && data(second.player).entry(second.player.getUUID()) == null, "Caster death removes all active and pending records bearing her UUID");
            helper.assertTrue(PlayerCurses.state(first.player).equals(CurseState.NONE)
                    && PlayerCurses.state(second.player).equals(CurseState.NONE), "Both online players reconcile immediately after caster death");
        } finally { clear(caster, unrelated); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void discoveryIsPersistedAndDoesNotReplayParticlesOnRepeatedChecksOrReload(GameTestHelper helper) {
        Witch witch = witch(helper);
        try (var fixture = new PlayerFixture(helper)) {
            ServerPlayer player = fixture.player;
            var particles = new AtomicInteger();
            fixture.channel.pipeline().addLast("curse_lifecycle_particles", new ChannelOutboundHandlerAdapter() {
                @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                    if (message instanceof ClientboundLevelParticlesPacket packet && packet.getParticle().getType() == ParticleTypes.WITCH)
                        particles.incrementAndGet();
                    super.write(context, message, promise);
                }
            });
            helper.assertTrue(PlayerCurses.schedule(player, witch, R196Curse.EQUIPMENT_DECAY, 0), "The discovery fixture is scheduled");
            PlayerCurses.reconcile(player);
            helper.assertFalse(PlayerCurses.state(player).known(), "Merely realizing the curse does not identify the effect");
            PlayerCurses.reveal(player);
            fixture.channel.runPendingTasks();
            helper.assertTrue(PlayerCurses.state(player).known() && data(player).entry(player.getUUID()).known(), "Discovery updates the authoritative entry and visible mirror together");
            helper.assertValueEqual(particles.get(), 1, "The first discovery sends exactly one witch-particle burst to the nearby player");
            var known = data(player).entry(player.getUUID());
            var restored = WitchCurseData.load(data(player).save(new CompoundTag(), player.registryAccess()), player.registryAccess());
            helper.assertTrue(known.equals(restored.entry(player.getUUID())), "Saving and loading preserves the discovered effect");
            PlayerCurses.reveal(player);
            PlayerCurses.has(player, R196Curse.EQUIPMENT_DECAY, true);
            data(player).replace(restored.entry(player.getUUID()));
            player.setData(MiteAttachments.CURSE_STATE, CurseState.NONE);
            PlayerCurses.reconcile(player);
            PlayerCurses.reveal(player);
            fixture.channel.runPendingTasks();
            helper.assertValueEqual(particles.get(), 1, "Repeated triggers and reloading a known record do not replay discovery particles");
            helper.assertTrue(PlayerCurses.state(player).known(), "A reloaded mirror keeps the known effect");
        } finally { clear(witch); }
        helper.succeed();
    }

    private static final class PlayerFixture implements AutoCloseable {
        ServerPlayer player;
        final EmbeddedChannel channel;
        PlayerFixture(GameTestHelper helper) {
            this(helper, new GameProfile(UUID.randomUUID(), "curse-" + UUID.randomUUID().toString().substring(0, 8)));
        }
        PlayerFixture(GameTestHelper helper, GameProfile profile) {
            var cookie = CommonListenerCookie.createInitial(profile, false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            BlockPos pos = helper.absolutePos(new BlockPos(2, 10, 1));
            player.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            player.setNoGravity(true);
        }
        @Override public void close() {
            PlayerCurses.cure(player);
            if (player.server.getPlayerList().getPlayer(player.getUUID()) == player) player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}

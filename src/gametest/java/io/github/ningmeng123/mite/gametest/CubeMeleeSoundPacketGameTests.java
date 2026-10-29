package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.entity.MobCorrosion;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real attack packets and received sound/steam packets, including immune and creative hits. @author NingMeng123 */
@GameTestHolder("mite_corrosion_sound")
@PrefixGameTestTemplate(false)
public final class CubeMeleeSoundPacketGameTests {
    private CubeMeleeSoundPacketGameTests() {}

    @GameTest(template = "empty")
    public static void swordPacketsHissOnceForBothAcidCubesInSurvivalAndCreative(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE))
                for (MobKind kind : List.of(MobKind.OOZE, MobKind.PUDDING)) {
                    fixture.prepare(mode, new ItemStack(Items.IRON_SWORD));
                    Slime cube = fixture.cube(kind, 1D);
                    try {
                        float health = cube.getHealth();
                        fixture.attack(cube);
                        fixture.assertAcidFeedback(cube, health, mode + "/" + kind + "/iron_sword");
                        int wear = fixture.player.getMainHandItem().getDamageValue();
                        helper.assertTrue(mode == GameType.SURVIVAL ? wear > 0 : wear == 0,
                                "Survival still corrodes iron, while creative feedback never damages it: " + mode + "/" + kind + " wear=" + wear);
                    } finally { cube.discard(); }
                }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyAndAcidProofHandsStillReceiveOneAcidCubeHissWithoutMaterialLoss(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE))
                for (MobKind kind : List.of(MobKind.OOZE, MobKind.PUDDING))
                    for (boolean empty : new boolean[]{true, false}) {
                        ItemStack held = empty ? ItemStack.EMPTY : new ItemStack(Items.LAPIS_LAZULI);
                        helper.assertFalse(MobCorrosion.susceptible(held, true), "The negative material fixture really is acid-proof");
                        fixture.prepare(mode, held);
                        Slime cube = fixture.cube(kind, 1D);
                        try {
                            float health = cube.getHealth();
                            fixture.attack(cube);
                            fixture.assertAcidFeedback(cube, health, mode + "/" + kind + (empty ? "/empty" : "/lapis"));
                            ItemStack after = fixture.player.getMainHandItem();
                            helper.assertTrue(empty ? after.isEmpty() : after.is(Items.LAPIS_LAZULI) && after.getCount() == 1 && after.getDamageValue() == 0,
                                    "Pure feedback cannot create, consume or damage the attacker's acid-proof held item");
                        } finally { cube.discard(); }
                    }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryCubeContactsDoNotGainPureAcidFeedback(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE))
                for (MobKind kind : new MobKind[]{MobKind.JELLY, MobKind.BLOB, null})
                    for (boolean empty : new boolean[]{true, false}) {
                        ItemStack held = empty ? ItemStack.EMPTY : new ItemStack(Items.LAPIS_LAZULI);
                        helper.assertFalse(MobCorrosion.susceptible(held, false), "The ordinary-cube control is pepsin-proof");
                        fixture.prepare(mode, held);
                        Slime cube = fixture.cube(kind, 1D);
                        try {
                            float health = cube.getHealth();
                            fixture.attack(cube);
                            helper.assertValueEqual(fixture.attacks.size(), 1, "The ordinary control reaches native Player.attack");
                            helper.assertTrue(cube.getHealth() < health && fixture.incoming.contains(cube),
                                    "The ordinary control is a real effective health-damage hit, not a refused attack");
                            fixture.assertNoFeedback("ordinary " + mode + "/" + kind + (empty ? "/empty" : "/lapis"));
                            helper.assertTrue(empty ? fixture.player.getMainHandItem().isEmpty()
                                            : fixture.player.getMainHandItem().is(Items.LAPIS_LAZULI) && fixture.player.getMainHandItem().getCount() == 1,
                                    "Ordinary pepsin-proof controls retain the held material");
                        } finally { cube.discard(); }
                    }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void swingMissingTargetAndOutOfReachPacketsCannotProduceAcidFeedback(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE))
                for (MobKind kind : List.of(MobKind.OOZE, MobKind.PUDDING)) {
                    fixture.prepare(mode, new ItemStack(Items.IRON_SWORD));
                    fixture.player.connection.handleAnimate(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                    fixture.assertNoFeedback("Swinging into air in " + mode);
                    helper.assertTrue(fixture.attacks.isEmpty(), "A swing packet alone never becomes an entity melee contact");
                    Slime missing = (Slime)MiteMobs.type(kind).create(helper.getLevel());
                    helper.assertTrue(helper.getLevel().getEntity(missing.getId()) == null, "The missing-target control is absent from the native entity tracker");
                    fixture.attack(missing);
                    fixture.assertNoFeedback("An absent target in " + mode);
                    helper.assertTrue(fixture.attacks.isEmpty(), "An unknown entity ID never reaches native Player.attack");
                    Slime far = fixture.cube(kind, 8D);
                    try {
                        helper.assertFalse(SourcePlayerReach.canMelee(fixture.player, far), "The refusal control is outside even creative melee reach");
                        fixture.attack(far);
                        fixture.assertNoFeedback("An out-of-reach " + kind + " in " + mode);
                        helper.assertTrue(fixture.attacks.isEmpty(), "The reach check rejects the packet before the melee transaction");
                        helper.assertValueEqual(fixture.player.getMainHandItem().getDamageValue(), 0, "Refused and empty swings never corrode the weapon");
                    } finally { far.discard(); missing.discard(); }
                }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelledNativeAttackCannotProduceAcidFeedback(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE))
                for (MobKind kind : List.of(MobKind.OOZE, MobKind.PUDDING)) {
                    fixture.prepare(mode, new ItemStack(Items.IRON_SWORD));
                    Slime cube = fixture.cube(kind, 1D);
                    int[] cancelled = {0};
                    Consumer<AttackEntityEvent> cancel = event -> {
                        if (event.getEntity() == fixture.player && event.getTarget() == cube) { cancelled[0]++; event.setCanceled(true); }
                    };
                    NeoForge.EVENT_BUS.addListener(cancel);
                    try {
                        fixture.attack(cube);
                        helper.assertValueEqual(cancelled[0], 1, "The packet reaches the real cancellable native attack event");
                        fixture.assertNoFeedback("A cancelled " + mode + "/" + kind + " attack");
                        helper.assertValueEqual(fixture.player.getMainHandItem().getDamageValue(), 0, "A cancelled attack cannot corrode the sword");
                    } finally { NeoForge.EVENT_BUS.unregister(cancel); cube.discard(); }
                }
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final List<ClientboundSoundPacket> sounds = new ArrayList<>();
        final List<ClientboundLevelParticlesPacket> particles = new ArrayList<>();
        final List<Entity> attacks = new ArrayList<>(), incoming = new ArrayList<>();
        final Consumer<AttackEntityEvent> attackObserver;
        final Consumer<LivingIncomingDamageEvent> damageObserver;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-cube-packet"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            attackObserver = event -> { if (event.getEntity() == player) attacks.add(event.getTarget()); };
            damageObserver = event -> { if (event.getSource().getDirectEntity() == player) incoming.add(event.getEntity()); };
            try {
                NetworkRegistry.configureMockConnection(connection);
                player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
                player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 15, 1))));
                player.setNoGravity(true); player.setOnGround(true);
                player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
                player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10D);
                player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024D);
                for (int tick = 0; tick < 60; tick++) player.tick();
                channel.pipeline().addLast("mite_cube_packet_feedback", new ChannelOutboundHandlerAdapter() {
                    @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                        if (message instanceof ClientboundSoundPacket sound && sound.getSound().value() == SoundEvents.FIRE_EXTINGUISH) sounds.add(sound);
                        if (message instanceof ClientboundLevelParticlesPacket particle && particle.getParticle().getType() == ParticleTypes.CLOUD) particles.add(particle);
                        super.write(context, message, promise);
                    }
                });
                NeoForge.EVENT_BUS.addListener(attackObserver);
                NeoForge.EVENT_BUS.addListener(damageObserver);
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void prepare(GameType mode, ItemStack held) {
            player.setGameMode(mode); player.removeAllEffects(); player.setHealth(100F);
            player.setOnGround(true); player.setSprinting(false); player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
            player.getFoodData().setFoodLevel(6); ((DualFoodData)player.getFoodData()).mite$setNutrition(0);
            player.getInventory().clearContent(); player.setItemInHand(InteractionHand.MAIN_HAND, held);
            channel.runPendingTasks(); sounds.clear(); particles.clear(); attacks.clear(); incoming.clear();
        }

        Slime cube(MobKind kind, double distance) {
            Slime cube = kind == null ? EntityType.SLIME.create(helper.getLevel()) : (Slime)MiteMobs.type(kind).create(helper.getLevel());
            helper.assertTrue(cube != null, "The actual slime entity type creates the fixture");
            cube.setSize(1, true); cube.setNoAi(true); cube.setNoGravity(true); cube.setPersistenceRequired();
            cube.setPos(player.position().add(distance, 0, 0)); cube.setOnGround(true);
            cube.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D); cube.setHealth(100F);
            helper.getLevel().getChunkAt(cube.blockPosition());
            helper.assertTrue(helper.getLevel().addFreshEntity(cube), "The real cube enters the server entity tracker");
            helper.assertTrue(helper.getLevel().getEntity(cube.getId()) == cube, "Native attack-packet lookup resolves the exact cube");
            if (distance <= 1D) helper.assertTrue(SourcePlayerReach.canMelee(player, cube),
                    "The positive melee fixture is within real eye-to-hitbox reach, including an empty hand");
            return cube;
        }

        void attack(Entity target) {
            player.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(target, false));
            channel.runPendingTasks();
        }

        void assertAcidFeedback(Slime cube, float health, String label) {
            channel.runPendingTasks();
            helper.assertValueEqual(attacks.size(), 1, "Exactly one native Player.attack event reaches the immune cube: " + label);
            helper.assertTrue(attacks.getFirst() == cube, "The native attack event targets the tracked cube: " + label);
            helper.assertTrue(cube.isInvulnerableTo(player.damageSources().playerAttack(player)) && cube.getHealth() == health && incoming.isEmpty(),
                    "The real immune health-damage path remains refused while contact still has feedback: " + label);
            helper.assertValueEqual(sounds.size(), 1, "The attacking client's actual connection receives exactly one hiss: " + label);
            var sound = sounds.getFirst();
            helper.assertTrue(sound.getSource() == SoundSource.PLAYERS && sound.getVolume() == .7F && sound.getPitch() >= 1.2F && sound.getPitch() <= 2F,
                    "The real sound packet preserves the low-version category, volume and randomized pitch: " + label);
            // Native getters convert their fixed-point integers through float, which loses more
            // than 1/8 block at distant GameTest coordinates. Compare the native wire encoding.
            var expectedPosition = new ClientboundSoundPacket(sound.getSound(), SoundSource.PLAYERS,
                    player.getX(), player.getY() + player.getBbHeight() * .5D, player.getZ(), .7F, 1.6F, 0L);
            helper.assertTrue(sound.getX() == expectedPosition.getX() && sound.getY() == expectedPosition.getY()
                            && sound.getZ() == expectedPosition.getZ(),
                    "The hiss is emitted at the attacker's native encoded sound position: " + label);
            helper.assertValueEqual(particles.size(), 1, "One shared steam packet accompanies the real melee contact: " + label);
            helper.assertValueEqual(particles.getFirst().getCount(), 5, "The native steam packet retains five cloud particles: " + label);
        }

        void assertNoFeedback(String label) {
            channel.runPendingTasks();
            helper.assertValueEqual(sounds.size(), 0, "No false hiss packet: " + label);
            helper.assertValueEqual(particles.size(), 0, "No false steam packet: " + label);
        }

        @Override public void close() {
            NeoForge.EVENT_BUS.unregister(attackObserver); NeoForge.EVENT_BUS.unregister(damageObserver);
            try {
                if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player);
            } finally { channel.finishAndReleaseAll(); }
        }
    }
}

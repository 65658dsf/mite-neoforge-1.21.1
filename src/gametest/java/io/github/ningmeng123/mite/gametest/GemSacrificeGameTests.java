package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native use packets conserve gems while reproducing R196 glass feedback and shared timing. @author NingMeng123 */
@GameTestHolder("mite_gem_sacrifice")
@PrefixGameTestTemplate(false)
public final class GemSacrificeGameTests {
    private static final List<Item> GEMS = List.of(Items.LAPIS_LAZULI, Items.QUARTZ, Items.EMERALD, Items.DIAMOND);

    private GemSacrificeGameTests() {}

    @GameTest(template = "empty")
    public static void nativePacketsSendOneGlassSoundAndFiveOrientedGemFragmentsToSelfAndObservers(GameTestHelper helper) {
        for (Item gem : GEMS) for (float yaw : new float[]{0F, 90F}) {
            try (Fixture user = new Fixture(helper); Fixture observer = new Fixture(helper)) {
                float pitch = yaw == 0F ? 0F : 30F;
                user.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(gem));
                user.player.setHealth(2F);
                user.clearPackets();
                observer.clearPackets();
                user.use(InteractionHand.MAIN_HAND, 1, yaw, pitch);
                helper.assertTrue(user.player.getMainHandItem().isEmpty(), "Even the last held gem is consumed exactly once");
                int expectedExperience = gem == Items.LAPIS_LAZULI ? 25 : gem == Items.QUARTZ ? 50 : gem == Items.EMERALD ? 250 : 500;
                helper.assertValueEqual(user.player.totalExperience, expectedExperience, "One real packet awards the independently specified whole-gem value");
                helper.assertValueEqual(user.player.getHealth(), 2F, "Breaking feedback never turns sacrifice into healing");
                verifyFeedback(helper, user, user.player, gem);
                verifyFeedback(helper, observer, user.player, gem);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allGemsAndBothHandsShareTenTicksAndRejectedPacketsNeverExtendTheDeadline(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 3));
            fixture.use(InteractionHand.MAIN_HAND, 1, 0F, 0F);
            helper.assertValueEqual(fixture.player.totalExperience, 500, "The first sacrifice succeeds immediately");
            helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 2, "The first packet consumes exactly one diamond");
            for (Item gem : GEMS) helper.assertTrue(fixture.player.getCooldowns().isOnCooldown(gem), "Success starts one shared cooldown for every gem");
            fixture.clearPackets();
            for (InteractionHand hand : InteractionHand.values()) for (Item gem : GEMS) {
                fixture.player.setItemInHand(hand, new ItemStack(gem, 2));
                fixture.use(hand, 1, 0F, 0F);
                helper.assertValueEqual(fixture.player.getItemInHand(hand).getCount(), 2, "A duplicate or cross-hand packet cannot spend a second gem");
                helper.assertValueEqual(fixture.player.totalExperience, 500, "Switching gem identity or hand cannot bypass the original deadline");
            }
            fixture.advance(9);
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.QUARTZ, 2));
            fixture.use(InteractionHand.MAIN_HAND, 2, 0F, 0F);
            helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 2, "The ninth tick is still cooling down");
            helper.assertValueEqual(fixture.player.totalExperience, 500, "An early request receives no additional experience");
            helper.assertTrue(fixture.feedback().isEmpty(), "Rejected packets produce no sound or particle replay");
            fixture.advance(1);
            for (Item gem : GEMS) helper.assertFalse(fixture.player.getCooldowns().isOnCooldown(gem), "Rejected requests do not postpone the exact tenth-tick expiry");
            fixture.use(InteractionHand.MAIN_HAND, 3, 0F, 0F);
            helper.assertValueEqual(fixture.player.totalExperience, 550, "At the exact boundary one quartz supplies the unchanged fifty-point base reward");
            helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 1, "The boundary request consumes one quartz rather than replaying rejected input");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void quartzUsesTheRealBlockFallbackWhileQuartzBlocksKeepTheirNativePlacement(GameTestHelper helper) {
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        var oldFloor = helper.getLevel().getBlockState(floor);
        var oldAbove = helper.getLevel().getBlockState(floor.above());
        try (Fixture fixture = new Fixture(helper)) {
            helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
            var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0D, 0.5D, 0D), Direction.UP, floor, false);
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.QUARTZ, 2));
            helper.assertFalse(fixture.player.getMainHandItem().getItem() instanceof BlockItem, "The sacrificed vanilla quartz is a whole item, not a quartz block");
            helper.assertTrue(SourcePlayerReach.canReachBlock(fixture.player, floor, InteractionHand.MAIN_HAND),
                    "The real quartz block-use fixture is inside the unchanged survival interaction range");
            helper.assertFalse(SourcePlayerReach.canReachBlock(fixture.player, helper.absolutePos(new BlockPos(3, 1, 3)), InteractionHand.MAIN_HAND),
                    "The original farther test coordinate remains correctly rejected by the native source-reach gate");
            InteractionResult block = fixture.player.gameMode.useItemOn(fixture.player, helper.getLevel(), fixture.player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            helper.assertValueEqual(block, InteractionResult.PASS, "Plain stone and the native quartz useOn path allow the ordinary item-use fallback");
            helper.assertValueEqual(fixture.player.totalExperience, 0, "The block phase does not separately sacrifice a gem");
            InteractionResult used = fixture.player.gameMode.useItem(fixture.player, helper.getLevel(), fixture.player.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertTrue(used.consumesAction(), "The actual server item-use fallback accepts quartz below the retained ITE level gate");
            helper.assertValueEqual(fixture.player.totalExperience, 50, "Vanilla quartz uses the base fifty-point sacrifice value");
            helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 1, "One raw quartz is consumed, not the entire stack");
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.QUARTZ_BLOCK, 2));
            helper.assertTrue(fixture.player.getMainHandItem().getItem() instanceof BlockItem, "The independent control exercises a real vanilla BlockItem");
            InteractionResult placed = fixture.player.gameMode.useItemOn(fixture.player, helper.getLevel(), fixture.player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(placed.consumesAction() && helper.getLevel().getBlockState(floor.above()).is(Blocks.QUARTZ_BLOCK), "Quartz blocks retain their native placement rather than becoming sacrifice actions");
            helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 1, "Normal quartz block placement spends one block");
            helper.assertValueEqual(fixture.player.totalExperience, 50, "The quartz block control awards no sacrifice experience");
        } finally {
            helper.getLevel().setBlockAndUpdate(floor.above(), oldAbove);
            helper.getLevel().setBlockAndUpdate(floor, oldFloor);
        }
        helper.succeed();
    }

    private static void verifyFeedback(GameTestHelper helper, Fixture recipient, ServerPlayer user, Item gem) {
        List<Object> feedback = recipient.feedback();
        var sounds = feedback.stream().filter(ClientboundSoundPacket.class::isInstance).map(ClientboundSoundPacket.class::cast).toList();
        var particles = feedback.stream().filter(ClientboundLevelParticlesPacket.class::isInstance).map(ClientboundLevelParticlesPacket.class::cast).toList();
        helper.assertValueEqual(sounds.size(), 1, "Self and nearby observers each receive exactly one break sound");
        var sound = sounds.getFirst();
        helper.assertTrue(sound.getSound().value() == SoundEvents.GLASS_BREAK && sound.getSource() == SoundSource.PLAYERS
                        && sound.getVolume() == 0.375F && sound.getPitch() >= 0.8F && sound.getPitch() <= 1.2F,
                "Every gem sends the original glass sound at 1.5 times its former volume with unchanged random pitch");
        helper.assertValueEqual(particles.size(), 5, "Self and nearby observers each receive the five individual item fragments");
        float pitch = -user.getXRot() * (float) (Math.PI / 180D);
        float yaw = -user.getYRot() * (float) (Math.PI / 180D);
        for (var packet : particles) {
            helper.assertTrue(packet.getParticle() instanceof ItemParticleOption option && option.getItem().is(gem)
                            && option.getItem().getCount() == 1 && packet.getCount() == 0 && packet.getMaxSpeed() == 1F,
                    "Each exact-velocity particle retains the sacrificed item even after its final stack became empty");
            Vec3 position = inverseNativeRotation(new Vec3(packet.getX() - user.getX(), packet.getY() - user.getEyeY(), packet.getZ() - user.getZ()), yaw, pitch);
            Vec3 velocity = inverseNativeRotation(new Vec3(packet.getXDist(), packet.getYDist() - 0.05D, packet.getZDist()), yaw, pitch);
            helper.assertTrue(position.x >= -0.15001D && position.x <= 0.15001D && position.y >= -0.90001D && position.y <= -0.29999D
                            && Math.abs(position.z - 0.6D) < 0.00001D,
                    "The fragment positions rotate the original eye-relative R196 bounds: local=" + position
                            + ", packet=" + new Vec3(packet.getX(), packet.getY(), packet.getZ()) + ", eye=" + user.getEyePosition()
                            + ", yaw=" + user.getYRot() + ", pitch=" + user.getXRot());
            helper.assertTrue(velocity.x >= -0.05001D && velocity.x <= 0.05001D && velocity.y >= 0.09999D && velocity.y <= 0.20001D
                            && Math.abs(velocity.z) < 0.00001D,
                    "The fragment velocities preserve R196's rotated upward motion: local=" + velocity
                            + ", yaw=" + user.getYRot() + ", pitch=" + user.getXRot());
        }
    }

    private static Vec3 inverseNativeRotation(Vec3 rotated, float yaw, float pitch) {
        double yawCosine = Mth.cos(yaw), yawSine = Mth.sin(yaw);
        double yawNorm = yawCosine * yawCosine + yawSine * yawSine;
        double localHorizontal = (rotated.x * yawCosine - rotated.z * yawSine) / yawNorm;
        double localDepth = (rotated.x * yawSine + rotated.z * yawCosine) / yawNorm;
        double pitchCosine = Mth.cos(pitch), pitchSine = Mth.sin(pitch);
        double pitchNorm = pitchCosine * pitchCosine + pitchSine * pitchSine;
        return new Vec3(localHorizontal, (rotated.y * pitchCosine - localDepth * pitchSine) / pitchNorm,
                (rotated.y * pitchSine + localDepth * pitchCosine) / pitchNorm);
    }

    private static final class Fixture implements AutoCloseable {
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final List<Object> packets = new ArrayList<>();

        private Fixture(GameTestHelper helper) {
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-gem-fx"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            try {
                player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
                player.setGameMode(GameType.SURVIVAL);
                player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 2))));
                player.setNoGravity(true);
                player.setOnGround(true);
                player.getInventory().clearContent();
                player.removeAllEffects();
                PlayerExperience.setTotal(player, 0);
                channel.pipeline().addLast("mite_gem_feedback", new ChannelOutboundHandlerAdapter() {
                    @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                        if (message instanceof ClientboundSoundPacket || message instanceof ClientboundLevelParticlesPacket) packets.add(message);
                        super.write(context, message, promise);
                    }
                });
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private void use(InteractionHand hand, int sequence, float yaw, float pitch) {
            player.connection.handleUseItem(new ServerboundUseItemPacket(hand, sequence, yaw, pitch));
        }
        private void advance(int ticks) {
            for (int tick = 0; tick < ticks; tick++) player.getCooldowns().tick();
        }
        private void clearPackets() {
            channel.runPendingTasks();
            packets.clear();
        }
        private List<Object> feedback() {
            channel.runPendingTasks();
            return List.copyOf(packets);
        }
        @Override public void close() {
            try {
                ServerPlayer registered = player.server.getPlayerList().getPlayer(player.getUUID());
                if (registered != null) player.server.getPlayerList().remove(registered);
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }
}

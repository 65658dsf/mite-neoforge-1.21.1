package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import io.github.ningmeng123.mite.network.BucketPlacementInputPayload;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.mite.world.BucketPlacementIntent;
import io.github.ningmeng123.mite.world.PlacementDelay;
import io.github.ningmeng123.mite.world.TemporaryFluidChanges;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises Ctrl intent through the real server use-packet handler instead of a direct placement scope. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class BucketPlacementNetworkGameTests {
    private BucketPlacementNetworkGameTests() {}

    @GameTest(template = "empty")
    public static void firstControlUsePacketPlacesPaidSourcesForBothHandsAndEveryBucketMetal(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            int sequence = 1;
            for (InteractionHand hand : InteractionHand.values()) for (boolean water : new boolean[]{true, false}) {
                for (Item item : filledBuckets(water)) {
                    f.clear();
                    PlayerExperience.setTotal(f.player, 1000);
                    f.player.setItemInHand(hand, new ItemStack(item));
                    BucketPlacementIntent.receive(f.player, hand, sequence, true);
                    f.use(hand, sequence++);
                    h.assertTrue(h.getLevel().getFluidState(f.target).is(water ? FluidTags.WATER : FluidTags.LAVA),
                            "The first real use packet places the correct liquid for " + item + " in " + hand);
                    h.assertTrue(h.getLevel().getFluidState(f.target).isSource(), "The matching Ctrl packet creates a complete liquid source");
                    h.assertFalse(f.temporary(), "The real packet scope cancels finite decay for a paid source");
                    h.assertValueEqual(f.player.totalExperience, 900, "The real server packet spends exactly100 points once");
                    Item empty = item instanceof MiteBucketItem metal ? MiteFoods.get(metal.metal() + "_bucket") : Items.BUCKET;
                    h.assertTrue(f.player.getItemInHand(hand).is(empty), "The real packet replaces the held item with its matching empty bucket");
                    h.assertValueEqual(f.player.getItemInHand(hand).getCount(), 1, "One full bucket returns exactly one empty vessel");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryPacketAfterControlDoesNotInheritItsPaidSourceMode(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            BucketPlacementIntent.receive(f.player, InteractionHand.MAIN_HAND, 80, true);
            f.use(InteractionHand.MAIN_HAND, 80);
            h.assertValueEqual(f.player.totalExperience, 900, "The explicitly armed source request is charged once");
            h.assertFalse(f.temporary(), "The armed source has no deadline");
            f.clear(); // Intentionally does not clear input intent: the packet handler must already have consumed it.
            f.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(MiteFoods.get("copper_lava_bucket")));
            f.use(InteractionHand.OFF_HAND, 81);
            h.assertTrue(f.temporary(), "A following unarmed offhand packet creates only finite lava");
            h.assertValueEqual(f.player.totalExperience, 900, "A later ordinary packet cannot spend the preceding request's source cost");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mismatchedPacketSequenceCannotSpendOrArmASubsequentPlacement(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            BucketPlacementIntent.receive(f.player, InteractionHand.MAIN_HAND, 90, true);
            f.use(InteractionHand.MAIN_HAND, 91);
            h.assertTrue(f.temporary(), "A different real use sequence cannot claim the Ctrl request");
            h.assertValueEqual(f.player.totalExperience, 1000, "A mismatched packet spends no experience");
            f.clear();
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            f.use(InteractionHand.MAIN_HAND, 90);
            h.assertTrue(f.temporary(), "Replaying the old sequence after a mismatch cannot recover the discarded request");
            h.assertValueEqual(f.player.totalExperience, 1000, "The discarded request cannot be charged later");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oppositeHandPacketCannotSpendTheMainHandControlRequest(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            f.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(MiteFoods.get("copper_water_bucket")));
            BucketPlacementIntent.receive(f.player, InteractionHand.MAIN_HAND, 100, true);
            f.use(InteractionHand.OFF_HAND, 100);
            h.assertTrue(f.temporary(), "A real offhand packet cannot use the main-hand request even with the same sequence");
            h.assertValueEqual(f.player.totalExperience, 1000, "The wrong hand never pays a source cost");
            h.assertTrue(f.player.getMainHandItem().is(Items.WATER_BUCKET), "The unrelated main-hand bucket remains full");
            TemporaryFluidChanges.get(h.getLevel()).cancel(f.target);
            h.getLevel().setBlock(f.target, Blocks.AIR.defaultBlockState(), 2);
            PlacementDelay.clear(f.player);
            f.use(InteractionHand.MAIN_HAND, 100);
            h.assertTrue(f.temporary(), "The mismatched hand consumes the intent before a later main-hand packet can reuse it");
            h.assertValueEqual(f.player.totalExperience, 1000, "Reusing the original hand and sequence still cannot spend the discarded request");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void placementInputWireCodecPreservesHandSequenceAndControlMode(GameTestHelper h) {
        for (InteractionHand hand : InteractionHand.values()) for (boolean source : new boolean[]{false, true}) {
            var original = new BucketPlacementInputPayload(hand, 2_000_123, source);
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                BucketPlacementInputPayload.STREAM_CODEC.encode(buffer, original);
                var decoded = BucketPlacementInputPayload.STREAM_CODEC.decode(buffer);
                h.assertValueEqual(decoded, original, "One encoded input retains its exact hand, prediction sequence and modifier");
                h.assertValueEqual(buffer.readableBytes(), 0, "Decoding consumes exactly one complete input payload");
            } finally { buffer.release(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void immediatePourSharesSixTickCooldownAndDiscardsRejectedControlPackets(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            fixture.use(InteractionHand.MAIN_HAND, 300);
            helper.assertTrue(helper.getLevel().getFluidState(fixture.target).is(FluidTags.WATER),
                    "The first real bucket packet pours immediately, without a preparation tick");
            helper.assertTrue(fixture.temporary(), "An immediate ordinary pour still has its finite water deadline");
            helper.assertValueEqual(PlacementDelay.remainingTicks(fixture.player), 6,
                    "A successful pour starts exactly six ticks of cooldown");
            TemporaryFluidChanges.get(helper.getLevel()).cancel(fixture.target);
            helper.getLevel().setBlock(fixture.target, Blocks.AIR.defaultBlockState(), 2);
            fixture.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.LAVA_BUCKET));
            BucketPlacementIntent.receive(fixture.player, InteractionHand.OFF_HAND, 301, true);
            fixture.use(InteractionHand.OFF_HAND, 301);
            helper.assertTrue(helper.getLevel().getFluidState(fixture.target).isEmpty(),
                    "Switching hand and liquid cannot bypass the shared cooldown");
            helper.assertTrue(fixture.player.getOffhandItem().is(Items.LAVA_BUCKET), "A rejected pour retains its full bucket");
            helper.assertValueEqual(fixture.player.totalExperience, 1000, "A rejected Ctrl request spends no source experience");
            helper.runAfterDelay(5, () -> {
                try {
                    helper.assertValueEqual(PlacementDelay.remainingTicks(fixture.player), 1,
                            "The original deadline still has one tick remaining");
                    BucketPlacementIntent.receive(fixture.player, InteractionHand.OFF_HAND, 302, true);
                    fixture.use(InteractionHand.OFF_HAND, 302);
                    helper.assertTrue(helper.getLevel().getFluidState(fixture.target).isEmpty(),
                            "A repeated request is still refused at tick five");
                    helper.assertValueEqual(PlacementDelay.remainingTicks(fixture.player), 1,
                            "Rejected requests do not extend the deadline");
                    helper.runAfterDelay(1, () -> {
                        try {
                            helper.assertValueEqual(PlacementDelay.remainingTicks(fixture.player), 0,
                                    "Placement becomes available at tick six");
                            helper.assertTrue(helper.getLevel().getFluidState(fixture.target).isEmpty(),
                                    "The elapsed cooldown never replays a refused placement");
                            fixture.use(InteractionHand.OFF_HAND, 303);
                            helper.assertTrue(helper.getLevel().getFluidState(fixture.target).is(FluidTags.LAVA),
                                    "A fresh request pours immediately at the deadline");
                            helper.assertTrue(fixture.temporary(), "Rejected Ctrl requests cannot arm the later ordinary pour");
                            helper.assertValueEqual(fixture.player.totalExperience, 1000,
                                    "The later unarmed request cannot charge either rejected source intent");
                            helper.assertValueEqual(PlacementDelay.remainingTicks(fixture.player), 6,
                                    "The new successful pour starts its own cooldown");
                            helper.succeed();
                        } finally { fixture.close(); }
                    });
                } catch (RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static List<Item> filledBuckets(boolean water) {
        List<Item> result = new ArrayList<>();
        result.add(water ? Items.WATER_BUCKET : Items.LAVA_BUCKET);
        if (water) result.addAll(List.of(Items.COD_BUCKET, Items.SALMON_BUCKET, Items.PUFFERFISH_BUCKET,
                Items.TROPICAL_FISH_BUCKET, Items.TADPOLE_BUCKET, Items.AXOLOTL_BUCKET));
        for (String metal : MiteFoods.METALS) result.add(MiteFoods.get(metal + (water ? "_water_bucket" : "_lava_bucket")));
        return result;
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final BlockPos target;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-bucket-net"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            target = helper.absolutePos(new BlockPos(3, 2, 3));
            player.moveTo(target.getX() + .5, target.getY() + 2, target.getZ() + .5, 0, 90);
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            player.removeAllEffects();
            PlayerExperience.setTotal(player, 1000);
            clear();
        }

        private void use(InteractionHand hand, int sequence) {
            player.connection.handleUseItem(new ServerboundUseItemPacket(hand, sequence, 0F, 90F));
        }

        private boolean temporary() { return TemporaryFluidChanges.get(helper.getLevel()).pending().containsKey(target.asLong()); }

        private void clear() {
            PlacementDelay.clear(player);
            TemporaryFluidChanges.get(helper.getLevel()).cancel(target);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                helper.getLevel().setBlock(target.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
                helper.getLevel().setBlock(target.offset(x, 0, z), (x == 0 && z == 0 ? Blocks.AIR : Blocks.STONE).defaultBlockState(), 2);
                helper.getLevel().setBlock(target.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }

        @Override public void close() {
            try {
                PlacementDelay.clear(player);
                TemporaryFluidChanges.get(helper.getLevel()).cancel(target);
                helper.getLevel().setBlock(target, Blocks.AIR.defaultBlockState(), 2);
                BucketPlacementIntent.clear(player);
                player.getInventory().clearContent();
                player.server.getPlayerList().remove(player);
            } finally { channel.finishAndReleaseAll(); }
        }
    }
}

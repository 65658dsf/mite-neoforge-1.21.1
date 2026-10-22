package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.mite.world.BucketPlacementController;
import io.github.ningmeng123.mite.world.BucketPlacementIntent;
import io.github.ningmeng123.mite.world.PlacementDelay;
import io.github.ningmeng123.mite.world.TemporaryFluidChanges;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real bucket pickup, inventory conservation and server-authoritative payment feedback. @author NingMeng123 */
@GameTestHolder("mite_bucket_pickup")
@PrefixGameTestTemplate(false)
public final class BucketPickupGameTests {
    private BucketPickupGameTests() {}

    @GameTest(template = "empty")
    public static void everyMetalAndHandCanCollectAllFlowingWaterLevels(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            for (Item empty : emptyBuckets()) for (InteractionHand hand : InteractionHand.values()) {
                for (int waterLevel = 1; waterLevel <= 15; waterLevel++) {
                    f.clear();
                    h.getLevel().setBlock(f.target, water(waterLevel), 2);
                    f.player.setItemInHand(hand, new ItemStack(empty));
                    var result = f.use(hand);
                    h.assertTrue(result.getResult().consumesAction(), "Real use targets flowing water level " + waterLevel + " with " + empty + " in " + hand);
                    h.assertTrue(result.getObject().is(filledWater(empty)), "One flowing cell fills the original metal vessel");
                    h.assertValueEqual(result.getObject().getCount(), 1, "Exactly one water bucket is obtained");
                    h.assertTrue(h.getLevel().getBlockState(f.target).isAir(), "Pickup removes the targeted flowing cell");
                    h.assertValueEqual(f.player.totalExperience, 1000, "Collecting water never charges source-placement experience");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinarySourceWaterStillFillsEveryMetalBucket(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            for (Item empty : emptyBuckets()) for (InteractionHand hand : InteractionHand.values()) {
                f.clear();
                h.getLevel().setBlock(f.target, water(0), 2);
                f.player.setItemInHand(hand, new ItemStack(empty));
                var result = f.use(hand);
                h.assertTrue(result.getResult().consumesAction() && result.getObject().is(filledWater(empty)),
                        "Source pickup retains the matching water bucket for " + empty + " in " + hand);
                h.assertTrue(h.getLevel().getBlockState(f.target).isAir(), "Native source pickup still removes the source");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nearerFlowingWaterIsCollectedBeforeASourceBehindIt(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            for (Item empty : emptyBuckets()) {
                f.clear();
                h.getLevel().setBlock(f.target.below(), water(0), 2);
                h.getLevel().setBlock(f.target, water(4), 2);
                f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(empty));
                var result = f.use(InteractionHand.MAIN_HAND);
                h.assertTrue(result.getResult().consumesAction() && result.getObject().is(filledWater(empty)),
                        "The real raycast selects the nearer flowing water for " + empty);
                h.assertTrue(h.getLevel().getBlockState(f.target).isAir(), "The front flowing cell is collected");
                h.assertTrue(h.getLevel().getBlockState(f.target.below()).equals(water(0)), "A deeper source is not collected through the flowing water");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void flowingLavaCannotBeCollectedByTheWaterExtension(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            for (Item empty : emptyBuckets()) for (int lavaLevel : new int[]{1, 4, 7, 8}) {
                f.clear();
                BlockState lava = Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, lavaLevel);
                h.getLevel().setBlock(f.target, lava, 2);
                f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(empty));
                var result = f.use(InteractionHand.MAIN_HAND);
                h.assertFalse(result.getResult().consumesAction(), "Flowing lava remains ineligible for pickup with " + empty);
                h.assertTrue(result.getObject().is(empty), "Rejected lava pickup retains the empty bucket");
                h.assertTrue(h.getLevel().getBlockState(f.target).equals(lava), "Rejected lava pickup preserves the actual fluid cell");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void solidOcclusionAndAdventurePermissionsPreventFlowingWaterPickup(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            for (Item empty : emptyBuckets()) for (boolean occluded : new boolean[]{true, false}) {
                f.clear();
                h.getLevel().setBlock(f.target, water(4), 2);
                f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(empty));
                if (occluded) h.getLevel().setBlock(f.target.above(), Blocks.STONE.defaultBlockState(), 2);
                else f.player.getAbilities().mayBuild = false;
                var result = f.use(InteractionHand.MAIN_HAND);
                h.assertFalse(result.getResult().consumesAction(), "A solid obstruction or missing build permission rejects " + empty);
                h.assertTrue(result.getObject().is(empty), "Denied pickup retains the vessel");
                h.assertTrue(h.getLevel().getBlockState(f.target).equals(water(4)), "Denied pickup cannot remove water through a wall or permission restriction");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void stackedEmptyBucketsConsumeOneAndDeliverOneMatchingWaterBucket(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            for (Item empty : emptyBuckets()) for (InteractionHand hand : InteractionHand.values()) {
                f.clear();
                h.getLevel().setBlock(f.target, water(7), 2);
                ItemStack stack = new ItemStack(empty, 2);
                h.assertTrue(stack.getMaxStackSize() >= 2, "The empty vessel legitimately supports a two-bucket stack");
                f.player.setItemInHand(hand, stack);
                var result = f.use(hand);
                h.assertTrue(result.getResult().consumesAction(), "A stacked empty vessel can collect flowing water");
                h.assertTrue(result.getObject().is(empty) && result.getObject().getCount() == 1, "Exactly one empty vessel remains in the used hand");
                h.assertValueEqual(f.player.getInventory().countItem(filledWater(empty)), 1, "Exactly one matching filled vessel enters the inventory");
                h.assertValueEqual(f.player.getInventory().countItem(empty), 1, "The entire inventory conserves the second empty vessel");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void collectedWaterCancelsItsOldTemporaryDecayRecord(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            for (int waterLevel : new int[]{0, 4, 8}) for (Item empty : emptyBuckets()) {
                f.clear();
                h.getLevel().setBlock(f.target, water(waterLevel), 2);
                TemporaryFluidChanges.get(h.getLevel()).schedule(f.target, true, h.getLevel().getGameTime() + 16);
                f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(empty));
                h.assertTrue(f.use(InteractionHand.MAIN_HAND).getResult().consumesAction(), "Pickup accepts a water cell carrying an old finite deadline");
                h.assertTrue(TemporaryFluidChanges.get(h.getLevel()).at(f.target) == null,
                        "Collected water leaves no deadline that could downgrade a later paid source");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void paidSourcesSendOneOriginalDrainSoundToThePayingPlayer(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            for (Item empty : emptyBuckets()) for (boolean water : new boolean[]{true, false}) {
                f.clear();
                Item filled = empty instanceof MiteBucketItem metal
                        ? MiteFoods.get(metal.metal() + (water ? "_water_bucket" : "_lava_bucket"))
                        : water ? Items.WATER_BUCKET : Items.LAVA_BUCKET;
                h.assertTrue(f.pour(filled, true), "An adequately funded permanent source is placed");
                h.assertValueEqual(f.player.totalExperience, 900, "A successful permanent source charges exactly one hundred points");
                List<ClientboundSoundPacket> sounds = f.drainSounds();
                h.assertValueEqual(sounds.size(), 1, "The payer receives exactly one original level-drain sound for " + filled);
                var sound = sounds.getFirst();
                h.assertTrue(sound.getSource() == SoundSource.PLAYERS && sound.getVolume() >= .9F && sound.getVolume() < 1.1F
                                && sound.getPitch() >= .9F && sound.getPitch() < 1.1F,
                        "The sound packet retains R196's player category and randomized volume/pitch bounds");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void freeBlockedAndCanceledSourceAttemptsDoNotPlayDrainSound(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            for (int scenario = 0; scenario < 6; scenario++) {
                f.clear();
                if (scenario == 1) PlayerExperience.setTotal(f.player, 99);
                if (scenario == 2) f.player.setGameMode(GameType.CREATIVE);
                if (scenario == 3) h.getLevel().setBlock(f.target, water(0), 2);
                if (scenario == 4) h.getLevel().setBlock(f.target, Blocks.STONE.defaultBlockState(), 2);
                Consumer<PlayerXpEvent.XpChange> veto = event -> {
                    if (event.getEntity() == f.player && event.getAmount() < 0) event.setCanceled(true);
                };
                if (scenario == 5) NeoForge.EVENT_BUS.addListener(PlayerXpEvent.XpChange.class, veto);
                try {
                    int before = f.player.totalExperience;
                    boolean placed = f.pour(Items.WATER_BUCKET, scenario != 0);
                    h.assertValueEqual(placed, scenario < 4, "Ordinary, insufficient-XP, creative and existing-source pours succeed; blocked/vetoed requests fail");
                    h.assertValueEqual(f.player.totalExperience, before, "An uncharged interaction leaves experience unchanged");
                    h.assertValueEqual(f.drainSounds().size(), 0, "No charge means no drain sound in scenario " + scenario);
                } finally {
                    if (scenario == 5) NeoForge.EVENT_BUS.unregister(veto);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lavaThatImmediatelyHardensRefundsPaymentAndDoesNotPlayDrainSound(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            h.getLevel().setBlock(f.target.east(), water(0), 2);
            h.assertTrue(f.pour(Items.LAVA_BUCKET, true), "A lava bucket still succeeds when adjacent water hardens its contents");
            h.assertTrue(h.getLevel().getFluidState(f.target).isEmpty(), "Immediate hardening did not actually produce a lasting liquid source");
            h.assertValueEqual(f.player.totalExperience, 1000, "The unsuccessful source creation refunds its payment");
            h.assertValueEqual(f.drainSounds().size(), 0, "A refunded source must not emit the payment sound");
        }
        h.succeed();
    }

    private static BlockState water(int level) {
        return Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, level);
    }

    private static List<Item> emptyBuckets() {
        List<Item> buckets = new ArrayList<>();
        buckets.add(Items.BUCKET);
        for (String metal : MiteFoods.METALS) buckets.add(MiteFoods.get(metal + "_bucket"));
        return buckets;
    }

    private static Item filledWater(Item empty) {
        return empty instanceof MiteBucketItem metal ? MiteFoods.get(metal.metal() + "_water_bucket") : Items.WATER_BUCKET;
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final BlockPos target;
        private final List<ClientboundSoundPacket> sounds = new ArrayList<>();

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-bucket-flow"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            target = helper.absolutePos(new BlockPos(3, 2, 3));
            player.moveTo(target.getX() + .5, target.getY() + 2, target.getZ() + .5, 0, 90);
            player.setNoGravity(true);
            player.setOnGround(true);
            player.removeAllEffects();
            channel.pipeline().addLast("mite_bucket_feedback", new ChannelOutboundHandlerAdapter() {
                @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                    if (message instanceof ClientboundSoundPacket sound) sounds.add(sound);
                    super.write(context, message, promise);
                }
            });
            clear();
        }

        private void clear() {
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().mayBuild = true;
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 1000);
            PlacementDelay.clear(player);
            TemporaryFluidChanges.get(helper.getLevel()).cancel(target);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                helper.getLevel().setBlock(target.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
                helper.getLevel().setBlock(target.offset(x, 0, z), (x == 0 && z == 0 ? Blocks.AIR : Blocks.STONE).defaultBlockState(), 2);
                helper.getLevel().setBlock(target.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 2);
            }
            channel.runPendingTasks();
            sounds.clear();
        }

        private InteractionResultHolder<ItemStack> use(InteractionHand hand) {
            var result = player.getItemInHand(hand).use(helper.getLevel(), player, hand);
            player.setItemInHand(hand, result.getObject());
            return result;
        }

        private boolean pour(Item item, boolean source) {
            ItemStack stack = new ItemStack(item);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            return BucketPlacementController.withIntent(player, source,
                    () -> ((BucketItem) item).emptyContents(player, helper.getLevel(), target, null, stack));
        }

        private List<ClientboundSoundPacket> drainSounds() {
            channel.runPendingTasks();
            return sounds.stream().filter(sound -> "mite:imported.random.level_drain".equals(
                    BuiltInRegistries.SOUND_EVENT.getKey(sound.getSound().value()).toString())).toList();
        }

        @Override public void close() {
            try {
                PlacementDelay.clear(player);
                TemporaryFluidChanges.get(helper.getLevel()).cancel(target);
                BucketPlacementIntent.clear(player);
                player.getInventory().clearContent();
                player.server.getPlayerList().remove(player);
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }
}

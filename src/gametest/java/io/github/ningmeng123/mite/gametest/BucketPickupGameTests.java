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
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
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
                    h.assertTrue(h.getLevel().getBlockState(f.target).equals(water(waterLevel)), "Ordinary pickup preserves the targeted flowing cell");
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
                h.assertTrue(h.getLevel().getBlockState(f.target).equals(water(0)), "Ordinary pickup preserves the source");
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
                var result = f.use(InteractionHand.MAIN_HAND, true);
                h.assertTrue(result.getResult().consumesAction() && result.getObject().is(filledWater(empty)),
                        "The real raycast selects the nearer flowing water for " + empty);
                h.assertTrue(h.getLevel().getBlockState(f.target).isAir(), "The front flowing cell is collected");
                h.assertTrue(h.getLevel().getBlockState(f.target.below()).equals(water(0)), "A deeper source is not collected through the flowing water");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyMetalAndHandCanCollectAllLavaLevelsWithoutRemovingThem(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            for (Item empty : emptyBuckets()) for (InteractionHand hand : InteractionHand.values()) for (int lavaLevel = 0; lavaLevel <= 15; lavaLevel++) {
                f.clear();
                BlockState lava = liquid(false, lavaLevel);
                h.getLevel().setBlock(f.target, lava, 2);
                f.player.setItemInHand(hand, new ItemStack(empty));
                var result = f.use(hand);
                h.assertTrue(result.getResult().consumesAction(), "Real use accepts source or flowing lava level " + lavaLevel + " with " + empty + " in " + hand);
                h.assertTrue(result.getObject().is(filledLiquid(empty, false)), "Lava fills the matching vessel with the deterministic nonmelting roll");
                h.assertTrue(h.getLevel().getBlockState(f.target).equals(lava), "Ordinary lava pickup preserves its original liquid cell");
                h.assertValueEqual(f.player.totalExperience, 1000, "Collecting lava never charges source-placement experience");
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
    public static void onlyControlPickupCancelsTheCollectedLiquidsTemporaryDecayRecord(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            int sequence = 200;
            for (boolean water : new boolean[]{true, false}) for (int fluidLevel : new int[]{0, 4, 8}) for (Item empty : emptyBuckets()) {
                f.clear();
                BlockState before = liquid(water, fluidLevel);
                h.getLevel().setBlock(f.target, before, 2);
                var changes = TemporaryFluidChanges.get(h.getLevel());
                changes.schedule(f.target, water, h.getLevel().getGameTime() + 48);
                var pending = changes.at(f.target);
                f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(empty));
                f.networkUse(InteractionHand.MAIN_HAND, sequence++, false);
                h.assertTrue(f.player.getMainHandItem().is(filledLiquid(empty, water)), "Ordinary pickup fills the original vessel");
                h.assertTrue(h.getLevel().getBlockState(f.target).equals(before), "Ordinary pickup retains the liquid carrying the finite deadline");
                h.assertTrue(pending.equals(changes.at(f.target)), "Ordinary pickup preserves the existing decay deadline exactly");
                f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(empty));
                f.networkUse(InteractionHand.MAIN_HAND, sequence++, true);
                h.assertTrue(f.player.getMainHandItem().is(filledLiquid(empty, water)), "Ctrl pickup also fills the matching vessel");
                h.assertTrue(h.getLevel().getBlockState(f.target).isAir(), "Ctrl pickup removes the liquid cell");
                h.assertTrue(changes.at(f.target) == null, "Only actual removal clears the deadline that could affect a later source");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realControlPacketsCollectBothLiquidsInEitherHandWithoutExperience(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            int sequence = 400;
            for (Item empty : emptyBuckets()) for (InteractionHand hand : InteractionHand.values()) {
                for (boolean water : new boolean[]{true, false}) for (int fluidLevel : new int[]{0, 4, 8}) {
                    for (boolean control : new boolean[]{false, true}) {
                        f.clear();
                        BlockState before = liquid(water, fluidLevel);
                        h.getLevel().setBlock(f.target, before, 2);
                        PlayerExperience.setTotal(f.player, 0);
                        f.player.setItemInHand(hand, new ItemStack(empty));
                        f.networkUse(hand, sequence++, control);
                        h.assertTrue(f.player.getItemInHand(hand).is(filledLiquid(empty, water)),
                                "The actual use packet fills " + empty + " in " + hand + " for liquid=" + water + ", level=" + fluidLevel + ", Ctrl=" + control);
                        h.assertTrue(control ? h.getLevel().getBlockState(f.target).isAir() : h.getLevel().getBlockState(f.target).equals(before),
                                "Only the matching Ctrl use packet removes the selected liquid cell");
                        h.assertValueEqual(f.player.totalExperience, 0, "Ctrl collection is independent of the placement fee and cannot create experience debt");
                        h.assertValueEqual(f.drainSounds().size(), 0, "Picking up either liquid never plays a source-payment sound");
                    }
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidOrReleasedControlRequestsCannotRemoveLiquidsOrArmTheFollowingUse(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            for (int scenario = 0; scenario < 4; scenario++) {
                f.clear();
                h.getLevel().setBlock(f.target, water(0), 2);
                ItemStack original = new ItemStack(Items.BUCKET);
                f.player.setItemInHand(InteractionHand.MAIN_HAND, original);
                f.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BUCKET));
                int sequence = 900 + scenario * 10;
                BucketPlacementIntent.receive(f.player, InteractionHand.MAIN_HAND, sequence, true);
                InteractionHand usedHand = scenario == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                int usedSequence = scenario == 0 ? sequence + 1 : sequence;
                if (scenario == 2) f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                if (scenario == 3) BucketPlacementIntent.receive(f.player, InteractionHand.MAIN_HAND, sequence, false);
                f.packet(usedHand, usedSequence);
                h.assertTrue(f.player.getItemInHand(usedHand).is(Items.WATER_BUCKET), "A mismatched or released request still permits ordinary water collection");
                h.assertTrue(h.getLevel().getBlockState(f.target).equals(water(0)), "Sequence, hand, stack and released-key checks prevent unauthorized removal in scenario " + scenario);
                f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                f.packet(InteractionHand.MAIN_HAND, sequence);
                h.assertTrue(f.player.getMainHandItem().is(Items.WATER_BUCKET), "A later unarmed pickup remains available");
                h.assertTrue(h.getLevel().getBlockState(f.target).equals(water(0)), "A consumed stale request cannot remove the source during a following use");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void anExpiredControlRequestCannotRemoveTheSource(GameTestHelper h) {
        Fixture f = new Fixture(h);
        h.getLevel().setBlock(f.target, water(0), 2);
        f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        BucketPlacementIntent.receive(f.player, InteractionHand.MAIN_HAND, 980, true);
        h.runAfterDelay(7, () -> {
            try {
                f.packet(InteractionHand.MAIN_HAND, 980);
                h.assertTrue(f.player.getMainHandItem().is(Items.WATER_BUCKET), "An expired modifier still allows ordinary pickup");
                h.assertTrue(h.getLevel().getBlockState(f.target).equals(water(0)), "A request older than five ticks cannot remove the source");
                h.assertValueEqual(f.player.totalExperience, 1000, "Expiring a pickup modifier cannot spend experience");
                h.succeed();
            } finally { f.close(); }
        });
    }

    @GameTest(template = "empty")
    public static void creativeBucketsRemoveLiquidsOnlyWithTheMatchingControlPacket(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            int sequence = 1000;
            for (Item empty : emptyBuckets()) for (InteractionHand hand : InteractionHand.values()) {
                for (boolean water : new boolean[]{true, false}) for (boolean control : new boolean[]{false, true}) {
                    f.clear();
                    f.player.setGameMode(GameType.CREATIVE);
                    h.getLevel().setBlock(f.target, liquid(water, 0), 2);
                    f.player.setItemInHand(hand, new ItemStack(empty));
                    f.networkUse(hand, sequence++, control);
                    h.assertTrue(f.player.getItemInHand(hand).is(empty), "Creative pickup preserves the held empty vessel");
                    h.assertValueEqual(f.player.getInventory().countItem(filledLiquid(empty, water)), 1, "Creative collection grants the matching filled vessel exactly once");
                    h.assertTrue(control ? h.getLevel().getBlockState(f.target).isAir() : h.getLevel().getBlockState(f.target).equals(liquid(water, 0)),
                            "Creative mode obeys the same Ctrl-only removal rule");
                    h.assertValueEqual(f.player.totalExperience, 1000, "Creative collection is free");
                }
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
        return liquid(true, level);
    }

    private static BlockState liquid(boolean water, int level) {
        return (water ? Blocks.WATER : Blocks.LAVA).defaultBlockState().setValue(LiquidBlock.LEVEL, level);
    }

    private static List<Item> emptyBuckets() {
        List<Item> buckets = new ArrayList<>();
        buckets.add(Items.BUCKET);
        for (String metal : MiteFoods.METALS) buckets.add(MiteFoods.get(metal + "_bucket"));
        return buckets;
    }

    private static Item filledWater(Item empty) {
        return filledLiquid(empty, true);
    }

    private static Item filledLiquid(Item empty, boolean water) {
        return empty instanceof MiteBucketItem metal ? MiteFoods.get(metal.metal() + (water ? "_water_bucket" : "_lava_bucket"))
                : water ? Items.WATER_BUCKET : Items.LAVA_BUCKET;
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
            BucketPlacementIntent.clear(player);
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
            return use(hand, false);
        }

        private InteractionResultHolder<ItemStack> use(InteractionHand hand, boolean control) {
            helper.getLevel().random.setSeed(0L);
            var result = BucketPlacementController.withIntent(player, control,
                    () -> player.getItemInHand(hand).use(helper.getLevel(), player, hand));
            player.setItemInHand(hand, result.getObject());
            return result;
        }

        private void networkUse(InteractionHand hand, int sequence, boolean control) {
            BucketPlacementIntent.receive(player, hand, sequence, control);
            packet(hand, sequence);
        }

        private void packet(InteractionHand hand, int sequence) {
            helper.getLevel().random.setSeed(0L);
            player.connection.handleUseItem(new ServerboundUseItemPacket(hand, sequence, 0F, 90F));
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

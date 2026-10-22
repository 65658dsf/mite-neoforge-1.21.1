package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.mite.world.BucketPlacementController;
import io.github.ningmeng123.mite.world.BucketPlacementIntent;
import io.github.ningmeng123.mite.world.PlacementDelay;
import io.github.ningmeng123.mite.world.TemporaryFluidChanges;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real iron/metal bucket entry points and authoritative source-placement transactions. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class BucketPlacementGameTests {
    private BucketPlacementGameTests() {}

    @GameTest(template = "empty")
    public static void allIronAndMetalBucketsNormallyPourFlowingWaterAndLavaWithoutSpendingExperience(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1000)) {
            for (boolean water : new boolean[]{true, false}) for (Item item : filledBuckets(water)) {
                f.clear();
                BucketItem bucket = (BucketItem)item;
                h.assertTrue(bucket.emptyContents(f.player, h.getLevel(), f.target, null, new ItemStack(item)),
                        "Normal placement succeeds for " + item);
                var fluid = h.getLevel().getFluidState(f.target);
                h.assertTrue(fluid.is(water ? net.minecraft.tags.FluidTags.WATER : net.minecraft.tags.FluidTags.LAVA),
                        "The expected liquid reaches the real level for " + item);
                h.assertTrue(fluid.isSource(), "R196 first places a full liquid cell before its deferred decay for " + item);
                var pending = TemporaryFluidChanges.get(h.getLevel()).pending().get(f.target.asLong());
                h.assertTrue(pending != null && pending.water() == water, "The initially full cell has a finite liquid-specific decay record");
                h.assertValueEqual(pending.due(), h.getLevel().getGameTime() + (water ? 16 : 48),
                        "The source-derived delay is 16 water ticks or 48 lava ticks");
                h.assertValueEqual(f.player.totalExperience, 1000, "A flowing pour has no experience cost");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void isolatedNormalWaterAndLavaDisappearOnTheirRealScheduledFluidLogic(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 0)) {
            for (boolean water : new boolean[]{true, false}) {
                f.clear();
                BucketItem bucket = (BucketItem)(water ? Items.WATER_BUCKET : Items.LAVA_BUCKET);
                h.assertTrue(bucket.emptyContents(f.player, h.getLevel(), f.target, null, new ItemStack(bucket)),
                        "The normal finite pour is accepted");
                TemporaryFluidChanges data = TemporaryFluidChanges.get(h.getLevel());
                var pending = data.pending().get(f.target.asLong());
                CompoundTag saved = data.save(new CompoundTag(), h.getLevel().registryAccess());
                var loaded = TemporaryFluidChanges.load(saved, h.getLevel().registryAccess());
                h.assertValueEqual(loaded.pending().get(f.target.asLong()).due(), pending.due(),
                        "The finite deadline survives SavedData serialization");
                data.schedule(f.target, water, h.getLevel().getGameTime());
                data.tick(h.getLevel());
                h.assertFalse(h.getLevel().getFluidState(f.target).isSource(), "The due deadline converts the full cell into flowing liquid");
                var fluid = water ? Fluids.FLOWING_WATER : Fluids.FLOWING_LAVA;
                fluid.tick(h.getLevel(), f.target, h.getLevel().getFluidState(f.target));
                h.assertTrue(h.getLevel().getFluidState(f.target).isEmpty(),
                        "An unsupported finite liquid drains instead of becoming a persistent pool");
                h.assertValueEqual(f.player.totalExperience, 0, "Drainage cannot spend or award experience");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void automatedBucketPourWithoutPlayerCannotProduceFreeSources(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 0)) {
            for (boolean water : new boolean[]{true, false}) for (Item item : filledBuckets(water)) {
                f.clear();
                BucketItem bucket = (BucketItem)item;
                h.assertTrue(bucket.emptyContents(null, h.getLevel(), f.target, null, new ItemStack(item)),
                        "The same no-operator entry point used by dispensers still pours " + item);
                h.assertTrue(TemporaryFluidChanges.get(h.getLevel()).pending().containsKey(f.target.asLong()),
                        "Automation cannot bypass the finite lifetime without a paid player source request for " + item);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualNormalUseReturnsTheMatchingEmptyVesselInEitherHand(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1000)) {
            for (InteractionHand hand : InteractionHand.values()) for (boolean water : new boolean[]{true, false}) {
                for (Item item : filledBuckets(water)) {
                    f.clear();
                    f.player.setItemInHand(hand, new ItemStack(item));
                    var result = f.player.getItemInHand(hand).use(h.getLevel(), f.player, hand);
                    h.assertTrue(result.getResult().consumesAction(), "Real use accepts the target for " + item + " in " + hand);
                    Item empty = item instanceof io.github.ningmeng123.mite.item.MiteBucketItem metal
                            ? MiteFoods.get(metal.metal() + "_bucket") : Items.BUCKET;
                    h.assertTrue(result.getObject().is(empty), "The used liquid returns exactly its original metal vessel");
                    h.assertValueEqual(result.getObject().getCount(), 1, "One full vessel yields one empty vessel");
                    h.assertTrue(TemporaryFluidChanges.get(h.getLevel()).pending().containsKey(f.target.asLong()),
                            "Hand selection does not turn ordinary use into a permanent source");
                    h.assertValueEqual(f.player.totalExperience, 1000, "Ordinary use is free in both hands");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void controlRequestedIronAndMetalSourcesChargeExactlyOneHundredPoints(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1000)) {
            for (boolean water : new boolean[]{true, false}) for (Item item : filledBuckets(water)) {
                f.clear();
                PlayerExperience.setTotal(f.player, 1000);
                ItemStack held = new ItemStack(item);
                f.player.setItemInHand(InteractionHand.MAIN_HAND, held);
                boolean placed = BucketPlacementController.withIntent(f.player, true,
                        () -> ((BucketItem)item).emptyContents(f.player, h.getLevel(), f.target, null, held));
                h.assertTrue(placed, "A sufficiently funded source request succeeds for " + item);
                h.assertTrue(h.getLevel().getFluidState(f.target).isSource(), "Ctrl creates a complete source liquid cell");
                h.assertFalse(TemporaryFluidChanges.get(h.getLevel()).pending().containsKey(f.target.asLong()),
                        "The paid source has no finite deadline");
                h.assertValueEqual(f.player.totalExperience, 900, "Every source material costs exactly 100 points");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void insufficientExperienceFallsBackToFiniteLiquidWithoutCreatingDebt(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 0)) {
            for (int experience : new int[]{-50, 0, 99}) for (boolean water : new boolean[]{true, false}) {
                f.clear();
                PlayerExperience.setTotal(f.player, experience);
                BucketItem bucket = (BucketItem)(water ? Items.WATER_BUCKET : Items.LAVA_BUCKET);
                h.assertTrue(BucketPlacementController.withIntent(f.player, true,
                        () -> bucket.emptyContents(f.player, h.getLevel(), f.target, null, new ItemStack(bucket))),
                        "R196 insufficient-XP Ctrl pour remains a normal finite placement");
                h.assertTrue(TemporaryFluidChanges.get(h.getLevel()).pending().containsKey(f.target.asLong()),
                        "Insufficient XP cannot purchase a permanent source");
                h.assertValueEqual(f.player.totalExperience, experience, "Finite fallback does not increase experience debt");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void creativePlacementAlwaysMakesPermanentSourcesWithoutSpendingPoints(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 0)) {
            f.player.setGameMode(GameType.CREATIVE);
            for (boolean request : new boolean[]{false, true}) for (boolean water : new boolean[]{true, false}) {
                f.clear();
                BucketItem bucket = (BucketItem)(water ? Items.WATER_BUCKET : Items.LAVA_BUCKET);
                h.assertTrue(BucketPlacementController.withIntent(f.player, request,
                        () -> bucket.emptyContents(f.player, h.getLevel(), f.target, null, new ItemStack(bucket))),
                        "Creative use succeeds regardless of modifier");
                h.assertTrue(h.getLevel().getFluidState(f.target).isSource(), "Creative liquid is a real source");
                h.assertFalse(TemporaryFluidChanges.get(h.getLevel()).pending().containsKey(f.target.asLong()),
                        "Creative sources are not temporary");
                h.assertValueEqual(f.player.totalExperience, 0, "Creative placement is free");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradingFiniteLiquidCancelsItsOldDeadlineButExistingNaturalSourceIsFree(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1000)) {
            for (boolean water : new boolean[]{true, false}) {
                f.clear();
                PlayerExperience.setTotal(f.player, 1000);
                BucketItem bucket = (BucketItem)(water ? Items.WATER_BUCKET : Items.LAVA_BUCKET);
                h.assertTrue(bucket.emptyContents(f.player, h.getLevel(), f.target, null, new ItemStack(bucket)), "Create the temporary cell first");
                h.assertTrue(TemporaryFluidChanges.get(h.getLevel()).pending().containsKey(f.target.asLong()), "The temporary deadline exists");
                PlacementDelay.clear(f.player);
                h.assertTrue(BucketPlacementController.withIntent(f.player, true,
                        () -> bucket.emptyContents(f.player, h.getLevel(), f.target, null, new ItemStack(bucket))),
                        "Ctrl upgrades the pending liquid to a source");
                h.assertValueEqual(f.player.totalExperience, 900, "Temporary-to-permanent upgrade costs once");
                h.assertFalse(TemporaryFluidChanges.get(h.getLevel()).pending().containsKey(f.target.asLong()), "Upgrade removes the old deadline");
                PlacementDelay.clear(f.player);
                h.assertTrue(BucketPlacementController.withIntent(f.player, true,
                        () -> bucket.emptyContents(f.player, h.getLevel(), f.target, null, new ItemStack(bucket))),
                        "Pouring at an existing source retains native success behavior");
                h.assertValueEqual(f.player.totalExperience, 900, "Existing permanent liquid does not charge a second time");
                PlacementDelay.clear(f.player);
                h.assertTrue(bucket.emptyContents(f.player, h.getLevel(), f.target, null, new ItemStack(bucket)), "Normal pouring at an existing source is harmless");
                h.assertFalse(TemporaryFluidChanges.get(h.getLevel()).pending().containsKey(f.target.asLong()), "Normal pouring cannot schedule natural-source deletion");
                h.assertValueEqual(f.player.totalExperience, 900, "Existing sources stay free for an ordinary pour too");
                f.clear();
                PlayerExperience.setTotal(f.player, 1000);
                var flowing = water ? Fluids.FLOWING_WATER : Fluids.FLOWING_LAVA;
                h.getLevel().setBlock(f.target, flowing.getFlowing(4, false).createLegacyBlock(), 2);
                h.assertTrue(BucketPlacementController.withIntent(f.player, true,
                        () -> bucket.emptyContents(f.player, h.getLevel(), f.target, null, new ItemStack(bucket))),
                        "Ctrl can also upgrade a genuine flowing cell after its original temporary deadline has elapsed");
                h.assertTrue(h.getLevel().getFluidState(f.target).isSource(), "The formerly flowing liquid actually becomes a source");
                h.assertValueEqual(f.player.totalExperience, 900, "Upgrading a flowing cell costs the same one hundred points");
                h.assertFalse(TemporaryFluidChanges.get(h.getLevel()).pending().containsKey(f.target.asLong()), "A flowing-to-source upgrade is permanent");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mixingWaterAndLavaProducesSolidBlocksWithoutChargingSourceExperience(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1000)) {
            for (boolean pouringWater : new boolean[]{true, false}) for (boolean existingSource : new boolean[]{true, false}) {
                f.clear();
                var previous = pouringWater ? Fluids.FLOWING_LAVA : Fluids.FLOWING_WATER;
                var existing = existingSource ? previous.getSource(false) : previous.getFlowing(4, false);
                h.getLevel().setBlock(f.target, existing.createLegacyBlock(), 2);
                TemporaryFluidChanges.get(h.getLevel()).schedule(f.target, !pouringWater, h.getLevel().getGameTime() + 48);
                BucketItem bucket = (BucketItem)(pouringWater ? Items.WATER_BUCKET : Items.LAVA_BUCKET);
                int score = f.player.getScore();
                h.assertTrue(BucketPlacementController.withIntent(f.player, true,
                        () -> bucket.emptyContents(f.player, h.getLevel(), f.target, null, new ItemStack(bucket))),
                        "Mixed-liquid interaction remains a successful bucket pour");
                h.assertTrue(h.getLevel().getBlockState(f.target).is(pouringWater && existingSource ? Blocks.OBSIDIAN : Blocks.COBBLESTONE),
                        "Water over a lava source makes obsidian; the other mixed cases make cobblestone");
                h.assertValueEqual(f.player.totalExperience, 1000, "Hardening liquid into stone does not create a source and has no fee");
                h.assertValueEqual(f.player.getScore(), score, "Mixed liquid cannot leave a partial source-payment score change");
                h.assertFalse(TemporaryFluidChanges.get(h.getLevel()).pending().containsKey(f.target.asLong()),
                        "The old liquid deadline is canceled after it becomes stone");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fishAndOtherMobBucketsUseTheOrdinaryTemporaryAndPaidSourceRules(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1000)) {
            for (Item item : new Item[]{Items.COD_BUCKET, Items.SALMON_BUCKET, Items.PUFFERFISH_BUCKET,
                    Items.TROPICAL_FISH_BUCKET, Items.AXOLOTL_BUCKET, Items.TADPOLE_BUCKET}) for (boolean source : new boolean[]{false, true}) {
                f.clear();
                PlayerExperience.setTotal(f.player, 1000);
                BucketItem bucket = (BucketItem)item;
                h.assertTrue(BucketPlacementController.withIntent(f.player, source,
                        () -> bucket.emptyContents(f.player, h.getLevel(), f.target, null, new ItemStack(bucket))),
                        "The shared liquid controller accepts every aquatic mob bucket: " + item);
                h.assertTrue(h.getLevel().getFluidState(f.target).isSource(), "Mob buckets initially place a complete cell before ordinary decay");
                h.assertValueEqual(TemporaryFluidChanges.get(h.getLevel()).pending().containsKey(f.target.asLong()), !source,
                        "Only Ctrl placement avoids the ordinary temporary deadline");
                h.assertValueEqual(f.player.totalExperience, source ? 900 : 1000, "Only a requested permanent source pays the ordinary fee");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledOrModifiedExperienceTransactionsDoNotPlaceOrConsumeSourceBuckets(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 100)) {
            for (int mode = 0; mode < 4; mode++) {
                f.clear();
                PlayerExperience.setTotal(f.player, 100);
                f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
                int intervention = mode;
                int[] calls = {0};
                Consumer<PlayerXpEvent.XpChange> points = event -> {
                    if (event.getEntity() != f.player || event.getAmount() >= 0 || intervention > 1) return;
                    calls[0]++;
                    if (intervention == 0) event.setCanceled(true); else event.setAmount(0);
                };
                Consumer<PlayerXpEvent.LevelChange> levels = event -> {
                    if (event.getEntity() != f.player || event.getLevels() >= 0 || intervention < 2) return;
                    calls[0]++;
                    if (intervention == 2) event.setCanceled(true); else event.setLevels(0);
                };
                NeoForge.EVENT_BUS.addListener(PlayerXpEvent.XpChange.class, points);
                NeoForge.EVENT_BUS.addListener(PlayerXpEvent.LevelChange.class, levels);
                try {
                    var result = BucketPlacementController.withIntent(f.player, true,
                            () -> f.player.getMainHandItem().use(h.getLevel(), f.player, InteractionHand.MAIN_HAND));
                    h.assertTrue(calls[0] > 0, "Source placement consults the real applicable experience event");
                    h.assertFalse(result.getResult().consumesAction(), "A payment veto rejects the source placement");
                    h.assertTrue(result.getObject().is(Items.WATER_BUCKET), "A veto retains the filled vessel");
                    h.assertTrue(h.getLevel().getFluidState(f.target).isEmpty(), "No liquid appears before approval");
                    h.assertValueEqual(f.player.totalExperience, 100, "A veto leaves all experience points unchanged");
                    h.assertFalse(TemporaryFluidChanges.get(h.getLevel()).pending().containsKey(f.target.asLong()), "Rejected source has no stray cleanup record");
                } finally {
                    NeoForge.EVENT_BUS.unregister(points);
                    NeoForge.EVENT_BUS.unregister(levels);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void solidBlockedAndMantleEvaporatedPoursCannotChargeExperience(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1000)) {
            h.getLevel().setBlock(f.target, Blocks.STONE.defaultBlockState(), 2);
            h.assertFalse(BucketPlacementController.withIntent(f.player, true,
                    () -> ((BucketItem)Items.WATER_BUCKET).emptyContents(f.player, h.getLevel(), f.target, null, new ItemStack(Items.WATER_BUCKET))),
                    "An unreplaceable target cannot accept water without a recursive hit fallback");
            h.assertValueEqual(f.player.totalExperience, 1000, "A blocked target costs no experience");
            f.clear();
            h.getLevel().setBlock(f.target.below(), io.github.ningmeng123.mite.registry.MiteWorldBlocks.MANTLE.get().defaultBlockState(), 2);
            h.assertTrue(BucketPlacementController.withIntent(f.player, true,
                    () -> ((BucketItem)Items.WATER_BUCKET).emptyContents(f.player, h.getLevel(), f.target, null, new ItemStack(Items.WATER_BUCKET))),
                    "Mantle retains its evaporated placement success behavior");
            h.assertTrue(h.getLevel().getFluidState(f.target).isEmpty(), "Water evaporates rather than creating a paid source");
            h.assertValueEqual(f.player.totalExperience, 1000, "Evaporation never spends the source fee");
            h.assertFalse(TemporaryFluidChanges.get(h.getLevel()).pending().containsKey(f.target.asLong()), "Evaporation creates no temporary cell");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryWaterCannotCreatePermanentWaterloggingButPaidControlCan(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1000)) {
            h.getLevel().setBlock(f.target, Blocks.OAK_SLAB.defaultBlockState(), 2);
            BucketItem bucket = (BucketItem)Items.WATER_BUCKET;
            h.assertFalse(bucket.emptyContents(f.player, h.getLevel(), f.target, null, new ItemStack(bucket)),
                    "Without a hit fallback, ordinary water cannot turn a slab into a free source");
            h.assertFalse(h.getLevel().getBlockState(f.target).getValue(BlockStateProperties.WATERLOGGED), "Rejected ordinary use leaves the slab dry");
            h.assertTrue(BucketPlacementController.withIntent(f.player, true,
                    () -> bucket.emptyContents(f.player, h.getLevel(), f.target, null, new ItemStack(bucket))),
                    "A funded Ctrl request can use modern waterlogging");
            h.assertTrue(h.getLevel().getBlockState(f.target).getValue(BlockStateProperties.WATERLOGGED), "Paid placement actually waterlogs the slab");
            h.assertValueEqual(f.player.totalExperience, 900, "Waterlogging spends the same 100 points as a source");
            h.assertFalse(TemporaryFluidChanges.get(h.getLevel()).pending().containsKey(f.target.asLong()), "Paid waterlogging has no decay deadline");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceIntentIsBoundToBothSequenceAndHandAndCanOnlyBeConsumedOnce(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1000)) {
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            f.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.LAVA_BUCKET));
            BucketPlacementIntent.receive(f.player, InteractionHand.MAIN_HAND, 14, true);
            h.assertFalse(BucketPlacementIntent.consume(f.player, InteractionHand.OFF_HAND, 14), "Main-hand modifier cannot authorize offhand placement");
            h.assertFalse(BucketPlacementIntent.consume(f.player, InteractionHand.MAIN_HAND, 14), "A hand mismatch consumes the request instead of arming a later use");
            BucketPlacementIntent.receive(f.player, InteractionHand.OFF_HAND, 20, true);
            h.assertFalse(BucketPlacementIntent.consume(f.player, InteractionHand.OFF_HAND, 21), "A different interaction sequence cannot claim the request");
            h.assertFalse(BucketPlacementIntent.consume(f.player, InteractionHand.OFF_HAND, 20), "The original sequence cannot replay a mismatched request");
            BucketPlacementIntent.receive(f.player, InteractionHand.OFF_HAND, 25, true);
            h.assertTrue(BucketPlacementIntent.consume(f.player, InteractionHand.OFF_HAND, 25), "An exact current bucket and sequence accepts Ctrl intent");
            h.assertFalse(BucketPlacementIntent.consume(f.player, InteractionHand.OFF_HAND, 25), "The same accepted source intent cannot be reused");
            h.assertValueEqual(f.player.totalExperience, 1000, "Input authorization itself does not mutate experience");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void changingBucketIdentityOrSelectedMainSlotInvalidatesAQueuedSourceIntent(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1000)) {
            ItemStack water = new ItemStack(Items.WATER_BUCKET);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, water);
            BucketPlacementIntent.receive(f.player, InteractionHand.MAIN_HAND, 30, true);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, water.copy());
            h.assertFalse(BucketPlacementIntent.consume(f.player, InteractionHand.MAIN_HAND, 30), "Even an identical replacement stack cannot reuse a previous bucket's request");
            f.player.setItemInHand(InteractionHand.MAIN_HAND, water);
            BucketPlacementIntent.receive(f.player, InteractionHand.MAIN_HAND, 31, true);
            int selected = f.player.getInventory().selected;
            f.player.getInventory().selected = (selected + 1) % 9;
            f.player.setItemInHand(InteractionHand.MAIN_HAND, water);
            h.assertFalse(BucketPlacementIntent.consume(f.player, InteractionHand.MAIN_HAND, 31), "Moving the same object to another selected slot invalidates its bound request");
            f.player.getInventory().selected = selected;
            h.assertValueEqual(f.player.totalExperience, 1000, "Invalid inputs never charge experience");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void supportedEmptyAndFilledLiquidBucketsCanArmCtrlIntent(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1000)) {
            for (Item unsupported : new Item[]{Items.MILK_BUCKET, Items.POWDER_SNOW_BUCKET, MiteFoods.get("water_bowl")}) {
                f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(unsupported));
                h.assertFalse(BucketPlacementIntent.isSupportedBucket(f.player.getMainHandItem()), "Only supported liquid vessels accept the shared Ctrl input: " + unsupported);
                BucketPlacementIntent.receive(f.player, InteractionHand.MAIN_HAND, 40, true);
                h.assertFalse(BucketPlacementIntent.consume(f.player, InteractionHand.MAIN_HAND, 40), "Unsupported item cannot request source placement: " + unsupported);
            }
            List<Item> empties = new ArrayList<>();
            empties.add(Items.BUCKET);
            for (String metal : MiteFoods.METALS) empties.add(MiteFoods.get(metal + "_bucket"));
            for (Item empty : empties) {
                f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(empty));
                h.assertTrue(BucketPlacementIntent.isSupportedBucket(f.player.getMainHandItem()), "Every empty metal bucket accepts Ctrl pickup input: " + empty);
                h.assertFalse(BucketPlacementIntent.isPlacementBucket(f.player.getMainHandItem()), "Empty buckets do not acquire paid-source placement semantics: " + empty);
                BucketPlacementIntent.receive(f.player, InteractionHand.MAIN_HAND, 40, true);
                h.assertTrue(BucketPlacementIntent.consume(f.player, InteractionHand.MAIN_HAND, 40), "A matching empty bucket can consume its Ctrl pickup intent: " + empty);
            }
            for (Item supported : new Item[]{Items.COD_BUCKET, Items.SALMON_BUCKET, Items.PUFFERFISH_BUCKET,
                    Items.TROPICAL_FISH_BUCKET, Items.TADPOLE_BUCKET, Items.AXOLOTL_BUCKET}) {
                f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(supported));
                BucketPlacementIntent.receive(f.player, InteractionHand.MAIN_HAND, 40, true);
                h.assertTrue(BucketPlacementIntent.consume(f.player, InteractionHand.MAIN_HAND, 40), "Aquatic mob buckets share the supported Ctrl intent: " + supported);
            }
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            BucketPlacementIntent.receive(f.player, InteractionHand.MAIN_HAND, 41, true);
            BucketPlacementIntent.receive(f.player, InteractionHand.MAIN_HAND, 41, false);
            h.assertFalse(BucketPlacementIntent.consume(f.player, InteractionHand.MAIN_HAND, 41), "A later released modifier clears the old request");
            f.player.setGameMode(GameType.SPECTATOR);
            BucketPlacementIntent.receive(f.player, InteractionHand.MAIN_HAND, 42, true);
            h.assertFalse(BucketPlacementIntent.consume(f.player, InteractionHand.MAIN_HAND, 42), "Spectators cannot arm a paid liquid placement");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void unusedSourceIntentExpiresBeforeItCanAffectALaterBucketUse(GameTestHelper h) {
        Fixture f = new Fixture(h, 1000);
        f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        BucketPlacementIntent.receive(f.player, InteractionHand.MAIN_HAND, 50, true);
        h.runAfterDelay(7, () -> {
            try {
                h.assertFalse(BucketPlacementIntent.consume(f.player, InteractionHand.MAIN_HAND, 50), "Ctrl requests expire after the bounded five-tick input window");
                h.assertValueEqual(f.player.totalExperience, 1000, "Expiration never spends experience");
            } finally { f.close(); }
            h.succeed();
        });
    }

    private static List<Item> filledBuckets(boolean water) {
        List<Item> result = new ArrayList<>();
        result.add(water ? Items.WATER_BUCKET : Items.LAVA_BUCKET);
        for (String metal : MiteFoods.METALS) result.add(MiteFoods.get(metal + (water ? "_water_bucket" : "_lava_bucket")));
        return result;
    }

    @GameTest(template = "empty")
    public static void fallingPoursClearTheOldDeadlineBeforeAPaidSourceIsPlacedBack(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1000)) {
            var level = h.getLevel();
            BucketItem bucket = (BucketItem) Items.WATER_BUCKET;
            h.assertTrue(bucket.emptyContents(f.player, level, f.target, null, new ItemStack(bucket)), "A normal pour schedules a finite initial cell");
            level.setBlockAndUpdate(f.target.below(), Blocks.AIR.defaultBlockState());
            h.assertTrue(io.github.ningmeng123.mite.world.SourceFluidFall.tryFall(level, f.target, level.getFluidState(f.target)),
                    "The actual freefall handler moves the unsupported whole water cell");
            var changes = TemporaryFluidChanges.get(level);
            h.assertTrue(changes.at(f.target) == null, "Freefall removes the old coordinate's pending downgrade");
            h.assertTrue(changes.at(f.target.below()) != null, "The moved cell has its independent source freefall deadline");
            PlacementDelay.clear(f.player);
            h.assertTrue(BucketPlacementController.withIntent(f.player, true,
                    () -> bucket.emptyContents(f.player, level, f.target, null, new ItemStack(bucket))),
                    "A paid source can be placed back at the vacated coordinate");
            h.assertValueEqual(f.player.totalExperience, 900, "The replacement source pays exactly one source fee");
            h.assertTrue(changes.at(f.target) == null && level.getFluidState(f.target).isSource(),
                    "The newly paid source cannot inherit the old finite pour's timer");
            changes.cancel(f.target.below());
        }
        h.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final BlockPos target;

        private Fixture(GameTestHelper helper, int experience) {
            this.helper = helper;
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-bucket"), false);
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
            PlayerExperience.setTotal(player, experience);
            clear();
        }

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
                BucketPlacementIntent.clear(player);
                player.getInventory().clearContent();
                player.server.getPlayerList().remove(player);
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }
}

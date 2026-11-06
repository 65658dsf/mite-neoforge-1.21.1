package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.entity.animal.Livestock032;
import io.github.ningmeng123.mite.entity.animal.Livestock032View;
import io.github.ningmeng123.mite.entity.animal.LivestockRules032;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.NutritionState;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.mite.world.BucketPlacementIntent;
import io.github.ningmeng123.mite.world.PlacementDelay;
import io.github.ningmeng123.mite.world.TemporaryFluidChanges;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

@GameTestHolder("mite_gameplay")
@PrefixGameTestTemplate(false)
public final class GameplayBalanceGameTests {
    private GameplayBalanceGameTests() {}

    @GameTest(template = "empty")
    public static void emptyHungerRejectsBlocksAndBucketsWithoutConsumingAnything(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.player.getFoodData().setFoodLevel(0);
            ((DualFoodData) fixture.player.getFoodData()).mite$setNutrition(6);
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS, 2));
            fixture.block(1);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target).isAir(), "Zero hunger blocks the real placement packet even with nutrition remaining");
            helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 2, "Denied block placement consumes nothing");
            helper.assertValueEqual(PlacementDelay.remainingTicks(fixture.player), 0, "Hunger refusal does not start a cooldown");
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            PlayerExperience.setTotal(fixture.player, 1000);
            BucketPlacementIntent.receive(fixture.player, InteractionHand.MAIN_HAND, 2, true);
            fixture.bucket(InteractionHand.MAIN_HAND, 2);
            helper.assertTrue(helper.getLevel().getFluidState(fixture.target).isEmpty()
                    && fixture.player.getMainHandItem().is(Items.WATER_BUCKET), "Zero hunger also refuses pouring before consuming the bucket");
            helper.assertValueEqual(fixture.player.totalExperience, 1000, "Denied pouring spends no source experience");
            fixture.player.getFoodData().setFoodLevel(1);
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS, 2));
            fixture.block(3);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target).is(Blocks.GLASS), "One hunger point restores placement immediately");
            helper.assertValueEqual(PlacementDelay.remainingMillis(fixture.player), 0L, "Successful server placement leaves input timing to the client");
            fixture.clearTarget();
            PlacementDelay.clear(fixture.player);
            fixture.player.getFoodData().setFoodLevel(0);
            fixture.player.setGameMode(GameType.CREATIVE);
            fixture.block(4);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target).is(Blocks.GLASS), "Creative placement keeps its exemption at zero hunger");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void grassRemainsSuspendedWhenItsSupportIsRemovedWithoutPlayerMining(GameTestHelper helper) {
        BlockPos soil = helper.absolutePos(new BlockPos(2, 10, 2));
        helper.getLevel().setBlock(soil.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(soil, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        helper.getLevel().removeBlock(soil.below(), false);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(soil).is(Blocks.GRASS_BLOCK), "Unsupported grass remains after a real lower-neighbor update");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class, new AABB(soil).inflate(2),
                    falling -> falling.getBlockState().is(Blocks.GRASS_BLOCK)).isEmpty(), "Support removal cannot create a falling grass entity");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void sameTickBlockPacketsEachCommitExactlyOnePlacement(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS, 2));
            fixture.block(1);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target).is(Blocks.GLASS), "The first real block packet places immediately");
            helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 1, "The first placement consumes one block");
            fixture.clearTarget();
            fixture.block(2);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target).is(Blocks.GLASS), "Packets arriving together each execute their valid placement");
            helper.assertTrue(fixture.player.getMainHandItem().isEmpty(), "Two accepted packets consume exactly two blocks");
            helper.assertValueEqual(PlacementDelay.remainingMillis(fixture.player), 0L, "Input timing belongs to the client");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void firstPaidWaterBucketPlacesImmediatelyAndHungerRejectedIntentCannotArmTheNextPour(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            PlayerExperience.setTotal(fixture.player, 1000);
            BucketPlacementIntent.receive(fixture.player, InteractionHand.MAIN_HAND, 1, true);
            fixture.bucket(InteractionHand.MAIN_HAND, 1);
            helper.assertTrue(helper.getLevel().getFluidState(fixture.target).isSource(), "The first paid source appears immediately through the real use packet");
            helper.assertValueEqual(fixture.player.totalExperience, 900, "Immediate source placement spends one hundred experience exactly once");
            helper.assertFalse(TemporaryFluidChanges.get(helper.getLevel()).pending().containsKey(fixture.target.asLong()), "The accepted Ctrl request makes a permanent source");
            fixture.clearTarget();
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            fixture.player.getFoodData().setFoodLevel(0);
            BucketPlacementIntent.receive(fixture.player, InteractionHand.MAIN_HAND, 2, true);
            fixture.bucket(InteractionHand.MAIN_HAND, 2);
            helper.assertTrue(helper.getLevel().getFluidState(fixture.target).isEmpty(), "Hunger rejects the next armed placement");
            helper.assertTrue(fixture.player.getMainHandItem().is(Items.WATER_BUCKET), "The hunger refusal preserves the full bucket");
            helper.assertValueEqual(fixture.player.totalExperience, 900, "The hunger refusal preserves source experience");
            fixture.player.getFoodData().setFoodLevel(1);
            fixture.bucket(InteractionHand.MAIN_HAND, 3);
            helper.assertTrue(TemporaryFluidChanges.get(helper.getLevel()).pending().containsKey(fixture.target.asLong()), "The following ordinary request creates temporary water after consuming the rejected Ctrl intent");
            helper.assertValueEqual(fixture.player.totalExperience, 900, "Each request uses its own source-payment intent");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sameTickHotbarAndHandChangesUseEachPacketsCurrentStack(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS, 2));
            fixture.block(1);
            fixture.clearTarget();
            fixture.player.getInventory().selected = 1;
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS, 2));
            fixture.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE, 2));
            fixture.block(2);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target).is(Blocks.GLASS), "The changed hotbar slot supplies the next block");
            fixture.clearTarget();
            fixture.block(InteractionHand.OFF_HAND, 3);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target).is(Blocks.STONE), "The subsequent offhand packet supplies its own block");
            helper.assertValueEqual(fixture.player.getOffhandItem().getCount(), 1, "The offhand placement consumes exactly one item");
            helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 1, "The selected main-hand placement consumes exactly one item");
            helper.assertValueEqual(fixture.player.getInventory().getItem(0).getCount(), 1, "The original hotbar stack retains its remaining block");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sameTickBlockAndLiquidPacketsEachCommitTheirCurrentItem(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS, 2));
            fixture.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WATER_BUCKET));
            fixture.block(1);
            fixture.clearTarget();
            fixture.bucket(InteractionHand.OFF_HAND, 2);
            helper.assertFalse(helper.getLevel().getFluidState(fixture.target).isEmpty(), "A valid liquid packet arriving after a block packet places its water");
            helper.assertTrue(fixture.player.getOffhandItem().is(Items.BUCKET), "The accepted liquid placement returns one empty bucket");
            fixture.clearTarget();
            fixture.block(3);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target).is(Blocks.GLASS), "A following valid block packet commits immediately");
            helper.assertTrue(fixture.player.getMainHandItem().isEmpty(), "The two block packets consume exactly two blocks");
            helper.assertValueEqual(PlacementDelay.remainingMillis(fixture.player), 0L, "Server arrivals create no additional input deadline");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void codBucketImmediatelyReleasesOneFishIntoTemporaryWater(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COD_BUCKET));
            PlayerExperience.setTotal(fixture.player, 1000);
            fixture.bucket(InteractionHand.MAIN_HAND, 1);
            helper.assertFalse(helper.getLevel().getFluidState(fixture.target).isEmpty(), "The first fish bucket places its water immediately");
            helper.assertTrue(TemporaryFluidChanges.get(helper.getLevel()).pending().containsKey(fixture.target.asLong()), "An ordinary fish bucket uses the same temporary water as a normal bucket");
            var fish = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Cod.class, new AABB(fixture.target).inflate(3));
            helper.assertValueEqual(fish.size(), 1, "The native immediate bucket action releases exactly one cod");
            helper.assertTrue(fixture.player.getMainHandItem().is(Items.BUCKET), "The accepted fish placement returns one empty bucket");
            helper.assertValueEqual(fixture.player.totalExperience, 1000, "An ordinary fish-bucket pour spends no source XP");
            fish.forEach(net.minecraft.world.entity.Entity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void everyMobBucketNormallyReleasesOneMobIntoTemporaryWater(GameTestHelper helper) {
        verifyMobBucketSequence(helper, false, 1000, 0);
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void everyMobBucketControlUsePaysForSourceAndStillReleasesOneMob(GameTestHelper helper) {
        verifyMobBucketSequence(helper, true, 1000, 0);
    }

    @GameTest(template = "empty")
    public static void axolotlBucketControlWithInsufficientExperienceFallsBackToTemporaryWater(GameTestHelper helper) {
        verifyMobBucketSequence(helper, true, 99, 5);
    }

    private static void verifyMobBucketSequence(GameTestHelper helper, boolean control, int experience, int index) {
        var items = new net.minecraft.world.item.Item[]{Items.COD_BUCKET, Items.SALMON_BUCKET, Items.PUFFERFISH_BUCKET,
                Items.TROPICAL_FISH_BUCKET, Items.TADPOLE_BUCKET, Items.AXOLOTL_BUCKET};
        var types = new EntityType<?>[]{EntityType.COD, EntityType.SALMON, EntityType.PUFFERFISH,
                EntityType.TROPICAL_FISH, EntityType.TADPOLE, EntityType.AXOLOTL};
        if (index == items.length) {
            helper.succeed();
            return;
        }
        Fixture fixture = new Fixture(helper);
        fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(items[index]));
        PlayerExperience.setTotal(fixture.player, experience);
        BucketPlacementIntent.receive(fixture.player, InteractionHand.MAIN_HAND, 1, control);
        try {
                fixture.bucket(InteractionHand.MAIN_HAND, 1);
                boolean source = control && experience >= 100;
                helper.assertFalse(helper.getLevel().getFluidState(fixture.target).isEmpty(), "Native bucket use immediately places its water: " + items[index]);
                helper.assertValueEqual(TemporaryFluidChanges.get(helper.getLevel()).pending().containsKey(fixture.target.asLong()), !source,
                        "Only an affordable Ctrl request creates permanent water: " + items[index]);
                helper.assertValueEqual(fixture.player.totalExperience, experience - (source ? 100 : 0), "Mob buckets use the ordinary source payment rules: " + items[index]);
                var mobs = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(fixture.target).inflate(3), mob -> mob.getType() == types[index]);
                helper.assertValueEqual(mobs.size(), 1, "One accepted packet releases exactly one matching bucket mob: " + items[index]);
                helper.assertTrue(fixture.player.getMainHandItem().is(Items.BUCKET) && fixture.player.getMainHandItem().getCount() == 1,
                        "One completed mob release returns one empty bucket: " + items[index]);
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(items[index]));
                fixture.bucket(InteractionHand.MAIN_HAND, 2);
                helper.assertValueEqual(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                        new AABB(fixture.target).inflate(3), mob -> mob.getType() == types[index]).size(), 2,
                        "A second full bucket arriving in the same tick releases exactly one additional creature");
                helper.assertTrue(fixture.player.getMainHandItem().is(Items.BUCKET), "The second accepted creature placement returns its empty vessel");
                helper.assertValueEqual(fixture.player.totalExperience, experience - (source ? 100 : 0), "The second ordinary creature request uses its own payment intent");
                helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                        new AABB(fixture.target).inflate(3), mob -> mob.getType() == types[index])
                        .forEach(net.minecraft.world.entity.Entity::discard);
        } finally { fixture.close(); }
        verifyMobBucketSequence(helper, control, experience, index + 1);
    }

    @GameTest(template = "empty")
    public static void outOfReachPacketsRemainRejectedUntilAFreshReachableRequest(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS, 2));
        fixture.block(1);
        fixture.clearTarget();
        fixture.player.setPos(fixture.player.position().add(20D, 0D, 0D));
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(fixture.target), Direction.UP, fixture.target.below(), false);
        fixture.player.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, 2));
        helper.assertTrue(helper.getLevel().getBlockState(fixture.target).isAir(), "The original server handler rejects an out-of-reach packet");
        helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 1, "The rejected request preserves the held block");
        helper.runAfterDelay(4, () -> {
            try {
                helper.assertTrue(helper.getLevel().getBlockState(fixture.target).isAir(), "Server ticks leave the rejected request unexecuted");
                fixture.block(3);
                helper.assertTrue(helper.getLevel().getBlockState(fixture.target).is(Blocks.GLASS), "A fresh reachable packet places immediately");
                helper.assertTrue(fixture.player.getMainHandItem().isEmpty(), "The fresh accepted request consumes the final block");
                helper.succeed();
            } finally { fixture.close(); }
        });
    }

    @GameTest(template = "empty")
    public static void powderSnowAndFollowingBlockPacketsCommitImmediately(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.POWDER_SNOW_BUCKET));
            fixture.block(1);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target).is(Blocks.POWDER_SNOW), "The solid bucket block-item path places immediately");
            helper.assertTrue(fixture.player.getMainHandItem().is(Items.BUCKET), "The snow placement returns one empty vessel");
            fixture.clearTarget();
            fixture.player.getFoodData().setFoodLevel(1);
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS));
            fixture.block(2);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target).is(Blocks.GLASS), "The following block packet commits in the same server tick");
            helper.assertTrue(fixture.player.getMainHandItem().isEmpty(), "The accepted block consumes its single item");
            helper.assertValueEqual(PlacementDelay.remainingMillis(fixture.player), 0L, "Server solid-bucket handling leaves input timing to the client");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledBlockPlacementDoesNotStartCooldownOrConsumeItsItem(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS, 2));
            boolean[] reached = {false};
            Consumer<BlockEvent.EntityPlaceEvent> cancel = event -> {
                if (event.getEntity() == fixture.player) {
                    reached[0] = true;
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BlockEvent.EntityPlaceEvent.class, cancel);
            try { fixture.block(1); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            helper.assertTrue(reached[0], "The real placement reaches NeoForge's cancellable transaction");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target).isAir() && fixture.player.getMainHandItem().getCount() == 2,
                    "Cancellation rolls back both the predicted block and item consumption");
            helper.assertValueEqual(PlacementDelay.remainingTicks(fixture.player), 0, "A rejected transaction never starts a placement cooldown");
            fixture.block(2);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target).is(Blocks.GLASS), "A non-canceled request in the same tick may still place immediately");
            helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 1, "Only the committed placement consumes one block");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void failedCollisionAndOrdinaryWorkbenchInteractionDoNotStartPlacementCooldown(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS, 2));
            helper.getLevel().setBlock(fixture.target, Blocks.BEDROCK.defaultBlockState(), 2);
            fixture.block(1);
            helper.assertValueEqual(PlacementDelay.remainingTicks(fixture.player), 0, "An occupied destination is not a successful placement");
            helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 2, "Collision refusal leaves the entire stack intact");
            fixture.clearTarget();
            helper.getLevel().setBlock(fixture.target.below(), MiteWorkbenches.TABLES.get("flint").get().defaultBlockState(), 2);
            fixture.block(2);
            helper.assertTrue(fixture.player.containerMenu != fixture.player.inventoryMenu, "Holding a block still opens the actual workbench menu");
            helper.assertValueEqual(PlacementDelay.remainingTicks(fixture.player), 0, "A successful workbench interaction is not mistaken for a placed block");
            fixture.player.closeContainer();
            helper.getLevel().setBlock(fixture.target.below(), Blocks.STONE.defaultBlockState(), 2);
            fixture.block(3);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target).is(Blocks.GLASS), "The preceding interactions do not delay a first genuine placement");
            fixture.clearTarget();
            helper.getLevel().setBlock(fixture.target.below(), MiteWorkbenches.TABLES.get("flint").get().defaultBlockState(), 2);
            fixture.block(4);
            helper.assertTrue(fixture.player.containerMenu != fixture.player.inventoryMenu, "The server placement gate preserves the ordinary workbench interaction path");
            helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 1, "Only the single genuine placement consumes an item");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void failedBlueberryPlantingPreservesFoodFallbackBeforeAndAfterBlockPlacement(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.player.getFoodData().setFoodLevel(0);
            ((DualFoodData) fixture.player.getFoodData()).mite$setNutrition(0);
            NutritionState.set(fixture.player, new NutritionState(1, 0, 0, 0, 0, -1));
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteFoods.BLUEBERRIES.get(), 2));
            fixture.block(1);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target).isAir() && fixture.player.isUsingItem(), "Failed planting retains the native food-use fallback");
            fixture.player.stopUsingItem();
            fixture.player.getFoodData().setFoodLevel(1);
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS));
            fixture.block(2);
            fixture.clearTarget();
            fixture.player.getFoodData().setFoodLevel(0);
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteFoods.BLUEBERRIES.get(), 2));
            fixture.block(3);
            helper.assertTrue(fixture.player.isUsingItem(), "The server preserves food fallback after the previous block packet");
            helper.assertValueEqual(PlacementDelay.remainingMillis(fixture.player), 0L, "The server preserves client ownership of input timing");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sameTickWaterLilyPacketsCommitThroughTheirSeparateUsePath(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            helper.getLevel().setBlock(fixture.target, Blocks.WATER.defaultBlockState(), 2);
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LILY_PAD, 2));
            fixture.bucket(InteractionHand.MAIN_HAND, 1);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target.above()).is(Blocks.LILY_PAD), "The first water-plant use places through its native use method");
            helper.getLevel().setBlock(fixture.target.above(), Blocks.AIR.defaultBlockState(), 2);
            fixture.bucket(InteractionHand.MAIN_HAND, 2);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target.above()).is(Blocks.LILY_PAD), "The following valid water-plant packet commits in the same server tick");
            helper.assertTrue(fixture.player.getMainHandItem().isEmpty(), "The two water-plant requests consume exactly two items");
            helper.assertValueEqual(PlacementDelay.remainingMillis(fixture.player), 0L, "Water-plant use creates its input deadline on the client");
            helper.getLevel().setBlock(fixture.target.above(), Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeServerPlacementRetainsItsAuthoritativeGateExemption(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.player.setGameMode(GameType.CREATIVE);
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS, 2));
            fixture.block(1);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target).is(Blocks.GLASS), "The first creative placement remains immediate");
            fixture.clearTarget();
            fixture.block(2);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.target).is(Blocks.GLASS), "The server accepts creative placement independently of the client's input lock");
            helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 2, "Native creative placement still consumes no items");
            helper.assertValueEqual(PlacementDelay.remainingTicks(fixture.player), 0, "Creative actions have their input deadline on the client");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void boneMealCannotGrowOrdinaryCherryOrMangroveSaplings(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(new BlockPos(2, 3, 2));
        for (var sapling : new net.minecraft.world.level.block.Block[]{Blocks.OAK_SAPLING, Blocks.CHERRY_SAPLING, Blocks.MANGROVE_PROPAGULE}) {
            helper.getLevel().setBlock(target.below(), Blocks.DIRT.defaultBlockState(), 2);
            var state = sapling.defaultBlockState();
            helper.getLevel().setBlock(target, state, 2);
            var boneMeal = new ItemStack(Items.BONE_MEAL, 2);
            helper.assertFalse(BoneMealItem.applyBonemeal(boneMeal, helper.getLevel(), target, null), "The shared player/dispenser bone-meal path refuses " + sapling);
            helper.assertTrue(helper.getLevel().getBlockState(target).equals(state) && boneMeal.getCount() == 2, "Refusal preserves sapling stage and bone meal");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recoveryRequiresExactlyThreeHealthyMinutesAndRestartsAfterRelapse(GameTestHelper helper) {
        var cow = EntityType.COW.create(helper.getLevel());
        Livestock032.needs(cow, new LivestockRules032.Needs(.1F, 1F, 1F));
        Livestock032.needs(cow, new LivestockRules032.Needs(1F, 1F, 1F));
        for (int tick = 0; tick < LivestockRules032.RECOVERY_TICKS - 1; tick++) Livestock032.tickRecovery(cow);
        helper.assertTrue(!Livestock032.well(cow) && ((Livestock032View)cow).mite$recoveryTicks() == 1, "3599 healthy ticks leave one final sick tick");
        Livestock032.tickRecovery(cow);
        helper.assertTrue(Livestock032.well(cow), "The 3600th healthy tick completes recovery");
        Livestock032.needs(cow, new LivestockRules032.Needs(1F, .1F, 1F));
        Livestock032.needs(cow, new LivestockRules032.Needs(1F, 1F, 1F));
        for (int tick = 0; tick < 100; tick++) Livestock032.tickRecovery(cow);
        CompoundTag saved = new CompoundTag();
        cow.saveWithoutId(saved);
        var restored = EntityType.COW.create(helper.getLevel());
        restored.load(saved);
        helper.assertValueEqual(Livestock032.state(restored).getInt("recovery_ticks"), 3500, "Saving and loading retains recovery progress");
        Livestock032.needs(restored, new LivestockRules032.Needs(1F, 1F, .1F));
        helper.assertValueEqual(Livestock032.state(restored).getInt("recovery_ticks"), 3600, "A new deficiency restarts the full recovery interval");
        cow.discard(); restored.discard(); helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modernRabbitBreedingAlsoUsesR196Cooldown(GameTestHelper helper) {
        var first = EntityType.RABBIT.create(helper.getLevel());
        var second = EntityType.RABBIT.create(helper.getLevel());
        Vec3 position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
        first.setPos(position);
        second.setPos(position);
        first.spawnChildFromBreeding(helper.getLevel(), second);
        helper.assertTrue(first.getAge() == 16000 && second.getAge() == 16000, "Both modern parents receive the source nk.setGrowingAgeAfterBreeding interval");
        helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Rabbit.class, new AABB(first.blockPosition()).inflate(2), rabbit -> rabbit.isBaby()).forEach(net.minecraft.world.entity.Entity::discard);
        first.discard(); second.discard(); helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breedingCooldownCountsThroughSixThousandAndEventuallyReachesZero(GameTestHelper helper) {
        for (var type : new EntityType<?>[]{EntityType.COW, EntityType.RABBIT, EntityType.TURTLE}) {
            var animal = (net.minecraft.world.entity.animal.Animal)type.create(helper.getLevel());
            animal.setNoAi(true);
            animal.setNoGravity(true);
            animal.setAge(6001);
            animal.aiStep();
            helper.assertValueEqual(animal.getAge(), 6000, "A running cooldown decrements through 6000 without restarting for " + type);
            for (int tick = 0; tick < 6000; tick++) animal.aiStep();
            helper.assertValueEqual(animal.getAge(), 0, "The actual age tick eventually completes the cooldown for " + type);
            animal.discard();
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final BlockPos target;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-prepare"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            channel.runPendingTasks();
            int initialTeleport = -1;
            for (Object packet : channel.outboundMessages())
                if (packet instanceof ClientboundPlayerPositionPacket position) initialTeleport = position.getId();
            helper.assertTrue(initialTeleport >= 0, "The real login emits its initial position packet before interaction testing");
            player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(initialTeleport));
            player.setGameMode(GameType.SURVIVAL);
            target = helper.absolutePos(new BlockPos(2, 8, 2));
            helper.getLevel().setBlock(target.below(), Blocks.STONE.defaultBlockState(), 2);
            helper.getLevel().setBlock(target, Blocks.AIR.defaultBlockState(), 2);
            player.moveTo(target.getX() + .5, target.getY() + 2, target.getZ() + .5, 0F, 90F);
            player.setNoGravity(true);
            player.setOnGround(false);
            player.getInventory().clearContent();
        }

        private void block(int sequence) {
            block(InteractionHand.MAIN_HAND, sequence);
        }

        private void block(InteractionHand hand, int sequence) {
            player.setPos(target.getX() + 1.8D, target.getY(), target.getZ() + .5D);
            var hit = new BlockHitResult(Vec3.atBottomCenterOf(target), Direction.UP, target.below(), false);
            helper.assertTrue(SourcePlayerReach.canReachBlock(player, hit.getBlockPos(), hand),
                    "The real block packet starts within the existing MITE interaction range");
            player.connection.handleUseItemOn(new ServerboundUseItemOnPacket(hand, hit, sequence));
        }

        private void bucket(InteractionHand hand, int sequence) {
            player.moveTo(target.getX() + .5, target.getY() + 2, target.getZ() + .5, 0F, 90F);
            player.connection.handleUseItem(new ServerboundUseItemPacket(hand, sequence, 0F, 90F));
        }

        private void clearTarget() {
            TemporaryFluidChanges.get(helper.getLevel()).cancel(target);
            helper.getLevel().setBlock(target, Blocks.AIR.defaultBlockState(), 2);
        }

        @Override public void close() {
            PlacementDelay.clear(player);
            player.stopUsingItem();
            player.closeContainer();
            BucketPlacementIntent.clear(player);
            TemporaryFluidChanges.get(helper.getLevel()).cancel(target);
            player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}

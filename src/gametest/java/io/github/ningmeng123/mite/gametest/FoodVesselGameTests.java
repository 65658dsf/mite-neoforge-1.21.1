package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteAttachments;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.CowFoodState;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.NutritionState;
import io.github.ningmeng123.mite.world.BucketPlacementController;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.TriState;

/** Real interaction events and native vessel methods with independent conservation expectations. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FoodVesselGameTests {
    private static final BlockPos CENTER = new BlockPos(1, 1, 1);
    private static final List<String> METALS = List.of("copper", "silver", "gold", "mithril", "adamantium", "ancient_metal");

    private FoodVesselGameTests() {}

    @GameTest(template = "empty")
    public static void milkBowlRejectsNauseaBeforeCleansingThenConsumesOneServingForNutritionOne(GameTestHelper helper) {
        Player player = player(helper, false);
        ItemStack milk = new ItemStack(MiteFoods.MILK_BOWL.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, milk);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 400, 0));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 300, 0));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 0));
        milk.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertFalse(player.isUsingItem(), "Existing nausea blocks starting the new milk bowl");
        ItemStack denied = milk.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(denied.is(MiteFoods.MILK_BOWL.get()), "Direct completion cannot bypass the same qualification");
        helper.assertValueEqual(denied.getCount(), 2, "Rejected milk does not consume a bowl");
        helper.assertTrue(player.hasEffect(MobEffects.CONFUSION) && player.hasEffect(MobEffects.POISON)
                && player.hasEffect(MobEffects.MOVEMENT_SPEED), "Rejected milk clears neither its blocker nor other positive/negative effects");
        helper.assertValueEqual(player.getInventory().countItem(Items.BOWL), 0, "Rejected milk manufactures no empty bowl");
        assertEnergy(helper, player, 0, 0, "Rejected milk supplies nothing");
        player.removeEffect(MobEffects.CONFUSION);
        ItemStack remaining = milk.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(remaining.is(MiteFoods.MILK_BOWL.get()), "Accepted first serving leaves the other full bowl");
        helper.assertValueEqual(remaining.getCount(), 1, "Accepted milk consumes one of two servings");
        helper.assertTrue(player.getActiveEffects().isEmpty(), "Accepted milk clears positive and negative effects");
        helper.assertValueEqual(player.getInventory().countItem(Items.BOWL), 1, "Accepted milk produces exactly one wooden bowl");
        assertEnergy(helper, player, 0, 1, "Source milk bowl supplies nutrition1 without satiation");
        helper.assertValueEqual(NutritionState.get(player).protein(), 8000, "The accepted nutrition contributes source protein once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fourRealBowlInteractionsUseTwentyFiveMilkEachAndTheFifthIsRejected(GameTestHelper helper) {
        Player player = player(helper, false);
        Cow cow = cow(helper);
        try {
            CowFoodState.set(cow, new CowFoodState(1, 100, .5F, .5F, .5F));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOWL, 5));
            for (int interaction = 1; interaction <= 4; interaction++) {
                InteractionResult result = player.interactOn(cow, InteractionHand.MAIN_HAND);
                helper.assertTrue(result.consumesAction(), "Real cow interaction accepts an available milk serving");
                helper.assertValueEqual(CowFoodState.get(cow).milk(), 100 - interaction * 25, "Each wooden bowl uses25 of100 milk");
                helper.assertTrue(player.getMainHandItem().is(Items.BOWL), "Nonfinal empty bowls remain in the held stack");
                helper.assertValueEqual(player.getMainHandItem().getCount(), 5 - interaction, "Each milk serving consumes exactly one empty bowl");
                helper.assertValueEqual(player.getInventory().countItem(MiteFoods.MILK_BOWL.get()), interaction, "Each interaction adds precisely one milk bowl");
            }
            helper.assertValueEqual(player.interactOn(cow, InteractionHand.MAIN_HAND), InteractionResult.FAIL, "The fifth serving is rejected before native interaction can duplicate milk");
            helper.assertValueEqual(CowFoodState.get(cow).milk(), 0, "Rejected milking cannot make a negative budget");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Rejected fifth attempt retains the unused empty bowl");
            helper.assertValueEqual(player.getInventory().countItem(MiteFoods.MILK_BOWL.get()), 4, "Rejected fifth attempt produces no milk bowl");
        } finally { cow.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ironAndAllSixMetalBucketsUseOneHundredMilkAndReturnTheCorrectFilledMaterial(GameTestHelper helper) {
        Player player = player(helper, false);
        Cow cow = cow(helper);
        try {
            verifyMilkBucket(helper, player, cow, Items.BUCKET, Items.MILK_BUCKET);
            for (String metal : METALS)
                verifyMilkBucket(helper, player, cow, MiteFoods.get(metal + "_bucket"), MiteFoods.get(metal + "_milk_bucket"));
            cow.setAge(-24000);
            CowFoodState.set(cow, new CowFoodState(1, 100, .5F, .5F, .5F));
            for (Item empty : List.of(Items.BOWL, Items.BUCKET, MiteFoods.get("copper_bucket"))) {
                ItemStack held = new ItemStack(empty);
                player.setItemInHand(InteractionHand.MAIN_HAND, held);
                helper.assertValueEqual(player.interactOn(cow, InteractionHand.MAIN_HAND), InteractionResult.FAIL, "Baby cows cannot supply any source milk volume");
                helper.assertTrue(player.getMainHandItem().is(empty), "Baby rejection keeps the same empty container");
                helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Baby rejection consumes no container");
                helper.assertValueEqual(CowFoodState.get(cow).milk(), 100, "Baby rejection does not spend the stored milk value");
            }
        } finally { cow.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cowDrinksQuarterWaterFromOneBowlAndFourVolumesFromABucketWithoutChangingMilk(GameTestHelper helper) {
        Player player = player(helper, false);
        Cow cow = cow(helper);
        try {
            CowFoodState.set(cow, new CowFoodState(1, 37, .5F, .125F, .5F));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteFoods.WATER_BOWL.get(), 2));
            helper.assertTrue(player.interactOn(cow, InteractionHand.MAIN_HAND).consumesAction(), "Thirsty cow accepts source water bowl");
            helper.assertValueEqual(CowFoodState.get(cow).water(), .375F, "One standard bowl volume restores exactly.25 water");
            helper.assertValueEqual(CowFoodState.get(cow).milk(), 37, "Watering cannot spend or manufacture milk");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Only one of two water bowls is consumed");
            helper.assertValueEqual(player.getInventory().countItem(Items.BOWL), 1, "Watering returns exactly one empty bowl");
            CowFoodState.set(cow, new CowFoodState(1, 37, .5F, .125F, .5F));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteFoods.get("silver_water_bucket")));
            helper.assertTrue(player.interactOn(cow, InteractionHand.MAIN_HAND).consumesAction(), "Thirsty cow accepts four-volume source bucket");
            helper.assertValueEqual(CowFoodState.get(cow).water(), 1F, "Four volumes add1 water and clamp at1");
            helper.assertTrue(player.getMainHandItem().is(MiteFoods.get("silver_bucket")), "Watering returns the same metal empty bucket");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Watering returns a single matching empty bucket");
            helper.assertValueEqual(CowFoodState.get(cow).milk(), 37, "Bucket water also preserves milk");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteFoods.WATER_BOWL.get()));
            helper.assertFalse(player.interactOn(cow, InteractionHand.MAIN_HAND).consumesAction(), "A cow with water at least.5 does not consume another drinking container");
            helper.assertTrue(player.getMainHandItem().is(MiteFoods.WATER_BOWL.get()), "Nonthirsty attempt retains the full water bowl");
            assertEnergy(helper, player, 0, 0, "Giving water to a cow never feeds the player");
        } finally { cow.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cowAttachmentSavesFoodWaterFreedomMilkAndItsManureCountdown(GameTestHelper helper) {
        Cow original = new Cow(EntityType.COW, helper.getLevel());
        Cow restored = new Cow(EntityType.COW, helper.getLevel());
        CowFoodState expected = new CowFoodState(1, 37, .3125F, .4375F, .875F);
        CowFoodState.set(original, expected);
        original.setData(MiteAttachments.COW_MANURE_COUNTDOWN, 23456);
        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        restored.load(saved);
        helper.assertValueEqual(CowFoodState.get(restored), expected, "Whole entity NBT restores F/W/R and milk exactly");
        helper.assertValueEqual(restored.getData(MiteAttachments.COW_MANURE_COUNTDOWN), 23456, "The source manure timer survives entity reload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void healthyAdultMilkProductionOccursOnlyOnHundredTickRollsAndBabiesNeverProduceMilk(GameTestHelper helper) {
        Cow cow = cow(helper);
        try {
            // rr overrides the parent's grass-block source: only tall grass and dandelions feed cows.
            helper.setBlock(CENTER.below(), Blocks.GRASS_BLOCK);
            helper.setBlock(new BlockPos(0, 0, 1), Blocks.GRASS_BLOCK);
            helper.setBlock(new BlockPos(0, 1, 1), Blocks.SHORT_GRASS);
            helper.setBlock(new BlockPos(2, 1, 1), Blocks.WATER);
            CowFoodState before = new CowFoodState(1, 0, .5F, .5F, .5F);
            CowFoodState.set(cow, before);
            cow.tickCount = 99;
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(cow));
            helper.assertValueEqual(CowFoodState.get(cow), before, "Nonboundary tick cannot update F/W/R or restore milk");
            cow.getRandom().setSeed(1L); // LegacyRandomSource first nextInt(10)=5: deterministic successful9/10 roll.
            cow.tickCount = 100;
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(cow));
            CowFoodState produced = CowFoodState.get(cow);
            helper.assertValueEqual(produced.milk(), 1, "One healthy adult production roll restores one milk");
            helper.assertValueEqual(produced.food(), .6F, "Near source grass restores.1 food");
            helper.assertValueEqual(produced.water(), .6F, "Near exposed water restores.1 water");
            helper.assertValueEqual(produced.freedom(), .6F, "Uncrowded outdoor cow restores.1 freedom");
            cow.getRandom().setSeed(0L); // First nextInt(10)=0: source10% roll skips the entire wellness update.
            cow.tickCount = 200;
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(cow));
            helper.assertValueEqual(CowFoodState.get(cow), produced, "Failed production roll changes neither milk nor wellness");
            cow.setAge(-24000);
            CowFoodState.set(cow, before);
            cow.getRandom().setSeed(1L);
            cow.tickCount = 300;
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(cow));
            helper.assertValueEqual(CowFoodState.get(cow).milk(), 0, "Healthy baby wellness updates still cannot produce milk");
        } finally { cow.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fillingOneOfThreeBowlsLeavesTheWaterSourceAndConvertsOnlyOneContainer(GameTestHelper helper) {
        Player player = player(helper, false);
        BlockPos pos = helper.absolutePos(CENTER);
        helper.setBlock(CENTER.below(), Blocks.STONE);
        helper.setBlock(CENTER, Blocks.WATER);
        lookDownAt(helper, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOWL, 3));
        assertSourceWaterInReach(helper, player);
        PlayerInteractEvent.RightClickItem event = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
        helper.assertTrue(event.isCanceled() && event.getCancellationResult().consumesAction(), "Registered event handles empty-bowl water pickup once");
        helper.assertTrue(helper.getLevel().getFluidState(pos).isSource(), "Bowl sampling does not remove the source water");
        helper.assertTrue(player.getMainHandItem().is(Items.BOWL), "The other empty bowls remain in the held stack");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "Exactly one empty bowl was converted");
        helper.assertValueEqual(player.getInventory().countItem(MiteFoods.WATER_BOWL.get()), 1, "Exactly one water bowl was produced");
        assertEnergy(helper, player, 0, 0, "Water collection itself is not drinking completion");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void waterBowlMoistensOneFarmlandCellAndAMetalBucketMoistensTheThreeByThreeArea(GameTestHelper helper) {
        Player player = player(helper, false);
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
            helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteFoods.WATER_BOWL.get(), 2));
        helper.assertTrue(rightClickTop(helper, player, CENTER).consumesAction(), "Real block interaction waters the center soil");
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
            helper.assertValueEqual(helper.getBlockState(new BlockPos(x, 1, z)).getValue(FarmBlock.MOISTURE), x == 1 && z == 1 ? 7 : 0,
                    "A bowl moistens exactly one cell, not its neighbors");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "One-cell watering consumes one full bowl");
        helper.assertValueEqual(player.getInventory().countItem(Items.BOWL), 1, "One-cell watering returns one empty bowl");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteFoods.get("gold_water_bucket")));
        helper.assertTrue(rightClickTop(helper, player, CENTER).consumesAction(), "A bucket moistens nearby dry soil even when its center is already wet");
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
            helper.assertValueEqual(helper.getBlockState(new BlockPos(x, 1, z)).getValue(FarmBlock.MOISTURE), 7, "A bucket moistens the full3x3 farmland area");
        helper.assertTrue(player.getMainHandItem().is(MiteFoods.get("gold_bucket")), "Watering returns the original material empty bucket");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Three-cell-radius watering returns only one empty bucket");
        assertEnergy(helper, player, 0, 0, "Watering does not add player food resources");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullyWetFarmlandRejectsRepeatedWateringWithoutPlacingWaterOrSpendingContainers(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            BlockPos soil = new BlockPos(x, 1, z);
            helper.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            helper.setBlock(soil.above(), Blocks.AIR);
        }
        for (Item water : List.of(MiteFoods.WATER_BOWL.get(), Items.WATER_BUCKET, MiteFoods.get("gold_water_bucket"))) {
            Player player = player(helper, false);
            int count = water == MiteFoods.WATER_BOWL.get() ? 2 : 1;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(water, count));
            NutritionState before = NutritionState.get(player);
            for (int attempt = 0; attempt < 2; attempt++) {
                var event = blockClick(helper, player, CENTER, false);
                helper.assertTrue(event.isCanceled(), "Fully wet soil cancels the real event before native bucket placement");
                helper.assertValueEqual(event.getCancellationResult(), InteractionResult.FAIL,
                        "Repeated watering on completely wet soil fails instead of falling through");
                for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
                    BlockPos soil = new BlockPos(x, 1, z);
                    helper.assertValueEqual(helper.getBlockState(soil).getValue(FarmBlock.MOISTURE), 7,
                            "Repeated watering preserves each wet farmland cell");
                    helper.assertTrue(helper.getBlockState(soil.above()).isAir()
                                    && helper.getLevel().getFluidState(helper.absolutePos(soil.above())).isEmpty(),
                            "Rejected watering cannot place a water block above the crops");
                }
                assertUnusedVessel(helper, player, water, count, before, "Completely wet farmland");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spectatorAndCooldownCannotWaterOrDouseThroughEventsOrNativeBowlUse(GameTestHelper helper) {
        for (int condition = 0; condition < 2; condition++) {
            for (Item vessel : List.of(MiteFoods.WATER_BOWL.get(), MiteFoods.get("copper_water_bucket"),
                    MiteFoods.CHICKEN_SOUP.get(), MiteFoods.ICE_CREAM.get())) {
                Player player = player(helper, condition == 0 ? GameType.SPECTATOR : GameType.SURVIVAL);
                int count = vessel == MiteFoods.get("copper_water_bucket") ? 1 : 2;
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(vessel, count));
                if (condition == 1) player.getCooldowns().addCooldown(vessel, 100);
                String context = condition == 0 ? "Spectator" : "Item cooldown";
                NutritionState before = NutritionState.get(player);
                if (vessel == MiteFoods.WATER_BOWL.get() || vessel == MiteFoods.get("copper_water_bucket")) {
                    helper.setBlock(CENTER, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));
                    helper.setBlock(CENTER.above(), Blocks.AIR);
                    var event = blockClick(helper, player, CENTER, false);
                    helper.assertFalse(event.getCancellationResult().consumesAction(), context + " cannot trigger registered watering");
                    helper.assertValueEqual(helper.getBlockState(CENTER).getValue(FarmBlock.MOISTURE), 0,
                            context + " leaves dry farmland untouched");
                    helper.assertTrue(helper.getBlockState(CENTER.above()).isAir(), context + " places no water above soil");
                    helper.assertFalse(nativeUseOnTop(helper, player, CENTER).consumesAction(),
                            context + " also blocks native bowl useOn completion");
                    helper.assertValueEqual(helper.getBlockState(CENTER).getValue(FarmBlock.MOISTURE), 0,
                            context + " native bowl path cannot change dry farmland");
                    assertUnusedVessel(helper, player, vessel, count, before, context + " watering");
                }
                helper.setBlock(CENTER.below(), Blocks.NETHERRACK);
                helper.setBlock(CENTER, Blocks.FIRE);
                var event = blockClick(helper, player, CENTER.below(), false);
                helper.assertFalse(event.getCancellationResult().consumesAction(), context + " cannot trigger registered dousing");
                helper.assertTrue(helper.getBlockState(CENTER).is(Blocks.FIRE), context + " event preserves fire");
                helper.assertFalse(nativeUseOnTop(helper, player, CENTER.below()).consumesAction(),
                        context + " also blocks native bowl dousing");
                helper.assertTrue(helper.getBlockState(CENTER).is(Blocks.FIRE), context + " native bowl path preserves fire");
                assertUnusedVessel(helper, player, vessel, count, before, context + " dousing");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deniedItemUseCannotWaterOrDouseAndProducesNoEmptyVessels(GameTestHelper helper) {
        for (Item vessel : List.of(MiteFoods.WATER_BOWL.get(), Items.WATER_BUCKET,
                MiteFoods.get("silver_water_bucket"), MiteFoods.CHICKEN_SOUP.get(), MiteFoods.ICE_CREAM.get())) {
            Player player = player(helper, false);
            int count = vessel == MiteFoods.WATER_BOWL.get() || vessel == MiteFoods.CHICKEN_SOUP.get()
                    || vessel == MiteFoods.ICE_CREAM.get() ? 2 : 1;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(vessel, count));
            NutritionState before = NutritionState.get(player);
            if (vessel != MiteFoods.CHICKEN_SOUP.get() && vessel != MiteFoods.ICE_CREAM.get()) {
                helper.setBlock(CENTER, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));
                helper.setBlock(CENTER.above(), Blocks.AIR);
                var event = blockClick(helper, player, CENTER, true);
                helper.assertTrue(event.getUseItem().isFalse(), "The event retains the external item-use veto");
                helper.assertFalse(event.getCancellationResult().consumesAction(), "Denied item use cannot report successful watering");
                helper.assertValueEqual(helper.getBlockState(CENTER).getValue(FarmBlock.MOISTURE), 0,
                        "Denied item use cannot moisten the farmland");
                helper.assertTrue(helper.getBlockState(CENTER.above()).isAir(), "Denied item use cannot place water above soil");
                assertUnusedVessel(helper, player, vessel, count, before, "Denied item-use watering");
            }
            helper.setBlock(CENTER.below(), Blocks.NETHERRACK);
            helper.setBlock(CENTER, Blocks.FIRE);
            var event = blockClick(helper, player, CENTER.below(), true);
            helper.assertTrue(event.getUseItem().isFalse(), "Dousing cannot override the external item-use veto");
            helper.assertFalse(event.getCancellationResult().consumesAction(), "Denied item use cannot report successful dousing");
            helper.assertTrue(helper.getBlockState(CENTER).is(Blocks.FIRE), "Denied item use keeps the fire lit");
            assertUnusedVessel(helper, player, vessel, count, before, "Denied item-use dousing");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spectatorCooldownAndCanceledItemEventsCannotFillEmptyBowls(GameTestHelper helper) {
        for (int condition = 0; condition < 3; condition++) {
            Player player = player(helper, condition == 0 ? GameType.SPECTATOR : GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOWL, 3));
            if (condition == 1) player.getCooldowns().addCooldown(Items.BOWL, 100);
            helper.setBlock(CENTER.below(), Blocks.STONE);
            helper.setBlock(CENTER, Blocks.WATER);
            lookDownAt(helper, player);
            assertSourceWaterInReach(helper, player);
            NutritionState before = NutritionState.get(player);
            var event = new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND);
            // RightClickItem has no useItem flag; its equivalent external veto is cancellation.
            if (condition == 2) {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
            NeoForge.EVENT_BUS.post(event);
            helper.assertFalse(event.getCancellationResult().consumesAction(), "Restricted item event cannot fill the bowl");
            helper.assertTrue(player.getMainHandItem().is(Items.BOWL), "Restricted pickup keeps the held empty bowls");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 3, "Restricted pickup spends no empty bowl");
            helper.assertValueEqual(player.getInventory().countItem(MiteFoods.WATER_BOWL.get()), 0,
                    "Restricted pickup produces no full water bowl");
            helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(CENTER)).isSource(),
                    "Restricted pickup leaves the source water in place");
            assertEnergy(helper, player, 0, 0, "Restricted pickup adds no food reserves");
            helper.assertValueEqual(NutritionState.get(player), before, "Restricted pickup adds no micronutrients or sugar response");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void soupAndIceCreamDouseFireWithoutFeedingThePlayerOrReturningTwoBowls(GameTestHelper helper) {
        for (Item food : List.of(MiteFoods.CHICKEN_SOUP.get(), MiteFoods.ICE_CREAM.get())) {
            Player player = player(helper, false);
            helper.setBlock(CENTER.below(), Blocks.NETHERRACK);
            helper.setBlock(CENTER, Blocks.FIRE);
            NutritionState before = NutritionState.get(player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(food, 2));
            helper.assertTrue(rightClickTop(helper, player, CENTER.below()).consumesAction(), "Source contents douse the adjacent fire through the interaction event");
            helper.assertTrue(helper.getBlockState(CENTER).isAir(), "Dousing removes fire");
            helper.assertTrue(player.getMainHandItem().is(food), "The other full food bowl remains in the held stack");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Dousing spends exactly one full bowl");
            helper.assertValueEqual(player.getInventory().countItem(Items.BOWL), 1, "Dousing produces exactly one empty bowl");
            assertEnergy(helper, player, 0, 0, "Dousing is not source food consumption");
            helper.assertValueEqual(NutritionState.get(player), before, "Dousing adds no micronutrients or sugar response");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void copperWaterPickupAndPlacementPreserveTheMaterialAndTotalContainerCount(GameTestHelper helper) {
        Player player = player(helper, false);
        helper.setBlock(CENTER.below(), Blocks.STONE);
        helper.setBlock(CENTER, Blocks.WATER);
        lookDownAt(helper, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteFoods.get("copper_bucket"), 2));
        assertSourceWaterInReach(helper, player);
        var pickup = BucketPlacementController.withIntent(player, true,
                () -> player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND));
        helper.assertTrue(pickup.getResult().consumesAction(), "Ctrl metal bucket use collects source water");
        player.setItemInHand(InteractionHand.MAIN_HAND, pickup.getObject());
        helper.assertTrue(helper.getBlockState(CENTER).isAir(), "Ctrl pickup removes the source before the same vessel pours it back");
        helper.assertValueEqual(player.getInventory().countItem(MiteFoods.get("copper_bucket")), 1, "Pickup consumes one of two empty copper buckets");
        helper.assertValueEqual(player.getInventory().countItem(MiteFoods.get("copper_water_bucket")), 1, "Pickup produces one copper water bucket");
        int fullSlot = slotContaining(player, MiteFoods.get("copper_water_bucket"));
        player.getInventory().selected = fullSlot;
        // The shared ingestion fixture starts empty; placement now requires positive hunger.
        player.getFoodData().setFoodLevel(1);
        var placement = player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(placement.getResult().consumesAction(), "Native water placement succeeds on the exposed floor");
        player.setItemInHand(InteractionHand.MAIN_HAND, placement.getObject());
        helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(CENTER)).isSource(), "The source water is placed again");
        helper.assertValueEqual(player.getInventory().countItem(MiteFoods.get("copper_bucket")), 2, "Pickup plus placement conserves the original two copper containers");
        helper.assertValueEqual(player.getInventory().countItem(MiteFoods.get("copper_water_bucket")), 0, "Survival placement consumes the full copper water bucket");
        helper.assertValueEqual(player.getInventory().countItem(Items.BUCKET), 0, "No conversion silently returns iron instead of copper");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullInventoryPickupDropsOneMatchingWaterBucketAndCreativePlacementProducesNoEmptyBucket(GameTestHelper helper) {
        Player survival = player(helper, false);
        for (int slot = 0; slot < 36; slot++) survival.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        survival.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteFoods.get("mithril_bucket"), 2));
        helper.setBlock(CENTER.below(), Blocks.STONE);
        helper.setBlock(CENTER, Blocks.WATER);
        lookDownAt(helper, survival);
        assertSourceWaterInReach(helper, survival);
        AABB area = survival.getBoundingBox().inflate(4);
        Set<UUID> previous = new HashSet<>();
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).forEach(entity -> previous.add(entity.getUUID()));
        List<ItemEntity> drops = List.of();
        try {
            var pickup = survival.getMainHandItem().use(helper.getLevel(), survival, InteractionHand.MAIN_HAND);
            helper.assertTrue(pickup.getResult().consumesAction(), "Full inventory does not cancel water collection");
            survival.setItemInHand(InteractionHand.MAIN_HAND, pickup.getObject());
            helper.assertTrue(survival.getMainHandItem().is(MiteFoods.get("mithril_bucket")), "Remaining held bucket keeps its original material");
            helper.assertValueEqual(survival.getMainHandItem().getCount(), 1, "Full inventory pickup spends one empty bucket");
            drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
                    .filter(entity -> !previous.contains(entity.getUUID())).toList();
            helper.assertValueEqual(drops.size(), 1, "Full inventory creates exactly one overflow entity");
            helper.assertTrue(drops.getFirst().getItem().is(MiteFoods.get("mithril_water_bucket")), "The overflow holds water in the original metal bucket");
            helper.assertValueEqual(drops.getFirst().getItem().getCount(), 1, "The overflow contains one water bucket");
        } finally { drops.forEach(ItemEntity::discard); }

        Player creative = player(helper, true);
        lookDownAt(helper, creative);
        creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteFoods.get("adamantium_water_bucket")));
        var placement = creative.getMainHandItem().use(helper.getLevel(), creative, InteractionHand.MAIN_HAND);
        helper.assertTrue(placement.getResult().consumesAction(), "Creative metal water placement succeeds");
        creative.setItemInHand(InteractionHand.MAIN_HAND, placement.getObject());
        helper.assertTrue(creative.getMainHandItem().is(MiteFoods.get("adamantium_water_bucket")), "Creative placement preserves the full water bucket");
        helper.assertValueEqual(creative.getMainHandItem().getCount(), 1, "Creative placement does not multiply the held full bucket");
        helper.assertValueEqual(creative.getInventory().countItem(MiteFoods.get("adamantium_bucket")), 0, "Creative placement returns no free empty bucket");
        helper.assertValueEqual(creative.getInventory().countItem(Items.BUCKET), 0, "Creative placement cannot create a free iron bucket either");
        helper.succeed();
    }

    private static void verifyMilkBucket(GameTestHelper helper, Player player, Cow cow, Item empty, Item milk) {
        CowFoodState.set(cow, new CowFoodState(1, 100, .5F, .5F, .5F));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(empty));
        helper.assertTrue(player.interactOn(cow, InteractionHand.MAIN_HAND).consumesAction(), "Real bucket interaction succeeds: " + empty);
        helper.assertValueEqual(CowFoodState.get(cow).milk(), 0, "A bucket uses four volumes times25 milk: " + empty);
        helper.assertTrue(player.getMainHandItem().is(milk), "The filled milk result matches the original bucket material: " + empty);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "One empty bucket produces one milk bucket");
        helper.assertValueEqual(player.getInventory().countItem(empty), 0, "The converted single empty bucket is not duplicated");
    }

    private static Player player(GameTestHelper helper, boolean creative) {
        return player(helper, creative ? GameType.CREATIVE : GameType.SURVIVAL);
    }

    private static Player player(GameTestHelper helper, GameType mode) {
        Player player = helper.makeMockPlayer(mode);
        player.getAbilities().instabuild = mode.isCreative();
        player.experienceLevel = 35;
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(CENTER));
        player.setPos(center.x, center.y, center.z);
        player.getFoodData().setFoodLevel(0);
        ((DualFoodData) player.getFoodData()).mite$setNutrition(0);
        player.getFoodData().setSaturation(0F);
        NutritionState.set(player, new NutritionState(1, 0, 0, 0, 0, -1));
        return player;
    }

    private static Cow cow(GameTestHelper helper) {
        Cow cow = new Cow(EntityType.COW, helper.getLevel());
        Vec3 center = Vec3.atBottomCenterOf(helper.absolutePos(CENTER));
        cow.setPos(center.x, center.y, center.z);
        cow.setAge(0);
        cow.setNoAi(true);
        helper.getLevel().addFreshEntity(cow);
        cow.setData(MiteAttachments.COW_MANURE_COUNTDOWN, 12000);
        return cow;
    }

    private static void lookDownAt(GameTestHelper helper, Player player) {
        BlockPos pos = helper.absolutePos(CENTER);
        // The one-cell empty template is encased in barriers. Give the real ray an
        // unobstructed column and keep the source within the player's actual reach.
        helper.setBlock(CENTER.above(), Blocks.AIR);
        helper.setBlock(CENTER.above(2), Blocks.AIR);
        player.setPos(pos.getX() + .5D, pos.getY() + .125D, pos.getZ() + .5D);
        player.setXRot(90F);
        player.xRotO = 90F;
        player.setYRot(0F);
        player.yRotO = 0F;
    }

    private static void assertSourceWaterInReach(GameTestHelper helper, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1F).scale(player.blockInteractionRange()));
        BlockHitResult hit = helper.getLevel().clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
        BlockPos expected = helper.absolutePos(CENTER);
        helper.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(expected)
                        && helper.getLevel().getFluidState(hit.getBlockPos()).isSource(),
                "Ray fixture must hit the source water: type=" + hit.getType() + ", pos=" + hit.getBlockPos()
                        + ", block=" + helper.getLevel().getBlockState(hit.getBlockPos())
                        + ", eye=" + eye + ", reach=" + player.blockInteractionRange());
    }

    private static InteractionResult rightClickTop(GameTestHelper helper, Player player, BlockPos relative) {
        var event = blockClick(helper, player, relative, false);
        return event.isCanceled() ? event.getCancellationResult() : nativeUseOnTop(helper, player, relative);
    }

    private static PlayerInteractEvent.RightClickBlock blockClick(GameTestHelper helper, Player player, BlockPos relative, boolean denyItem) {
        BlockPos pos = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        if (denyItem) event.setUseItem(TriState.FALSE);
        return NeoForge.EVENT_BUS.post(event);
    }

    private static InteractionResult nativeUseOnTop(GameTestHelper helper, Player player, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static void assertUnusedVessel(GameTestHelper helper, Player player, Item item, int count,
                                         NutritionState before, String context) {
        helper.assertTrue(player.getMainHandItem().is(item), context + " keeps the original full vessel");
        helper.assertValueEqual(player.getMainHandItem().getCount(), count, context + " consumes no container");
        helper.assertValueEqual(player.getInventory().countItem(Items.BOWL), 0, context + " produces no empty bowl");
        helper.assertValueEqual(player.getInventory().countItem(Items.BUCKET), 0, context + " produces no empty iron bucket");
        for (String metal : METALS)
            helper.assertValueEqual(player.getInventory().countItem(MiteFoods.get(metal + "_bucket")), 0,
                    context + " produces no empty " + metal + " bucket");
        assertEnergy(helper, player, 0, 0, context + " adds no food reserves");
        helper.assertValueEqual(NutritionState.get(player), before, context + " adds no micronutrients or sugar response");
    }

    private static int slotContaining(Player player, Item item) {
        for (int slot = 0; slot < 9; slot++) if (player.getInventory().getItem(slot).is(item)) return slot;
        throw new IllegalStateException("Expected matching water bucket in the hotbar");
    }

    private static void assertEnergy(GameTestHelper helper, Player player, int satiation, int nutrition, String context) {
        helper.assertValueEqual(player.getFoodData().getFoodLevel(), satiation, context + " (satiation)");
        helper.assertValueEqual(((DualFoodData) player.getFoodData()).mite$getNutrition(), nutrition, context + " (nutrition)");
    }
}

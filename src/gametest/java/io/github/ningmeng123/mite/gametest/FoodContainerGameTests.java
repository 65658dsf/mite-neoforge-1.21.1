package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.NutritionState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual native finish and placed cake interactions, including container conservation. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FoodContainerGameTests {
    private FoodContainerGameTests() {}

    @GameTest(template = "empty")
    public static void mushroomStewConsumesOneAndReturnsExactlyOneBowlPerServing(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        ItemStack stew = new ItemStack(Items.MUSHROOM_STEW, 4);
        ItemStack remaining = stew.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(remaining.is(Items.MUSHROOM_STEW), "The first serving leaves the remaining stew");
        helper.assertValueEqual(remaining.getCount(), 3, "One completion consumes only one of four servings");
        helper.assertValueEqual(player.getInventory().countItem(Items.BOWL), 1, "First serving puts exactly one bowl in inventory");
        assertEnergy(helper, player, 2, 4, "First mushroom stew supplies its R196 S2/N4");
        for (int serving = 2; serving <= 3; serving++) {
            setEnergy(player, 0, 0);
            remaining = remaining.finishUsingItem(helper.getLevel(), player);
            helper.assertValueEqual(remaining.getCount(), 4 - serving, "Each further serving consumes one stew");
            helper.assertValueEqual(player.getInventory().countItem(Items.BOWL), serving, "Each nonfinal serving adds one bowl");
        }
        setEnergy(player, 0, 0);
        remaining = remaining.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(remaining.is(Items.BOWL), "Final serving directly returns an empty bowl");
        helper.assertValueEqual(remaining.getCount(), 1, "Final replacement contains exactly one bowl");
        helper.assertValueEqual(player.getInventory().countItem(Items.BOWL), 3, "Final replacement does not add a second bowl to inventory");
        helper.assertValueEqual(player.getInventory().countItem(Items.BOWL) + remaining.getCount(), 4, "Four consumed servings produce precisely four containers");
        assertEnergy(helper, player, 2, 4, "Final serving also supplies exactly S2/N4");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullInventoryDropsTheSoleBowlAndKeepsTheOtherThreeStews(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        ItemStack stew = new ItemStack(Items.MUSHROOM_STEW, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, stew);
        AABB area = player.getBoundingBox().inflate(3);
        Set<UUID> existing = existingDrops(helper, area);
        List<ItemEntity> created = List.of();
        try {
            ItemStack remaining = stew.finishUsingItem(helper.getLevel(), player);
            created = newDrops(helper, area, existing);
            helper.assertTrue(remaining.is(Items.MUSHROOM_STEW), "Full inventory must not replace the nonfinal stew stack with a bowl");
            helper.assertValueEqual(remaining.getCount(), 3, "Full inventory still consumes exactly one serving");
            helper.assertValueEqual(player.getInventory().countItem(Items.MUSHROOM_STEW), 3, "The other three stews remain in the occupied hand slot");
            helper.assertValueEqual(player.getInventory().countItem(Items.BOWL), 0, "No bowl is secretly inserted into an occupied slot");
            helper.assertValueEqual(created.size(), 1, "Only one new item entity is produced");
            helper.assertTrue(created.getFirst().getItem().is(Items.BOWL), "The overflow entity is the empty container");
            helper.assertValueEqual(created.getFirst().getItem().getCount(), 1, "The dropped container has count one");
            assertEnergy(helper, player, 2, 4, "Full inventory cannot suppress the consumed food's resources");
        } finally {
            created.forEach(ItemEntity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeConsumptionKeepsTheStewAndDoesNotProduceExtraBowls(GameTestHelper helper) {
        Player player = player(helper, GameType.CREATIVE);
        ItemStack stew = new ItemStack(Items.MUSHROOM_STEW, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, stew);
        AABB area = player.getBoundingBox().inflate(3);
        Set<UUID> existing = existingDrops(helper, area);
        ItemStack remaining = stew.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(remaining.is(Items.MUSHROOM_STEW), "Creative finish keeps the food rather than returning a container");
        helper.assertValueEqual(remaining.getCount(), 4, "Creative finish does not consume a serving");
        helper.assertValueEqual(player.getInventory().countItem(Items.BOWL), 0, "Creative finish does not manufacture extra bowls");
        helper.assertValueEqual(newDrops(helper, area, existing).size(), 0, "Creative finish drops no extra container");
        assertEnergy(helper, player, 2, 4, "Creative use still routes the profile through the same food state");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void milkClearsPositiveAndNegativeEffectsBeforeNutritionAndReturnsOneBucket(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        NutritionState.set(player, new NutritionState(1, 0, 0, 0, 0, -1));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0));
        ItemStack milk = new ItemStack(Items.MILK_BUCKET);
        helper.assertValueEqual(milk.getUseDuration(player), 32, "Milk completes after the source32-tick duration");
        ItemStack remaining = milk.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(remaining.is(Items.BUCKET), "Milk returns its matching iron bucket");
        helper.assertValueEqual(remaining.getCount(), 1, "Milk returns only one replacement bucket");
        helper.assertTrue(milk.isEmpty(), "The single survival milk item is consumed");
        helper.assertValueEqual(player.getInventory().countItem(Items.BUCKET), 0, "Single-item replacement does not add a duplicate bucket to inventory");
        helper.assertFalse(player.hasEffect(MobEffects.MOVEMENT_SPEED), "Milk clears positive effects");
        helper.assertFalse(player.hasEffect(MobEffects.POISON), "Milk clears negative effects");
        assertEnergy(helper, player, 0, 4, "Milk adds four nutrition without adding satiation");
        helper.assertValueEqual(NutritionState.get(player).protein(), 32_000, "Milk nutrition contributes source protein exactly once");
        helper.assertValueEqual(NutritionState.get(player).fat(), 0, "Milk does not invent essential-fat nutrients");
        helper.assertValueEqual(NutritionState.get(player).plants(), 0, "Milk does not invent plant nutrients");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nauseatedMilkUseDoesNotCleanseEffectsConsumeTheBucketOrAddNutrition(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 600, 0));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 0));
        NutritionState before = NutritionState.get(player);
        ItemStack milk = new ItemStack(Items.MILK_BUCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, milk);
        milk.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertFalse(player.isUsingItem(), "Milk cannot begin while nauseated");
        ItemStack remaining = milk.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(remaining.is(Items.MILK_BUCKET), "Direct completion also preserves the full milk bucket");
        helper.assertValueEqual(remaining.getCount(), 1, "Rejected completion consumes no milk");
        helper.assertTrue(player.hasEffect(MobEffects.CONFUSION), "Rejected milk does not clear its blocking nausea");
        helper.assertTrue(player.hasEffect(MobEffects.POISON), "Rejected milk does not grant free poison cleansing");
        helper.assertTrue(player.hasEffect(MobEffects.REGENERATION), "Rejected milk leaves positive effects intact");
        helper.assertValueEqual(player.getInventory().countItem(Items.BUCKET), 0, "Rejected milk produces no empty bucket");
        helper.assertValueEqual(NutritionState.get(player), before, "Rejected milk adds no micronutrients");
        assertEnergy(helper, player, 0, 0, "Rejected milk adds neither food resource");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void honeyUsesThirtyTwoTicksCuresPoisonAndReturnsOneGlassBottlePerServing(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        ItemStack honey = new ItemStack(Items.HONEY_BOTTLE, 2);
        helper.assertValueEqual(honey.getUseDuration(player), 32, "Mapped honey uses32 ticks instead of the native fixed40");
        helper.assertValueEqual(honey.getUseAnimation(), UseAnim.DRINK, "Honey retains its drinking animation");
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0));
        ItemStack remaining = honey.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(remaining.is(Items.HONEY_BOTTLE), "First honey serving preserves the rest of the stack");
        helper.assertValueEqual(remaining.getCount(), 1, "First completion consumes one honey bottle");
        helper.assertValueEqual(player.getInventory().countItem(Items.GLASS_BOTTLE), 1, "First completion adds exactly one empty glass bottle");
        helper.assertFalse(player.hasEffect(MobEffects.POISON), "Modern honey keeps its poison cure");
        assertEnergy(helper, player, 6, 6, "Modern honey adaptation supplies six of each resource");
        helper.assertValueEqual(NutritionState.get(player).resistance(), 9_600, "Explicit modern honey sugar2000 contributes resistance9600 once");
        setEnergy(player, 0, 0);
        remaining = remaining.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(remaining.is(Items.GLASS_BOTTLE), "Final honey serving returns the empty bottle directly");
        helper.assertValueEqual(remaining.getCount(), 1, "Final replacement is one glass bottle");
        helper.assertValueEqual(player.getInventory().countItem(Items.GLASS_BOTTLE), 1, "Final replacement does not create a second inventory bottle");
        helper.assertValueEqual(player.getInventory().countItem(Items.GLASS_BOTTLE) + remaining.getCount(), 2, "Two servings create exactly two containers");
        assertEnergy(helper, player, 6, 6, "Final honey serving also adds its mapped food values only once");
        helper.assertValueEqual(NutritionState.get(player).resistance(), 19_200, "Two honey servings contribute their sugar exactly twice");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void placedCakeHasSixInstantSlicesEachWithSourceFoodProteinAndSugar(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        NutritionState.set(player, new NutritionState(1, 0, 0, 0, 0, -1));
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos pos = helper.absolutePos(relative);
        helper.setBlock(relative.below(), Blocks.STONE);
        helper.setBlock(relative, Blocks.CAKE);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        try {
            for (int slice = 1; slice <= 6; slice++) {
                setEnergy(player, 0, 0);
                var before = helper.getLevel().getBlockState(pos);
                helper.assertTrue(before.is(Blocks.CAKE), "A cake remains before each of the six real interactions");
                helper.assertValueEqual(before.getValue(CakeBlock.BITES), slice - 1, "Source cake state tracks the previously eaten slices");
                var result = before.useWithoutItem(helper.getLevel(), player, hit);
                helper.assertTrue(result.consumesAction(), "Actual CakeBlock use consumes the interaction immediately");
                helper.assertFalse(player.isUsingItem(), "Eating a placed slice does not start32-tick handheld use");
                assertEnergy(helper, player, 2, 2, "Each placed slice supplies source S2/N2 immediately");
                helper.assertValueEqual(NutritionState.get(player).protein(), slice * 16_000, "Each slice supplies N2 times8000 protein");
                helper.assertValueEqual(NutritionState.get(player).fat(), 0, "Cake does not contain the old discarded fat flag");
                helper.assertValueEqual(NutritionState.get(player).plants(), 0, "Cake supplies no plant micronutrients");
                helper.assertValueEqual(NutritionState.get(player).resistance(), slice * 796, "Each source166-sugar slice casts166*4.8F to796");
                var after = helper.getLevel().getBlockState(pos);
                if (slice < 6) {
                    helper.assertTrue(after.is(Blocks.CAKE), "The cake remains until its sixth slice");
                    helper.assertValueEqual(after.getValue(CakeBlock.BITES), slice, "Each interaction increments the source bite state once");
                } else helper.assertTrue(after.isAir(), "The sixth slice removes the cake; there is no seventh native slice");
            }
        } finally {
            helper.getLevel().removeBlock(pos, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void suspiciousStewConsumesBeforeItsOwnNauseaAndRejectsTheNextServingWithoutFreeEffects(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        ItemStack stew = new ItemStack(Items.SUSPICIOUS_STEW, 2);
        stew.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, new SuspiciousStewEffects(
                List.of(new SuspiciousStewEffects.Entry(MobEffects.CONFUSION, 200))));
        ItemStack remaining = stew.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(remaining.is(Items.SUSPICIOUS_STEW), "Accepted first serving retains the other stew");
        helper.assertValueEqual(remaining.getCount(), 1, "Its own nausea cannot prevent consumption of the already accepted serving");
        helper.assertValueEqual(player.getInventory().countItem(Items.BOWL), 1, "Accepted serving returns exactly one empty bowl");
        assertEnergy(helper, player, 2, 4, "Accepted suspicious stew adds source-adapted S2/N4 before its own nausea");
        MobEffectInstance nausea = player.getEffect(MobEffects.CONFUSION);
        helper.assertTrue(nausea != null, "Accepted serving still applies the captured custom nausea");
        helper.assertValueEqual(nausea.getDuration(), 200, "Custom stew nausea has exactly its component duration");
        helper.assertValueEqual(nausea.getAmplifier(), 0, "Custom stew nausea remains amplifier zero");
        // Age only the effect so a rejected second completion cannot silently refresh it to200.
        nausea.tick(player, () -> {});
        helper.assertValueEqual(nausea.getDuration(), 199, "Regression fixture makes an accidental effect refresh observable");
        NutritionState before = NutritionState.get(player);
        ItemStack denied = remaining.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(denied.is(Items.SUSPICIOUS_STEW), "Existing nausea blocks the second serving before it can return a bowl");
        helper.assertValueEqual(denied.getCount(), 1, "Rejected second completion does not consume the remaining stew");
        helper.assertValueEqual(player.getInventory().countItem(Items.BOWL), 1, "Rejected completion cannot add another container");
        assertEnergy(helper, player, 2, 4, "Rejected second completion does not add S/N");
        helper.assertValueEqual(NutritionState.get(player), before, "Rejected second completion does not add micronutrients or sugar");
        helper.assertValueEqual(player.getEffect(MobEffects.CONFUSION).getDuration(), 199, "Rejected completion does not grant a free effect refresh");

        Player creative = player(helper, GameType.CREATIVE);
        ItemStack creativeStew = new ItemStack(Items.SUSPICIOUS_STEW, 2);
        creativeStew.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, new SuspiciousStewEffects(
                List.of(new SuspiciousStewEffects.Entry(MobEffects.CONFUSION, 200))));
        ItemStack creativeRemaining = creativeStew.finishUsingItem(helper.getLevel(), creative);
        helper.assertTrue(creativeRemaining.is(Items.SUSPICIOUS_STEW), "Creative custom stew preserves the food rather than returning a container");
        helper.assertValueEqual(creativeRemaining.getCount(), 2, "Creative custom stew does not consume either serving");
        helper.assertValueEqual(creative.getInventory().countItem(Items.BOWL), 0, "Creative custom stew produces no extra bowl");
        helper.assertTrue(creative.hasEffect(MobEffects.CONFUSION), "Creative preservation does not discard accepted custom effects");
        helper.succeed();
    }

    private static Player player(GameTestHelper helper, GameType type) {
        Player player = helper.makeMockPlayer(type);
        player.getAbilities().instabuild = type.isCreative();
        player.experienceLevel = 35;
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
        player.setPos(center.x, center.y, center.z);
        setEnergy(player, 0, 0);
        return player;
    }

    private static void setEnergy(Player player, int satiation, int nutrition) {
        player.getFoodData().setFoodLevel(satiation);
        ((DualFoodData)player.getFoodData()).mite$setNutrition(nutrition);
    }

    private static void assertEnergy(GameTestHelper helper, Player player, int satiation, int nutrition, String context) {
        helper.assertValueEqual(player.getFoodData().getFoodLevel(), satiation, context + " (satiation)");
        helper.assertValueEqual(((DualFoodData)player.getFoodData()).mite$getNutrition(), nutrition, context + " (nutrition)");
    }

    private static Set<UUID> existingDrops(GameTestHelper helper, AABB area) {
        Set<UUID> ids = new HashSet<>();
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).forEach(entity -> ids.add(entity.getUUID()));
        return ids;
    }

    private static List<ItemEntity> newDrops(GameTestHelper helper, AABB area, Set<UUID> existing) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
                .filter(entity -> !existing.contains(entity.getUUID())).toList();
    }
}

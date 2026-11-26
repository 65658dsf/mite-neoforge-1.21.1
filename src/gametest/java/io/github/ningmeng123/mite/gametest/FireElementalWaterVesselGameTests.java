package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.registry.MiteMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises direct right-click water damage and container handling. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FireElementalWaterVesselGameTests {
    private FireElementalWaterVesselGameTests() {}

    @GameTest(template = "empty")
    public static void waterBucketDamagesFireElementalAndReturnsMatchingMetal(GameTestHelper helper) {
        Mob target = fireElemental(helper);
        Player player = player(helper, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteFoods.get("copper_water_bucket")));

        helper.assertTrue(player.interactOn(target, InteractionHand.MAIN_HAND).consumesAction(), "Water bucket consumes the entity interaction");
        helper.assertValueEqual(target.getHealth(), 20F, "Health after twenty water damage");
        ItemStack held = player.getMainHandItem();
        helper.assertTrue(held.is(MiteFoods.get("copper_bucket")) && held.getCount() == 1, "Water bucket returns exactly one empty copper bucket to the same hand");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void offhandWaterBowlConsumesOneServingAndReturnsOneBowl(GameTestHelper helper) {
        Mob target = fireElemental(helper);
        Player player = player(helper, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(MiteFoods.WATER_BOWL.get(), 3));

        helper.assertTrue(player.interactOn(target, InteractionHand.OFF_HAND).consumesAction(), "Offhand water bowl consumes the entity interaction");
        helper.assertValueEqual(target.getHealth(), 35F, "Health after five water damage");
        ItemStack held = player.getOffhandItem();
        helper.assertTrue(held.is(MiteFoods.WATER_BOWL.get()) && held.getCount() == 2, "Offhand retains the other two filled bowls");
        helper.assertTrue(player.getMainHandItem().is(Items.STICK), "Main hand retains its original item");
        helper.assertValueEqual(player.getInventory().countItem(Items.BOWL), 1, "Empty bowls returned to the inventory");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void waterBottlePassesAndCreativeWaterBucketIsKept(GameTestHelper helper) {
        Mob target = fireElemental(helper);
        Player player = player(helper, GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, PotionContents.createItemStack(Items.POTION, Potions.WATER));

        helper.assertFalse(player.interactOn(target, InteractionHand.MAIN_HAND).consumesAction(), "Drinking water bottle passes entity interaction");
        helper.assertValueEqual(target.getHealth(), 40F, "Health after water bottle interaction");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(player.interactOn(target, InteractionHand.MAIN_HAND).consumesAction(), "Creative water bucket still damages the target");
        helper.assertValueEqual(target.getHealth(), 20F, "Health after creative water bucket interaction");
        helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET), "Creative use retains the native water bucket");
        helper.assertValueEqual(player.getInventory().countItem(Items.BUCKET), 0, "Empty buckets created by creative use");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void milkAndSoupUseFivePointDose(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        Mob milkTarget = fireElemental(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MILK_BUCKET));

        helper.assertTrue(player.interactOn(milkTarget, InteractionHand.MAIN_HAND).consumesAction(), "Milk bucket can douse a fire elemental");
        helper.assertValueEqual(milkTarget.getHealth(), 35F, "A milk bucket deals five damage");
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "Milk bucket leaves an empty bucket");
        milkTarget.discard();

        Mob soupTarget = fireElemental(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteFoods.CHICKEN_SOUP.get()));
        helper.assertTrue(player.interactOn(soupTarget, InteractionHand.MAIN_HAND).consumesAction(), "Soup can douse a fire elemental");
        helper.assertValueEqual(soupTarget.getHealth(), 35F, "A soup bowl deals five damage");
        helper.assertTrue(player.getMainHandItem().is(Items.BOWL), "Soup leaves an empty bowl");
        helper.succeed();
    }

    private static Mob fireElemental(GameTestHelper helper) {
        Mob target = MiteMobs.type(MobKind.FIRE_ELEMENTAL).create(helper.getLevel());
        target.moveTo(helper.absolutePos(new BlockPos(2, 2, 1)).getBottomCenter());
        target.setNoAi(true);
        target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40D);
        target.setHealth(40F);
        helper.getLevel().addFreshEntity(target);
        return target;
    }

    private static Player player(GameTestHelper helper, GameType type) {
        Player player = helper.makeMockPlayer(type);
        type.updatePlayerAbilities(player.getAbilities());
        player.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)).getBottomCenter());
        return player;
    }
}

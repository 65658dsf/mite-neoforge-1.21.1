package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.crafting.CraftingAccessRules;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.registry.MiteCreativeTabs;
import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import io.github.ningmeng123.mite.registry.MiteFoods;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native lemon seed placement, crop growth and harvest. @author NingMeng123 */
@GameTestHolder("mite_gameplay")
@PrefixGameTestTemplate(false)
public final class LemonCropGameTests {
    private LemonCropGameTests() {}

    @GameTest(template = "empty")
    public static void oneLemonMakesTwoSeedsInTheInventoryGrid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var menu = helper.makeMockPlayer(GameType.SURVIVAL).inventoryMenu;
        var grid = menu.getCraftSlots();
        var input = CraftingInput.of(grid.getWidth(), grid.getHeight(),
                List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(MiteFoods.LEMON.get())));
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
        helper.assertValueEqual(recipe.id(), ResourceLocation.fromNamespaceAndPath("mite", "lemon_seeds"),
                "A single lemon matches the seed extraction recipe in the inventory grid");
        var output = recipe.value().assemble(input, level.registryAccess());
        helper.assertTrue(output.is(MiteFoodPlants.LEMON_SEEDS.get()), "Seed extraction returns the registered crop seeds");
        helper.assertValueEqual(output.getCount(), 2, "One lemon yields two seeds");
        helper.assertTrue(CraftingAccessRules.canCraft(grid, output) && WorkbenchRules.allows(menu, output, level),
                "Seed extraction needs no workbench");
        var unlock = level.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath("mite", "recipes/misc/lemon_seeds"));
        helper.assertTrue(unlock != null && unlock.value().rewards().recipes().contains(recipe.id()),
                "The lemon inventory criterion unlocks the seed recipe");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lemonSeedsPlantAndGrowOnFarmland(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos soil = pos.below();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack seeds = new ItemStack(MiteFoodPlants.LEMON_SEEDS.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, seeds);
        var use = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(soil).add(0, 0.5, 0), Direction.UP, soil, false));

        level.setBlockAndUpdate(soil, Blocks.DIRT.defaultBlockState());
        helper.assertFalse(seeds.useOn(use).consumesAction(), "Lemon seeds require farmland");
        helper.assertValueEqual(seeds.getCount(), 2, "The rejected placement consumes no seeds");

        level.setBlockAndUpdate(soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        helper.assertTrue(seeds.useOn(use).consumesAction(), "Lemon seeds plant on ordinary farmland");
        helper.assertValueEqual(seeds.getCount(), 1, "Planting consumes exactly one seed");
        var crop = MiteFoodPlants.LEMON_CROP.get();
        helper.assertTrue(level.getBlockState(pos).is(crop), "The native placement creates the registered lemon crop");
        helper.assertValueEqual(crop.getAge(level.getBlockState(pos)), 0, "A newly planted lemon starts at age zero");
        helper.assertTrue(crop.getCloneItemStack(level, pos, level.getBlockState(pos)).is(seeds.getItem()),
                "Picking the crop returns the registered seed");
        helper.assertValueEqual(MiteCreativeTabs.group(seeds.getItem()), MiteCreativeTabs.Group.FOODS,
                "Lemon seeds appear with the MITE food plants");

        var growthRandom = new LegacyRandomSource(196L) {
            @Override public int nextInt(int bound) { return 0; }
        };
        for (int age = 1; age <= crop.getMaxAge(); age++) {
            level.getBlockState(pos).randomTick(level, pos, growthRandom);
            helper.assertValueEqual(crop.getAge(level.getBlockState(pos)), age, "Successful natural growth advances one stage");
        }
        helper.assertTrue(crop.isMaxAge(level.getBlockState(pos)), "The final age is mature");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lemonHarvestReturnsSeedAndOnlyMaturePlantsYieldFruit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos.below(), Blocks.FARMLAND.defaultBlockState());
        var crop = MiteFoodPlants.LEMON_CROP.get();
        var seed = MiteFoodPlants.LEMON_SEEDS.get();
        var lemon = MiteFoods.LEMON.get();
        for (int age = 0; age <= crop.getMaxAge(); age++) {
            level.setBlockAndUpdate(pos, crop.getStateForAge(age));
            helper.assertTrue(level.destroyBlock(pos, true), "The planted crop can be harvested");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos),
                    entity -> entity.getItem().is(seed) || entity.getItem().is(lemon));
            int seeds = 0;
            int fruit = 0;
            for (ItemEntity drop : drops) {
                ItemStack stack = drop.getItem();
                if (stack.is(seed)) seeds += stack.getCount();
                else fruit += stack.getCount();
                drop.discard();
            }
            boolean mature = age == crop.getMaxAge();
            helper.assertValueEqual(seeds, mature ? 2 : 1, "A mature crop provides a second seed to expand the farm");
            helper.assertValueEqual(fruit, mature ? 1 : 0, "Only a mature crop yields a lemon");
        }
        helper.succeed();
    }
}

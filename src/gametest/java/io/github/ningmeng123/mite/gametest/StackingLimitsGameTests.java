package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.rules.StackingRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual registry limits, metadata depth and stack-local overrides share the source upper bound. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class StackingLimitsGameTests {
    private StackingLimitsGameTests() {}

    @GameTest(template = "empty")
    public static void everyMetalIngotAndRawMineralStacksToFour(GameTestHelper helper) {
        for (String id : new String[]{"minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:gold_ingot",
                "minecraft:netherite_ingot", "minecraft:raw_copper", "minecraft:raw_iron", "minecraft:raw_gold",
                "mite:silver_ingot", "mite:ancient_metal_ingot", "mite:mithril_ingot", "mite:adamantium_ingot",
                "mite:vibranium_ingot", "mite:raw_silver", "mite:raw_mithril", "mite:raw_adamantium"}) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
            helper.assertValueEqual(stack.getMaxStackSize(), 4, "The mineral cap applies to " + id);
            stack.setCount(8);
            ItemStack loaded = ItemStack.parse(helper.getLevel().registryAccess(), stack.save(helper.getLevel().registryAccess())).orElseThrow();
            helper.assertValueEqual(loaded.getCount(), 8, "Loading old overfull mineral stacks cannot delete items");
            helper.assertValueEqual(loaded.getMaxStackSize(), 4, "Old mineral stacks still use the new transfer cap");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allConfirmedAndAdaptedRegistryIdentitiesUseTheDeclaredStackLimit(GameTestHelper helper) {
        for (var row : StackingRules.limits().entrySet()) {
            var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(row.getKey())).orElseThrow();
            helper.assertFalse(item == Items.AIR, "A source carried identity cannot resolve to air: " + row.getKey());
            ItemStack stack = new ItemStack(item);
            helper.assertValueEqual(stack.getMaxStackSize(), row.getValue(), "Actual carried limit: " + row.getKey());
            helper.assertValueEqual(stack.get(DataComponents.MAX_STACK_SIZE), row.getValue(), "Shared registry component: " + row.getKey());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snowDepthKeepsSourceThirtyTwoSingleLayersAndEightMultiLayers(GameTestHelper helper) {
        ItemStack plain = new ItemStack(Items.SNOW);
        helper.assertValueEqual(plain.getMaxStackSize(), 32, "Source subtype zero is ordinary single-layer snow");
        for (int layers = 1; layers <= 8; layers++) {
            ItemStack snow = plain.copy();
            snow.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(BlockStateProperties.LAYERS, layers));
            helper.assertValueEqual(snow.getMaxStackSize(), layers == 1 ? 32 : 8, "Source carried snow depth " + layers);
            ItemStack saved = ItemStack.parse(helper.getLevel().registryAccess(), snow.save(helper.getLevel().registryAccess())).orElseThrow();
            helper.assertValueEqual(saved.getMaxStackSize(), snow.getMaxStackSize(), "Snow's saved component depth retains its limit");
            helper.assertTrue(ItemStack.isSameItemSameComponents(saved, snow), "Saving cannot erase snow depth to permit a larger stack");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stackLocalOverridesCanLowerButCannotRaiseASourceUpperBound(GameTestHelper helper) {
        ItemStack stone = new ItemStack(Items.STONE, 64);
        stone.set(DataComponents.MAX_STACK_SIZE, 99);
        helper.assertValueEqual(stone.getCount(), 64, "Changing upper bounds must not silently destroy a legacy overfull stack");
        helper.assertValueEqual(stone.getMaxStackSize(), 4, "A stack-local component cannot bypass the source stone upper bound");
        stone.set(DataComponents.MAX_STACK_SIZE, 2);
        helper.assertValueEqual(stone.getMaxStackSize(), 2, "A stricter modern component remains respected");
        ItemStack snow = new ItemStack(Items.SNOW);
        snow.set(DataComponents.MAX_STACK_SIZE, 64);
        snow.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(BlockStateProperties.LAYERS, 4));
        helper.assertValueEqual(snow.getMaxStackSize(), 8, "Snow depth remains authoritative over a raised per-stack limit");
        helper.succeed();
    }
}

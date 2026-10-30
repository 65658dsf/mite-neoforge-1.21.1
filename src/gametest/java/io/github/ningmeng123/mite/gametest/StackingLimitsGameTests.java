package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteItemStacking;
import io.github.ningmeng123.mite.rules.StackingRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual registry limits, metadata depth and stack-local overrides share the source upper bound. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class StackingLimitsGameTests {
    private StackingLimitsGameTests() {}

    @GameTest(template = "empty")
    public static void allRegisteredLeafItemsUseEightInRegistryAndSavedStacks(GameTestHelper helper) {
        int leaves = 0;
        for (var item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof LeavesBlock)) continue;
            leaves++;
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            ItemStack stack = new ItemStack(item, 16);
            helper.assertValueEqual(stack.get(DataComponents.MAX_STACK_SIZE), 8, "Every actual leaf block item has the eight-item default: " + id);
            helper.assertValueEqual(stack.getMaxStackSize(), 8, "Every actual leaf block item has the eight-item transfer cap: " + id);
            helper.assertValueEqual(MiteItemStacking.limit(stack), 8, "Leaf families are included in the resolved dynamic upper-bound map: " + id);
            ItemStack loaded = ItemStack.parse(helper.getLevel().registryAccess(), stack.save(helper.getLevel().registryAccess())).orElseThrow();
            helper.assertValueEqual(loaded.get(DataComponents.MAX_STACK_SIZE), 8, "Saved leaves inherit the eight-item registry component: " + id);
            helper.assertValueEqual(loaded.getMaxStackSize(), 8, "Saved leaves retain the same eight-item transfer cap: " + id);
            helper.assertValueEqual(loaded.getCount(), 16, "Loading historical overfull leaves never deletes items: " + id);

            stack.set(DataComponents.MAX_STACK_SIZE, 2);
            loaded = ItemStack.parse(helper.getLevel().registryAccess(), stack.save(helper.getLevel().registryAccess())).orElseThrow();
            helper.assertValueEqual(loaded.get(DataComponents.MAX_STACK_SIZE), 2, "Saving preserves an explicitly stricter leaf component: " + id);
            helper.assertValueEqual(loaded.getMaxStackSize(), 2, "Explicit smaller leaf limits remain authoritative: " + id);
            helper.assertValueEqual(loaded.getCount(), 16, "A stricter leaf component cannot truncate the saved count: " + id);

            stack.set(DataComponents.MAX_STACK_SIZE, 64);
            loaded = ItemStack.parse(helper.getLevel().registryAccess(), stack.save(helper.getLevel().registryAccess())).orElseThrow();
            helper.assertValueEqual(loaded.get(DataComponents.MAX_STACK_SIZE), 64, "Loading retains a saved raised component without rewriting NBT: " + id);
            helper.assertValueEqual(loaded.getMaxStackSize(), 8, "The resolved leaf upper bound prevents raised stack components bypassing eight: " + id);
            helper.assertValueEqual(loaded.getCount(), 16, "Capping an overfull leaf stack never deletes stored items: " + id);
        }
        helper.assertTrue(leaves >= 10, "The actual registry includes all ten vanilla leaf types before checking addon families");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyMetalIngotStacksToEightWhileRawMineralsStayAtFour(GameTestHelper helper) {
        for (String id : new String[]{"minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:gold_ingot",
                "minecraft:netherite_ingot",
                "mite:silver_ingot", "mite:ancient_metal_ingot", "mite:mithril_ingot", "mite:adamantium_ingot",
                "mite:vibranium_ingot"}) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
            helper.assertValueEqual(stack.getMaxStackSize(), 8, "Every metal ingot uses the corrected eight-item cap: " + id);
            stack.setCount(16);
            ItemStack loaded = ItemStack.parse(helper.getLevel().registryAccess(), stack.save(helper.getLevel().registryAccess())).orElseThrow();
            helper.assertValueEqual(loaded.getCount(), 16, "Loading an old overfull ingot stack cannot delete items: " + id);
            helper.assertValueEqual(loaded.getMaxStackSize(), 8, "Saved ingots use the corrected transfer cap: " + id);
        }
        for (String id : new String[]{"minecraft:raw_copper", "minecraft:raw_iron", "minecraft:raw_gold",
                "mite:raw_silver", "mite:raw_mithril", "mite:raw_adamantium"}) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
            helper.assertValueEqual(stack.getMaxStackSize(), 4, "The four-item raw-mineral cap remains unchanged: " + id);
            stack.setCount(8);
            ItemStack loaded = ItemStack.parse(helper.getLevel().registryAccess(), stack.save(helper.getLevel().registryAccess())).orElseThrow();
            helper.assertValueEqual(loaded.getCount(), 8, "Loading old overfull raw-mineral stacks cannot delete items");
            helper.assertValueEqual(loaded.getMaxStackSize(), 4, "Saved raw minerals still use their four-item transfer cap");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedIngotDefaultsFollowEightButExplicitStricterComponentsArePreserved(GameTestHelper helper) {
        // Ordinary old stacks stored an ID/count plus component differences, not the old
        // default MAX_STACK_SIZE. This is the actual old NBT shape, rather than a new stack.
        CompoundTag ordinaryOld = new CompoundTag();
        ordinaryOld.putString("id", "minecraft:iron_ingot"); ordinaryOld.putInt("count", 16);
        helper.assertFalse(ordinaryOld.contains("components"), "The ordinary old fixture has no saved max-stack component patch");
        ItemStack inherited = ItemStack.parse(helper.getLevel().registryAccess(), ordinaryOld).orElseThrow();
        helper.assertValueEqual(inherited.getCount(), 16, "Loading ordinary old ingots preserves every item even above the cap");
        helper.assertValueEqual(inherited.getMaxStackSize(), 8, "Ordinary old ingots inherit the corrected registry default without migration");

        ItemStack lower = new ItemStack(Items.IRON_INGOT, 16);
        lower.set(DataComponents.MAX_STACK_SIZE, 4);
        CompoundTag lowerTag = (CompoundTag)lower.save(helper.getLevel().registryAccess());
        helper.assertValueEqual(lowerTag.getCompound("components").getInt("minecraft:max_stack_size"), 4,
                "An explicitly stricter max-stack component is really present in saved NBT");
        ItemStack loadedLower = ItemStack.parse(helper.getLevel().registryAccess(), lowerTag).orElseThrow();
        helper.assertValueEqual(loadedLower.getCount(), 16, "A deliberately stricter saved component does not truncate the stored count");
        helper.assertValueEqual(loadedLower.getMaxStackSize(), 4, "Explicit four-item components keep their stricter limit after loading");

        ItemStack raised = new ItemStack(Items.IRON_INGOT, 16);
        raised.set(DataComponents.MAX_STACK_SIZE, 64);
        ItemStack loadedRaised = ItemStack.parse(helper.getLevel().registryAccess(), raised.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertValueEqual(loadedRaised.getCount(), 16, "A raised saved component cannot discard old items during normalization");
        helper.assertValueEqual(loadedRaised.get(DataComponents.MAX_STACK_SIZE), 64, "Loading retains the explicit component rather than rewriting the save");
        helper.assertValueEqual(loadedRaised.getMaxStackSize(), 8, "A raised saved component cannot bypass the managed eight-ingot upper bound");
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

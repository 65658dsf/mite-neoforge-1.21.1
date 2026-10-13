package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.crafting.LeatherMendingRecipe;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.ArmorDyeRecipe;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real redstone/dispense integration, including fixed-quality dyes and operator-free repair rejection. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class QualityCrafterGameTests {
    @GameTest(template = "empty", timeoutTicks = 650)
    public static void automationManufacturesOnlyOrdinaryEquipmentOneBatchPerPulse(GameTestHelper helper) {
        Machine machine = new Machine(helper, 1);
        helper.setBlock(machine.position.south(), MiteWorkbenches.TABLES.get("copper").get());
        for (int slot : new int[]{0, 1, 2}) machine.crafter.setItem(slot, new ItemStack(Items.COPPER_INGOT, 2));
        for (int slot : new int[]{4, 7}) machine.crafter.setItem(slot, new ItemStack(Items.STICK, 2));
        List<ItemStack> original = machine.snapshot();
        helper.assertTrue(CrafterBlock.getPotentialResults(helper.getLevel(), machine.crafter.asCraftInput()).isPresent(),
                "The real registered copper-pick recipe matches the machine inputs");
        machine.pulse();
        // D1250, ordinary quality and copper bench: (round(1150^0.8)+100)/1.3 truncates to 293.
        // The vanilla pulse first schedules activation four ticks later, as in normal gameplay.
        helper.runAfterDelay(293, () -> {
            helper.assertTrue(machine.chest.isEmpty(), "The actual crafter cannot emit a tool before its complete 293-tick period");
            machine.assertInputs(original, "Unfinished manufacture consumes no ingredients");
        });
        helper.runAfterDelay(302, () -> {
            helper.assertValueEqual(machine.count(MiteTools.get("copper_pickaxe")), 1, "One redstone request creates exactly one pick");
            ItemStack pick = machine.output(MiteTools.get("copper_pickaxe"));
            helper.assertValueEqual(pick.getOrDefault(EquipmentDurability.QUALITY.get(), -1), 2,
                    "Operator-free manufacture writes ordinary quality on the actual emitted item");
            helper.assertValueEqual(pick.getMaxDamage(), 4800, "Ordinary copper output uses its R196 base durability");
            for (int slot : new int[]{0, 1, 2, 4, 7}) helper.assertValueEqual(machine.crafter.getItem(slot).getCount(), 1,
                    "The actual dispense consumes only one of each ingredient");
        });
        helper.runAfterDelay(605, () -> {
            helper.assertValueEqual(machine.count(MiteTools.get("copper_pickaxe")), 1,
                    "Keeping a second complete recipe in the machine does not start another batch");
            for (int slot : new int[]{0, 1, 2, 4, 7}) helper.assertValueEqual(machine.crafter.getItem(slot).getCount(), 1,
                    "No extra input is consumed after the paid period would also have elapsed");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void dyeAutomationPreservesFineComponentsAndWaitsItsLongerPeriod(GameTestHelper helper) {
        Machine ordinary = new Machine(helper, 1);
        Machine fine = new Machine(helper, 4);
        ItemStack ordinaryChest = decoratedChest(helper, Quality.AVERAGE);
        ItemStack fineChest = decoratedChest(helper, Quality.FINE);
        ordinary.crafter.setItem(0, ordinaryChest.copy());
        ordinary.crafter.setItem(1, new ItemStack(Items.RED_DYE));
        fine.crafter.setItem(0, fineChest.copy());
        fine.crafter.setItem(1, new ItemStack(Items.RED_DYE));
        for (Machine machine : List.of(ordinary, fine)) helper.assertTrue(
                CrafterBlock.getPotentialResults(helper.getLevel(), machine.crafter.asCraftInput()).orElseThrow().value() instanceof ArmorDyeRecipe,
                "The test exercises the real armor-dye special recipe");
        List<ItemStack> originalFine = fine.snapshot();
        ordinary.pulse();
        fine.pulse();
        // R196 dye D100 -> ordinary 100/1.2 = 83; fine D200 -> 140/1.2 = 116.
        helper.runAfterDelay(83, () -> {
            helper.assertTrue(ordinary.chest.isEmpty() && fine.chest.isEmpty(), "Neither quality is dyed before its own full period");
        });
        helper.runAfterDelay(92, () -> {
            assertDyed(helper, ordinary, ordinaryChest, Quality.AVERAGE, 16);
            helper.assertTrue(fine.chest.isEmpty(), "An existing fine piece cannot use the faster ordinary dye period");
            fine.assertInputs(originalFine, "Fine dye waits without consuming its armor or dye");
        });
        helper.runAfterDelay(116, () -> {
            helper.assertTrue(fine.chest.isEmpty(), "The vanilla initial activation delay cannot be skipped for fine dye");
            fine.assertInputs(originalFine, "Fine dye still preserves all inputs until its completion tick");
        });
        helper.runAfterDelay(125, () -> {
            assertDyed(helper, fine, fineChest, Quality.FINE, 24);
            helper.assertTrue(fine.crafter.isEmpty() && ordinary.crafter.isEmpty(), "Both dye batches consume exactly one armor and one dye");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 510)
    public static void automationCannotBypassQualityEligibilityWithEitherRepairRecipe(GameTestHelper helper) {
        Machine combine = new Machine(helper, 1);
        Machine mend = new Machine(helper, 4);
        ItemStack first = new ItemStack(Items.LEATHER_CHESTPLATE);
        EquipmentDurability.setQuality(first, Quality.FINE);
        first.setDamageValue(9);
        combine.crafter.setItem(0, first.copy());
        combine.crafter.setItem(1, first.copy());
        mend.crafter.setItem(0, first.copy());
        mend.crafter.setItem(1, new ItemStack(Items.STRING, 3));
        helper.assertTrue(CrafterBlock.getPotentialResults(helper.getLevel(), combine.crafter.asCraftInput()).orElseThrow().value() instanceof RepairItemRecipe,
                "Two damaged fine armor items match the genuine combine-repair recipe before the machine policy applies");
        helper.assertTrue(CrafterBlock.getPotentialResults(helper.getLevel(), mend.crafter.asCraftInput()).orElseThrow().value() instanceof LeatherMendingRecipe,
                "Fine armor and string match the registered R196 leather-mending recipe before operator validation");
        List<ItemStack> combineInputs = combine.snapshot();
        List<ItemStack> mendInputs = mend.snapshot();
        combine.pulse();
        mend.pulse();
        helper.runAfterDelay(500, () -> {
            helper.assertTrue(combine.chest.isEmpty() && mend.chest.isEmpty(),
                    "Machines without an operator cannot use either repair recipe to bypass quality eligibility");
            combine.assertInputs(combineInputs, "Rejected combining leaves both damaged armor stacks unchanged");
            mend.assertInputs(mendInputs, "Rejected mending leaves the damaged armor and all three strings unchanged");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 170)
    public static void automationRejectsDamagedHighQualityArmorDyeWithoutRepairingIt(GameTestHelper helper) {
        Machine machine = new Machine(helper, 1);
        ItemStack damaged = decoratedChest(helper, Quality.FINE);
        damaged.setDamageValue(1);
        machine.crafter.setItem(0, damaged.copy());
        machine.crafter.setItem(1, new ItemStack(Items.BLUE_DYE, 2));
        helper.assertTrue(CrafterBlock.getPotentialResults(helper.getLevel(), machine.crafter.asCraftInput()).orElseThrow().value() instanceof ArmorDyeRecipe,
                "The damage restriction is checked after the actual vanilla dye recipe matches");
        List<ItemStack> original = machine.snapshot();
        machine.pulse();
        helper.runAfterDelay(150, () -> {
            helper.assertTrue(machine.chest.isEmpty(), "A matched dye recipe cannot bypass the R196 damaged-ingredient restriction");
            machine.assertInputs(original, "Rejected dyeing changes neither damage, quality, enchantments, name, color nor dye count");
            helper.succeed();
        });
    }

    private static ItemStack decoratedChest(GameTestHelper helper, Quality quality) {
        ItemStack chest = new ItemStack(Items.LEATHER_CHESTPLATE);
        EquipmentDurability.setQuality(chest, quality);
        chest.set(DataComponents.DYED_COLOR, new DyedItemColor(0x336699, false));
        chest.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 自动品质测试"));
        chest.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.VANISHING_CURSE), 1);
        return chest;
    }

    private static void assertDyed(GameTestHelper helper, Machine machine, ItemStack before, Quality quality, int maxDamage) {
        helper.assertValueEqual(machine.count(Items.LEATHER_CHESTPLATE), 1, "One pulse emits precisely one dyed armor piece");
        ItemStack result = machine.output(Items.LEATHER_CHESTPLATE);
        helper.assertValueEqual(EquipmentDurability.quality(result), quality, "Dye automation preserves the original fixed quality");
        helper.assertValueEqual(result.getMaxDamage(), maxDamage, "Dye output retains its original quality-adjusted maximum");
        helper.assertTrue(result.get(DataComponents.DYED_COLOR) != null
                        && result.get(DataComponents.DYED_COLOR).rgb() != before.get(DataComponents.DYED_COLOR).rgb(),
                "The emitted armor is actually recolored");
        ItemStack expected = before.copy();
        expected.set(DataComponents.DYED_COLOR, result.get(DataComponents.DYED_COLOR));
        helper.assertTrue(ItemStack.matches(result, expected), "Only dye color changes; all other item components survive the real dispense");
    }

    private static final class Machine {
        private final GameTestHelper helper;
        private final BlockPos position;
        private final CrafterBlockEntity crafter;
        private final Container chest;

        private Machine(GameTestHelper helper, int z) {
            this.helper = helper;
            position = new BlockPos(1, 1, z);
            helper.setBlock(position, Blocks.CRAFTER.defaultBlockState()
                    .setValue(BlockStateProperties.ORIENTATION, FrontAndTop.EAST_UP));
            helper.setBlock(position.east(), Blocks.CHEST);
            crafter = (CrafterBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(position));
            chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(position.east()));
        }

        private void pulse() { helper.pulseRedstone(position.west(), 5); }

        private List<ItemStack> snapshot() {
            List<ItemStack> inputs = new ArrayList<>();
            for (int slot = 0; slot < crafter.getContainerSize(); slot++) inputs.add(crafter.getItem(slot).copy());
            return inputs;
        }

        private void assertInputs(List<ItemStack> expected, String message) {
            for (int slot = 0; slot < crafter.getContainerSize(); slot++) helper.assertTrue(
                    ItemStack.matches(crafter.getItem(slot), expected.get(slot)), message + " (slot " + slot + ")");
        }

        private int count(Item item) {
            int count = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(item)) count += chest.getItem(slot).getCount();
            return count;
        }

        private ItemStack output(Item item) {
            for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(item)) return chest.getItem(slot);
            helper.fail("Expected machine output " + item);
            return ItemStack.EMPTY;
        }
    }
}

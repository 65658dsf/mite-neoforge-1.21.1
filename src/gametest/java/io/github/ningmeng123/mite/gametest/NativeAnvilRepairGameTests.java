package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.crafting.AnvilExperienceAccess;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.ExperienceRules;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Repeated native repairs keep their level prices without the old forty-level rejection. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class NativeAnvilRepairGameTests {
    private record Material(Item equipment, Item ingredient) {}
    private static final List<Material> MATERIALS = List.of(new Material(Items.ELYTRA, Items.PHANTOM_MEMBRANE),
            new Material(Items.MACE, Items.BREEZE_ROD));

    private NativeAnvilRepairGameTests() {}

    @GameTest(template = "empty")
    public static void highPriorWorkMaterialRepairsRemainAvailableAndChargeEachRepairOnce(GameTestHelper helper) {
        for (Material material : MATERIALS) for (Take take : Take.values()) try (Fixture f = new Fixture(helper)) {
            ItemStack input = new ItemStack(material.equipment());
            helper.assertFalse(EquipmentDurability.isManaged(input), "The native repair fixture is independent of source quality maintenance rules");
            input.setDamageValue(20);
            input.set(DataComponents.REPAIR_COST, 63);
            ItemStack ingredients = new ItemStack(material.ingredient(), 3);
            int levels = 300;
            for (int operation = 0; operation < 2; operation++) {
                input.setDamageValue(20);
                f.prepare(input, ingredients);
                int fee = operation == 0 ? 64 : 128;
                int resultWork = operation == 0 ? 127 : 255;
                helper.assertFalse(((AnvilExperienceAccess)f.menu).mite$usesExperiencePoints(),
                        "A native material repair retains its level payment mode");
                helper.assertValueEqual(f.menu.getCost(), fee, "Native material repair charges one level plus its full prior-work penalty");
                helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "High prior work cannot hide a native material repair");
                helper.assertValueEqual(f.menu.getSlot(2).getItem().getDamageValue(), 0, "One native repair ingredient restores the twenty missing durability");
                helper.assertValueEqual(f.menu.getSlot(2).getItem().getOrDefault(DataComponents.REPAIR_COST, 0), resultWork,
                        "A native repair continues recording its increased prior-work value");
                helper.assertTrue(f.menu.getSlot(2).mayPickup(f.owner), "A player with enough levels may retrieve the high-cost material repair");
                ItemStack result = f.take(take, material.equipment());
                helper.assertTrue(result.is(material.equipment()), "Actual normal and quick pickup produce repaired native equipment");
                helper.assertValueEqual(result.getDamageValue(), 0, "Actual material-repair pickup retains full durability");
                levels -= fee;
                helper.assertValueEqual(f.owner.experienceLevel, levels, "The native level fee is charged exactly once");
                helper.assertValueEqual(f.owner.totalExperience, ExperienceRules.experienceRequiredForLevel(levels),
                        "Native repair preserves its exact zero-fraction level payment arithmetic");
                helper.assertTrue(f.menu.getSlot(0).getItem().isEmpty(), "Material repair consumes its equipment input once");
                helper.assertValueEqual(f.menu.getSlot(1).getItem().getCount(), 2 - operation,
                        "Material repair consumes exactly one repair ingredient");
                int paidTotal = f.owner.totalExperience;
                take.run(f.menu, f.owner);
                helper.assertValueEqual(f.owner.totalExperience, paidTotal, "A second exhausted-result click cannot charge the material repair twice");
                helper.assertValueEqual(f.menu.getSlot(1).getItem().getCount(), 2 - operation,
                        "A second exhausted-result click cannot consume another repair ingredient");
                input = result;
                ingredients = f.menu.getSlot(1).getItem().copy();
                f.clearTransferred();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void highPriorWorkEquipmentCombinationsCanBeRepeatedAndChargeNativeLevelsOnce(GameTestHelper helper) {
        for (Material material : MATERIALS) for (Take take : Take.values()) try (Fixture f = new Fixture(helper)) {
            ItemStack input = new ItemStack(material.equipment());
            helper.assertFalse(EquipmentDurability.isManaged(input), "The native combination fixture is independent of source quality maintenance rules");
            input.setDamageValue(input.getMaxDamage() - 20);
            input.set(DataComponents.REPAIR_COST, 63);
            int levels = 300;
            for (int operation = 0; operation < 2; operation++) {
                ItemStack donor = new ItemStack(material.equipment());
                donor.setDamageValue(donor.getMaxDamage() - 20);
                int maximum = input.getMaxDamage();
                int expectedDamage = Math.max(0, maximum - (maximum - input.getDamageValue() + 20 + (int)(maximum * .12F)));
                f.prepare(input, donor);
                int fee = operation == 0 ? 65 : 129;
                int resultWork = operation == 0 ? 127 : 255;
                helper.assertFalse(((AnvilExperienceAccess)f.menu).mite$usesExperiencePoints(),
                        "A native equipment combination retains its level payment mode");
                helper.assertValueEqual(f.menu.getCost(), fee, "Native equipment combination charges two levels plus its full prior-work penalty");
                helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "High prior work cannot hide a native equipment combination");
                helper.assertValueEqual(f.menu.getSlot(2).getItem().getDamageValue(), expectedDamage,
                        "Native equipment combination retains its own remaining-durability and twelve-percent repair calculation");
                helper.assertValueEqual(f.menu.getSlot(2).getItem().getOrDefault(DataComponents.REPAIR_COST, 0), resultWork,
                        "A repeated native combination continues recording its prior-work value");
                ItemStack result = f.take(take, material.equipment());
                helper.assertTrue(result.is(material.equipment()), "Actual normal and quick pickup produce the combined native equipment");
                helper.assertValueEqual(result.getDamageValue(), expectedDamage, "Actual native combination pickup keeps the calculated repair");
                levels -= fee;
                helper.assertValueEqual(f.owner.experienceLevel, levels, "The native combination level fee is charged exactly once");
                helper.assertValueEqual(f.owner.totalExperience, ExperienceRules.experienceRequiredForLevel(levels),
                        "Native combination preserves its exact zero-fraction level payment arithmetic");
                helper.assertTrue(f.menu.getSlot(0).getItem().isEmpty() && f.menu.getSlot(1).getItem().isEmpty(),
                        "Each native combination consumes both equipment inputs exactly once");
                int paidTotal = f.owner.totalExperience;
                take.run(f.menu, f.owner);
                helper.assertValueEqual(f.owner.totalExperience, paidTotal, "An exhausted native combination cannot charge twice");
                input = result;
                f.clearTransferred();
            }
        }
        helper.succeed();
    }

    private enum Take {
        PICKUP, SHIFT_CLICK, DIRECT_QUICK_MOVE;
        void run(AnvilMenu menu, Player owner) {
            if (this == DIRECT_QUICK_MOVE) menu.quickMoveStack(owner, 2);
            else menu.clicked(2, 0, this == PICKUP ? ClickType.PICKUP : ClickType.QUICK_MOVE, owner);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final Player owner;
        final BlockPos pos;
        final AnvilMenu menu;
        final BlockState original;
        final BlockState below;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            pos = helper.absolutePos(new BlockPos(1, 1, 1));
            original = helper.getLevel().getBlockState(pos);
            below = helper.getLevel().getBlockState(pos.below());
            helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(pos, Blocks.ANVIL.defaultBlockState(), 3);
            owner = helper.makeMockPlayer(GameType.SURVIVAL);
            owner.moveTo(pos.getCenter());
            PlayerExperience.setTotal(owner, ExperienceRules.experienceRequiredForLevel(300));
            menu = new AnvilMenu(71, owner.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        }
        void prepare(ItemStack first, ItemStack second) {
            menu.getSlot(1).set(ItemStack.EMPTY);
            menu.setItemName("");
            menu.getSlot(0).set(first);
            menu.getSlot(1).set(second);
        }
        ItemStack take(Take take, Item item) {
            take.run(menu, owner);
            if (!menu.getCarried().isEmpty()) return menu.getCarried().copy();
            for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
                ItemStack stack = owner.getInventory().getItem(slot);
                if (stack.is(item)) return stack.copy();
            }
            return ItemStack.EMPTY;
        }
        void clearTransferred() { menu.setCarried(ItemStack.EMPTY); owner.getInventory().clearContent(); }
        @Override public void close() {
            clearTransferred();
            menu.getSlot(0).set(ItemStack.EMPTY);
            menu.getSlot(1).set(ItemStack.EMPTY);
            helper.getLevel().setBlock(pos, original, 3);
            helper.getLevel().setBlock(pos.below(), below, 3);
        }
    }
}

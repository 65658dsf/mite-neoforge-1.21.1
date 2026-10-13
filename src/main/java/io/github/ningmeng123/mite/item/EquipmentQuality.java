package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Quality identity and preservation across modern maintenance menus. @author NingMeng123 */
public final class EquipmentQuality {
    private EquipmentQuality() {}

    public static boolean sameQuality(ItemStack first, ItemStack second) {
        return !EquipmentDurability.isManaged(first) || !EquipmentDurability.isManaged(second)
                || EquipmentDurability.quality(first) == EquipmentDurability.quality(second);
    }

    public static boolean sameColor(ItemStack first, ItemStack second) {
        var firstColor = first.get(DataComponents.DYED_COLOR);
        var secondColor = second.get(DataComponents.DYED_COLOR);
        return firstColor == null ? secondColor == null
                : secondColor != null && firstColor.rgb() == secondColor.rgb();
    }

    public static boolean isManagedCombine(ItemStack first, ItemStack second) {
        return !first.isEmpty() && !second.isEmpty() && first.is(second.getItem())
                && EquipmentDurability.isManaged(first);
    }

    /** The source stores an unscaled base; never copy its getMaxDamage() back into the component. */
    public static void preserve(ItemStack source, ItemStack result) {
        if (source.isEmpty() || result.isEmpty() || !EquipmentDurability.isManaged(source)
                || !EquipmentDurability.isManaged(result)) return;
        EquipmentDurability.setQuality(result, EquipmentDurability.quality(source));
        int base = source.is(result.getItem()) ? source.getOrDefault(DataComponents.MAX_DAMAGE, 1)
                : new ItemStack(result.getItem()).getOrDefault(DataComponents.MAX_DAMAGE, 1);
        result.set(DataComponents.MAX_DAMAGE, base);
    }

    public static void combineWithoutBonus(ItemStack first, ItemStack second, ItemStack result) {
        preserve(first, result);
        result.setDamageValue(EquipmentDurabilityRules.combinedRepairDamage(first.getDamageValue(),
                second.getDamageValue(), first.getMaxDamage()));
    }

    public static boolean maintenanceAllowed(Player player, AbstractContainerMenu menu) {
        if (!(menu instanceof AnvilMenu) && !(menu instanceof GrindstoneMenu)) return true;
        ItemStack first = menu.getSlot(0).getItem();
        ItemStack second = menu.getSlot(1).getItem();
        ItemStack result = menu.getSlot(2).getItem();
        if (first.isEmpty() || !EquipmentDurability.isManaged(first)) return true;
        if (isManagedCombine(first, second)) {
            if (!sameQuality(first, second) || !sameColor(first, second)) return false;
        }
        // Renaming, applying an enchanted book and single-item disenchanting do not repair wear.
        return result.isEmpty() || !result.is(first.getItem()) || result.getDamageValue() >= first.getDamageValue()
                || CraftingQuality.canRepair(player, first);
    }

    public static void refreshMaintenance(AbstractContainerMenu menu) {
        if (menu instanceof AnvilMenu anvil) anvil.createResult();
        else if (menu instanceof GrindstoneMenu) menu.slotsChanged(menu.getSlot(0).container);
    }

    /** R196 ye.applyRandomItemStackDamageForMob: only unenchanted generated wooden/leather gear downgrades. */
    public static void applyGeneratedMobQuality(ItemStack stack) {
        if (stack.isEmpty() || !EquipmentDurability.isManaged(stack) || stack.isEnchanted()
                || stack.has(EquipmentDurability.QUALITY.get())) return;
        var tool = EquipmentDurability.toolProfile(stack);
        boolean wood = stack.is(Items.BOW) || tool != null && tool.materialDurability() == 0.5F;
        boolean leather = stack.is(Items.LEATHER_HELMET) || stack.is(Items.LEATHER_CHESTPLATE)
                || stack.is(Items.LEATHER_LEGGINGS) || stack.is(Items.LEATHER_BOOTS);
        Quality quality = EquipmentDurability.quality(stack);
        if ((wood || leather) && quality.ordinal() >= Quality.AVERAGE.ordinal())
            EquipmentDurability.setQuality(stack, Quality.fromOrdinal(quality.ordinal() - 1));
    }

    /** R196 og:838 excludes badly worn, non-picked-up poor held equipment from 75 percent of drops. */
    public static boolean hasPoorHeldDropPenalty(ItemStack stack, EquipmentSlot slot, float dropChance) {
        return slot == EquipmentSlot.MAINHAND && dropChance > 0F && dropChance <= 1F
                && !stack.isEmpty() && EquipmentDurability.isManaged(stack) && !stack.isEnchanted()
                && stack.isDamaged() && EquipmentDurability.quality(stack).ordinal() < Quality.AVERAGE.ordinal()
                && (float)stack.getDamageValue() / (float)stack.getMaxDamage() > 0.7F;
    }
}

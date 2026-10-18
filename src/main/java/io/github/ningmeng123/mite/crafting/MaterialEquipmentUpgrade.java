package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.item.EquipmentQuality;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Personal equipment data survives a material change; intrinsic properties come from the target. @author NingMeng123 */
public final class MaterialEquipmentUpgrade {
    private MaterialEquipmentUpgrade() {}

    public static ItemStack assemble(ItemStack source, Item target) {
        ItemStack result = new ItemStack(target);
        if (source.isEmpty() || result.isEmpty()) return ItemStack.EMPTY;
        int previousMaximum = source.getMaxDamage();
        int previousDamage = source.getDamageValue();
        int targetBaseMaximum = result.getOrDefault(DataComponents.MAX_DAMAGE, 0);

        result.applyComponents(source.getComponentsPatch().forget(type -> type == DataComponents.MAX_DAMAGE
                || type == DataComponents.DAMAGE || type == DataComponents.MAX_STACK_SIZE
                || type == DataComponents.ATTRIBUTE_MODIFIERS || type == DataComponents.TOOL
                || type == DataComponents.FIRE_RESISTANT));
        EquipmentQuality.preserve(source, result);
        if (targetBaseMaximum > 0) {
            result.set(DataComponents.MAX_DAMAGE, targetBaseMaximum);
            if (previousMaximum > 0) {
                int newMaximum = result.getMaxDamage();
                int damage = (int)Math.clamp(Math.round((double)previousDamage * newMaximum / previousMaximum),
                        0L, Math.max(newMaximum - 1L, 0L));
                result.setDamageValue(damage);
            }
        }
        return result;
    }
}

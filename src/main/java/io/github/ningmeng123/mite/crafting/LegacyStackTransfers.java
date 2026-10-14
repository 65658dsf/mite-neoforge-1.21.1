package io.github.ningmeng123.mite.crafting;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Retain a known legacy source only during its own bounded decrement or failed-transfer rollback. @author NingMeng123 */
public final class LegacyStackTransfers {
    private LegacyStackTransfers() {}

    public static boolean preserve(Container container, int slot, ItemStack replacement, ItemStack original) {
        if (original == null || original.isEmpty() || original.getCount() <= original.getMaxStackSize()
                || replacement.isEmpty() || replacement.getCount() <= replacement.getMaxStackSize()
                || replacement.getCount() > original.getCount()
                || !ItemStack.isSameItemSameComponents(original, replacement)) return false;
        ItemStack current = container.getItem(slot);
        if (!ItemStack.isSameItemSameComponents(current, original)) return false;
        int before = original.getCount();
        int now = current.getCount();
        int desired = replacement.getCount();
        // The one permitted increase undoes exactly the native one-item extraction that failed.
        if (now != before && now != desired && !(desired == before && now == before - 1)) return false;
        current.setCount(desired);
        container.setChanged();
        return true;
    }
}

package io.github.ningmeng123.mite.furnace;

import java.util.function.Predicate;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** GUI insertion delegates to the same rules as sided automation. @author NingMeng123 */
public final class ThermalSlot extends Slot {
    private final Predicate<ItemStack> accepts;
    public ThermalSlot(Container container, int slot, int x, int y, Predicate<ItemStack> accepts) {
        super(container, slot, x, y); this.accepts = accepts;
    }
    @Override public boolean mayPlace(ItemStack stack) { return accepts.test(stack); }
}

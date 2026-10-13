package io.github.ningmeng123.mite.furnace;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.world.inventory.DataSlot;

/** Ordinary container packets carry the authoritative heat state. @author NingMeng123 */
public final class ThermalDataSlot extends DataSlot {
    private final IntSupplier getter;
    private final IntConsumer setter;
    public ThermalDataSlot(IntSupplier getter, IntConsumer setter) { this.getter = getter; this.setter = setter; }
    @Override public int get() { return getter.getAsInt(); }
    @Override public void set(int value) { setter.accept(value); }
}

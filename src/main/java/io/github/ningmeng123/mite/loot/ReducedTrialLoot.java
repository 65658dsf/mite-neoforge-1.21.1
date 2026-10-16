package io.github.ningmeng123.mite.loot;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import net.minecraft.world.item.ItemStack;

/** Marks a completed reward roll that actually lost resources to trial balancing. @author NingMeng123 */
public final class ReducedTrialLoot extends ObjectArrayList<ItemStack> {
    public ReducedTrialLoot(Collection<? extends ItemStack> loot) {
        super(loot);
    }
}

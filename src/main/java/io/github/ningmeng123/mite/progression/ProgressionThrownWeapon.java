package io.github.ningmeng123.mite.progression;

import net.minecraft.world.item.ItemStack;

/** A thrown weapon exposes its recoverable stack rather than its visual copy. @author NingMeng123 */
public interface ProgressionThrownWeapon {
    ItemStack mite$progressionWeapon();
    void mite$syncProgressionWeapon();
}

package io.github.ningmeng123.mite.ranged;

import net.minecraft.world.InteractionHand;

/** Tracking metadata makes two-handed cast models agree with the authoritative hook. @author NingMeng123 */
public interface FishingHookView {
    InteractionHand mite$castHand();
    String mite$hookMaterial();
}

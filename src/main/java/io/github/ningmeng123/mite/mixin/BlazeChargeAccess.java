package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.entity.monster.Blaze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Synchronizes the native blaze fire/charge flag for its source attack goal. @author NingMeng123 */
@Mixin(Blaze.class)
public interface BlazeChargeAccess {
    @Invoker("setCharged") void mite$setCharged(boolean charged);
}

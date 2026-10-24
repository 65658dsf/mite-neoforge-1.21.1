package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** A successful native warning reaches summoning strength while preserving admission and shared cooldown. @author NingMeng123 */
@Mixin(WardenSpawnTracker.class)
public abstract class WardenSpawnTrackerMixin {
    @ModifyArg(method = "increaseWarningLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/monster/warden/WardenSpawnTracker;setWarningLevel(I)V"), index = 0)
    private int mite$firstWarningSummons(int original) { return WardenSpawnTracker.MAX_WARNING_LEVEL; }
}

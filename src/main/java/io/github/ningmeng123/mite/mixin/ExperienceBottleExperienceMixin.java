package io.github.ningmeng123.mite.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;

/** ut.onImpact releases the same two-hundred points paid by the source table conversion. @author NingMeng123 */
@Mixin(ThrownExperienceBottle.class)
public abstract class ExperienceBottleExperienceMixin {
    @ModifyArg(method = "onHit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"),
            index = 2, require = 1, allow = 1)
    private int mite$sourceStoredExperience(int original) {
        // Retain the original projectile impact event, splash, native orb split/merge and discard flow.
        return 200;
    }
}

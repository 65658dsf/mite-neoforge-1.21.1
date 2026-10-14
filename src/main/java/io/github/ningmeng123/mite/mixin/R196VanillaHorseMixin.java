package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.rules.R196VanillaMobRules;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native horse randomization already equals rs.cP/cQ/cR; source attribute updates also adjust current health. @author NingMeng123 */
@Mixin(AbstractHorse.class)
public abstract class R196VanillaHorseMixin {
    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void mite$healthFollowsSourceRandomMaximum(CallbackInfoReturnable<SpawnGroupData> cir) {
        AbstractHorse horse = (AbstractHorse)(Object)this;
        if (R196VanillaMobRules.managed(horse.getType())) horse.setHealth(horse.getMaxHealth());
    }
}

package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.entity.R196VanillaMobEvents;
import io.github.ningmeng123.mite.rules.R196VanillaMobRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Source XP is independent of equipment and modern zombie baby multipliers; modern XP enchantments remain in the caller. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class R196VanillaLivingMixin {
    @WrapOperation(method = "getExperienceReward", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getBaseExperienceReward()I"))
    private int mite$sourceBaseExperience(LivingEntity entity, Operation<Integer> original) {
        return entity instanceof Mob mob && R196VanillaMobRules.managed(entity.getType()) ? R196VanillaMobRules.experience(mob) : original.call(entity);
    }
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void mite$sourceNaturalRecovery(CallbackInfo ci) {
        if ((Object)this instanceof Mob mob) R196VanillaMobEvents.naturalHealing(mob);
    }
}

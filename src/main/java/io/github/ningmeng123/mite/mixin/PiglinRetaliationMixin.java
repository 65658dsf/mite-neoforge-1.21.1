package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.entity.PiglinRetaliation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserves retaliation and help calls without exempting ordinary hoglin hunts. @author NingMeng123 */
@Mixin(PiglinAi.class)
public abstract class PiglinRetaliationMixin {
    @WrapMethod(method = "wasHurtBy")
    private static void mite$hurtRetaliation(Piglin piglin, LivingEntity attacker, Operation<Void> original) {
        PiglinRetaliation.begin();
        try { original.call(piglin, attacker); }
        finally { PiglinRetaliation.end(); }
    }

    @WrapMethod(method = "maybeRetaliate")
    private static void mite$groupRetaliation(AbstractPiglin piglin, LivingEntity attacker, Operation<Void> original) {
        PiglinRetaliation.begin();
        try { original.call(piglin, attacker); }
        finally { PiglinRetaliation.end(); }
    }

    @Inject(method = "setAngerTarget", at = @At("RETURN"))
    private static void mite$rememberRetaliation(AbstractPiglin piglin, LivingEntity target, CallbackInfo ci) {
        PiglinRetaliation.angerTargetSet(piglin, target);
    }
}

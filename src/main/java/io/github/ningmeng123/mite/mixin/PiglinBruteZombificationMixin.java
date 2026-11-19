package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps piglin brutes from zombifying while in the Overworld. @author NingMeng123 */
@Mixin(AbstractPiglin.class)
public abstract class PiglinBruteZombificationMixin {
    @ModifyReturnValue(method = "isConverting", at = @At("RETURN"))
    private boolean mite$preventOverworldBruteZombification(boolean original) {
        AbstractPiglin piglin = (AbstractPiglin)(Object)this;
        return original && (piglin.getType() != EntityType.PIGLIN_BRUTE || !piglin.level().dimension().equals(Level.OVERWORLD));
    }
}

package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.entity.MobCombat;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep native protection/effects and self-immunity without adding 85% resistance to source natural defense. @author NingMeng123 */
@Mixin(Witch.class)
public abstract class SourceWitchDefenseMixin {
    @WrapOperation(method = "getDamageAfterMagicAbsorb", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean mite$avoidDuplicateInnateResistance(DamageSource source, TagKey<DamageType> tag, Operation<Boolean> original) {
        if (tag.equals(DamageTypeTags.WITCH_RESISTANT_TO) && MobCombat.defending((Witch)(Object)this)) return false;
        return original.call(source, tag);
    }
}

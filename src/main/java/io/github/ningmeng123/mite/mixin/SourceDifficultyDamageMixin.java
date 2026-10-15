package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.world.SourceDifficultyDamage;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.damagesource.IScalingFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep the modern hurt/event pipeline while replacing only its default difficulty transform. @author NingMeng123 */
@Mixin(Player.class)
public abstract class SourceDifficultyDamageMixin {
    @WrapOperation(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/damagesource/IScalingFunction;scaleDamage(Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/entity/player/Player;FLnet/minecraft/world/Difficulty;)F"))
    private float mite$sourceIncomingDamage(IScalingFunction function, DamageSource source, Player player,
            float amount, Difficulty difficulty, Operation<Float> original) {
        return SourceDifficultyDamage.usesStandardScaling(function) ? SourceDifficultyDamage.incoming(source, amount)
                : original.call(function, source, player, amount, difficulty);
    }
}

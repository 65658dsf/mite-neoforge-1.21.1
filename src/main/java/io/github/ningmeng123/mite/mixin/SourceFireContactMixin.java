package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** nn.d(8) adds ten ticks to ordinary fire ignition before burning-time and protection modifiers. @author NingMeng123 */
@Mixin(BaseFireBlock.class)
public abstract class SourceFireContactMixin {
    @WrapOperation(method = "entityInside", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean mite$sourceContactDamage(Entity entity, DamageSource source, float amount, Operation<Boolean> original,
            BlockState state, Level level, BlockPos pos, Entity touching) {
        if (state.is(Blocks.FIRE) && !(entity instanceof LivingEntity) && entity.tickCount % 10 != 0) return false;
        return original.call(entity, source, amount);
    }

    @WrapOperation(method = "entityInside", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;igniteForSeconds(F)V"))
    private void mite$sourceContactDuration(Entity entity, float seconds, Operation<Void> original,
            BlockState state, Level level, BlockPos pos, Entity touching) {
        original.call(entity, state.is(Blocks.FIRE) ? seconds + .5F : seconds);
    }
}

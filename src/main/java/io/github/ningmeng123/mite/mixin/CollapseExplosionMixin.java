package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.world.CollapseSecondaryTriggers;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Wait for each actual block explosion transaction, including custom furnaces and protected blocks. @author NingMeng123 */
@Mixin(Explosion.class)
public abstract class CollapseExplosionMixin {
    @WrapOperation(method = "finalizeExplosion", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;onExplosionHit(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;Ljava/util/function/BiConsumer;)V"))
    private void mite$undermineExplodedSupport(BlockState state, Level level, BlockPos pos,
            Explosion explosion, BiConsumer<ItemStack, BlockPos> drops, Operation<Void> original) {
        original.call(state, level, pos, explosion, drops);
        CollapseSecondaryTriggers.exploded(level, pos, state, explosion);
    }
}

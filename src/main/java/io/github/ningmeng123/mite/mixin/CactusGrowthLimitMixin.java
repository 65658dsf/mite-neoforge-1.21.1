package io.github.ningmeng123.mite.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R196 also caps hand-placed cactus columns at three blocks. @author NingMeng123 */
@Mixin(CactusBlock.class)
public abstract class CactusGrowthLimitMixin {
    public boolean canDropFromExplosion(BlockState state,BlockGetter level,BlockPos pos,Explosion explosion) { return false; }
    @Inject(method="canSurvive",at=@At("RETURN"),cancellable=true)
    private void mite$legalHeight(BlockState state,LevelReader level,BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        int height=1;
        while (height<=3 && level.getBlockState(pos.below(height)).is(Blocks.CACTUS)) height++;
        if (height>3) cir.setReturnValue(false);
    }
}

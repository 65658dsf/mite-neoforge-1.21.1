package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.ai.ConspicuousCactus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replacing support sand or the base cactus resets the source trap counter for all removal paths. @author NingMeng123 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class CactusTrapRemovalMixin {
    @Inject(method="onRemove",at=@At("HEAD"))
    private void mite$releaseSandTrap(Level level,BlockPos pos,BlockState replacement,boolean moving,CallbackInfo ci) {
        BlockState previous=(BlockState)(Object)this;
        if(level instanceof ServerLevel server&&!previous.is(replacement.getBlock())) ConspicuousCactus.removed(server,pos,previous);
    }
}

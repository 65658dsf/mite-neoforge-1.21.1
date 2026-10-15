package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.survival.CropSoil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace native soil timing without replacing the native block or its saved properties. @author NingMeng123 */
@Mixin(FarmBlock.class)
public abstract class CropSoilMixin extends Block {
    protected CropSoilMixin(BlockBehaviour.Properties properties) { super(properties); }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        super.onPlace(state, level, pos, previous, moved);
        CropSoil.hydrateOnPlace(level, pos, state);
    }

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void mite$sourceSoilTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        CropSoil.tick(level, pos, state, random);
        ci.cancel();
    }

    @WrapOperation(method = "fallOn", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;onFarmlandTrample(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;FLnet/minecraft/world/entity/Entity;)Z"))
    private boolean mite$sourceTrampling(Level level, BlockPos pos, BlockState dirt, float fallDistance, Entity entity,
                                        Operation<Boolean> original) {
        return CropSoil.trample(level, pos, fallDistance, entity);
    }
}

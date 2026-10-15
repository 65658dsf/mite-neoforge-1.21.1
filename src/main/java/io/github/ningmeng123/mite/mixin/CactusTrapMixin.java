package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.entity.ai.ConspicuousCactus;
import io.github.ningmeng123.mite.survival.SpecialPlantGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only successful real cactus damage updates source trap evidence; canceled damage never does. @author NingMeng123 */
@Mixin(CactusBlock.class)
public abstract class CactusTrapMixin {
    @WrapOperation(method="entityInside",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean mite$rememberActualContact(Entity victim,DamageSource source,float amount,Operation<Boolean> original,
            BlockState state,Level level,BlockPos pos,Entity unused) {
        boolean hurt=original.call(victim,source,amount);
        if(hurt&&level instanceof ServerLevel server) ConspicuousCactus.afterActualContact(server,pos,victim);
        return hurt;
    }
    @Inject(method="randomTick",at=@At("HEAD"),cancellable=true)
    private void mite$trapDecay(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci) {
        SpecialPlantGrowth.cactusTick(level,pos,state,random);
        ci.cancel();
    }
}

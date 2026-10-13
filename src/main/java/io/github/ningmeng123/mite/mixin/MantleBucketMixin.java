package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.MantleHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies to vanilla and the inherited MITE metal bucket path. @author NingMeng123 */
@Mixin(BucketItem.class)
public abstract class MantleBucketMixin {
    @Shadow @Final private Fluid content;
    @Inject(method = "emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void mite$evaporate(Player player, Level level, BlockPos pos, BlockHitResult hit, ItemStack container, CallbackInfoReturnable<Boolean> cir) {
        if (content == Fluids.WATER && level.getBlockState(pos).canBeReplaced(Fluids.WATER)
                && MantleHeat.evaporatesWater(level, pos)) {
            if (!level.isClientSide) level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, .5F, 2.6F);
            cir.setReturnValue(true);
        }
    }
}

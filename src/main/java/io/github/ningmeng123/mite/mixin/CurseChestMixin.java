package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Source ank protects wooden, trapped and metal chests while other workstations remain usable. @author NingMeng123 */
@Mixin(ChestBlock.class)
public abstract class CurseChestMixin {
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void mite$cannotOpenChest(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (!PlayerCurses.has(player, R196Curse.CANNOT_OPEN_CHESTS, true)) return;
        if (!level.isClientSide) level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, .2F, 1F);
        cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
    }
}

package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.TrialBlockDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cover native direct block rewards without filtering unrelated entities in the chamber. @author NingMeng123 */
@Mixin(Block.class)
public abstract class TrialDirectBlockDropsMixin {
    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void mite$naturalTrialDirectItem(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
        if (TrialBlockDrops.suppresses(level, pos)) ci.cancel();
    }

    @Inject(method = "popResourceFromFace", at = @At("HEAD"), cancellable = true)
    private static void mite$naturalTrialFaceItem(Level level, BlockPos pos, Direction face, ItemStack stack, CallbackInfo ci) {
        if (TrialBlockDrops.suppresses(level, pos)) ci.cancel();
    }

    @Inject(method = "popExperience", at = @At("HEAD"), cancellable = true)
    private void mite$naturalTrialExperience(ServerLevel level, BlockPos pos, int experience, CallbackInfo ci) {
        if (TrialBlockDrops.suppresses(level, pos)) ci.cancel();
    }
}

package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Falling anvils keep exact wear and do not gain vanilla random damage stages. @author NingMeng123 */
@Mixin(FallingBlockEntity.class)
public abstract class FunctionalFallingBlockMixin {
    @Shadow public CompoundTag blockData;
    @Unique private static final ThreadLocal<CompoundTag> mite$fallingAnvilData = new ThreadLocal<>();
    @Inject(method = "fall", at = @At("HEAD"))
    private static void mite$saveAnvilBeforeRemoval(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<FallingBlockEntity> cir) {
        mite$fallingAnvilData.remove();
        if (level.getBlockEntity(pos) instanceof MaterialAnvilBlockEntity entity)
            mite$fallingAnvilData.set(entity.saveWithoutMetadata(level.registryAccess()));
    }
    @Inject(method = "fall", at = @At("RETURN"))
    private static void mite$transferAnvilWear(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<FallingBlockEntity> cir) {
        CompoundTag tag = mite$fallingAnvilData.get();
        if (tag != null) cir.getReturnValue().blockData = tag;
        mite$fallingAnvilData.remove();
    }
    @Redirect(method = "causeFallDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/AnvilBlock;damage(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState mite$noRandomWearFromFall(BlockState state) { return state.getBlock() instanceof AnvilBlock ? state : AnvilBlock.damage(state); }
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;spawnAtLocation(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity mite$dropAnvilWithWear(FallingBlockEntity entity, ItemLike item) {
        if (entity.getBlockState().getBlock() instanceof AnvilBlock && blockData != null) {
            ItemStack stack = new ItemStack(item);
            stack.set(MiteFunctionalBlocks.ANVIL_DAMAGE.get(), Math.max(blockData.getInt("mite_damage"), 0));
            return entity.spawnAtLocation(stack);
        }
        return entity.spawnAtLocation(item);
    }
}

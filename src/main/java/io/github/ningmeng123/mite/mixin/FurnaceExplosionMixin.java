package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.block.FurnaceExplosionDrops;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The retained vanilla furnace uses R196's cobblestone explosion model. @author NingMeng123 */
@Mixin(BlockBehaviour.class)
public abstract class FurnaceExplosionMixin {
    @Inject(method = "onExplosionHit", at = @At("HEAD"), cancellable = true)
    private void mite$explode(BlockState state, Level level, BlockPos pos, Explosion explosion,
            BiConsumer<ItemStack, BlockPos> drops, CallbackInfo ci) {
        if (state.is(Blocks.FURNACE)) { FurnaceExplosionDrops.explode(state, level, pos, explosion, drops); ci.cancel(); }
    }
}

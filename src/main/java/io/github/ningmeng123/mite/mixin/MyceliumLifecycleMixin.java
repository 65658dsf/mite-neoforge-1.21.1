package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.FungalGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.MyceliumBlock;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Source mycelium grows in shaded interiors, propagates slowly and is harmed by daylight. @author NingMeng123 */
@Mixin(MyceliumBlock.class)
public abstract class MyceliumLifecycleMixin extends SpreadingSnowyDirtBlock {
    protected MyceliumLifecycleMixin(BlockBehaviour.Properties properties) { super(properties); }
    @Override protected void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) { FungalGrowth.myceliumTick(level,pos,random); }
}

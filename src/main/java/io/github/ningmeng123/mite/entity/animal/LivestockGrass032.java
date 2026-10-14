package io.github.ningmeng123.mite.entity.animal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.neoforge.event.EventHooks;

/** Grass trampling metadata persists in a modern state property, keeping source sheep grazing and animal pressure. @author NingMeng123 */
public final class LivestockGrass032 {
    public static final IntegerProperty TRAMPLINGS=IntegerProperty.create("mite_tramplings",0,15);
    private LivestockGrass032() {}
    public static int tramplings(BlockState state) { return state.hasProperty(TRAMPLINGS)?state.getValue(TRAMPLINGS):0; }
    public static void trampleUnder(Mob mob) {
        if(!(mob.level() instanceof ServerLevel level) || !EventHooks.canEntityGrief(level,mob)) return;
        var memory=(Livestock032View)mob; BlockPos pos=mob.blockPosition().below();
        if(mob.tickCount%1000==0) memory.mite$setLastTrampled(BlockPos.ZERO);
        if(memory.mite$lastTrampled().equals(pos)) return;
        memory.mite$setLastTrampled(pos);
        BlockState ground=level.getBlockState(pos);
        if(!ground.is(Blocks.GRASS_BLOCK) || !ground.hasProperty(TRAMPLINGS)) return;
        int count=Math.min(15,tramplings(ground)+1); level.setBlock(pos,ground.setValue(TRAMPLINGS,count),2);
        float effect=LivestockRules032.tramplingEffect(count)*(mob instanceof Cow?2F:1F);
        if(effect<.2F || mob.getRandom().nextFloat()>=effect*2F) return;
        var above=level.getBlockState(pos.above());
        if(above.getBlock() instanceof net.minecraft.world.level.block.BushBlock)
            level.destroyBlock(pos.above(),!(mob instanceof Cow) && !(mob instanceof net.minecraft.world.entity.animal.Pig)
                    && !(mob instanceof net.minecraft.world.entity.animal.Sheep) && !Livestock032.shortVegetation(above) && !(above.getBlock() instanceof CropBlock),mob);
    }
    /** R196 grass random ticks reduce one neighbouring trampling count when bright, while modern grass spreading remains. */
    public static void recover(ServerLevel level,BlockPos center,RandomSource random) {
        if(level.getMaxLocalRawBrightness(center.above())<13) return;
        for(int attempt=0;attempt<4;attempt++) {
            BlockPos pos=center.offset(random.nextInt(3)-1,random.nextInt(5)-3,random.nextInt(3)-1);
            if(!level.hasChunkAt(pos)) continue;
            var state=level.getBlockState(pos);
            if(!state.is(Blocks.GRASS_BLOCK) || tramplings(state)<=0 || level.getMaxLocalRawBrightness(pos.above())<4
                    || level.getBlockState(pos.above()).getLightBlock(level,pos.above())>2) continue;
            level.setBlock(pos,state.setValue(TRAMPLINGS,tramplings(state)-1),2); return;
        }
    }
}

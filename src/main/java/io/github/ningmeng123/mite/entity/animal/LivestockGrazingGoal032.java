package io.github.ningmeng123.mite.entity.animal;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.event.EventHooks;
import io.github.ningmeng123.mite.entity.CubeCropBlight;

/** pn: sheep eat for forty ticks; only an actual bite restores wool and food. @author NingMeng123 */
public final class LivestockGrazingGoal032 extends Goal {
    private final Sheep sheep;
    private int ticks;
    public LivestockGrazingGoal032(Sheep sheep) { this.sheep=sheep; setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.JUMP)); }
    @Override public boolean canUse() {
        if(sheep.hurtTime>0 || R196AnimalAi032.state(sheep).getBoolean("decided_to_flee") || sheep.getRandom().nextInt(sheep.isBaby()?50:1000)!=0) return false;
        var pos=sheep.blockPosition(); var state=sheep.level().getBlockState(pos);
        if(Livestock032.shortVegetation(state) || state.getBlock() instanceof CropBlock && !CubeCropBlight.dead(state)) return true;
        var below=sheep.level().getBlockState(pos.below());
        if(below.is(Blocks.GRASS_BLOCK) && LivestockGrass032.tramplings(below)<=3) return true;
        return R196AnimalAi032.predatorsNear(sheep,sheep.getBoundingBox().inflate(16,2,16)).isEmpty();
    }
    @Override public boolean canContinueToUse() { return ticks>0 && sheep.hurtTime<=0 && !R196AnimalAi032.state(sheep).getBoolean("decided_to_flee"); }
    @Override public void start() { ticks=40; sheep.level().broadcastEntityEvent(sheep,(byte)10); sheep.getNavigation().stop(); }
    @Override public void stop() { ticks=0; }
    public int eatingTicks() { return ticks; }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() {
        ticks=Math.max(0,ticks-1);
        if(ticks!=4 || !EventHooks.canEntityGrief(sheep.level(),sheep)) return;
        var pos=sheep.blockPosition(); var state=sheep.level().getBlockState(pos);
        if(Livestock032.shortVegetation(state) || state.getBlock() instanceof CropBlock) {
            sheep.level().destroyBlock(pos,false,sheep); sheep.ate();
        } else if(sheep.level().getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) {
            sheep.level().levelEvent(2001,pos.below(),Block.getId(Blocks.GRASS_BLOCK.defaultBlockState()));
            sheep.level().setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3); sheep.ate();
        }
    }
}

package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.block.OnionCropBlock;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** Shared crop disease lifecycle; cubes and source blood-moon exposure use the same saved block state. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class CubeCropBlight {
    // Native BooleanProperty enumerates true first; CropBlock.any() must instead yield healthy new crops.
    private static final class HealthyDefaultProperty extends BooleanProperty {
        HealthyDefaultProperty(String name) { super(name); }
        @Override public Collection<Boolean> getPossibleValues() { return List.of(false,true); }
    }
    public static final BooleanProperty BLIGHTED=new HealthyDefaultProperty("mite_blighted");
    public static final BooleanProperty DEAD=new HealthyDefaultProperty("mite_dead");
    private CubeCropBlight() {}
    public static boolean blighted(BlockState state) { return state.getBlock() instanceof OnionCropBlock ? state.getValue(OnionCropBlock.BLIGHTED) : state.hasProperty(BLIGHTED) && state.getValue(BLIGHTED); }
    public static boolean dead(BlockState state) { return state.getBlock() instanceof OnionCropBlock ? state.getValue(OnionCropBlock.DEAD) : state.hasProperty(DEAD) && state.getValue(DEAD); }
    public static boolean infect(ServerLevel level,BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState state=level.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock) || blighted(state) || dead(state)) return false;
        level.setBlockAndUpdate(pos,state.setValue(state.getBlock() instanceof OnionCropBlock ? OnionCropBlock.BLIGHTED : BLIGHTED,true));
        particles(level,pos);
        return true;
    }
    private static void particles(ServerLevel level,BlockPos pos) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.DEAD_BUSH.defaultBlockState()),pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,6,.25,.3,.25,.01);
    }
    public static boolean randomTick(ServerLevel level,BlockPos pos,BlockState state,RandomSource random) {
        if (!blighted(state) && !dead(state))
            return io.github.ningmeng123.mite.world.LunarCropEffects.infectOnRandomTick(level,pos,state,random);
        particles(level,pos);
        if (dead(state)) return true;
        if (random.nextInt(64)==0) {
            BlockState dead=io.github.ningmeng123.mite.survival.CropLifecycle.deadState(state,true);
            level.setBlockAndUpdate(pos,dead);
            return true;
        }
        if (!random.nextBoolean()) {
            int x=random.nextInt(3)-1,y=random.nextInt(3)-1,z=random.nextInt(3)-1;
            if (x!=0 || y!=0 || z!=0) infect(level,pos.offset(x,y,z));
        }
        return true;
    }
    public static void cure(ServerLevel level,BlockPos pos,BlockState state) {
        if (blighted(state) && !dead(state)) level.setBlockAndUpdate(pos,state.setValue(state.getBlock() instanceof OnionCropBlock ? OnionCropBlock.BLIGHTED : BLIGHTED,false));
    }
    @SubscribeEvent public static void drops(BlockDropsEvent event) {
        BlockState state=event.getState();
        // Managed crops already select source loot before global loot modifiers and drop events.
        if(io.github.ningmeng123.mite.survival.CropLifecycle.managed(state)
                ||io.github.ningmeng123.mite.survival.SpecialPlantGrowth.managedStem(state))return;
        if (blighted(state) || dead(state)) {
            event.getDrops().clear(); event.setDroppedExperience(0);
            // apt bypasses the mature yield and fortune path: one poisonous potato, unless the crop is dead.
            if (state.is(Blocks.POTATOES) && !dead(state) && event.getLevel().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
                BlockPos pos=event.getPos();
                event.getDrops().add(new ItemEntity(event.getLevel(),pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,new ItemStack(Items.POISONOUS_POTATO)));
            }
        }
    }
}

package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.rules.LunarCalendar;
import io.github.ningmeng123.mite.worldgen.R196OutdoorChecks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** R196 mushrooms, cultured mycelium and manure-dependent giant growth. @author NingMeng123 */
public final class FungalGrowth {
    public static final IntegerProperty GROWTH=IntegerProperty.create("mite_growth",0,3);
    private FungalGrowth() {}
    public static boolean mushroom(BlockState state) { return state.is(Blocks.BROWN_MUSHROOM)||state.is(Blocks.RED_MUSHROOM); }
    private static boolean passable(LevelReader level,BlockPos pos) {
        return level.getFluidState(pos).isEmpty() && !level.getBlockState(pos).blocksMotion();
    }
    public static boolean outdoors(LevelReader level,BlockPos pos) {
        if(level instanceof ServerLevel server) return R196OutdoorChecks.outdoors(server,pos);
        if(!level.dimensionType().hasSkyLight()) return false;
        for(int rise=0;rise<=2;rise++) {
            BlockPos origin=pos.above(rise);
            if(rise>0&&!passable(level,origin)) continue;
            boolean[][] reached=new boolean[7][7]; reached[3][3]=true;
            if(passable(level,origin)&&level.canSeeSky(origin)) return true;
            for(int distance=1;distance<=4;distance++) for(int x=-3;x<=3;x++) for(int z=-3;z<=3;z++) {
                if(Math.abs(x)+Math.abs(z)!=distance || distance>3&&(Math.abs(x)!=2||Math.abs(z)!=2)) continue;
                boolean reachable=x!=0&&reached[3+x-Integer.signum(x)][3+z] || z!=0&&reached[3+x][3+z-Integer.signum(z)];
                BlockPos point=origin.offset(x,0,z);
                if(!reachable||!passable(level,point)) continue;
                if(level.canSeeSky(point)) return true;
                reached[x+3][z+3]=true;
            }
        }
        return false;
    }
    public static boolean survives(LevelReader level,BlockPos pos,BlockState state) {
        BlockState soil=level.getBlockState(pos.below());
        if(level.dimensionType().hasSkyLight() && level.canSeeSky(pos)) return false;
        if(state.is(Blocks.RED_MUSHROOM)) return soil.is(Blocks.GRASS_BLOCK);
        if(!state.is(Blocks.BROWN_MUSHROOM)) return false;
        return level.getRawBrightness(pos,0)<=13 && (soil.is(Blocks.STONE)||soil.is(Blocks.GRAVEL)||soil.is(Blocks.DIRT)
                ||soil.getBlock() instanceof FarmBlock||soil.is(Blocks.MYCELIUM));
    }
    public static boolean placement(LevelReader level,BlockPos pos,BlockState state) {
        if(!survives(level,pos,state)) return false;
        if(state.is(Blocks.BROWN_MUSHROOM)) return !outdoors(level,pos);
        if(!outdoors(level,pos)) return false;
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
            BlockPos neighbor=pos.offset(x,0,z);
            if(passable(level,neighbor)&&level.canSeeSky(neighbor)) return false;
        }
        return true;
    }
    public static boolean canGiant(LevelReader level,BlockPos pos,BlockState state) {
        if(!survives(level,pos,state)) return false;
        return state.is(Blocks.RED_MUSHROOM)?level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)&&outdoors(level,pos)
                :state.is(Blocks.BROWN_MUSHROOM)&&level.getBlockState(pos.below()).is(Blocks.MYCELIUM)&&!outdoors(level,pos);
    }
    public static boolean culture(ServerLevel level,BlockPos mushroom) {
        if(!level.getBlockState(mushroom).is(Blocks.BROWN_MUSHROOM)) return false;
        BlockState soil=level.getBlockState(mushroom.below());
        if(!(soil.getBlock() instanceof FarmBlock)||soil.getValue(FarmBlock.MOISTURE)==0||!FoodAgriculture.fertilized(soil)
                ||outdoors(level,mushroom)||level.getRawBrightness(mushroom,0)>13) return false;
        level.setBlockAndUpdate(mushroom.below(),Blocks.MYCELIUM.defaultBlockState()); return true;
    }
    public static void mushroomTick(ServerLevel level,BlockPos pos,BlockState state,RandomSource random) {
        if(!level.isAreaLoaded(pos,4)) return;
        if(!survives(level,pos,state)) { level.destroyBlock(pos,true); return; }
        int draw=random.nextInt(100);
        if(draw==0) {
            int remaining=5;
            for(BlockPos nearby:BlockPos.betweenClosed(pos.offset(-4,-1,-4),pos.offset(4,1,4)))
                if(level.getBlockState(nearby).is(state.getBlock())&&--remaining<=0) return;
            BlockPos target=nearby(pos,random);
            for(int attempt=0;attempt<4;attempt++) {
                if(level.isEmptyBlock(target)&&survives(level,target,state)) pos=target;
                target=nearby(pos,random);
            }
            if(level.isEmptyBlock(target)&&survives(level,target,state)) {
                if(state.is(Blocks.BROWN_MUSHROOM)&&level.getBlockState(target.below()).is(Blocks.MYCELIUM)&&random.nextInt(4)>0) return;
                level.setBlock(target,state.setValue(GROWTH,0),2);
            }
        } else if((draw==1||draw==2)&&state.is(Blocks.BROWN_MUSHROOM))
            ((MushroomBlock)state.getBlock()).growMushroom(level,pos,state,random);
        else if(draw<10&&state.is(Blocks.BROWN_MUSHROOM)) culture(level,pos);
    }
    private static BlockPos nearby(BlockPos origin,RandomSource random) {
        return origin.offset(random.nextInt(3)-1,random.nextInt(2)-random.nextInt(2),random.nextInt(3)-1);
    }
    public static boolean myceliumLegal(LevelReader level,BlockPos pos) {
        BlockPos above=pos.above(); BlockState state=level.getBlockState(above);
        return state.getBlock() instanceof HugeMushroomBlock || !state.isFaceSturdy(level,above,Direction.DOWN);
    }
    public static void myceliumTick(ServerLevel level,BlockPos pos,RandomSource random) {
        if(!level.isAreaLoaded(pos,4)) return;
        if(!level.getFluidState(pos.above()).isEmpty() || !myceliumLegal(level,pos)) {
            level.setBlockAndUpdate(pos,Blocks.DIRT.defaultBlockState()); return;
        }
        if(random.nextInt(4)>0) return;
        boolean suitable=level.getMaxLocalRawBrightness(pos.above())<=13&&!outdoors(level,pos);
        if(!suitable) {
            boolean daytime=LunarCalendar.daytime((int)Math.floorMod(level.getDayTime(),24000L));
            boolean freezing=level.getBiome(pos).value().coldEnoughToSnow(pos);
            if(daytime&&(!level.isRaining()||freezing)) level.setBlockAndUpdate(pos,Blocks.DIRT.defaultBlockState());
            return;
        }
        if(random.nextInt(256)==0&&level.isEmptyBlock(pos.above())) {
            int count=0;
            for(BlockPos nearby:BlockPos.betweenClosed(pos.offset(-4,-2,-4),pos.offset(4,2,4)))
                if(level.getBlockState(nearby).getBlock() instanceof MushroomBlock&&++count>2) return;
            level.setBlockAndUpdate(pos.above(),Blocks.BROWN_MUSHROOM.defaultBlockState()); return;
        }
        BlockPos target=pos;
        for(int attempt=0;attempt<8;attempt++) {
            int x=random.nextInt(3)-1,y=random.nextInt(5)-2,z=random.nextInt(3)-1;
            target=pos.offset(x,y,z);
            BlockState state=level.getBlockState(target);
            if((y < -1 || y > 1 || x==0 || z==0) && cultureSoil(state)
                    &&level.getMaxLocalRawBrightness(target.above())<=13&&myceliumLegal(level,target)) break;
        }
        if(myceliumLegal(level,target)&&level.getFluidState(target.above()).isEmpty()&&cultureSoil(level.getBlockState(target))
                &&level.getMaxLocalRawBrightness(target.above())<=13&&!outdoors(level,target))
            level.setBlockAndUpdate(target,Blocks.MYCELIUM.defaultBlockState());
    }
    private static boolean cultureSoil(BlockState state) { return state.is(Blocks.DIRT)||state.is(Blocks.GRASS_BLOCK)||state.getBlock() instanceof FarmBlock; }
}

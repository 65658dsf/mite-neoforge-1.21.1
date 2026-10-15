package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.block.OnionCropBlock;
import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.rules.CropGrowthRules;
import io.github.ningmeng123.mite.world.LunarCropEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;

/** Source growth, drought and disease order for food crops; saved state keys remain compatible. @author NingMeng123 */
public final class CropLifecycle {
    private CropLifecycle() {}
    public static boolean managed(BlockState state) {
        return state.is(Blocks.WHEAT)||state.is(Blocks.CARROTS)||state.is(Blocks.POTATOES)
                ||state.is(Blocks.BEETROOTS)||state.getBlock() instanceof OnionCropBlock;
    }
    public static boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
        var soil=level.getBlockState(pos.below());
        var decision=soil.canSustainPlant(level,pos.below(),Direction.UP,state);
        if(!decision.isDefault()) return decision.isTrue();
        return soil.getBlock() instanceof FarmBlock && (level.canSeeSky(pos)||level.getRawBrightness(pos,0)>=8);
    }
    public static float growthRate(BlockState state,ServerLevel level,BlockPos pos) {
        var center=level.getBlockState(pos.below());
        if(center.hasProperty(FarmBlock.MOISTURE)&&center.getValue(FarmBlock.MOISTURE)==0) return 0F;
        float rate=1F;
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
            BlockPos below=pos.offset(x,-1,z);var soil=level.getBlockState(below);
            var decision=soil.canSustainPlant(level,below,Direction.UP,state);
            boolean supports=decision.isDefault()?soil.getBlock() instanceof FarmBlock:decision.isTrue();
            float contribution=0F;
            if(supports) {
                boolean wet=soil.hasProperty(FarmBlock.MOISTURE)?soil.getValue(FarmBlock.MOISTURE)>0:soil.isFertile(level,below);
                contribution=wet||FoodAgriculture.fertilized(soil)?3F:1F;
            }
            rate+=x!=0||z!=0?contribution/4F:contribution;
        }
        var block=state.getBlock();
        boolean ew=sameLiving(level.getBlockState(pos.east()),block)||sameLiving(level.getBlockState(pos.west()),block);
        boolean ns=sameLiving(level.getBlockState(pos.north()),block)||sameLiving(level.getBlockState(pos.south()),block);
        boolean diagonal=false;
        for(int x:new int[]{-1,1}) for(int z:new int[]{-1,1}) diagonal|=sameLiving(level.getBlockState(pos.offset(x,0,z)),block);
        if(diagonal||(ew&&ns))rate/=2F;
        if(FoodAgriculture.fertilized(center))rate*=1.5F;
        rate*=CropGrowthRules.temperatureModifier(level.getBiome(pos).value().getBaseTemperature(),.8F,1.2F);
        rate*=.25F;
        return rate*FoodAgriculture.proximity(level,pos,block);
    }
    private static boolean sameLiving(BlockState state,net.minecraft.world.level.block.Block block) {
        return state.is(block)&&!CubeCropBlight.dead(state);
    }
    public static boolean tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(!managed(state))return false;
        if(!level.isAreaLoaded(pos,2))return true;
        if(!state.canSurvive(level,pos)) {
            boolean nearby=level.getNearestPlayer(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,16,false)!=null;
            CropHarvest.withReason(CropHarvest.Reason.ILLEGAL,level,pos,()->level.destroyBlock(pos,nearby));
            return true;
        }
        if(CubeCropBlight.dead(state))return true;
        var crop=(CropBlock)state.getBlock();float growth=growthRate(state,level,pos);
        if(growth==0F&&!FoodAgriculture.hasWater(level,pos.below())) {
            if(random.nextFloat()<.05F) {
                if(crop.isMaxAge(state)) CropHarvest.withReason(CropHarvest.Reason.DROUGHT,level,pos,()->{
                    net.minecraft.world.level.block.Block.dropResources(state,level,pos);
                    level.removeBlock(pos,false);
                });
                else level.setBlock(pos,deadState(state,false),3);
            }
            return true;
        }
        if(CubeCropBlight.blighted(state)) { CubeCropBlight.randomTick(level,pos,state,random);return true; }
        if(LunarCropEffects.infectOnRandomTick(level,pos,state,random))return true;
        int light=level.getMaxLocalRawBrightness(pos.above());
        if(random.nextFloat()<CropGrowthRules.blightChance(level.getBiome(pos).value().getBaseTemperature(),FoodAgriculture.humid(level,pos),light,state.is(Blocks.POTATOES))) {
            CubeCropBlight.infect(level,pos);return true;
        }
        if(light!=15||growth==0F||crop.isMaxAge(state))return true;
        boolean grow=random.nextInt(CropGrowthRules.growthDenominator(growth))==0;
        if(CommonHooks.canCropGrow(level,pos,state,grow)&&level.setBlock(pos,crop.getStateForAge(crop.getAge(state)+1),2)) {
            FoodAgriculture.onCropGrowth(level,pos.below(),random);
            CommonHooks.fireCropGrowPost(level,pos,state);
        }
        return true;
    }
    public static BlockState deadState(BlockState state,boolean fromDisease) {
        var crop=(CropBlock)state.getBlock();int age=crop.getAge(state);
        if(fromDisease&&crop.isMaxAge(state))age=Math.max(0,age-1);
        boolean onion=state.getBlock() instanceof OnionCropBlock;
        return crop.getStateForAge(age).setValue(onion?OnionCropBlock.DEAD:CubeCropBlight.DEAD,true)
                .setValue(onion?OnionCropBlock.BLIGHTED:CubeCropBlight.BLIGHTED,fromDisease);
    }
    public static void trample(ServerLevel level,BlockPos pos,Entity entity) {
        CropHarvest.withReason(CropHarvest.Reason.TRAMPLE,level,pos,()->level.destroyBlock(pos,!(entity instanceof Cow),entity));
    }
}

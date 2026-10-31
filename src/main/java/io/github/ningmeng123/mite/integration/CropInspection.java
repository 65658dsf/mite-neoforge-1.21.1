package io.github.ningmeng123.mite.integration;

import io.github.ningmeng123.mite.block.BlueberryBushBlock;
import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.survival.CropLifecycle;
import io.github.ningmeng123.mite.survival.FoodAgriculture;
import io.github.ningmeng123.mite.survival.FungalGrowth;
import io.github.ningmeng123.mite.survival.SpecialPlantGrowth;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only crop inspection shared by optional viewers, with no linkage to their APIs. @author NingMeng123 */
public final class CropInspection {
    public enum Kind { FOOD, STEM, BERRY, SOIL, COCOA, WART, MUSHROOM, CANE, CACTUS }
    public enum Health { NONE, HEALTHY, BLIGHTED, DEAD }
    // TEMPERATURE is retained only so version-one payloads from an older server still decode.
    public enum Condition { NONE, GROWING, BLIGHTED, DEAD, DRY, LIGHT, TEMPERATURE, UNSUITABLE, FRUIT_PRESENT, WRONG_DIMENSION, GIANT_READY }
    public record Snapshot(Kind kind,Health health,int age,int maxAge,int moisture,boolean fertilized,
            Condition condition,int light,int irrigation,int height,int heightLimit) {
        public Snapshot {
            // Old diagnostics cannot establish a current growth condition after the temperature gate was removed.
            if(condition==Condition.TEMPERATURE)condition=Condition.NONE;
        }
    }
    private CropInspection() {}

    /** Block-state-only fallback for a client that has not yet received server inspection data. */
    public static Snapshot state(BlockState state) {
        Kind kind;Health health=Health.NONE;int age=-1,max=-1,moisture=-1;boolean fertile=false;
        if(CropLifecycle.managed(state)) {
            kind=Kind.FOOD;var crop=(CropBlock)state.getBlock();age=crop.getAge(state);max=crop.getMaxAge();
            health=CubeCropBlight.dead(state)?Health.DEAD:CubeCropBlight.blighted(state)?Health.BLIGHTED:Health.HEALTHY;
        } else if(SpecialPlantGrowth.managedStem(state)) {
            kind=Kind.STEM;age=state.hasProperty(StemBlock.AGE)?state.getValue(StemBlock.AGE):7;max=7;
            health=SpecialPlantGrowth.dead(state)?Health.DEAD:Health.HEALTHY;
        } else if(state.getBlock() instanceof BlueberryBushBlock) {kind=Kind.BERRY;age=state.getValue(SweetBerryBushBlock.AGE);max=3;}
        else if(state.getBlock() instanceof FarmBlock) {kind=Kind.SOIL;moisture=state.getValue(FarmBlock.MOISTURE);fertile=FoodAgriculture.fertilized(state);}
        else if(state.is(Blocks.COCOA)) {kind=Kind.COCOA;age=state.getValue(CocoaBlock.AGE);max=2;}
        else if(state.is(Blocks.NETHER_WART)) {kind=Kind.WART;age=state.getValue(NetherWartBlock.AGE);max=3;}
        else if(FungalGrowth.mushroom(state)) {kind=Kind.MUSHROOM;age=state.getValue(FungalGrowth.GROWTH);max=3;}
        else if(state.is(Blocks.SUGAR_CANE))kind=Kind.CANE;
        else if(state.is(Blocks.CACTUS))kind=Kind.CACTUS;
        else return null;
        Condition condition=health==Health.DEAD?Condition.DEAD:health==Health.BLIGHTED?Condition.BLIGHTED:Condition.NONE;
        return new Snapshot(kind,health,age,max,moisture,fertile,condition,-1,-1,-1,-1);
    }

    public static Snapshot inspect(ServerLevel level,BlockPos pos,BlockState state) {
        Snapshot base=state(state);if(base==null)return null;
        int moisture=base.moisture(),irrigation=-1,height=-1,limit=-1,light=-1;boolean fertile=base.fertilized();
        Condition condition=base.condition();
        if(!level.isAreaLoaded(pos,4))return base;
        if(base.kind()==Kind.SOIL||base.kind()==Kind.FOOD||base.kind()==Kind.STEM) {
            BlockPos soil=base.kind()==Kind.SOIL?pos:pos.below();var below=level.getBlockState(soil);
            if(below.getBlock() instanceof FarmBlock) {moisture=below.getValue(FarmBlock.MOISTURE);fertile=FoodAgriculture.fertilized(below);irrigation=FoodAgriculture.hasWater(level,soil)?1:0;}
        }
        boolean canGrow=base.health()!=Health.DEAD&&base.health()!=Health.BLIGHTED;
        if(base.kind()==Kind.MUSHROOM)condition=FungalGrowth.canGiant(level,pos,state)?Condition.GIANT_READY:Condition.UNSUITABLE;
        else if(base.kind()!=Kind.SOIL&&base.kind()!=Kind.CANE&&base.kind()!=Kind.CACTUS&&canGrow) {
            boolean mature=base.age()>=0&&base.age()>=base.maxAge();
            if(!state.canSurvive(level,pos))condition=Condition.UNSUITABLE;
            else if(base.kind()==Kind.WART&&!level.dimension().equals(Level.NETHER))condition=Condition.WRONG_DIMENSION;
            else if(base.kind()==Kind.STEM&&state.getBlock() instanceof AttachedStemBlock)condition=Condition.FRUIT_PRESENT;
            else if(!mature) {
                light=level.getMaxLocalRawBrightness(pos.above());
                boolean needsDaylight=base.kind()==Kind.FOOD||base.kind()==Kind.STEM||base.kind()==Kind.BERRY;
                if((base.kind()==Kind.FOOD||base.kind()==Kind.STEM)&&moisture==0)condition=Condition.DRY;
                else if(needsDaylight&&light!=15)condition=Condition.LIGHT;
                else condition=Condition.GROWING;
            }
        }
        if(base.kind()==Kind.CANE||base.kind()==Kind.CACTUS) {
            BlockPos bottom=pos;while(bottom.getY()>level.getMinBuildHeight()&&level.getBlockState(bottom.below()).is(state.getBlock()))bottom=bottom.below();
            BlockPos top=pos;while(top.getY()+1<level.getMaxBuildHeight()&&level.getBlockState(top.above()).is(state.getBlock()))top=top.above();
            height=top.getY()-bottom.getY()+1;limit=base.kind()==Kind.CANE?3:SpecialPlantGrowth.cactusHeight(level,bottom.below());
        }
        return new Snapshot(base.kind(),base.health(),base.age(),base.maxAge(),moisture,fertile,condition,light,irrigation,height,limit);
    }

    public static CompoundTag save(Snapshot s) {
        var tag=new CompoundTag();tag.putInt("version",1);tag.putString("kind",s.kind().name());tag.putString("health",s.health().name());
        tag.putInt("age",s.age());tag.putInt("maxAge",s.maxAge());tag.putInt("moisture",s.moisture());tag.putBoolean("fertilized",s.fertilized());
        tag.putString("condition",s.condition().name());tag.putInt("light",s.light());tag.putInt("irrigation",s.irrigation());tag.putInt("height",s.height());tag.putInt("heightLimit",s.heightLimit());return tag;
    }
    public static Snapshot load(CompoundTag tag) {
        if(tag.getInt("version")!=1)return null;
        try {
            var s=new Snapshot(Kind.valueOf(tag.getString("kind")),Health.valueOf(tag.getString("health")),tag.getInt("age"),tag.getInt("maxAge"),
                    tag.getInt("moisture"),tag.getBoolean("fertilized"),Condition.valueOf(tag.getString("condition")),tag.getInt("light"),tag.getInt("irrigation"),tag.getInt("height"),tag.getInt("heightLimit"));
            if(s.age() < -1||s.maxAge() < -1||s.maxAge()==0||(s.age()<0)!=(s.maxAge()<0)||s.maxAge()>7||s.age()>s.maxAge()||s.moisture() < -1||s.moisture()>7
                    ||s.light() < -1||s.light()>15||s.irrigation() < -1||s.irrigation()>1||s.height() < -1||s.height()>1024||s.heightLimit() < -1||s.heightLimit()>3)return null;
            return s;
        } catch(IllegalArgumentException e) {return null;}
    }
    public static List<Component> lines(Snapshot s) {
        if(s==null)return List.of();List<Component> result=new ArrayList<>();
        if(s.health()!=Health.NONE) {
            var color=s.health()==Health.HEALTHY?ChatFormatting.GREEN:s.health()==Health.BLIGHTED?ChatFormatting.YELLOW:ChatFormatting.RED;
            result.add(Component.translatable("tooltip.mite.crop.health",Component.translatable("tooltip.mite.crop.health."+s.health().name().toLowerCase(java.util.Locale.ROOT)).withStyle(color)));
        }
        if(s.age()>=0) {
            if(s.kind()==Kind.MUSHROOM)result.add(Component.translatable("tooltip.mite.crop.giant_progress",s.age(),s.maxAge()));
            else if(s.health()!=Health.DEAD)result.add(s.age()==s.maxAge()&&s.health()!=Health.BLIGHTED?Component.translatable("tooltip.mite.crop.mature").withStyle(ChatFormatting.GREEN)
                    :Component.translatable("tooltip.mite.crop.growth",s.age()*100/s.maxAge(),s.age(),s.maxAge()));
        }
        if(s.moisture()>=0)result.add(Component.translatable("tooltip.mite.crop.soil",s.moisture(),
                Component.translatable(s.fertilized()?"tooltip.mite.crop.fertilized":"tooltip.mite.crop.unfertilized").withStyle(s.fertilized()?ChatFormatting.GREEN:ChatFormatting.GRAY)));
        if(s.kind()==Kind.SOIL&&s.irrigation()>=0)result.add(Component.translatable("tooltip.mite.crop.irrigation",
                Component.translatable(s.irrigation()==1?"tooltip.mite.crop.water_present":"tooltip.mite.crop.water_absent")));
        if(s.condition()!=Condition.NONE&&s.condition()!=Condition.BLIGHTED&&s.condition()!=Condition.DEAD) {
            String key="tooltip.mite.crop.condition."+s.condition().name().toLowerCase(java.util.Locale.ROOT);
            boolean good=s.condition()==Condition.GROWING||s.condition()==Condition.GIANT_READY||s.condition()==Condition.FRUIT_PRESENT;
            result.add(Component.translatable("tooltip.mite.crop.condition",Component.translatable(key).withStyle(good?ChatFormatting.GREEN:ChatFormatting.YELLOW)));
        }
        if(s.health()==Health.BLIGHTED)result.add(Component.translatable("tooltip.mite.crop.cure").withStyle(ChatFormatting.YELLOW));
        else if(s.health()==Health.DEAD)result.add(Component.translatable("tooltip.mite.crop.no_cure").withStyle(ChatFormatting.GRAY));
        if(s.height()>0)result.add(Component.translatable("tooltip.mite.crop.height",s.height(),s.heightLimit()));
        return List.copyOf(result);
    }
}

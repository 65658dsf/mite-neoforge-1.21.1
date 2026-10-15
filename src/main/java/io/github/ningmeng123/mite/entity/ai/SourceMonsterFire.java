package io.github.ningmeng123.mite.entity.ai;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.R196Elemental;
import io.github.ningmeng123.mite.entity.R196FireElemental;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** nn's actual environment ignition consumes og's temporary tree pursuit boost. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class SourceMonsterFire {
    private static final Map<Mob,Integer> BOOST=new WeakHashMap<>();
    private SourceMonsterFire() {}
    public static void boost(Mob mob,int ticks) { if(!mob.level().isClientSide) { if(ticks>0) BOOST.put(mob,ticks); else BOOST.remove(mob); } }
    public static int boostTicks(Mob mob) { return BOOST.getOrDefault(mob,0); }
    public static float ignitionChance(Mob mob) {
        if(mob instanceof R196FireElemental) return .05F;
        if(mob instanceof R196Elemental earth&&earth.isMagma() || mob instanceof Blaze) return .04F;
        return mob.isOnFire()?(boostTicks(mob)>0?.01F:.00125F):0F;
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if(event.getEntity() instanceof Mob mob && R196MobSpawning.sourceClass(mob)!=null) tickSource(mob);
    }
    public static void tickSource(Mob mob) {
        if(!(mob.level() instanceof ServerLevel level)||!mob.isAlive()) return;
        int remaining=boostTicks(mob);
        float chance=ignitionChance(mob);
        if(chance>0F && mob.getRandom().nextFloat()<chance && level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)
                && EventHooks.canEntityGrief(level,mob)) spreadPulse(mob);
        // nn consumes the current boost during super.l_(); og.l_() decrements afterwards.
        if(remaining>0) boost(mob,remaining-1);
    }
    /** One successfully drawn source nn pulse; modern fire/grief hooks protect external claims. */
    public static boolean spreadPulse(Mob mob) {
        if(!(mob.level() instanceof ServerLevel level)||!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)||!EventHooks.canEntityGrief(level,mob)) return false;
        boolean changed=false; BlockPos feet=mob.blockPosition();
        for(int dy=0;dy<(int)mob.getBbHeight()+1;dy++) {
            BlockPos pos=feet.above(dy);
            if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos)) continue;
            if(level.isEmptyBlock(pos)) {
                if(mob.getRandom().nextInt(10)==0 && nearFlammable(level,pos)) {
                    changed|=level.setBlock(pos,BaseFireBlock.getState(level,pos),3);
                } else if(dy==0) {
                    BlockPos support=restingSupport(mob,level);
                    if(support!=null) changed|=melt(level,support);
                }
                continue;
            }
            BlockState state=level.getBlockState(pos);
            int odds=state.getFlammability(level,pos,Direction.UP);
            if(mob.getRandom().nextInt(100)<odds) {
                state.onCaughtFire(level,pos,Direction.UP,mob);
                if(mob.getRandom().nextInt(11)<5&&!level.isRainingAt(pos)) {
                    int age=1+mob.getRandom().nextInt(5)/4; BlockState fire=BaseFireBlock.getState(level,pos);
                    if(fire.hasProperty(FireBlock.AGE)) fire=fire.setValue(FireBlock.AGE,age);
                    level.setBlock(pos,fire,3);
                } else level.removeBlock(pos,false);
                changed=true;
            } else changed|=melt(level,pos);
        }
        return changed;
    }
    /** nn.getBlockRestingOn(.1) delegates to exact AABB contact, with no .1 height allowance. */
    @Nullable private static BlockPos restingSupport(Mob mob,ServerLevel level) {
        if(!mob.onGround()||mob.isPassenger()) return null;
        var bounds=mob.getBoundingBox(); var context=CollisionContext.of(mob);
        BlockPos closest=null; double closestDistance=Double.POSITIVE_INFINITY;
        for(int x=Mth.floor(bounds.minX);x<=Mth.ceil(bounds.maxX)-1;x++)
            for(int z=Mth.floor(bounds.minZ);z<=Mth.ceil(bounds.maxZ)-1;z++)
                for(int y=Mth.floor(bounds.minY)-1;y<=Mth.ceil(bounds.maxY)-1;y++) {
                    BlockPos pos=new BlockPos(x,y,z);
                    if(level.isOutsideBuildHeight(pos)||!level.hasChunkAt(pos)) continue;
                    for(var collision:level.getBlockState(pos).getCollisionShape(level,pos,context).toAabbs()) {
                        if(collision.maxY+y!=bounds.minY || collision.maxX+x<=bounds.minX || collision.minX+x>=bounds.maxX
                                || collision.maxZ+z<=bounds.minZ || collision.minZ+z>=bounds.maxZ) continue;
                        double dx=x+.5D-mob.getX(),dz=z+.5D-mob.getZ(),distance=dx*dx+dz*dz;
                        if(distance<closestDistance) { closest=pos; closestDistance=distance; }
                    }
                }
        return closest;
    }
    private static boolean nearFlammable(ServerLevel level,BlockPos pos) {
        for(Direction face:Direction.values()) {
            BlockPos neighbor=pos.relative(face);
            if(level.hasChunkAt(neighbor)&&level.getBlockState(neighbor).getFireSpreadSpeed(level,neighbor,face.getOpposite())>0) return true;
        }
        return false;
    }
    private static boolean melt(ServerLevel level,BlockPos pos) {
        if(!level.hasChunkAt(pos)) return false;
        BlockState state=level.getBlockState(pos);
        if(state.is(Blocks.ICE)) return level.setBlock(pos,Blocks.WATER.defaultBlockState(),3);
        if(state.is(Blocks.SNOW_BLOCK)) return level.setBlock(pos,Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,7),3);
        if(state.is(Blocks.SNOW)) {
            int layers=state.getValue(SnowLayerBlock.LAYERS);
            return layers==1?level.removeBlock(pos,false):level.setBlock(pos,state.setValue(SnowLayerBlock.LAYERS,layers-1),3);
        }
        return false;
    }
}

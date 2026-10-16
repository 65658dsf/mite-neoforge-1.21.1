package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.worldgen.R196SpawnQualification;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** js sleep interruptions: nearby monsters follow a valid path during forwarded time. @author NingMeng123 */
public final class SleepThreats {
    private SleepThreats() {}
    public static boolean interrupt(ServerLevel level,List<ServerPlayer> sleepers) {
        if(sleepers.isEmpty())return false;
        ServerPlayer player=sleepers.get(level.random.nextInt(sleepers.size()));
        if(!player.isSleeping())return false;
        List<Mob> mobs=List.of();
        int distance=0;
        while(distance<64 && mobs.size()<16) {
            distance+=16;
            mobs=level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(distance,distance/4D,distance),m->PlayerRest.hostile(m));
        }
        boolean[] tried=new boolean[mobs.size()];
        for(int attempt=0;attempt<Math.min(mobs.size(),16);attempt++) {
            int index=level.random.nextInt(mobs.size());
            if(tried[index])continue;
            tried[index]=true;
            Mob mob=mobs.get(index);
            if(mob.tickCount>0 && !(mob instanceof EnderMan) && path(mob,player))return wake(player);
        }
        Mob spawned=create(level,player);
        if(spawned==null)return false;
        boolean reaches=path(spawned,player);
        if(reaches || level.random.nextInt(2)==0) {
            net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(spawned,level,level.getCurrentDifficultyAt(spawned.blockPosition()),MobSpawnType.NATURAL,null);
            if(spawned.isRemoved() || spawned.isSpawnCancelled())return false;
            if(!level.addFreshEntity(spawned))return false;
            if(reaches)return wake(player);
        }
        return false;
    }
    private static boolean wake(ServerPlayer player) {
        player.stopSleepInBed(true,true);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.mite.sleep_mobs"),true);
        return true;
    }
    private static boolean path(Mob mob,ServerPlayer player) {
        BlockPos bed=player.getSleepingPos().orElse(null);
        if(bed==null)return false;
        var route=mob.getNavigation().createPath(bed,1);
        if(route==null || route.getEndNode()==null || route.getEndNode().asBlockPos().distSqr(bed)>2)return false;
        int start=Math.max(route.getNodeCount()-8,0), selected=start;
        for(int i=start;i<route.getNodeCount();i++) {
            var node=route.getNode(i);
            Vec3 eye=new Vec3(node.x+.5D,node.y+mob.getEyeHeight(),node.z+.5D);
            if(mob.level().clip(new ClipContext(eye,Vec3.atCenterOf(bed).add(0,1,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob)).getType()==HitResult.Type.MISS) {selected=i;break;}
        }
        var node=route.getNode(selected);
        mob.teleportTo(node.x+.5D,node.y,node.z+.5D);
        mob.clearFire();
        mob.setTarget(player);
        return true;
    }
    private static Mob create(ServerLevel level,ServerPlayer player) {
        for(int attempt=0;attempt<16;attempt++) {
            BlockPos pos=player.blockPosition().offset(level.random.nextInt(97)-48,level.random.nextInt(7)-3,level.random.nextInt(97)-48);
            if(!level.hasChunkAt(pos))continue;
            int down=0;while(down++<8 && level.isEmptyBlock(pos.below()))pos=pos.below();
            int up=0;while(up++<8 && !level.isEmptyBlock(pos))pos=pos.above();
            double nearest=Double.MAX_VALUE;
            for(ServerPlayer other:level.players())if(other.isAlive()&&!other.isSpectator())nearest=Math.min(nearest,other.distanceToSqr(Vec3.atCenterOf(pos)));
            if(nearest<64 || nearest>2304 || !level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above()) || level.isEmptyBlock(pos.below()))continue;
            var chosen=R196MobSpawning.select(level,pos,R196SpawnRules.Category.MONSTER,level.random,false);
            if(chosen==null)continue;
            Mob mob=chosen.type().create(level);
            if(mob==null || mob instanceof EnderMan || mob instanceof Slime)continue;
            mob.moveTo(pos.getX()+.5D,pos.getY(),pos.getZ()+.5D,level.random.nextFloat()*360F,0);
            if(R196SpawnQualification.qualifies(mob,level,true,false))return mob;
        }
        return null;
    }
}

package io.github.ningmeng123.mite.entity.animal;

import io.github.ningmeng123.mite.entity.MobCombat;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Wolf;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** sc full-water predation and ro hanging; lower-bound adaptation uses modern build height. @author NingMeng123 */
public final class SourceAquatic032 {
    private SourceAquatic032() {}
    public static boolean blueNight(Level level) { return !R196SpawnRules.daytime((int)Math.floorMod(level.getDayTime(),24000L)) && R196SpawnRules.moon(level.getDayTime(),level.dimension()==Level.OVERWORLD).blue(); }
    public static boolean playerShallow(Player player) {
        if(player.isPassenger()) return false;
        int x=Mth.floor(player.getX()),z=Mth.floor(player.getZ()),y=Mth.floor(player.getEyeY()); Level level=player.level();
        if(level.getFluidState(new BlockPos(x,y,z)).is(FluidTags.WATER)) return false;
        if(player.onGround()) return true;
        if(level.getFluidState(new BlockPos(x,--y,z)).is(FluidTags.WATER)) return !level.getFluidState(new BlockPos(x,--y,z)).is(FluidTags.WATER);
        if(level.getFluidState(new BlockPos(x,--y,z)).is(FluidTags.WATER)) return !level.getFluidState(new BlockPos(x,--y,z)).is(FluidTags.WATER);
        return false;
    }
    public static boolean prey(Squid squid,LivingEntity target) {
        if(target instanceof WaterAnimal || blueNight(squid.level())) return false;
        if(target instanceof Player p) return !p.isCreative() && !p.isSpectator() && !(p.getVehicle() instanceof Boat && squid.level().isDay()) && !playerShallow(p);
        return target instanceof Animal && !(target instanceof R196Wolf w && w.miteKind()==MobKind.HELLHOUND);
    }
    private static boolean fullWater(Level level,BlockPos pos) { var f=level.getFluidState(pos); return f.is(FluidTags.WATER) && (f.isSource() || f.getAmount()==8); }
    /** Voxel traversal tests every crossed cell including origin and limit, not only endpoint fluid. */
    public static boolean fullWaterRay(Level level,Vec3 origin,Vec3 end) {
        Vec3 delta=end.subtract(origin); int x=Mth.floor(origin.x),y=Mth.floor(origin.y),z=Mth.floor(origin.z);
        int ex=Mth.floor(end.x),ey=Mth.floor(end.y),ez=Mth.floor(end.z);
        int sx=delta.x>0?1:delta.x<0?-1:0,sy=delta.y>0?1:delta.y<0?-1:0,sz=delta.z>0?1:delta.z<0?-1:0;
        double tx=sx==0?Double.POSITIVE_INFINITY:((sx>0?x+1:x)-origin.x)/delta.x,ty=sy==0?Double.POSITIVE_INFINITY:((sy>0?y+1:y)-origin.y)/delta.y,tz=sz==0?Double.POSITIVE_INFINITY:((sz>0?z+1:z)-origin.z)/delta.z;
        double dx=sx==0?Double.POSITIVE_INFINITY:Math.abs(1/delta.x),dy=sy==0?Double.POSITIVE_INFINITY:Math.abs(1/delta.y),dz=sz==0?Double.POSITIVE_INFINITY:Math.abs(1/delta.z);
        for(int cells=0;cells<1024;cells++) {
            if(!fullWater(level,new BlockPos(x,y,z))) return false; if(x==ex&&y==ey&&z==ez) return true;
            double t=Math.min(tx,Math.min(ty,tz)); if(tx==t) { x+=sx;tx+=dx; } if(ty==t) { y+=sy;ty+=dy; } if(tz==t) { z+=sz;tz+=dz; }
        }
        return false;
    }
    private static Vec3 center(Entity entity) { return new Vec3(entity.getX(),entity.getY()+entity.getBbHeight()*.5,entity.getZ()); }
    public static boolean strikeWater(Squid squid,LivingEntity target) {
        Vec3 from=center(squid),mid=center(target);
        return fullWaterRay(squid.level(),from,mid) || fullWaterRay(squid.level(),from,new Vec3(mid.x,target.getY()+.01,mid.z)) || fullWaterRay(squid.level(),from,new Vec3(mid.x,target.getY()+target.getBbHeight()-.01,mid.z));
    }
    public static final class SquidSwim extends Goal {
        private final Squid squid;
        public SquidSwim(Squid squid) { this.squid=squid; }
        @Override public boolean canUse() { return true; }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            if(squid.getRandom().nextInt(50)!=0 && squid.isInWater() && squid.hasMovementVector()) return;
            LivingEntity target=squid.getTarget(); if(target instanceof Player p && playerShallow(p)) target=null;
            if(target==null || !target.isAlive() || squid.getRandom().nextInt(10)==0)
                target=squid.level().getEntitiesOfClass(LivingEntity.class,squid.getBoundingBox().inflate(16),e->e!=squid && e.isAlive() && prey(squid,e) && squid.distanceToSqr(e)<=256 && squid.getSensing().hasLineOfSight(e)).stream().min(Comparator.comparingDouble(squid::distanceToSqr)).orElse(null);
            if(blueNight(squid.level()) || target!=null && !strikeWater(squid,target)) target=null;
            squid.setTarget(target);
            if(target==null) { float angle=squid.getRandom().nextFloat()*Mth.TWO_PI; squid.setMovementVector(Mth.cos(angle)*.2F,-.1F+squid.getRandom().nextFloat()*.2F,Mth.sin(angle)*.2F); }
            else { Vec3 d=center(target).subtract(center(squid)).normalize().scale(.2); squid.setMovementVector((float)d.x,(float)d.y,(float)d.z); }
        }
    }
    public static void touch(Squid squid) {
        if(squid.level().isClientSide || squid.getType()!=EntityType.SQUID || !squid.isAlive()) return;
        for(Player player:squid.level().getEntitiesOfClass(Player.class,squid.getBoundingBox().inflate(1),Player::isAlive)) touchPlayer(squid,player);
        for(LivingEntity entity:squid.level().getEntitiesOfClass(LivingEntity.class,squid.getBoundingBox().inflate(.05),e->e!=squid && e.isAlive()))
            if(!(entity instanceof Player) && prey(squid,entity) && squid.getSensing().hasLineOfSight(entity)) entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,200,2));
    }
    /** sc.b_(uf) player contact is independent of the prey predicate and Blue Moon. */
    public static void touchPlayer(Squid squid,Player player) {
        if(!squid.level().isClientSide && squid.getType()==EntityType.SQUID && player.isAlive() && squid.distanceToSqr(player)<1 && !(player.getVehicle() instanceof Boat))
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,200,2));
    }
    public static void boatCollision(Boat boat,Entity other) {
        if(boat.level().isClientSide || !boat.isAlive() || !(other instanceof Squid squid) || squid.getType()!=EntityType.SQUID || squid.getTarget()==null || squid.getTarget().getVehicle()!=boat) return;
        int hits=boat.getPersistentData().getInt("mite_squid_collisions_032")+1; boat.getPersistentData().putInt("mite_squid_collisions_032",hits);
        if(hits>=6) { boat.discard(); if(boat.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOENTITYDROPS)) { for(int i=0;i<3;i++) boat.spawnAtLocation(Blocks.OAK_PLANKS); for(int i=0;i<2;i++) boat.spawnAtLocation(Items.STICK); } }
    }
    public static boolean canHang(Bat bat,BlockPos pos) {
        var state=bat.level().getBlockState(pos);
        return !state.is(Blocks.GLASS) && state.isFaceSturdy(bat.level(),pos,Direction.DOWN) && bat.level().isEmptyBlock(pos.below());
    }
    public static void batAi(Bat bat) {
        var data=bat.getPersistentData(); BlockPos ceiling=BlockPos.containing(bat.getX(),bat.getY()+bat.getBbHeight()+.12,bat.getZ());
        boolean prevented=data.getInt("mite_bat_prevent_hang_032")>0 || bat.getTarget()!=null || bat.hurtTime>0 || bat.level().getNearestPlayer(bat,4)!=null;
        if(bat.isResting()) {
            if(!canHang(bat,ceiling) || prevented || bat.getRandom().nextInt(1000)==0) { bat.setResting(false); bat.level().levelEvent(null,1025,bat.blockPosition(),0); }
            else { if(bat.getRandom().nextInt(200)==0) bat.yHeadRot=bat.getRandom().nextInt(360); data.putInt("mite_bat_hang_y_032",ceiling.getY()); }
            return;
        }
        BlockPos target=data.contains("mite_bat_flight_x_032")?new BlockPos(data.getInt("mite_bat_flight_x_032"),data.getInt("mite_bat_flight_y_032"),data.getInt("mite_bat_flight_z_032")):null;
        int floor=bat.level().getMinBuildHeight()+1;
        if(target!=null && (!bat.level().isEmptyBlock(target) || target.getY()<floor)) target=null;
        if(target==null || bat.getRandom().nextInt(30)==0 || target.distToCenterSqr(bat.position())<4) {
            target=new BlockPos(bat.getBlockX()+bat.getRandom().nextInt(7)-bat.getRandom().nextInt(7),Math.max(floor,(int)bat.getY()+bat.getRandom().nextInt(6)-2),bat.getBlockZ()+bat.getRandom().nextInt(7)-bat.getRandom().nextInt(7));
            data.putInt("mite_bat_flight_x_032",target.getX()); data.putInt("mite_bat_flight_y_032",target.getY()); data.putInt("mite_bat_flight_z_032",target.getZ());
        }
        Vec3 d=new Vec3(target.getX()+.5-bat.getX(),target.getY()+.1-bat.getY(),target.getZ()+.5-bat.getZ()),old=bat.getDeltaMovement();
        Vec3 next=old.add((Math.signum(d.x)*.5-old.x)*.1,(Math.signum(d.y)*.7-old.y)*.1,(Math.signum(d.z)*.5-old.z)*.1); bat.setDeltaMovement(next);
        bat.setYRot((float)(Math.atan2(next.z,next.x)*180/Math.PI)-90); bat.zza=.5F;
        if(!prevented && bat.getRandom().nextInt(data.getBoolean("mite_bat_initial_hang_032")?10:1)==0 && canHang(bat,ceiling)) { data.putInt("mite_bat_hang_y_032",ceiling.getY()); bat.setResting(true); }
        data.putBoolean("mite_bat_initial_hang_032",true);
    }
    public static void batTick(Bat bat) {
        if(bat.getType()!=EntityType.BAT) return;
        if(!bat.level().isClientSide) { int prevent=bat.getPersistentData().getInt("mite_bat_prevent_hang_032"); if(prevent>0) bat.getPersistentData().putInt("mite_bat_prevent_hang_032",prevent-1); }
        if(bat.isResting()) {
            // Native resting flag and authoritative entity position supply the client; no extra unsynced Y field is read on the client.
            int y=bat.level().isClientSide || !bat.getPersistentData().contains("mite_bat_hang_y_032")?Mth.floor(bat.getY()+bat.getBbHeight()+.12):bat.getPersistentData().getInt("mite_bat_hang_y_032");
            bat.setDeltaMovement(Vec3.ZERO); bat.setPos(bat.getX(),y-bat.getBbHeight()-.00625,bat.getZ());
        }
    }
}

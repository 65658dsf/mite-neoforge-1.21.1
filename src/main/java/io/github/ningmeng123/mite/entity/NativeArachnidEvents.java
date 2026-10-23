package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.ai.NativeLeapAtTargetGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Source EntityArachnid behavior for the two actual native identities; foreign spider subclasses stay untouched. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class NativeArachnidEvents {
    private NativeArachnidEvents() {}
    public static boolean managed(Spider spider) { return spider.getType()==EntityType.SPIDER || spider.getType()==EntityType.CAVE_SPIDER; }
    public static int throwInterval(Spider spider) { return spider.getType()==EntityType.CAVE_SPIDER ? 200 : 500; }
    public static void installGoals(Spider spider,GoalSelector actions,GoalSelector targets) {
        if (!managed(spider)) return;
        actions.removeAllGoals(goal -> goal instanceof LeapAtTargetGoal || goal instanceof MeleeAttackGoal);
        targets.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal<?>);
        actions.addGoal(3,new NativeLeapAtTargetGoal(spider));
        actions.addGoal(4,new MeleeAttackGoal(spider,1D,true));
    }
    @SubscribeEvent public static void afterTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Spider spider && managed(spider)) tickSource(spider);
    }
    public static void tickSource(Spider spider) {
        if (spider.level().isClientSide || !spider.isAlive() || spider.isNoAi()) return;
        SourceVanillaMobLoot.initialize(spider);
        acquireTarget(spider);
        if (spider.getTarget() instanceof Player && peacefulHere(spider) && !(spider.getLastHurtByMob() instanceof Player)
                && spider.getRandom().nextInt(100)==0) spider.setTarget(null);
        LivingEntity target=spider.getTarget();
        if (target!=null && SourceVanillaMobLoot.webStock(spider)>0 && MobCombat.ticksWithOffset(spider)%throwInterval(spider)==0
                && spider.distanceTo(target)<=8F && clearThrowRay(spider,target)) throwWeb(spider,target);
    }
    /** Source on.bl searches players every ten offset ticks, then has a 1% chance to look for a visible chicken. */
    public static void acquireTarget(Spider spider) {
        LivingEntity old=spider.getTarget();
        if (old!=null && !old.isAlive()) spider.setTarget(null);
        if (spider.getTarget()!=null || peacefulHere(spider)) return;
        double range=Math.min(24D,spider.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE));
        if (MobCombat.ticksWithOffset(spider)%10==0) {
            Player nearest=null; double distance=range*range;
            for (Player player:spider.level().players()) {
                if (player.isAlive() && !player.isCreative() && !player.isSpectator() && spider.distanceToSqr(player)<distance) {
                    nearest=player; distance=spider.distanceToSqr(player);
                }
            }
            if(nearest!=null) { spider.setTarget(nearest); return; }
        }
        if (!MonsterHunting.active(spider.level()) || spider.getRandom().nextFloat()>=.01F) return;
        Chicken nearest=null; double distance=Double.MAX_VALUE;
        for (Chicken chicken:spider.level().getEntitiesOfClass(Chicken.class,spider.getBoundingBox().inflate(range,range/4D,range))) {
            if (chicken.isAlive() && spider.getSensing().hasLineOfSight(chicken) && spider.distanceToSqr(chicken)<distance) {
                nearest=chicken; distance=spider.distanceToSqr(chicken);
            }
        }
        if(nearest!=null) { spider.setTarget(nearest); MonsterHunting.remember(spider, nearest); }
    }
    public static boolean peacefulHere(Spider spider) {
        return spider.getType()==EntityType.SPIDER && spider.getLightLevelDependentMagicValue()>.5F && isOutdoors(spider);
    }
    /** Source outdoor detection checks open routes on the local level and, through passable space, the next two levels. */
    public static boolean isOutdoors(Spider spider) {
        // Source abw checks an actually open column. Modern canSeeSky also admits diffused
        // skylight and can lag a same-tick roof update, so use the shared source column check.
        return spider.level() instanceof net.minecraft.server.level.ServerLevel level
                && io.github.ningmeng123.mite.worldgen.R196OutdoorChecks.outdoors(level,spider.blockPosition());
    }
    public static boolean clearThrowRay(Spider spider,LivingEntity target) {
        Vec3 start=spider.getEyePosition(),end=target.getEyePosition();
        if(blocked(spider,start,end)) {
            end=target.position().add(0,target.getBbHeight()*.25F,0);
            if(blocked(spider,start,end)) return false;
        }
        var hit=ProjectileUtil.getEntityHitResult(spider,start,end,new AABB(start,end).inflate(1D),
                entity -> entity.isAlive() && entity.isPickable() && !entity.isSpectator(),start.distanceToSqr(end));
        return hit!=null && hit.getEntity()==target;
    }
    private static boolean blocked(Spider spider,Vec3 start,Vec3 end) {
        return spider.level().clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.ANY,spider)).getType()!=HitResult.Type.MISS;
    }
    public static boolean throwWeb(Spider spider,LivingEntity target) {
        if(spider.level().isClientSide || !managed(spider) || SourceVanillaMobLoot.webStock(spider)<=0) return false;
        var web=new R196Web(spider.level(),spider);
        Vec3 predicted=target.position().add(target.getDeltaMovement().multiply(10,0,10));
        double x=predicted.x-spider.getX(),z=predicted.z-spider.getZ();
        double y=target.getEyeY()-1.1F-web.getY()+Math.sqrt(x*x+z*z)*.2F;
        web.shoot(x,y,z,.8F,0F);
        if(spider.isOnFire()) web.igniteForSeconds(10);
        if(!spider.level().addFreshEntity(web)) return false;
        spider.playSound(SoundEvents.SNOWBALL_THROW,1F,1F/(spider.getRandom().nextFloat()*.4F+.8F));
        SourceVanillaMobLoot.setWebStock(spider,SourceVanillaMobLoot.webStock(spider)-1); return true;
    }
    /** Shared source leap: strict (2,6), one draw in ten before the on-ground check, then .4 horizontal/.4 vertical. */
    public static boolean trySourceLeap(Spider spider,LivingEntity target) {
        float distance=spider.distanceTo(target);
        if(!(distance>2F && distance<6F) || spider.getRandom().nextInt(10)!=0 || !spider.onGround()) return false;
        Vec3 horizontal=target.position().subtract(spider.position()).multiply(1,0,1).normalize().scale(.4D),old=spider.getDeltaMovement();
        spider.setDeltaMovement(horizontal.x+old.x*.2D,.4D,horizontal.z+old.z*.2D);
        spider.setYRot((float)(Math.toDegrees(Math.atan2(target.getZ()-spider.getZ(),target.getX()-spider.getX()))-90D));
        var path=spider.getNavigation().createPath(target,0); if(path!=null) spider.getNavigation().moveTo(path,1D);
        return true;
    }
}

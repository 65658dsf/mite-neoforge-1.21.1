package io.github.ningmeng123.mite.entity.animal;

import io.github.ningmeng123.mite.entity.R196NpcNeeds;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.pathfinder.Path;

/** Enabled R196 common animal movement goals, using the modern terrain navigator. @author NingMeng123 */
public final class AnimalCommon032Goals {
    private AnimalCommon032Goals() {}
    public interface Marker {}
    public static final class Float extends net.minecraft.world.entity.ai.goal.FloatGoal implements Marker {
        private final PathfinderMob owner;
        public Float(PathfinderMob mob) { super(mob); owner=mob; }
        private boolean buoyant() {
            if(owner.horizontalCollision) return true;
            var slow=owner.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
            var fast=owner.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
            float modifier=(slow==null ? 0F : -.2F*(slow.getAmplifier()+1))+(fast==null ? 0F : .2F*(fast.getAmplifier()+1));
            if(owner.level().getBlockState(owner.blockPosition()).is(net.minecraft.world.level.block.Blocks.COBWEB)) modifier-=.75F;
            if(modifier<0F) modifier*=1F-io.github.ningmeng123.mite.entity.MobCombat.silverResistance(owner);
            return modifier>=-.5F;
        }
        @Override public boolean canUse() {
            if(!buoyant()) return false;
            if(owner.isInWater() && !owner.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
                    && owner.level().getFluidState(owner.blockPosition()).getHeight(owner.level(),owner.blockPosition())<1F) return owner.isInLava();
            return owner.isInWater() || owner.isInLava();
        }
        @Override public void tick() {
            if(!buoyant()) return;
            if(owner.level().getFluidState(BlockPos.containing(owner.getX(),owner.getEyeY()+.5D,owner.getZ())).is(net.minecraft.tags.FluidTags.WATER))
                owner.setDeltaMovement(owner.getDeltaMovement().x,.1F,owner.getDeltaMovement().z);
            else if(owner.getRandom().nextFloat()<.8F) owner.getJumpControl().jump();
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }
    }
    private static BlockPos randomFoot(PathfinderMob mob,int radius) {
        return SourceMovement032Goal.findFoot(mob,mob.blockPosition().offset(mob.getRandom().nextInt(radius*2+1)-radius,
                mob.getRandom().nextInt(7)-3,mob.getRandom().nextInt(radius*2+1)-radius));
    }
    private static BlockPos end(Path path) { return path.getEndNode()==null ? null : path.getEndNode().asBlockPos(); }
    private static double distance(BlockPos pos,List<? extends LivingEntity> threats) {
        double nearest=Double.MAX_VALUE;
        for(var threat:threats) nearest=Math.min(nearest,new net.minecraft.world.phys.Vec3(pos.getX(),pos.getY(),pos.getZ()).distanceToSqr(threat.position()));
        return nearest;
    }
    @Nullable private static Path away(PathfinderMob mob,List<? extends LivingEntity> threats,int radius,int minimum) {
        if(threats.isEmpty()) return null;
        double best=distance(mob.blockPosition(),threats); Path selected=null;
        for(int attempt=0;attempt<16;attempt++) {
            BlockPos target=randomFoot(mob,radius);
            if(distance(target,threats)<=best || distance(target,threats)<(double)minimum*minimum) continue;
            Path path=SourceMovement032Goal.pathTo(mob,target,radius); BlockPos finalPoint=path==null ? null : end(path);
            if(finalPoint==null || distance(finalPoint,threats)<=best || distance(finalPoint,threats)<(double)minimum*minimum) continue;
            best=distance(finalPoint,threats); selected=path;
        }
        return selected;
    }
    public static final class Flee extends SourceMovement032Goal implements Marker {
        private static final int REPLAN_INTERVAL=10;
        private final float panicChance;
        private int replanTicks;
        private net.minecraft.world.phys.Vec3 plannedThreat;
        public Flee(PathfinderMob mob,float speed,float panicChance) { super(mob,speed,true); this.panicChance=panicChance; }
        public void startPanicking() { R196AnimalAi032.state(mob).putInt("panic_countdown",100+mob.getRandom().nextInt(100)); }
        public boolean panicking() {
            if(mob.isOnFire() || R196AnimalAi032.spooked(mob)) startPanicking();
            return R196AnimalAi032.state(mob).getInt("panic_countdown")>0;
        }
        @Override public boolean canUse() {
            if(controlled()) return false;
            var state=R196AnimalAi032.state(mob);
            if(state.getBoolean("done_panicking") && mob.level().getRandom().nextInt(10000)==0) state.putBoolean("done_panicking",false);
            return R196AnimalAi032.considerFleeing(mob) || panicking();
        }
        @Override protected Path movementPath() {
            LivingEntity attacker=R196AnimalAi032.lastAttacker(mob);
            if(attacker!=null) {
                Path path=AnimalEscapePath.find(mob,attacker);
                if(path!=null) {
                    R196AnimalAi032.state(mob).putBoolean("fleeing",true);
                    if(mob instanceof Wolf wolf) NativeAnimalSpecial032.onFleeing(wolf);
                }
                // A known attacker never turns a failed escape search into a random run toward it.
                return path;
            }
            if(mob.getRandom().nextFloat()<panicChance) startPanicking();
            return randomPath();
        }
        @Nullable private Path randomPath() {
            var point=net.minecraft.world.entity.ai.util.DefaultRandomPos.getPos(mob,5,4);
            return point==null ? null : pathTo(mob,BlockPos.containing(point),48);
        }
        @Override public void start() {
            if(!R196AnimalAi032.state(mob).getBoolean("done_panicking") && mob.getRandom().nextFloat()<panicChance) startPanicking();
            if(!panicking()) R196AnimalAi032.state(mob).putBoolean("done_panicking",true);
            rememberPlan();
            super.start();
        }
        @Override public void tick() {
            super.tick();
            var state=R196AnimalAi032.state(mob); int before=state.getInt("panic_countdown");
            if(mob.isOnFire()) startPanicking();
            else if(before>0) { state.putInt("panic_countdown",before-1); if(before==1) state.putBoolean("done_panicking",true); }
            if(--replanTicks>0) return;
            replanTicks=REPLAN_INTERVAL;
            LivingEntity attacker=R196AnimalAi032.lastAttacker(mob);
            boolean moved=attacker!=null && (plannedThreat==null || attacker.position().distanceToSqr(plannedThreat)>=4D);
            if(moved || isStuck() || mob.getNavigation().isDone()) {
                rememberPlan();
                replan();
            }
        }
        private void rememberPlan() {
            LivingEntity attacker=R196AnimalAi032.lastAttacker(mob);
            plannedThreat=attacker==null ? null : attacker.position();
            replanTicks=REPLAN_INTERVAL;
        }
        private boolean controlled() { return mob.isPassenger() || mob.getFirstPassenger() instanceof Player; }
        @Override protected float movementSpeed() { return (movementSpeed+(panicking() ? .2F : 0F))*.95F; }
        @Override public boolean canContinueToUse() {
            if(controlled()) return false;
            R196AnimalAi032.considerStopFleeing(mob);
            return R196AnimalAi032.state(mob).getBoolean("decided_to_flee") || panicking();
        }
        @Override public void stop() { R196AnimalAi032.considerStopFleeing(mob); super.stop(); }
    }
    public static final class FoodItems extends SourceMovement032Goal implements Marker {
        private ItemEntity target;
        public FoodItems(PathfinderMob mob) { super(mob,1F,true); }
        @Override public boolean canUse() {
            if(R196AnimalAi032.isSpookedOrFleeing(mob)) return false;
            if(mob instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isTame() && pet.isOrderedToSit()) return false;
            if(mob.getPersistentData().getInt(R196NpcNeeds.COOLDOWN)>0 || mob.getRandom().nextInt(40)!=0 || mob.isBaby()) return false;
            return !candidates().isEmpty();
        }
        private List<ItemEntity> candidates() {
            return mob.level().getEntitiesOfClass(ItemEntity.class,mob.getBoundingBox().inflate(16,4,16),
                    item->item.isAlive() && !item.hasPickUpDelay() && R196NpcNeeds.food(mob,item.getItem()));
        }
        @Override protected Path movementPath() {
            int shortest=Integer.MAX_VALUE; Path selected=null; target=null;
            for(ItemEntity item:candidates()) {
                Path path=pathTo(mob,item.blockPosition(),16);
                if(path==null || path.getNodeCount()>=shortest || end(path)==null || !end(path).equals(item.blockPosition())) continue;
                selected=path; shortest=path.getNodeCount(); target=item;
            }
            return selected;
        }
        @Override public boolean canContinueToUse() {
            return !R196AnimalAi032.isSpookedOrFleeing(mob) && target!=null && target.isAlive()
                    && mob.getPersistentData().getInt(R196NpcNeeds.COOLDOWN)==0 && super.canContinueToUse();
        }
        @Override public void stop() { super.stop(); target=null; }
    }
    public static final class AvoidPredators extends SourceMovement032Goal implements Marker {
        private List<LivingEntity> predators=List.of();
        public AvoidPredators(PathfinderMob mob,float speed) { super(mob,speed,true); }
        @Override public boolean canUse() {
            if(mob.getRandom().nextInt(10)!=0) return false;
            var found=new ArrayList<>(R196AnimalAi032.predatorsNear(mob,mob.getBoundingBox().inflate(32,4,32)));
            if(mob instanceof AbstractHorse horse && NativeAnimalSpecial032.isShy(horse))
                found.addAll(mob.level().getEntitiesOfClass(Player.class,mob.getBoundingBox().inflate(32,4,32)));
            predators=found; return distance(mob.blockPosition(),predators)<256D;
        }
        @Override protected Path movementPath() { return away(mob,predators,32,0); }
        @Override protected float movementSpeed() {
            return movementSpeed*(predators.stream().anyMatch(e->e instanceof net.minecraft.world.entity.Mob hunter && hunter.getTarget()==mob) ? 1.2F : 1F);
        }
        @Override public void stop() { super.stop(); predators=List.of(); }
    }
    public static final class AvoidFire extends SourceMovement032Goal implements Marker {
        private List<BlockPos> fires=List.of();
        public AvoidFire(PathfinderMob mob) { super(mob,1F,true); }
        @Override public boolean canUse() {
            if(mob.getRandom().nextInt(10)!=0 || mob.fireImmune()) return false;
            return !nearFires(8,2,1).isEmpty();
        }
        private List<BlockPos> nearFires(int horizontal,int vertical,int maximum) {
            var found=new ArrayList<BlockPos>(); BlockPos origin=mob.blockPosition();
            for(BlockPos pos:BlockPos.betweenClosed(origin.offset(-horizontal,-vertical,-horizontal),origin.offset(horizontal,vertical,horizontal))) {
                if(mob.level().hasChunkAt(pos) && mob.level().getBlockState(pos).getBlock() instanceof BaseFireBlock) {
                    found.add(pos.immutable()); if(found.size()>=maximum) break;
                }
            }
            found.sort(java.util.Comparator.comparingDouble(p->p.distSqr(origin))); return found;
        }
        private double fireDistance(BlockPos pos) { return fires.stream().mapToDouble(p->p.distSqr(pos)).min().orElse(Double.MAX_VALUE); }
        @Override protected Path movementPath() {
            fires=nearFires(16,4,Integer.MAX_VALUE).stream().limit(16).toList(); if(fires.isEmpty()) return null;
            double best=fireDistance(mob.blockPosition()); Path selected=null;
            for(int attempt=0;attempt<16;attempt++) {
                BlockPos target=randomFoot(mob,8); if(fireDistance(target)<=best) continue;
                Path path=pathTo(mob,target,8); BlockPos point=path==null ? null : end(path);
                if(point==null || fireDistance(point)<=best) continue;
                best=fireDistance(point); selected=path;
            }
            return selected;
        }
    }
    public static final class LeaveFluid extends SourceMovement032Goal implements Marker {
        private final boolean lava;
        public LeaveFluid(PathfinderMob mob,float speed,boolean lava) { super(mob,speed,true); this.lava=lava; }
        public boolean leavesLava() { return lava; }
        @Override public boolean canUse() {
            if(!lava && (mob.getControllingPassenger() instanceof Player || Livestock032.sourceLivestock(mob) && Livestock032.isThirsty(mob))) return false;
            return mob.getRandom().nextInt(16)==0 && (lava ? mob.isInLava()
                    : !mob.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD) && mob.isInWater());
        }
        @Override protected Path movementPath() {
            double best=Double.MAX_VALUE; Path selected=null;
            for(int attempt=0;attempt<16;attempt++) {
                BlockPos target=randomFoot(mob,16);
                if(!passable(mob,target,false) || passable(mob,target.below(),true) || occupied(mob,target)) continue;
                double d=target.distSqr(mob.blockPosition()); if(d>=best) continue;
                Path path=pathTo(mob,target,16); BlockPos point=path==null ? null : end(path);
                if(point==null || passable(mob,point.below(),true) || occupied(mob,point)) continue;
                d=point.distSqr(mob.blockPosition()); if(d>=best) continue; best=d; selected=path;
            }
            if(selected==null && mob.getPersistentData().getCompound(R196AnimalAi032.STATE).contains("spawn"))
                selected=pathTo(mob,BlockPos.of(R196AnimalAi032.state(mob).getLong("spawn")),16);
            return selected;
        }
        @Override public boolean canContinueToUse() {
            return (lava || !Livestock032.sourceLivestock(mob) || !Livestock032.isThirsty(mob)) && super.canContinueToUse();
        }
    }
    public static final class RainShelter extends SourceMovement032Goal implements Marker {
        public RainShelter(PathfinderMob mob) { super(mob,1F,true); }
        @Override public boolean canUse() {
            return mob.getRandom().nextInt(16)==0 && (!Livestock032.sourceLivestock(mob) || !Livestock032.isThirsty(mob)) && mob.isInWaterOrRain() && mob.level().isRainingAt(mob.blockPosition());
        }
        @Override protected Path movementPath() {
            double best=Double.MAX_VALUE; Path selected=null;
            for(int attempt=0;attempt<16;attempt++) {
                BlockPos target=randomFoot(mob,16);
                if(!passable(mob,target,false) || mob.level().isRainingAt(target) || occupied(mob,target)) continue;
                double d=target.distSqr(mob.blockPosition()); if(d>=best) continue;
                Path path=pathTo(mob,target,16); BlockPos point=path==null ? null : end(path);
                if(point==null || mob.level().isRainingAt(point) || occupied(mob,point)) continue;
                d=point.distSqr(mob.blockPosition()); if(d>=best) continue; best=d; selected=path;
            }
            return selected;
        }
        @Override public boolean canContinueToUse() {
            return (!Livestock032.sourceLivestock(mob) || !Livestock032.isThirsty(mob)) && super.canContinueToUse();
        }
    }
}

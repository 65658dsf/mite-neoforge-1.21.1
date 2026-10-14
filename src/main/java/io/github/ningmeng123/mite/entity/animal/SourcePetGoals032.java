package io.github.ningmeng123.mite.entity.animal;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** R196 pq/qu/pw/pi/qf/qg/qq behavior, without native pet teleport or modern prey defaults. @author NingMeng123 */
public final class SourcePetGoals032 {
    private SourcePetGoals032() {}
    public static final class FollowOwner extends Goal {
        private final TamableAnimal pet; private final double stop; private LivingEntity owner; private int recalc; private float water;
        public FollowOwner(TamableAnimal pet,double stop) { this.pet=pet; this.stop=stop; setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public boolean canUse() { owner=pet.getOwner(); return owner!=null && !pet.isOrderedToSit() && pet.distanceToSqr(owner)>=100; }
        @Override public boolean canContinueToUse() { return owner!=null && owner.isAlive() && !pet.isOrderedToSit() && !pet.getNavigation().isDone() && pet.distanceToSqr(owner)>stop*stop; }
        @Override public void start() { recalc=0; water=pet.getPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER); pet.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER,0); }
        @Override public void stop() { pet.getNavigation().stop(); pet.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER,water); owner=null; }
        @Override public void tick() {
            pet.getLookControl().setLookAt(owner,10,pet.getMaxHeadXRot());
            if(--recalc<=0) { recalc=10; if(!pet.getNavigation().moveTo(owner,1) && !pet.isLeashed() && pet.distanceToSqr(owner)>=144
                    && io.github.ningmeng123.mite.entity.MobCombat.ticksWithOffset(pet)%10==0 && pet.getRandom().nextFloat()<.2F && pet instanceof Wolf)
                pet.playSound(SoundEvents.WOLF_AMBIENT,4,1); }
        }
    }
    public static final class Leap extends Goal {
        private final PathfinderMob mob; private final float height; private LivingEntity target;
        public Leap(PathfinderMob mob,float height) { this.mob=mob; this.height=height; setFlags(EnumSet.of(Flag.MOVE,Flag.JUMP)); }
        @Override public boolean canUse() { target=mob.getTarget(); if(target==null) return false; double d=mob.distanceToSqr(target); return d>=4 && d<=16 && mob.onGround() && mob.getRandom().nextInt(5)==0; }
        @Override public boolean canContinueToUse() { return !mob.onGround(); }
        @Override public void start() {
            double dx=target.getX()-mob.getX(),dz=target.getZ()-mob.getZ(),length=Math.sqrt(dx*dx+dz*dz); Vec3 old=mob.getDeltaMovement();
            if(length>0) mob.setDeltaMovement(old.x*1.2+dx/length*.4,height,old.z*1.2+dz/length*.4);
            mob.setYRot((float)(Math.atan2(dz,dx)*180/Math.PI)-90);
        }
    }
    public static final class Beg extends Goal {
        private final Wolf wolf; private Player player; private int remaining;
        public Beg(Wolf wolf) { this.wolf=wolf; setFlags(EnumSet.of(Flag.LOOK)); }
        private boolean interested() { var item=player.getMainHandItem(); return (!wolf.isTame() && item.is(Items.BONE)) || io.github.ningmeng123.mite.entity.R196NpcNeeds.meat(item); }
        private boolean calm() { return wolf.getTarget()==null && !wolf.getPersistentData().getBoolean(NativeAnimalSpecial032.HOSTILE); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public boolean canUse() { player=wolf.level().getNearestPlayer(wolf,8); return calm() && player!=null && interested(); }
        @Override public boolean canContinueToUse() { return calm() && player!=null && player.isAlive() && wolf.distanceToSqr(player)<=64 && remaining>0 && interested(); }
        @Override public void start() { wolf.setIsInterested(true); remaining=40+wolf.getRandom().nextInt(40); }
        @Override public void tick() { wolf.getLookControl().setLookAt(player,10,wolf.getMaxHeadXRot()); --remaining; }
        @Override public void stop() { wolf.setIsInterested(false); player=null; }
    }
    public static final class Prey extends Goal {
        private final Animal animal; private final Class<? extends LivingEntity> type; private final int chance; private final boolean sight; private final Predicate<LivingEntity> filter; private LivingEntity target; private int unseen;
        public Prey(Animal animal,Class<? extends LivingEntity> type,int chance,boolean sight) { this(animal,type,chance,sight,e->true); }
        public Prey(Animal animal,Class<? extends LivingEntity> type,int chance,boolean sight,Predicate<LivingEntity> filter) { this.animal=animal; this.type=type; this.chance=chance; this.sight=sight; this.filter=filter; setFlags(EnumSet.of(Flag.TARGET)); }
        private boolean hungry() { return !animal.isBaby() && animal.getAge()==0 && !animal.isInLove() && !(animal instanceof TamableAnimal pet && pet.isTame()); }
        private LivingEntity mount(LivingEntity entity) { return entity!=null && entity.getVehicle() instanceof LivingEntity living && !living.isInvulnerable()?living:entity; }
        private LivingEntity nearest() {
            double range=animal.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
            LivingEntity result=animal.level().getEntitiesOfClass(type,animal.getBoundingBox().inflate(range,6,range),e->e!=animal && e.isAlive() && filter.test(e) && animal.canAttack(e) && (!sight || animal.getSensing().hasLineOfSight(e)))
                    .stream().min(Comparator.comparingDouble(animal::distanceToSqr)).orElse(null);return mount(result);
        }
        @Override public boolean canUse() {
            if(!hungry() || animal.getRandom().nextInt(chance)!=0) return false;
            target=nearest();return target!=null;
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { target=mount(target);animal.setTarget(target);unseen=0;SourceAnimalDespawn032.refresh(animal,-1200); }
        @Override public boolean canContinueToUse() {
            if(!hungry() || target==null || !target.isAlive() || animal.getTarget()!=target || animal.distanceToSqr(target)>Math.pow(animal.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE),2))return false;
            if(sight) { if(animal.getSensing().hasLineOfSight(target))unseen=0;else if(++unseen>60)return false; }return true;
        }
        @Override public void tick() {
            if(animal.getRandom().nextInt(40)==0) {
                LivingEntity candidate=nearest();
                if(candidate!=null && candidate!=target && animal.getSensing().hasLineOfSight(candidate)) { stop();target=candidate;start(); }
            }
        }
        @Override public void stop() { if(animal.getTarget()==target) animal.setTarget(null); target=null; }
    }
    public static final class HostilePlayers extends Goal {
        private final Wolf wolf; private Player player;
        public HostilePlayers(Wolf wolf) { this.wolf=wolf; setFlags(EnumSet.of(Flag.TARGET)); }
        @Override public boolean canUse() {
            if(!wolf.getPersistentData().getBoolean(NativeAnimalSpecial032.HOSTILE) || wolf.isTame()) return false;
            player=wolf.level().getNearestPlayer(wolf.getX(),wolf.getY(),wolf.getZ(),wolf.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE),p->p instanceof Player player && !player.isCreative()&&!player.isSpectator()&&player.isAlive());
            return player!=null && wolf.getSensing().hasLineOfSight(player);
        }
        @Override public void start() { wolf.setTarget(player); }
        @Override public boolean canContinueToUse() { return player!=null && player.isAlive() && wolf.getPersistentData().getBoolean(NativeAnimalSpecial032.HOSTILE) && wolf.getTarget()==player; }
        @Override public void stop() { player=null; }
    }
    public static final class Tempt extends Goal {
        private final Animal animal; private final double speed; private final Predicate<ItemStack> food; private final boolean scared;
        private Player player; private int cooldown; private Vec3 lastPosition; private float lastPitch,lastYaw,oldWater; private boolean running;
        public Tempt(Animal animal,boolean large) { this(animal,.6,large?NativeAnimalSpecial032::largeFish:stack->stack.is(Items.COD),true); }
        public Tempt(Animal animal,double speed,Predicate<ItemStack> food,boolean scared) {
            this.animal=animal;this.speed=speed;this.food=food;this.scared=scared;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));
        }
        public boolean active() { return running; }
        public Player temptingPlayer() { return player; }
        private boolean match(Player p) { return food.test(p.getMainHandItem()); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public boolean canUse() {
            if(cooldown>0) { --cooldown; return false; }
            if(Livestock032.sourceLivestock(animal) && animal.getHealth()>=animal.getMaxHealth() && !Livestock032.needs(animal).well() && !Livestock032.isHungry(animal)) return false;
            Player previous=player; double best=Double.MAX_VALUE; Player closest=null;
            for(Player p:animal.level().players()) {
                double distance=animal.distanceTo(p); if(distance>10) continue;
                if(animal.getSensing().hasLineOfSight(p)) { if(!match(p))continue;distance+=p==previous?-4:0; }
                else {
                    if(p!=previous)continue;Path path=SourceMovement032Goal.pathTo(animal,p.blockPosition(),16);
                    if(path==null || path.getEndNode()==null || !path.getEndNode().asBlockPos().equals(p.blockPosition()))continue;
                    distance+=4;
                }
                if(distance<best) { best=distance; closest=p; }
            }
            player=closest; return player!=null;
        }
        @Override public boolean canContinueToUse() {
            if(player==null || !player.isAlive()) return false;
            if(scared && animal.distanceToSqr(player)<36) {
                if(lastPosition!=null && player.position().distanceToSqr(lastPosition)>.01 || Math.abs(player.getXRot()-lastPitch)>5 || Math.abs(player.getYRot()-lastYaw)>5) return false;
            } else lastPosition=player.position();
            lastPitch=player.getXRot(); lastYaw=player.getYRot(); return canUse();
        }
        @Override public void start() {
            running=true;lastPosition=player.position();lastPitch=player.getXRot();lastYaw=player.getYRot();oldWater=animal.getPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER);
            animal.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER,0);
        }
        @Override public void tick() { animal.getLookControl().setLookAt(player,30,animal.getMaxHeadXRot()); if(animal.distanceToSqr(player)<6.25) animal.getNavigation().stop(); else animal.getNavigation().moveTo(player,speed); }
        @Override public void stop() { player=null; animal.getNavigation().stop(); animal.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER,oldWater); cooldown=100; running=false; }
    }
    public static final class AvoidPlayer extends AvoidEntityGoal<Player> {
        private final Animal cat;
        public AvoidPlayer(Animal cat) { super(cat,Player.class,16,.8,1.33,p->p instanceof Player player && !player.isCreative()&&!player.isSpectator()); this.cat=cat; }
        @Override public boolean canUse() { return !(cat instanceof Cat c && c.isTame()) && super.canUse(); }
        @Override public boolean canContinueToUse() { return !(cat instanceof Cat c && c.isTame()) && super.canContinueToUse(); }
    }
    public static final class CatAttack extends Goal {
        private final Animal cat; private LivingEntity target; private int delay;
        public CatAttack(Animal cat) { this.cat=cat; setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public boolean canUse() { target=cat.getTarget(); return target!=null; }
        @Override public boolean canContinueToUse() { return target!=null && target.isAlive() && cat.distanceToSqr(target)<=225 && (!cat.getNavigation().isDone() || canUse()); }
        @Override public void stop() { target=null; cat.getNavigation().stop(); }
        @Override public void tick() {
            cat.getLookControl().setLookAt(target,30,30); double d=cat.distanceToSqr(target.getX(),target.getY(),target.getZ()),reach=cat.getBbWidth()*cat.getBbWidth()*4;
            double speed=d>reach && d<16?1.33:d<225?.6:.8; cat.getNavigation().moveTo(target,speed); delay=Math.max(0,delay-1);
            if(d<=reach && delay==0) { delay=20; target.hurt(cat.damageSources().mobAttack(cat),3); }
        }
    }
    /** Old rx can mate only when tamed; the modern wild Ocelot lacks that restriction. */
    public static final class CatBreed extends net.minecraft.world.entity.ai.goal.BreedGoal {
        private final Animal cat;
        public CatBreed(Animal cat) { super(cat,.8);this.cat=cat; }
        @Override public boolean canUse() { return cat instanceof Cat tame && tame.isTame() && super.canUse(); }
        @Override public boolean canContinueToUse() { return cat instanceof Cat tame && tame.isTame() && super.canContinueToUse(); }
    }
    public static final class CatPerch extends Goal {
        private final Cat cat; private BlockPos perch; private int elapsed,missed,duration;
        public CatPerch(Cat cat) { this.cat=cat; setFlags(EnumSet.of(Flag.MOVE,Flag.JUMP)); }
        private boolean valid(BlockPos pos) {
            var state=cat.level().getBlockState(pos);
            if(state.getBlock() instanceof ChestBlock && cat.level().getBlockEntity(pos) instanceof ChestBlockEntity chest) return ChestBlockEntity.getOpenCount(cat.level(),pos)==0;
            if(state.getBlock() instanceof AbstractFurnaceBlock) return state.hasProperty(AbstractFurnaceBlock.LIT)&&state.getValue(AbstractFurnaceBlock.LIT);
            return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART)==BedPart.FOOT;
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public boolean canUse() {
            if(!cat.isTame()||cat.isOrderedToSit()||cat.getRandom().nextDouble()>.0065) return false;
            BlockPos center=cat.blockPosition(); double best=Double.MAX_VALUE; perch=null;
            for(int x=-8;x<8;x++)for(int z=-8;z<8;z++) {
                BlockPos p=center.offset(x,0,z); double d=cat.distanceToSqr(Vec3.atCenterOf(p));
                if(d<best && valid(p) && cat.level().isEmptyBlock(p.above())) { perch=p; best=d; }
            }
            return perch!=null;
        }
        @Override public void start() { elapsed=missed=0; duration=cat.getRandom().nextInt(cat.getRandom().nextInt(1200)+1200)+1200; cat.setOrderedToSit(false); cat.getNavigation().moveTo(perch.getX()+.5,perch.getY()+1,perch.getZ()+.5,1.33); }
        @Override public boolean canContinueToUse() { return perch!=null && elapsed<=duration && missed<=60 && valid(perch); }
        @Override public void tick() { ++elapsed; cat.setOrderedToSit(false); if(cat.distanceToSqr(perch.getX(),perch.getY()+1,perch.getZ())>1) { cat.setInSittingPose(false); cat.getNavigation().moveTo(perch.getX()+.5,perch.getY()+1,perch.getZ()+.5,1.33); ++missed; } else { cat.setInSittingPose(true); --missed; } }
        @Override public void stop() { cat.setInSittingPose(false); perch=null; }
    }
    public static final class HorseRunCrazy extends Goal {
        private final AbstractHorse horse; private Vec3 destination;
        public HorseRunCrazy(AbstractHorse horse) { this.horse=horse; setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public boolean canUse() { if(horse.isTamed()||!horse.isVehicle()) return false; destination=DefaultRandomPos.getPos(horse,5,4); return destination!=null; }
        @Override public void start() { horse.getNavigation().moveTo(destination.x,destination.y,destination.z,1.2); }
        @Override public boolean canContinueToUse() { return !horse.getNavigation().isDone() && horse.isVehicle() && !horse.isTamed(); }
        @Override public void tick() { if(horse.getRandom().nextInt(50)==0) resolveRideAttempt(horse); }
    }
    public static void resolveRideAttempt(AbstractHorse horse) {
        Entity rider=horse.getFirstPassenger(); if(rider==null) return;
        if(rider instanceof Player player) {
            if(horse.getMaxTemper()>0 && horse.getRandom().nextInt(horse.getMaxTemper())<horse.getTemper() && !net.neoforged.neoforge.event.EventHooks.onAnimalTame(horse,player)) { horse.tameWithName(player); horse.getPersistentData().putInt(NativeAnimalSpecial032.HORSE_EATING,0); horse.getPersistentData().putInt(NativeAnimalSpecial032.HORSE_RIDING,0); return; }
            horse.modifyTemper(5); horse.getPersistentData().putInt(NativeAnimalSpecial032.HORSE_RIDING,4000);
        }
        if(rider instanceof LivingEntity living && horse.getRandom().nextInt(2)==0) living.fallDistance=3;
        horse.ejectPassengers(); horse.makeMad(); horse.level().broadcastEntityEvent(horse,(byte)6);
    }
}

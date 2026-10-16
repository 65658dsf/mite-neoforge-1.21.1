package io.github.ningmeng123.mite.entity.animal;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.MobCombat;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Bat;
import io.github.ningmeng123.mite.entity.R196Mob;
import io.github.ningmeng123.mite.entity.R196NpcNeeds;
import io.github.ningmeng123.mite.entity.R196Wolf;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Server-authoritative source animal fear/memory, peer warnings and enabled common goal registration. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class R196AnimalAi032 {
    public static final String STATE="mite_animal_ai_032";
    public static final double ALARM_RADIUS=16D;
    private R196AnimalAi032() {}
    public static boolean managed(Mob mob) {
        var type=mob.getType();
        return mob instanceof R196Wolf || type==EntityType.COW || type==EntityType.MOOSHROOM || type==EntityType.SHEEP || type==EntityType.PIG
                || type==EntityType.CHICKEN || type==EntityType.WOLF || type==EntityType.OCELOT || type==EntityType.CAT
                || type==EntityType.HORSE || type==EntityType.DONKEY || type==EntityType.MULE || type==EntityType.ZOMBIE_HORSE || type==EntityType.SKELETON_HORSE;
    }
    public static boolean hellhound(Mob mob) { return mob instanceof R196Mob source && source.miteKind()==MobKind.HELLHOUND; }
    public static boolean herdAnimal(Mob mob) {
        return managed(mob) && (Livestock032.sourceLivestock(mob) || mob instanceof AbstractHorse
                && !mob.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD));
    }
    public static CompoundTag state(Mob mob) {
        var data=mob.getPersistentData();
        if (!data.contains(STATE)) { var state=new CompoundTag(); state.putInt("version",1); data.put(STATE,state); }
        return data.getCompound(STATE);
    }
    @Nullable public static LivingEntity lastAttacker(Mob mob) {
        var state=state(mob);
        if (state.getInt("attacker_memory")<=0 || !state.hasUUID("attacker") || !(mob.level() instanceof ServerLevel level)) return null;
        Entity candidate=level.getEntity(state.getUUID("attacker"));
        return candidate instanceof LivingEntity living && living.isAlive() ? living : null;
    }
    public static void rememberAttacker(Mob mob,LivingEntity attacker) {
        var state=state(mob); state.putUUID("attacker",attacker.getUUID()); state.putInt("attacker_memory",900+mob.getRandom().nextInt(200));
    }
    public static boolean considerFleeing(Mob mob) {
        var state=state(mob); LivingEntity attacker=lastAttacker(mob); boolean decision;
        if (Livestock032.sourceLivestock(mob) || mob instanceof AbstractHorse)
            decision=attacker!=null && mob.distanceTo(attacker)<32F;
        else decision=attacker!=null && !mob.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)
                && mob.getHealth()/mob.getMaxHealth()<.2F && mob.distanceTo(attacker)<=32F && mob.getRandom().nextInt(2)==0;
        state.putBoolean("decided_to_flee",decision); return decision;
    }
    public static boolean considerStopFleeing(Mob mob) {
        var state=state(mob); LivingEntity attacker=lastAttacker(mob);
        boolean healthy=!Livestock032.sourceLivestock(mob) && !(mob instanceof AbstractHorse) && mob.getHealth()/mob.getMaxHealth()>=.2F;
        if(attacker==null || healthy) { state.putBoolean("decided_to_flee",false); state.putBoolean("fleeing",false); return true; }
        if(mob.distanceTo(attacker)>40F) { state.putBoolean("decided_to_flee",false); state.putBoolean("fleeing",false); return true; }
        return false;
    }
    public static boolean spooked(Mob mob) { return state(mob).getLong("spooked_until")>mob.level().getGameTime(); }
    public static void setSpooked(Mob mob,long until) { state(mob).putLong("spooked_until",until); }
    public static boolean isSpookedOrFleeing(Mob mob) {
        var state=state(mob); return spooked(mob) || state.getBoolean("decided_to_flee") || state.getBoolean("fleeing") || state.getInt("panic_countdown")>0;
    }
    public static boolean potentialPredator(Mob prey,LivingEntity candidate) {
        if(candidate==prey || !candidate.isAlive()) return false;
        boolean trueAnimal=prey instanceof Animal && managed(prey) && !hellhound(prey);
        if(candidate instanceof Wolf wolf && (wolf.getType()==EntityType.WOLF || wolf instanceof R196Wolf))
            return !wolf.isBaby() && !wolf.isTame() && prey instanceof Animal;
        if(candidate instanceof R196Bat bat) return trueAnimal && bat.preysUpon(prey);
        if(candidate.getType()==EntityType.SQUID) return trueAnimal && !SourceAquatic032.blueNight(candidate.level());
        if(candidate.getType()==EntityType.ZOMBIE || candidate instanceof R196Mob source
                && (source.miteKind()==MobKind.REVENANT || source.miteKind()==MobKind.GHOUL)) return prey instanceof Animal;
        if(candidate.getType()==EntityType.SILVERFISH || candidate instanceof R196Mob source
                && (source.miteKind().family()==MobKind.Family.SILVERFISH || source.miteKind().family()==MobKind.Family.CUBE)) return trueAnimal;
        // Source rx has a chicken/bat target goal but no preysUpon override, so is deliberately absent here.
        if(prey.getType()==EntityType.CHICKEN && candidate instanceof Spider
                && (candidate.getType()==EntityType.SPIDER || candidate.getType()==EntityType.CAVE_SPIDER || candidate instanceof R196Mob)) return true;
        // Modern/foreign species have no source predicate: respect an actual hunting target, never guess by name.
        return candidate instanceof Mob modern && R196MobSpawning.sourceClass(modern)==null && modern.getTarget()==prey;
    }
    public static List<LivingEntity> predatorsNear(Mob prey,AABB bounds) {
        return prey.level().getEntitiesOfClass(LivingEntity.class,bounds,candidate->potentialPredator(prey,candidate));
    }
    @SubscribeEvent public static void install(EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide || !(event.getEntity() instanceof PathfinderMob mob) || !managed(mob)) return;
        if(mob.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof AnimalCommon032Goals.Marker)) return;
        if(!state(mob).contains("spawn")) state(mob).putLong("spawn",mob.blockPosition().asLong());
        mob.goalSelector.removeAllGoals(g->g instanceof PanicGoal);
        mob.goalSelector.removeAllGoals(g->g instanceof net.minecraft.world.entity.ai.goal.FloatGoal);
        mob.goalSelector.addGoal(mob instanceof Wolf || mob.getType()==EntityType.OCELOT || mob.getType()==EntityType.CAT ? 1 : 0,new AnimalCommon032Goals.Float(mob));
        mob.setPathfindingMalus(PathType.WATER,mob.getType()==EntityType.CHICKEN ? 0F : 8F);
        if(hellhound(mob)) {
            mob.goalSelector.addGoal(4,new AnimalCommon032Goals.LeaveFluid(mob,1F,true));
            mob.goalSelector.addGoal(4,new AnimalCommon032Goals.LeaveFluid(mob,1F,false)); return;
        }
        mob.goalSelector.addGoal(1,new AnimalCommon032Goals.FoodItems(mob));
        mob.goalSelector.addGoal(2,new AnimalCommon032Goals.AvoidFire(mob));
        if(Livestock032.sourceLivestock(mob)) {
            float speed=mob.getType()==EntityType.COW || mob.getType()==EntityType.MOOSHROOM ? 1.6F : mob.getType()==EntityType.CHICKEN ? 1.3F : 1.4F;
            mob.goalSelector.addGoal(1,new AnimalCommon032Goals.Flee(mob,speed,mob.getType()==EntityType.CHICKEN ? .75F : .5F));
            mob.goalSelector.addGoal(2,new AnimalCommon032Goals.AvoidPredators(mob,1F));
            mob.goalSelector.addGoal(4,new AnimalCommon032Goals.RainShelter(mob));
            mob.goalSelector.addGoal(4,new AnimalCommon032Goals.LeaveFluid(mob,1F,false));
        } else if(mob instanceof AbstractHorse) {
            mob.goalSelector.addGoal(1,new AnimalCommon032Goals.Flee(mob,1.2F,.25F));
            mob.goalSelector.addGoal(2,new AnimalCommon032Goals.AvoidPredators(mob,1.05F));
            mob.goalSelector.addGoal(4,new AnimalCommon032Goals.RainShelter(mob));
            mob.goalSelector.addGoal(4,new AnimalCommon032Goals.LeaveFluid(mob,1F,false));
        } else if(mob instanceof Wolf) {
            mob.goalSelector.addGoal(1,new AnimalCommon032Goals.Flee(mob,1F,0F));
            mob.goalSelector.addGoal(4,new AnimalCommon032Goals.LeaveFluid(mob,1F,false));
        }
    }
    /** Equal-priority goals compete in insertion order, matching rp -> livestock -> species construction. */
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void orderGoals(EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob) || !managed(mob)) return;
        var goals=mob.goalSelector.getAvailableGoals().stream().filter(w->sourceGoalRank(mob,w.getGoal())>=0)
                .sorted(java.util.Comparator.comparingInt(w->sourceGoalRank(mob,w.getGoal()))).toList();
        for(var wrapped:goals) mob.goalSelector.removeGoal(wrapped.getGoal());
        for(var wrapped:goals) {
            int priority=wrapped.getPriority();
            if(mob.getType()==EntityType.PIG) {
                var goal=wrapped.getGoal();
                if(goal.getClass()==net.minecraft.world.entity.ai.goal.BreedGoal.class) priority=3;
                else if(goal.getClass()==net.minecraft.world.entity.ai.goal.FollowParentGoal.class) priority=5;
                else if(goal.getClass()==net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal.class) priority=6;
                else if(goal.getClass()==net.minecraft.world.entity.ai.goal.LookAtPlayerGoal.class) priority=7;
                else if(goal.getClass()==net.minecraft.world.entity.ai.goal.RandomLookAroundGoal.class) priority=8;
            }
            mob.goalSelector.addGoal(priority,wrapped.getGoal());
        }
    }
    private static int sourceGoalRank(Mob mob,net.minecraft.world.entity.ai.goal.Goal goal) {
        if(hellhound(mob)) {
            if(goal instanceof AnimalCommon032Goals.Float) return 0;
            if(goal instanceof AnimalCommon032Goals.LeaveFluid fluid) return fluid.leavesLava()?101:100;
            return 20;
        }
        if(goal instanceof AnimalCommon032Goals.FoodItems) return 0;
        if(goal instanceof AnimalCommon032Goals.AvoidFire) return 1;
        if(Livestock032.sourceLivestock(mob)) {
            if(goal instanceof LivestockSeeking032 seek) return seek.seeksWater()?3:2;
            if(goal instanceof LivestockFreedomGoal032) return 4;
            if(goal instanceof AnimalCommon032Goals.AvoidPredators) return 5;
            if(goal instanceof AnimalCommon032Goals.RainShelter) return 6;
            if(goal instanceof AnimalCommon032Goals.LeaveFluid) return 7;
            if(goal instanceof AnimalCommon032Goals.Float) return 8;
            if(goal.getClass()==net.minecraft.world.entity.ai.goal.BreedGoal.class) return 9;
            if(goal instanceof SourcePetGoals032.Tempt) return 10;
            if(goal.getClass()==net.minecraft.world.entity.ai.goal.FollowParentGoal.class) return 11;
            if(goal instanceof LivestockGrazingGoal032) return 12;
            if(goal.getClass()==net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal.class) return 13;
            if(goal.getClass()==net.minecraft.world.entity.ai.goal.LookAtPlayerGoal.class) return 14;
            if(goal.getClass()==net.minecraft.world.entity.ai.goal.RandomLookAroundGoal.class) return 15;
        } else {
            if(goal instanceof AnimalCommon032Goals.Float) return 8;
            // Dedicated source pet goals were installed in constructor order by NativeAnimalSpecial032.
            if(goal.getClass().getEnclosingClass()==SourcePetGoals032.class || goal.getClass()==net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal.class
                    || goal.getClass()==net.minecraft.world.entity.ai.goal.MeleeAttackGoal.class || goal.getClass()==net.minecraft.world.entity.ai.goal.BreedGoal.class
                    || goal.getClass()==net.minecraft.world.entity.ai.goal.FollowParentGoal.class || goal.getClass()==net.minecraft.world.entity.ai.goal.RandomStrollGoal.class
                    || goal.getClass()==net.minecraft.world.entity.ai.goal.LookAtPlayerGoal.class || goal.getClass()==net.minecraft.world.entity.ai.goal.RandomLookAroundGoal.class) return 20;
            if(goal instanceof AnimalCommon032Goals.AvoidPredators) return 101;
            if(goal instanceof AnimalCommon032Goals.RainShelter) return 102;
            if(goal instanceof AnimalCommon032Goals.LeaveFluid) return 103;
        }
        if(goal instanceof AnimalCommon032Goals.Flee) return 100;
        return -1;
    }
    @SubscribeEvent public static void damage(LivingDamageEvent.Post event) {
        if(event.getEntity().level().isClientSide || !(event.getEntity() instanceof Animal animal) || !managed(animal) || !(event.getNewDamage()>0F)) return;
        animal.resetLove();
        if(event.getSource().getEntity() instanceof LivingEntity attacker) {
            SourceAnimalDespawn032.refresh(animal,attacker instanceof net.minecraft.world.entity.player.Player?-9600:-1200);
            if(attacker instanceof Mob attackingMob && SourceAnimalDespawn032.managed(attackingMob)) SourceAnimalDespawn032.refresh(attackingMob,-1200);
            rememberAttacker(animal,attacker); considerFleeing(animal);
            if(attacker instanceof net.minecraft.world.entity.player.Player && herdAnimal(animal)) alarmHerd(animal,attacker);
            else if(!(animal instanceof TamableAnimal pet && pet.isTame())) warnPeers(animal,attacker);
        }
    }
    /** A real player hit, including arrows and fatal hits, immediately alerts nearby passive herds. */
    public static void alarmHerd(Mob source,LivingEntity attacker) {
        if(!(source.level() instanceof ServerLevel) || !herdAnimal(source)
                || !(attacker instanceof net.minecraft.world.entity.player.Player) || !attacker.isAlive()
                || attacker.level()!=source.level()) return;
        long until=Math.max(state(source).getLong("herd_alarm_until"),source.level().getGameTime()+200L+source.getRandom().nextInt(100));
        for(Mob peer:source.level().getEntitiesOfClass(Mob.class,source.getBoundingBox().inflate(ALARM_RADIUS))) {
            if(!peer.isAlive() || !herdAnimal(peer) || peer.distanceToSqr(source)>ALARM_RADIUS*ALARM_RADIUS) continue;
            rememberAttacker(peer,attacker);
            var fear=state(peer);
            fear.putLong("herd_alarm_until",Math.max(fear.getLong("herd_alarm_until"),until));
            setSpooked(peer,Math.max(fear.getLong("spooked_until"),until));
            fear.putBoolean("peer_spooked",true);
            considerFleeing(peer);
            if(peer instanceof Animal animal) animal.resetLove();
        }
    }
    public static void warnPeers(Mob source,LivingEntity attacker) {
        String identity=R196MobSpawning.sourceClass(source);
        for(Mob peer:source.level().getEntitiesOfClass(Mob.class,source.getBoundingBox().inflate(8,4,8))) {
            if(peer==source || !managed(peer) || !java.util.Objects.equals(identity,R196MobSpawning.sourceClass(peer))
                    || peer instanceof TamableAnimal pet && pet.isTame() || lastAttacker(peer)!=null) continue;
            rememberAttacker(peer,attacker);
            if(considerFleeing(peer)) { if(peer instanceof Animal animal) animal.resetLove(); }
            else { state(peer).remove("attacker"); state(peer).putInt("attacker_memory",0); }
        }
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if(event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob mob) || !managed(mob) || !mob.isAlive()) return;
        tickSource(mob);
    }
    public static void tickSource(Mob mob) {
        if(mob.level().isClientSide || !managed(mob) || !mob.isAlive()) return;
        var state=state(mob); int memory=state.getInt("attacker_memory");
        if(memory>0) state.putInt("attacker_memory",memory-1);
        if(state.getInt("attacker_memory")==0) { state.remove("attacker"); considerStopFleeing(mob); }
        else if(state.hasUUID("attacker") && mob.level() instanceof ServerLevel level && level.getEntity(state.getUUID("attacker")) instanceof LivingEntity attacker && !attacker.isAlive()) {
            state.putInt("attacker_memory",0); state.remove("attacker"); considerStopFleeing(mob);
        }
        if(!spooked(mob)) state.remove("spooked_until");
        if(state.getInt("attacker_memory")==0 && !spooked(mob)) state.remove("herd_alarm_until");
        if(mob.level().getGameTime()%4000L==0) state.putBoolean("peer_spooked",false);
        long alarmUntil=state.getLong("herd_alarm_until");
        LivingEntity threat=lastAttacker(mob);
        if(alarmUntil>0L && (alarmUntil<=mob.level().getGameTime() || threat==null)) return;
        if(mob.isNoAi() || !(Livestock032.sourceLivestock(mob) || alarmUntil>0L && herdAnimal(mob))
                || !(spooked(mob) || state.getBoolean("decided_to_flee") || state.getBoolean("fleeing")) || MobCombat.ticksWithOffset(mob)%20!=0) return;
        for(Mob peer:mob.level().getEntitiesOfClass(Mob.class,mob.getBoundingBox().inflate(8,4,8))) {
            if(peer==mob || !peer.isAlive() || !(alarmUntil>0L ? herdAnimal(peer) : Livestock032.sourceLivestock(peer))
                    || state(peer).getBoolean("peer_spooked") || spooked(peer) || state(peer).getBoolean("decided_to_flee") || state(peer).getBoolean("fleeing")
                    || !peer.getSensing().hasLineOfSight(mob) || !(peer instanceof PathfinderMob pathfinder)) continue;
            var path=SourceMovement032Goal.pathTo(pathfinder,mob.blockPosition(),8);
            if(path==null || path.getEndNode()==null || path.getEndNode().asBlockPos().distSqr(mob.blockPosition())>1D) continue;
            var peerState=state(peer);
            if(threat!=null) {
                peerState.putUUID("attacker",threat.getUUID());
                peerState.putInt("attacker_memory",state.getInt("attacker_memory"));
                considerFleeing(peer);
                if(peer instanceof Animal animal) animal.resetLove();
            }
            if(alarmUntil>0L) peerState.putLong("herd_alarm_until",alarmUntil);
            setSpooked(peer,alarmUntil>0L ? alarmUntil : mob.level().getGameTime()+400L+mob.level().getRandom().nextInt(400));
            peerState.putBoolean("peer_spooked",true);
        }
    }
}

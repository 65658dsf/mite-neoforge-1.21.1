package io.github.ningmeng123.mite.entity.animal;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Wolf;
import io.github.ningmeng123.mite.entity.R196NpcNeeds;
import io.github.ningmeng123.mite.registry.MiteFoodCatalog;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.rules.ProgressionRules;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.horse.*;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

/** Source sf/rx/rs pet interactions and dedicated goal ownership. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class NativeAnimalSpecial032 {
    public static final String TAME_COOLDOWN="mite_tame_cooldown_032", WOLF_TARGET_TIME="mite_wolf_target_time_032";
    public static final String HOSTILE="mite_wolf_hostile_032", HORSE_EATING="mite_horse_refuse_food_032", HORSE_RIDING="mite_horse_refuse_riding_032";
    private static final Map<Mob, SourcePetGoals032.Tempt> LARGE_FISH_TEMPT=Collections.synchronizedMap(new WeakHashMap<>());
    private static final java.util.Set<Mob> INSTALLED=Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
    private static final Map<TamableAnimal,Threat> THREATS=Collections.synchronizedMap(new WeakHashMap<>());
    private record Threat(Mob entity,int remaining) {}
    private NativeAnimalSpecial032() {}
    public static boolean wolf(Wolf wolf) { return wolf.getType()==EntityType.WOLF || wolf instanceof R196Wolf r && r.miteKind()==MobKind.DIRE_WOLF; }
    public static boolean horse(AbstractHorse horse) { return horse instanceof Horse || horse instanceof Donkey || horse instanceof Mule || horse instanceof SkeletonHorse || horse instanceof ZombieHorse; }
    public static boolean isShy(AbstractHorse horse) { return !horse.isBaby() && !horse.isTamed() && horse.getTemper()<=0; }
    public static boolean rawFish(ItemStack stack) { return stack.is(Items.COD) || largeFish(stack); }
    public static boolean largeFish(ItemStack stack) { return stack.is(io.github.ningmeng123.mite.registry.MiteFoods.RAW_LARGE_FISH.get()); }
    public static boolean installed(Mob mob) { return INSTALLED.contains(mob); }
    public static boolean shouldEat(Mob mob,ItemStack stack) {
        if (!(mob instanceof Animal animal) || animal.isBaby() || !animal.isAlive() || animal.getAge()!=0 || animal.isInLove()) return false;
        // rs inherits og.isFoodItem=false: hand feeding is a different path and dropped wheat is not accepted.
        if(mob instanceof AbstractHorse) return false;
        if(mob instanceof TamableAnimal pet && pet.isTame() && pet.getHealth()>pet.getMaxHealth()*.5F) return false;
        return mob instanceof Wolf w && wolf(w)?R196NpcNeeds.meat(stack):mob instanceof Cat || mob instanceof Ocelot?rawFish(stack):false;
    }
    public static void onFoodEaten(Mob mob,ItemStack stack) {
        if (!(mob instanceof Animal animal)) return;
        if(animal.getHealth()>=animal.getMaxHealth() && !stack.is(Items.ROTTEN_FLESH)) animal.setInLove(null);
        animal.heal(animal.getMaxHealth()*.5F);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void join(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide && e.getEntity() instanceof Mob mob) install(mob);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if(!(event.getEntity() instanceof Mob mob))return;
        LARGE_FISH_TEMPT.remove(mob);INSTALLED.remove(mob);
        if(mob instanceof TamableAnimal pet)THREATS.remove(pet);
        synchronized(THREATS) { THREATS.entrySet().removeIf(entry->entry.getValue().entity==mob); }
    }
    public static void install(Mob mob) {
        if (!(mob instanceof Wolf w && wolf(w)) && !(mob instanceof Cat) && !(mob instanceof Ocelot) && !(mob instanceof AbstractHorse h && horse(h)) && mob.getType()!=EntityType.SQUID) return;
        if (!INSTALLED.add(mob)) return;
        mob.goalSelector.removeAllGoals(g->true); mob.targetSelector.removeAllGoals(g->true);
        if (mob instanceof Wolf w) installWolf(w);
        else if (mob instanceof Cat || mob instanceof Ocelot) installCat((Animal)mob);
        else if (mob instanceof AbstractHorse h) {
            mob.goalSelector.addGoal(1,new SourcePetGoals032.HorseRunCrazy(h));
            mob.goalSelector.addGoal(2,new BreedGoal(h,1,AbstractHorse.class));
            mob.goalSelector.addGoal(3,new FollowParentGoal(h,1));
            mob.goalSelector.addGoal(6,new RandomStrollGoal(h,.9));
            mob.goalSelector.addGoal(7,new LookAtPlayerGoal(h,Player.class,6));
            mob.goalSelector.addGoal(8,new RandomLookAroundGoal(h));
            // Skeleton traps are a deliberate modern feature, not source rs's ordinary goals.
            if (h instanceof SkeletonHorse skeleton && skeleton.isTrap()) { skeleton.setTrap(false); skeleton.setTrap(true); }
        } else if (mob instanceof Squid squid) mob.goalSelector.addGoal(0,new SourceAquatic032.SquidSwim(squid));
    }
    private static void installWolf(Wolf wolf) {
        wolf.goalSelector.addGoal(2,new SitWhenOrderedToGoal(wolf));
        wolf.goalSelector.addGoal(3,new SourcePetGoals032.Leap(wolf,.4F));
        wolf.goalSelector.addGoal(4,new MeleeAttackGoal(wolf,1,true));
        wolf.goalSelector.addGoal(5,new SourcePetGoals032.FollowOwner(wolf,2));
        wolf.goalSelector.addGoal(6,new BreedGoal(wolf,1));
        wolf.goalSelector.addGoal(7,new RandomStrollGoal(wolf,1));
        wolf.goalSelector.addGoal(8,new SourcePetGoals032.Beg(wolf));
        wolf.goalSelector.addGoal(9,new LookAtPlayerGoal(wolf,Player.class,8));
        wolf.goalSelector.addGoal(9,new RandomLookAroundGoal(wolf));
        wolf.targetSelector.addGoal(1,new OwnerHurtByTargetGoal(wolf));
        wolf.targetSelector.addGoal(2,new OwnerHurtTargetGoal(wolf));
        wolf.targetSelector.addGoal(3,new HurtByTargetGoal(wolf).setAlertOthers());
        wolf.targetSelector.addGoal(3,new SourcePetGoals032.HostilePlayers(wolf));
        wolf.targetSelector.addGoal(4,new SourcePetGoals032.Prey(wolf,Chicken.class,200,false));
        wolf.targetSelector.addGoal(5,new SourcePetGoals032.Prey(wolf,Sheep.class,400,false));
        wolf.targetSelector.addGoal(6,new SourcePetGoals032.Prey(wolf,Pig.class,600,false));
        wolf.targetSelector.addGoal(7,new SourcePetGoals032.Prey(wolf,Cow.class,800,false));
        wolf.targetSelector.addGoal(5,new SourcePetGoals032.Prey(wolf,Skeleton.class,3200,true));
        wolf.targetSelector.addGoal(5,new SourcePetGoals032.Prey(wolf,io.github.ningmeng123.mite.entity.R196Undead.class,3200,true,
                prey->prey instanceof io.github.ningmeng123.mite.entity.R196Mob r && r.miteKind()==MobKind.GHOUL));
    }
    private static void installCat(Animal cat) {
        if (cat instanceof Cat tame) cat.goalSelector.addGoal(2,new SitWhenOrderedToGoal(tame));
        cat.goalSelector.addGoal(3,new SourcePetGoals032.Tempt(cat,false));
        var large=new SourcePetGoals032.Tempt(cat,true); LARGE_FISH_TEMPT.put(cat,large); cat.goalSelector.addGoal(3,large);
        cat.goalSelector.addGoal(4,new SourcePetGoals032.AvoidPlayer(cat));
        if (cat instanceof Cat tame) {
            cat.goalSelector.addGoal(5,new SourcePetGoals032.FollowOwner(tame,5));
            cat.goalSelector.addGoal(6,new SourcePetGoals032.CatPerch(tame));
        }
        cat.goalSelector.addGoal(7,new SourcePetGoals032.Leap(cat,.3F));
        cat.goalSelector.addGoal(8,new SourcePetGoals032.CatAttack(cat));
        cat.goalSelector.addGoal(9,new SourcePetGoals032.CatBreed(cat));
        cat.goalSelector.addGoal(10,new RandomStrollGoal(cat,.8));
        cat.goalSelector.addGoal(11,new LookAtPlayerGoal(cat,Player.class,10));
        cat.targetSelector.addGoal(1,new SourcePetGoals032.Prey(cat,Chicken.class,750,false));
        cat.targetSelector.addGoal(1,new SourcePetGoals032.Prey(cat,net.minecraft.world.entity.ambient.Bat.class,750,false));
    }
    public static int tamingOutcome(float roll,float levelRoll,int level,boolean dire) {
        if (dire) return R196Wolf.tamingOutcome(roll,levelRoll,level);
        if (roll<.05F) return -1; if (roll<.1F) return 0; if (roll>.9F) return 1;
        float adjusted=roll+levelRoll*ProgressionRules.attributeLevel(level)*.02F; return adjusted<.2F?-1:adjusted<.8F?0:1;
    }
    public static void onFleeing(Wolf wolf) { wolf.stopBeingAngry(); wolf.setTarget(null); wolf.getPersistentData().putInt(WOLF_TARGET_TIME,0); wolf.getPersistentData().putBoolean(HOSTILE,false); }
    /** oq.isAThreat uses the old hostile-marker interface, actual target and line of sight. */
    public static boolean isThreat(TamableAnimal pet,Mob entity) {
        if(!entity.isAlive()) return false;
        boolean hostile=entity instanceof net.minecraft.world.entity.monster.Enemy
                || entity instanceof io.github.ningmeng123.mite.entity.R196Mob source && source.miteKind().category()==net.minecraft.world.entity.MobCategory.MONSTER;
        if(!hostile || io.github.ningmeng123.mite.entity.animal.Livestock032.sourceLivestock(entity)) return false;
        if(entity instanceof AbstractHorse horse && !(horse instanceof SkeletonHorse || horse instanceof ZombieHorse)) return false;
        if(entity instanceof Wolf wolf && wolf.getOwner()==pet.getOwner()) return false;
        var target=entity.getTarget(); return (target==pet || target==pet.getOwner()) && pet.getSensing().hasLineOfSight(entity);
    }
    public static void noticeThreat(TamableAnimal pet,Mob entity) {
        if(entity!=null && entity.isAlive()) THREATS.put(pet,new Threat(entity,(pet.getRandom().nextInt(10)+5)*10));
        else THREATS.remove(pet);
    }
    public static @Nullable Mob threateningEntity(TamableAnimal pet) {
        Threat value=THREATS.get(pet);
        if(value==null) return null;
        if(value.remaining<=0 || !value.entity.isAlive() || pet.getTarget()!=null) { THREATS.remove(pet); return null; }
        return value.entity;
    }
    public static void tickThreat(TamableAnimal pet) {
        if(pet.isTame() && (pet.isOrderedToSit() || pet.getTarget()==null)
                && io.github.ningmeng123.mite.entity.MobCombat.ticksWithOffset(pet)%10==0 && pet.getRandom().nextFloat()<.25F
                && pet.level().getNearestPlayer(pet.getX(),pet.getY(),pet.getZ(),16,false)!=null) {
            for(Mob entity:pet.level().getEntitiesOfClass(Mob.class,pet.getBoundingBox().inflate(16,8,16),e->e!=pet)) if(isThreat(pet,entity)) {
                if(pet instanceof Wolf) pet.playSound(pet.getRandom().nextInt(8)==0?SoundEvents.WOLF_GROWL:SoundEvents.WOLF_AMBIENT,1,1);
                noticeThreat(pet,entity); break;
            }
        }
        Threat value=THREATS.get(pet); if(value!=null) THREATS.put(pet,new Threat(value.entity,value.remaining-1));
        Mob entity=threateningEntity(pet); if(entity!=null) pet.getLookControl().setLookAt(entity,10,pet.getMaxHeadXRot());
    }
    private static void startTargetCountdown(Wolf wolf) {
        int time=Math.min(800,800+wolf.getRandom().nextInt(100)-wolf.getRandom().nextInt(100));
        wolf.getPersistentData().putInt(WOLF_TARGET_TIME,time);
        if(wolf.isAngry()) wolf.setRemainingPersistentAngerTime(time);
    }
    @SubscribeEvent public static void wolfAttacked(LivingDamageEvent.Post event) {
        if(event.getNewDamage()<=0 || !(event.getSource().getDirectEntity() instanceof Wolf wolf) || !wolf(wolf) || wolf.level().isClientSide) return;
        if(event.getEntity().isAlive()) startTargetCountdown(wolf); else wolf.setTarget(null);
    }
    @SubscribeEvent public static void catHurt(LivingDamageEvent.Post event) {
        if(event.getNewDamage()>0 && event.getEntity() instanceof Cat cat && !cat.level().isClientSide) cat.setOrderedToSit(false);
    }
    @SubscribeEvent public static void target(LivingChangeTargetEvent e) {
        if (!(e.getEntity() instanceof Wolf w) || !wolf(w) || e.getEntity().level().isClientSide) return;
        if (e.getNewAboutToBeSetTarget()!=null && e.getNewAboutToBeSetTarget()==w.getOwner()) { e.setCanceled(true); return; }
        if (e.getNewAboutToBeSetTarget()!=null) {
            startTargetCountdown(w); int time=w.getPersistentData().getInt(WOLF_TARGET_TIME);
            if (e.getNewAboutToBeSetTarget() instanceof Player p && !w.isTame()) { w.getPersistentData().putBoolean(HOSTILE,true); w.setPersistentAngerTarget(p.getUUID()); w.setRemainingPersistentAngerTime(time); }
        }
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post e) {
        if (e.getEntity().level().isClientSide || !(e.getEntity() instanceof Mob mob) || !mob.isAlive()) return;
        if (mob instanceof Wolf w && wolf(w)) {
            decrement(mob,TAME_COOLDOWN); int time=decrement(mob,WOLF_TARGET_TIME);
            if (time==0 && !w.getPersistentData().getBoolean("mite_wolf_witch_ally_032")) { w.setTarget(null); w.stopBeingAngry(); w.getPersistentData().putBoolean(HOSTILE,false); }
            if (!w.isTame() && !R196SpawnRules.daytime((int)Math.floorMod(w.level().getDayTime(),24000L)) && R196SpawnRules.moon(w.level().getDayTime(),w.level().dimension()==net.minecraft.world.level.Level.OVERWORLD).blood()) {
                w.getPersistentData().putBoolean(HOSTILE,true); w.setRemainingPersistentAngerTime(800);
            }
        } else if (mob instanceof Cat || mob instanceof Ocelot) decrement(mob,TAME_COOLDOWN);
        else if (mob instanceof AbstractHorse h && horse(h)) {
            if (h.isTamed()) { h.getPersistentData().putInt(HORSE_EATING,0); h.getPersistentData().putInt(HORSE_RIDING,0); }
            else { decrement(h,HORSE_EATING); decrement(h,HORSE_RIDING); }
        }
        if(mob instanceof Squid squid) SourceAquatic032.touch(squid);
        if(mob instanceof TamableAnimal pet && (mob instanceof Cat || mob instanceof Wolf wolf && wolf(wolf))) tickThreat(pet);
    }
    private static int decrement(Mob mob,String key) { int i=Math.max(0,mob.getPersistentData().getInt(key)-1); mob.getPersistentData().putInt(key,i); return i; }
    /** null leaves modern armor, dyes, inventories, leads and other item interactions to vanilla. */
    public static @Nullable InteractionResult wolfInteract(Wolf wolf,Player player,InteractionHand hand) {
        if (!wolf(wolf)) return null;
        ItemStack item=player.getItemInHand(hand);
        if (wolf.getPersistentData().getBoolean(HOSTILE) || wolf.isAngry()) return InteractionResult.PASS;
        if (!wolf.isTame() && item.is(Items.BONE)) {
            if (wolf.getTarget()!=null) return InteractionResult.PASS;
            if (!wolf.level().isClientSide && wolf.getPersistentData().getInt(TAME_COOLDOWN)==0) {
                boolean dire=wolf instanceof R196Wolf; float roll=wolf.getRandom().nextFloat();
                float thresholdLow=dire?.4F:.1F,thresholdHigh=dire?.95F:.9F;
                int outcome=tamingOutcome(roll,roll>=thresholdLow && roll<=thresholdHigh?wolf.getRandom().nextFloat():0,player.experienceLevel,dire);
                item.consume(1,player);
                if (outcome>0 && !net.neoforged.neoforge.event.EventHooks.onAnimalTame(wolf,player)) {
                    wolf.tame(player); wolf.getNavigation().stop(); wolf.setTarget(null); wolf.setOrderedToSit(true); wolf.stopBeingAngry(); wolf.level().broadcastEntityEvent(wolf,(byte)7);
                } else {
                    wolf.getPersistentData().putInt(TAME_COOLDOWN,100);
                    if (outcome<0 && !SourceAquatic032.blueNight(wolf.level())) wolf.setTarget(player);
                    wolf.level().broadcastEntityEvent(wolf,(byte)6);
                }
            }
            return InteractionResult.sidedSuccess(wolf.level().isClientSide);
        }
        if (R196NpcNeeds.meat(item)) {
            if (wolf.isBaby() || wolf.getAge()!=0 || wolf.isInLove()) return InteractionResult.PASS;
            if (!wolf.level().isClientSide) {
                var profile=MiteFoodCatalog.profile(item);
                if (wolf.isTame() && wolf.getHealth()+1<wolf.getMaxHealth()) wolf.heal(profile==null?0:profile.nutrition());
                else { if (wolf.getHealth()>=wolf.getMaxHealth() && !item.is(Items.ROTTEN_FLESH)) wolf.setInLove(player); wolf.heal(wolf.getMaxHealth()*.5F); }
                item.consume(1,player);
            }
            return InteractionResult.sidedSuccess(wolf.level().isClientSide);
        }
        return null;
    }
    public static @Nullable InteractionResult catInteract(Animal animal,Player player,InteractionHand hand) {
        ItemStack item=player.getItemInHand(hand); if (!rawFish(item)) return null;
        if (animal instanceof Cat cat && cat.isTame()) {
            if (cat.isBaby() || cat.getAge()!=0 || cat.isInLove()) return InteractionResult.PASS;
            if (!cat.level().isClientSide) { if (cat.getHealth()>=cat.getMaxHealth()) cat.setInLove(player); cat.heal(cat.getMaxHealth()*.5F); item.consume(1,player); }
            return InteractionResult.sidedSuccess(cat.level().isClientSide);
        }
        SourcePetGoals032.Tempt temptation=LARGE_FISH_TEMPT.get(animal);
        if (temptation==null || !temptation.active() || player.distanceToSqr(animal)>=9) return InteractionResult.PASS;
        if (!animal.level().isClientSide && animal.getPersistentData().getInt(TAME_COOLDOWN)==0) {
            item.consume(1,player);
            if (animal.getRandom().nextInt(3)==0 && !net.neoforged.neoforge.event.EventHooks.onAnimalTame(animal,player)) {
                Cat cat;
                if (animal instanceof Ocelot ocelot) cat=ocelot.convertTo(EntityType.CAT,false);
                else cat=(Cat)animal;
                if (cat!=null) {
                    var variant=switch(animal.getRandom().nextInt(3)) { case 0->CatVariant.BLACK; case 1->CatVariant.RED; default->CatVariant.SIAMESE; };
                    // An existing modern Cat keeps its selected appearance; an old untamed ocelot becomes one of the source three cats.
                    if (animal instanceof Ocelot) cat.setVariant(BuiltInRegistries.CAT_VARIANT.getHolderOrThrow(variant));
                    cat.tame(player); cat.setOrderedToSit(true); cat.level().broadcastEntityEvent(cat,(byte)7);
                }
            } else { animal.getPersistentData().putInt(TAME_COOLDOWN,400); animal.level().broadcastEntityEvent(animal,animal instanceof Ocelot?(byte)40:(byte)6); }
        }
        return InteractionResult.sidedSuccess(animal.level().isClientSide);
    }
    /** rx.a(oi) creates two additional newborns on the world RNG's one-in-seven roll. */
    public static void ocelotSpawn(Ocelot parent,net.minecraft.world.level.ServerLevelAccessor world) {
        parent.setAge(0);
        if(world.getRandom().nextInt(7)!=0) return;
        for(int i=0;i<2;i++) {
            Ocelot child=EntityType.OCELOT.create(world.getLevel());if(child==null)continue;
            child.moveTo(parent.getX(),parent.getY(),parent.getZ(),parent.getYRot(),0);child.setAge(-64000);world.addFreshEntity(child);
        }
    }
    public record HorseFood(float healing,int growth,int temper,boolean breeding) {}
    public static @Nullable HorseFood horseFood(ItemStack stack) {
        if(stack.is(Items.WHEAT)) return new HorseFood(2,60,3,false);
        if(stack.is(Items.SUGAR)) return new HorseFood(1,30,3,false);
        if(stack.is(Items.BREAD)) return new HorseFood(7,180,3,false);
        if(stack.is(Items.HAY_BLOCK)) return new HorseFood(20,180,0,false);
        if(stack.is(Items.APPLE)) return new HorseFood(3,60,3,false);
        if(stack.is(Items.GOLDEN_CARROT)) return new HorseFood(4,60,5,true);
        if(stack.is(Items.GOLDEN_APPLE)||stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return new HorseFood(10,240,10,true);
        return null;
    }
    public static boolean horseEat(AbstractHorse horse,Player player,ItemStack stack) {
        var food=horseFood(stack); if(food==null || horse instanceof SkeletonHorse || horse instanceof ZombieHorse) return false;
        if(horse.level().isClientSide) return true;
        boolean accepted=false,healed=false;
        if(food.breeding && horse.isTamed() && horse.getAge()==0 && horse.getHealth()>=horse.getMaxHealth()) { horse.setInLove(player); accepted=true; }
        if(horse.getHealth()<horse.getMaxHealth()) { horse.heal(food.healing); accepted=true; healed=true; }
        else if(!horse.isTamed() && horse.getPersistentData().getInt(HORSE_EATING)>0) { horse.makeMad(); return false; }
        if(horse.isBaby()) { horse.setAge(Math.min(0,horse.getAge()+food.growth*5)); accepted=true; }
        // Actual rs condition compares modifier to max temper (100), not current temper.
        if(food.temper>0 && (accepted || !horse.isTamed()) && food.temper<horse.getMaxTemper()) { horse.modifyTemper(food.temper); accepted=true; }
        if(accepted && !horse.isTamed() && (!healed || horse.getHealth()>=horse.getMaxHealth())) horse.getPersistentData().putInt(HORSE_EATING,4000);
        if(accepted) { horse.playSound(SoundEvents.HORSE_EAT,1,1); horse.gameEvent(net.minecraft.world.level.gameevent.GameEvent.EAT); }
        return accepted;
    }
    public static @Nullable InteractionResult horseInteract(AbstractHorse horse,Player player,InteractionHand hand) {
        if(!horse(horse)) return null;
        if(horse.isTamed() && player.isSecondaryUseActive()) return null;
        ItemStack item=player.getItemInHand(hand);
        if(horse.isTamed() && horse.isWearingBodyArmor() && item.isEmpty()) return null;
        if(!horse.isTamed() && (horse instanceof SkeletonHorse || horse instanceof ZombieHorse)) return InteractionResult.PASS;
        if(!horse.isVehicle() && horseFood(item)!=null) {
            if(!horse.level().isClientSide && horseEat(horse,player,item)) item.consume(1,player);
            return InteractionResult.sidedSuccess(horse.level().isClientSide);
        }
        if(!horse.isTamed() && !item.isEmpty() && !item.is(Items.NAME_TAG) && !item.is(Items.LEAD) && !item.is(Items.HORSE_SPAWN_EGG)) {
            if(!horse.level().isClientSide) horse.makeMad(); return InteractionResult.sidedSuccess(horse.level().isClientSide);
        }
        if(!horse.isBaby() && !horse.isVehicle() && horse.getPersistentData().getInt(HORSE_RIDING)>0) {
            if(!horse.level().isClientSide) horse.makeMad(); return InteractionResult.sidedSuccess(horse.level().isClientSide);
        }
        return null;
    }
    /** Preserve two confirmed rs.a(nk) receiver bugs instead of silently applying modern inheritance. */
    public static AgeableMob horseOffspring(AbstractHorse mother,ServerLevel level,AgeableMob other) {
        AbstractHorse father=(AbstractHorse)other;
        AbstractHorse child=(mother instanceof Horse && father instanceof Donkey || mother instanceof Donkey && father instanceof Horse)?EntityType.MULE.create(level):
                mother instanceof Horse?EntityType.HORSE.create(level):mother instanceof Donkey?EntityType.DONKEY.create(level):EntityType.MULE.create(level);
        if(child==null) return null;
        if(child instanceof Horse newborn && mother instanceof Horse a && father instanceof Horse b) {
            int coat=mother.getRandom().nextInt(9); var variant=coat<4?a.getVariant():coat<8?b.getVariant():Variant.byId(mother.getRandom().nextInt(7));
            var markings=mother.getRandom().nextInt(5)<4?a.getMarkings():b.getMarkings(); ((io.github.ningmeng123.mite.mixin.NativeHorseVariant032Access)newborn).mite$setSourceVariantAndMarkings(variant,markings);
        }
        mother.getAttribute(Attributes.MAX_HEALTH).setBaseValue((mother.getMaxHealth()+father.getMaxHealth()+15+mother.getRandom().nextInt(8)+mother.getRandom().nextInt(9))/3D);
        mother.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue((mother.getAttributeValue(Attributes.JUMP_STRENGTH)+father.getAttributeValue(Attributes.JUMP_STRENGTH)+.4+mother.getRandom().nextDouble()*.2+mother.getRandom().nextDouble()*.2+mother.getRandom().nextDouble()*.2)/3D);
        mother.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue((mother.getAttributeValue(Attributes.MOVEMENT_SPEED)+father.getAttributeValue(Attributes.MOVEMENT_SPEED)+(.45+mother.getRandom().nextDouble()*.3+mother.getRandom().nextDouble()*.3+mother.getRandom().nextDouble()*.3)*.25)/3D);
        child.getAttribute(Attributes.MAX_HEALTH).setBaseValue(53); child.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.225); child.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(.7); child.setHealth(53);
        return child;
    }
}

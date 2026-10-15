package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.entity.ai.SourceCreeperSwellGoal;
import io.github.ningmeng123.mite.entity.ai.ZombieMoveToTreeGoal;
import io.github.ningmeng123.mite.rules.R196MobRules;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Source AI activation, smart zombies and the single tm/tr frenzy bonus. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class R196MonsterAi {
    public static final String SMART="mite_zombie_smart_035";
    private static final ResourceLocation FRENZY=ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID,"monster_frenzy_035");
    private static final ResourceLocation FRENZY_SPEED=ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID,"monster_frenzy_speed_035");
    private static final class Source {
        static final Set<String> MONSTERS=monsters();
        private static Set<String> monsters() {
            Set<String> result=new HashSet<>();
            for(var value:R196MobRules.sourceData().getAsJsonArray("entities")) {
                var row=value.getAsJsonObject();
                if(row.has("hierarchy")&&row.getAsJsonArray("hierarchy").asList().stream().anyMatch(v -> v.getAsString().equals("tm")))
                    result.add(row.get("source_class").getAsString());
            }
            return Set.copyOf(result);
        }
    }
    private R196MonsterAi() {}
    public static boolean monster(Mob mob) {
        if(mob instanceof R196Mob custom && custom.miteKind()==null) return false;
        String source=R196MobSpawning.sourceClass(mob);
        return source!=null&&Source.MONSTERS.contains(source);
    }
    /** Only the source families whose bf() actually enables the goal scheduler. */
    public static boolean managed(PathfinderMob mob) {
        if(mob instanceof R196Mob custom) {
            if(custom.miteKind()==null) return false;
            return switch(custom.miteKind().family()) {
                case UNDEAD,ZOMBIE,SKELETON,CREEPER,EARTH,FIRE -> true;
                default -> false;
            };
        }
        EntityType<?> type=mob.getType();
        return type==EntityType.ZOMBIE || type==EntityType.ZOMBIE_VILLAGER || type==EntityType.SKELETON
                || type==EntityType.WITHER_SKELETON || type==EntityType.CREEPER || type==EntityType.WITCH;
    }
    public static boolean frenzied(Mob mob) {
        if(mob instanceof BoneLordFrenzy bones && bones.mite$boneLordFrenzyTicks()>0) return true;
        return monster(mob) && mob.getType()!=EntityType.ENDERMAN
                && R196SpawnRules.moon(mob.level().getDayTime(),mob.level().dimension().equals(Level.OVERWORLD)).blood()
                && !R196SpawnRules.daytime((int)Math.floorMod(mob.level().getDayTime(),24000L));
    }
    public static boolean smart(Zombie zombie) {
        var state=zombie.getPersistentData();
        if(!state.contains(SMART)) state.putBoolean(SMART,zombie instanceof R196Zombie || zombie.getRandom().nextInt(8)==0);
        return state.getBoolean(SMART);
    }
    public static boolean diggingEnabled(PathfinderMob mob) {
        if(!managed(mob) || !R196DiggerGoal.watcher(mob)) return false;
        var tool=EquipmentDurability.toolProfile(mob.getMainHandItem());
        if(tool!=null) {
            switch(tool.family()) { case SWORD,CLUB,CUDGEL,SCYTHE: return false; default: break; }
        }
        return !(mob instanceof Zombie zombie) || smart(zombie) || frenzied(mob) || tool!=null;
    }
    public static void refreshFrenzyDamage(Mob mob) {
        boolean frenzy=frenzied(mob);
        var movement=mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if(movement!=null) {
            var previousSpeed=movement.getModifier(FRENZY_SPEED);
            if(frenzy && previousSpeed==null) movement.addTransientModifier(new AttributeModifier(FRENZY_SPEED,.2D,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            else if(!frenzy && previousSpeed!=null) movement.removeModifier(FRENZY_SPEED);
        }
        var attack=mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if(attack==null) return;
        double amount=frenzy?attack.getBaseValue()*.5D:0D;
        var previous=attack.getModifier(FRENZY);
        if(previous!=null && previous.amount()==amount) return;
        attack.removeModifier(FRENZY);
        if(amount!=0D) attack.addTransientModifier(new AttributeModifier(FRENZY,amount,AttributeModifier.Operation.ADD_VALUE));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if(event.isCanceled() || event.getLevel().isClientSide || !(event.getEntity() instanceof PathfinderMob mob)) return;
        if(mob.getType()==EntityType.SNOW_GOLEM) { replaceRanged(mob,1.25D,20,10F); return; }
        if(!managed(mob)) return;
        if(mob instanceof Zombie zombie) { smart(zombie); zombie.setCanBreakDoors(false); }
        boolean ghoul=mob instanceof R196Undead undead && undead.miteKind()==MobKind.GHOUL;
        if(mob.getNavigation() instanceof GroundPathNavigation ground && (mob instanceof Zombie
                || mob instanceof R196Undead undead && undead.miteKind()!=MobKind.SHADOW || mob instanceof R196Elemental)) {
            ground.setCanOpenDoors(true); ground.setCanPassDoors(true);
        }
        mob.goalSelector.removeAllGoals(goal -> goal instanceof BreakDoorGoal);
        if((mob instanceof Zombie || ghoul || mob instanceof R196Elemental)
                && mob.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof SourceBreakPortalGoal))
            mob.goalSelector.addGoal(1,new SourceBreakPortalGoal(mob));
        if(mob instanceof Creeper creeper) {
            mob.goalSelector.removeAllGoals(goal -> goal instanceof SwellGoal);
            if(mob.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof SourceCreeperSwellGoal))
                mob.goalSelector.addGoal(2,new SourceCreeperSwellGoal(creeper));
        }
        if(mob instanceof Zombie && mob.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof ZombieMoveToTreeGoal))
            mob.goalSelector.addGoal(3,new ZombieMoveToTreeGoal(mob));
        if(mob instanceof AbstractSkeleton skeleton) skeleton.reassessWeaponGoal();
        else if(mob instanceof Zombie || mob instanceof R196Undead) {
            mob.goalSelector.removeAllGoals(goal -> goal instanceof MeleeAttackGoal || goal instanceof SourceMeleeGoal);
            mob.goalSelector.addGoal(2,new SourceMeleeGoal(mob,Player.class,1D,false));
            mob.goalSelector.addGoal(3,new SourceMeleeGoal(mob,Villager.class,1D,true));
            if(mob instanceof Zombie || ghoul) mob.goalSelector.addGoal(4,new SourceMeleeGoal(mob,Animal.class,1D,true));
        } else {
            var old=mob.goalSelector.getAvailableGoals().stream().filter(g -> g.getGoal() instanceof MeleeAttackGoal).toList();
            for(var wrapped:old) { mob.goalSelector.removeGoal(wrapped.getGoal()); mob.goalSelector.addGoal(wrapped.getPriority(),new SourceMeleeGoal(mob,1D,false)); }
        }
        if(mob.getType()==EntityType.WITCH) replaceRanged(mob,1D,60,10F);
        SourceMonsterTargeting.install(mob);
        refreshFrenzyDamage(mob);
    }
    private static void replaceRanged(PathfinderMob mob,double speed,int ticks,float range) {
        if(!(mob instanceof RangedAttackMob shooter)) return;
        var old=mob.goalSelector.getAvailableGoals().stream().filter(g -> g.getGoal() instanceof RangedAttackGoal).toList();
        for(var wrapped:old) { mob.goalSelector.removeGoal(wrapped.getGoal()); mob.goalSelector.addGoal(wrapped.getPriority(),new SourceRangedAttackGoal(shooter,speed,ticks,range)); }
    }
    @SubscribeEvent public static void damage(LivingDamageEvent.Post event) {
        if(event.getEntity().level().isClientSide || !(event.getEntity() instanceof Zombie zombie) || !managed(zombie)
                || !(event.getSource().getEntity() instanceof Player) || !(event.getNewDamage()>0F)) return;
        zombie.getPersistentData().putBoolean(SMART,true);
    }
    @SubscribeEvent public static void pre(EntityTickEvent.Pre event) {
        if(event.getEntity() instanceof Mob mob && !mob.level().isClientSide && mob.isAlive()
                && (monster(mob)||mob instanceof BoneLordFrenzy)) refreshFrenzyDamage(mob);
    }
}

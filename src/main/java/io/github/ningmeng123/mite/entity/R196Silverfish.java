package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.R196MobRules;
import io.github.ningmeng123.mite.world.CollapseSecondaryTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The three source tq variants retain animal prey, 20-tick recruitment, and subtype hit/death effects. @author NingMeng123 */
public final class R196Silverfish extends Silverfish implements R196Mob {
    private final MobKind kind;
    private int summonDelay;
    private int fizzDelay;
    private boolean exploded;
    public R196Silverfish(EntityType<? extends Silverfish> type,Level level,MobKind kind) {
        super(type,level); this.kind=kind; xpReward=R196MobRules.entry(kind).experience();
    }
    @Override public MobKind miteKind() { return kind; }
    public boolean hasExploded() { return exploded; }
    @Override public void igniteForTicks(int ticks) { if (kind!=MobKind.NETHERSPAWN) super.igniteForTicks(ticks); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1,new FloatGoal(this));
        goalSelector.addGoal(4,new MeleeAttackGoal(this,1,false) {
            @Override protected boolean canPerformAttack(LivingEntity target) {
                return isTimeToAttack() && mob.distanceToSqr(target)<1.2*1.2 && target.getBoundingBox().maxY>mob.getBoundingBox().minY && target.getBoundingBox().minY<mob.getBoundingBox().maxY;
            }
            @Override protected void resetAttackCooldown() { super.resetAttackCooldown(); }
            @Override public boolean requiresUpdateEveryTick() { return true; }
        });
        // R196 tq does not use modern SilverfishMergeWithStoneGoal.
        goalSelector.addGoal(5,new RandomStrollGoal(this,1,10));
        targetSelector.addGoal(1,new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true) {
            @Override protected double getFollowDistance() { return 8; }
        });
        targetSelector.addGoal(3,new MonsterHunting.PreyGoal<>(this,Animal.class,true));
        targetSelector.addGoal(3,new MonsterHunting.PreyGoal<>(this,Villager.class,true));
    }
    @Override public boolean isInvulnerableTo(DamageSource source) {
        // Netherspawn overrides fire only, not lava, in its actual source.
        return super.isInvulnerableTo(source) || kind==MobKind.NETHERSPAWN && source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) && !source.is(DamageTypes.LAVA);
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        boolean affected=super.hurt(source,amount);
        if (affected && summonDelay<=0 && (source.getEntity()!=null || MobCombat.magic(source))) summonDelay=20;
        return affected;
    }
    @Override public boolean doHurtTarget(Entity target) {
        float before=target instanceof LivingEntity living ? living.getHealth() : 0;
        boolean affected=super.doHurtTarget(target);
        if (affected && target instanceof LivingEntity living && living.isAlive() && living.getHealth()<before) {
            if (kind==MobKind.COPPERSPINE) living.addEffect(new MobEffectInstance(MobEffects.POISON,480,0));
            if (kind==MobKind.HOARY_SILVERFISH) living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,50,5));
        }
        return affected;
    }
    @Override public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (kind==MobKind.NETHERSPAWN) level().addParticle(ParticleTypes.LARGE_SMOKE,getX()+(random.nextDouble()-.5)*getBbWidth(),getY()+random.nextDouble()*getBbHeight(),getZ()+(random.nextDouble()-.5)*getBbWidth(),0,0,0);
            return;
        }
        if (summonDelay>0 && --summonDelay==0) recruit();
        if (kind==MobKind.NETHERSPAWN && isInWaterOrRain() && --fizzDelay<=0) {
            playSound(SoundEvents.FIRE_EXTINGUISH,.7F,1.6F+(random.nextFloat()-random.nextFloat())*.4F);
            fizzDelay=random.nextInt(7)+2;
            if (random.nextInt(isInWater() ? 1 : 4)==0) hurt(MobCombat.source(this,"water"),1);
        }
    }
    public void recruit() {
        BlockPos origin=blockPosition();
        for (int y=0;y<=5 && y>=-5;y=(y<=0 ? 1 : 0)-y)
            for (int x=0;x<=10 && x>=-10;x=(x<=0 ? 1 : 0)-x)
                for (int z=0;z<=10 && z>=-10;z=(z<=0 ? 1 : 0)-z) {
                    BlockPos pos=origin.offset(x,y,z); if (!level().hasChunkAt(pos)) continue;
                    BlockState state=level().getBlockState(pos);
                    if (!(state.getBlock() instanceof InfestedBlock infested)) continue;
                    BlockState host=infested.hostStateByInfested(state);
                    if (net.neoforged.neoforge.event.EventHooks.canEntityGrief(level(),this)) {
                        if (level().removeBlock(pos,false)) CollapseSecondaryTriggers.removedSupport(level(),pos);
                    }
                    else level().setBlockAndUpdate(pos,host);
                    R196Infestation.spawn((ServerLevel)level(),pos,state,getTarget());
                    if (random.nextInt(4)==0) return;
                }
    }
    public static Silverfish createInfestedChild(Level level,BlockPos pos,BlockState host) {
        MobKind kind=null;
        var identity=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(host.getBlock());
        boolean copperspine=identity.getNamespace().equals("mite") && identity.getPath().contains("copperspine");
        if (host.getBlock() instanceof InfestedBlock infested) host=infested.hostStateByInfested(host);
        if (copperspine) kind=MobKind.COPPERSPINE;
        else if (host.is(Blocks.NETHERRACK)) kind=MobKind.NETHERSPAWN;
        else if (host.is(Blocks.STONE) && copperNearby(level,pos)) kind=MobKind.COPPERSPINE;
        else if (level.dimension().equals(io.github.ningmeng123.mite.worldgen.MiteWorldDimensions.UNDERWORLD)) kind=MobKind.HOARY_SILVERFISH;
        return kind==null ? EntityType.SILVERFISH.create(level) : (Silverfish)MiteMobs.type(kind).create(level);
    }
    private static boolean copperNearby(Level level,BlockPos pos) {
        for (BlockPos p:BlockPos.betweenClosed(pos.offset(-3,-3,-3),pos.offset(3,3,3))) {
            if (!level.hasChunkAt(p)) continue;
            BlockState state=level.getBlockState(p);
            if (state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)) return true;
        }
        return false;
    }
    @Override public void die(DamageSource source) {
        boolean explode=kind==MobKind.NETHERSPAWN && !exploded && !isInWaterOrRain() && !isInLava()
                && !(source.getDirectEntity() instanceof Snowball) && !MobCombat.water(source);
        super.die(source);
        if (explode && !level().isClientSide) {
            exploded=true;
            level().explode(this,getX(),getY()+getBbHeight()/4F,getZ(),1F,true,Level.ExplosionInteraction.MOB);
        }
    }
    @Override public boolean shouldBlockExplode(Explosion explosion,BlockGetter level,BlockPos pos,BlockState state,float power) {
        return kind!=MobKind.NETHERSPAWN || !(state.is(Blocks.NETHERRACK) || state.is(Blocks.NETHER_QUARTZ_ORE) || state.is(Blocks.NETHER_BRICKS))
                && super.shouldBlockExplode(explosion,level,pos,state,power);
    }
}

package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.rules.R196MobRules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/** Fire elemental with R196 water damage and enchanted weapon immunities. @author NingMeng123 */
public final class R196FireElemental extends Monster implements R196Mob {
    private final MobKind kind;
    private int fizzDelay;
    private int fireDelay;
    public R196FireElemental(EntityType<? extends Monster> type, Level level, MobKind kind) {
        super(type, level); this.kind = kind; xpReward = R196MobRules.entry(kind).experience();
    }
    @Override public MobKind miteKind() { return kind; }
    @Override public boolean biologicallyAlive() { return false; }
    @Override public boolean healsWithTime() { return false; }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ElementalShelterGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Villager.class, true));
    }
    @Override public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (super.isInvulnerableTo(source)) return true;
        if (MobCombat.water(source) || source.getDirectEntity() instanceof Snowball) return false;
        if (!MobCombat.magic(source)) return true;
        ItemStack weapon = MobCombat.weapon(source);
        if (weapon == null) return false;
        var enchantment = source.getDirectEntity() instanceof AbstractArrow ? Enchantments.FLAME : Enchantments.FIRE_ASPECT;
        var enchantments = weapon.getTagEnchantments();
        // R196 passes true to hasEnchantment, which requires exactly one enchantment.
        return enchantments.size() == 1 && enchantments.keySet().iterator().next().is(enchantment);
    }
    @Override public boolean doHurtTarget(Entity target) {
        float before = target instanceof LivingEntity living ? living.getHealth() : 0;
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living && living.isAlive() && living.getHealth() < before) target.igniteForSeconds(6);
        return hit;
    }
    @Override public void aiStep() {
        // R196 c() executes this before its parent, including when dry.
        if (!level().isClientSide) {
            if (MobCombat.ticksWithOffset(this)%40 == 0) hurt(MobCombat.source(this, "water"), 1F);
            if (isInWaterOrRain()) {
                if (--fizzDelay <= 0) {
                    playSound(SoundEvents.FIRE_EXTINGUISH, .7F, 1.6F+(random.nextFloat()-random.nextFloat())*.4F);
                    fizzDelay = random.nextInt(7)+2;
                    if (random.nextInt(isInWater() ? 1 : 4) == 0) hurt(MobCombat.source(this, "water"), 1F);
                }
                if (level() instanceof ServerLevel server) server.sendParticles(ParticleTypes.CLOUD, getX(), getY()+.5, getZ(), isInWater() ? 10 : 1, .3, .3, .3, .01);
            } else if (--fireDelay <= 0) {
                playSound(SoundEvents.FIRE_AMBIENT, 1+random.nextFloat(), random.nextFloat()*.7F+.3F);
                fireDelay = random.nextInt(21)+30;
                if (isInLava()) heal(4F);
            }
        }
        super.aiStep();
    }
    @Override public boolean displayFireAnimation() { return true; }
}

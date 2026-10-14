package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.rules.R196MobRules;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** A hostile, untamable hellhound and the source level-dependent dire wolf are separate species. @author NingMeng123 */
public final class R196Wolf extends Wolf implements R196Mob {
    private final MobKind kind;
    public R196Wolf(EntityType<? extends R196Wolf> type, Level level, MobKind kind) {
        super(type, level); this.kind = kind; xpReward = R196MobRules.entry(kind).experience();
        if (kind == MobKind.HELLHOUND) {
            goalSelector.removeAllGoals(goal -> true); targetSelector.removeAllGoals(goal -> true);
            goalSelector.addGoal(1, new FloatGoal(this)); goalSelector.addGoal(3, new MeleeAttackGoal(this, 1, true));
            goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1));
            goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8)); goalSelector.addGoal(7, new RandomLookAroundGoal(this));
            targetSelector.addGoal(1, new HurtByTargetGoal(this)); targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
            targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.animal.Animal.class, 20, true, false,
                    e -> !(e instanceof R196Wolf wolf && wolf.miteKind() == MobKind.HELLHOUND)));
        }
    }
    @Override public MobKind miteKind() { return kind; }
    @Override protected int getBaseExperienceReward() { return xpReward; }
    @Override protected void applyTamingSideEffects() {
        super.applyTamingSideEffects();
        if (kind == MobKind.DIRE_WOLF) { getAttribute(Attributes.MAX_HEALTH).setBaseValue(isTame() ? 24 : 16); getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(5); }
    }
    @Override public boolean isInvulnerableTo(DamageSource source) {
        return kind == MobKind.HELLHOUND && source.is(DamageTypeTags.IS_FIRE) || super.isInvulnerableTo(source);
    }
    @Override public boolean isFood(ItemStack stack) { return kind != MobKind.HELLHOUND && R196NpcNeeds.meat(stack); }
    @Override public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && kind == MobKind.HELLHOUND && target.isAlive() && random.nextFloat() < .4F) {
            target.igniteForSeconds(1 + random.nextInt(8)); playSound(io.github.ningmeng123.mite.registry.MiteMobSounds.get("imported.mob.hellhound.breath"), 4F, 1F);
        }
        return hit;
    }
    public static int tamingOutcome(float roll, float levelRoll, int level) {
        if (roll < .2F) return -1; if (roll < .4F) return 0; if (roll > .95F) return 1;
        float adjusted = roll + levelRoll * level * .02F;
        return adjusted < .5F ? -1 : adjusted < 1F ? 0 : 1;
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (kind == MobKind.HELLHOUND) return InteractionResult.PASS;
        ItemStack item = player.getItemInHand(hand);
        if (!isTame() && item.is(Items.BONE)) {
            if (level().isClientSide) return InteractionResult.SUCCESS;
            item.consume(1, player);
            float roll = random.nextFloat();
            int outcome = tamingOutcome(roll, roll >= .4F && roll <= .95F ? random.nextFloat() : 0F, player.experienceLevel);
            if (outcome > 0 && !net.neoforged.neoforge.event.EventHooks.onAnimalTame(this, player)) {
                tame(player); getNavigation().stop(); setTarget(null); setOrderedToSit(true); setHealth(getMaxHealth()); level().broadcastEntityEvent(this, (byte)7);
            } else {
                if (outcome < 0) { setTarget(player); setPersistentAngerTarget(player.getUUID()); startPersistentAngerTimer(); }
                level().broadcastEntityEvent(this, (byte)6);
            }
            return InteractionResult.CONSUME;
        }
        return super.mobInteract(player, hand);
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && kind == MobKind.DIRE_WOLF && !isBaby() && !isTame() && !isAngry()
                && !io.github.ningmeng123.mite.rules.R196SpawnRules.moon(level().getDayTime(), level().dimension().equals(Level.OVERWORLD)).blue() && random.nextFloat() < .004F) {
            Player prey = level().getNearestPlayer(this, 4);
            if (prey != null && !prey.isCreative() && !prey.isSpectator()) { setTarget(prey); setPersistentAngerTarget(prey.getUUID()); startPersistentAngerTimer(); }
        }
    }
    @Override protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source, boolean recentlyHit) {
        if (getPersistentData().getBoolean("mite_massive_fall_031") && random.nextFloat() >= .1F) return;
        if (kind == MobKind.DIRE_WOLF) spawnAtLocation(Items.LEATHER);
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FALL) && amount >= getMaxHealth() * .5F) getPersistentData().putBoolean("mite_massive_fall_031", true);
        return super.hurt(source, amount);
    }
    @Override protected SoundEvent getAmbientSound() { return kind == MobKind.HELLHOUND ? io.github.ningmeng123.mite.registry.MiteMobSounds.get(kind, "say") : super.getAmbientSound(); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return kind == MobKind.HELLHOUND ? io.github.ningmeng123.mite.registry.MiteMobSounds.get(kind, "hurt") : super.getHurtSound(source); }
    @Override protected SoundEvent getDeathSound() { return kind == MobKind.HELLHOUND ? io.github.ningmeng123.mite.registry.MiteMobSounds.get(kind, "death") : super.getDeathSound(); }
    @Override protected float getSoundVolume() { return kind == MobKind.DIRE_WOLF ? super.getSoundVolume() * 1.5F : super.getSoundVolume(); }
    @Override public float getVoicePitch() { return kind == MobKind.DIRE_WOLF ? super.getVoicePitch() * .8F : super.getVoicePitch(); }
}

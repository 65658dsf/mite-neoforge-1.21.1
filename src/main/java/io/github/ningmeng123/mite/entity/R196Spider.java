package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.R196MobRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Arachnid web stock, distinct toxins and phase movement are saved entity behavior. @author NingMeng123 */
public final class R196Spider extends Spider implements R196Mob {
    private final MobKind kind;
    private int webs, evasions, maximumEvasions;
    public R196Spider(EntityType<? extends R196Spider> type, Level level, MobKind kind) {
        super(type, level); this.kind = kind; xpReward = R196MobRules.entry(kind).experience();
        if (!level.isClientSide) {
            if (kind == MobKind.PHASE_SPIDER) maximumEvasions = evasions = random.nextInt(3) + 2;
            else { webs = random.nextInt(4); if (webs > 0 && kind != MobKind.DEMON_SPIDER) webs--; }
        }
    }
    @Override public MobKind miteKind() { return kind; }
    public int webStock() { return webs; }
    public int evasions() { return evasions; }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1, true)); goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, .8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8)); goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }
    @Override public boolean isInvulnerableTo(DamageSource source) {
        return kind == MobKind.DEMON_SPIDER && source.is(DamageTypeTags.IS_FIRE) || super.isInvulnerableTo(source);
    }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return super.causeFallDamage(distance, multiplier * (kind == MobKind.DEMON_SPIDER ? .5F : .25F), source);
    }
    @Override public boolean doHurtTarget(Entity target) {
        float before = target instanceof LivingEntity living ? living.getHealth() : 0F;
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living && living.isAlive() && living.getHealth() < before) {
            int poison = kind == MobKind.DEMON_SPIDER ? 480 : kind == MobKind.BLACK_WIDOW_SPIDER ? 960 : kind == MobKind.WOOD_SPIDER ? 240 : 0;
            if (poison > 0) living.addEffect(new MobEffectInstance(MobEffects.POISON, poison), this);
            if (kind == MobKind.DEMON_SPIDER) living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 5), this);
        }
        return hit;
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) return;
        int sourceTick = MobCombat.ticksWithOffset(this);
        if (!isNoAi()) NativeArachnidEvents.acquireTarget(this);
        if (kind == MobKind.PHASE_SPIDER) {
            if (evasions < maximumEvasions && sourceTick % 100 == 0) evasions++;
            var path = getNavigation().getPath();
            if (!isNoAi() && getTarget() != null && path != null && !path.isDone() && sourceTick % 10 == 0 && random.nextInt(3) == 0) {
                int remaining = path.getNodeCount() - path.getNextNodeIndex();
                if (remaining > 1) {
                    int advance = Math.clamp(random.nextInt(remaining), 1, 4);
                    var node = path.getNode(Math.min(path.getNextNodeIndex() + advance, path.getNodeCount() - 1));
                    if (distanceToSqr(node.x + .5, node.y, node.z + .5) > 3 && teleportToSource(node.x + .5, node.y, node.z + .5))
                        path.setNextNodeIndex(Math.max(0, Math.min(path.getNextNodeIndex() + advance - 1, path.getNodeCount() - 1)));
                }
            }
        }
        LivingEntity target = getTarget();
        if (!isNoAi() && target != null) trySourceLeap(target);
        if (!isNoAi() && webs > 0 && sourceTick % (kind == MobKind.DEMON_SPIDER ? 200 : 500) == 0 && target != null && distanceTo(target) <= 8) {
            if (NativeArachnidEvents.clearThrowRay(this, target)) throwWeb(target);
        }
    }
    /** EntityArachnid.a: strict distance (2,6), one draw in ten, vertical .4 and horizontal .4 plus .2 inertia. */
    public boolean trySourceLeap(LivingEntity target) {
        return NativeArachnidEvents.trySourceLeap(this, target);
    }
    public void throwWeb(LivingEntity target) {
        if (level().isClientSide || webs <= 0) return;
        R196Web web = new R196Web(level(), this);
        Vec3 predicted = target.position().add(target.getDeltaMovement().multiply(10, 0, 10));
        double x = predicted.x - getX(), z = predicted.z - getZ();
        double y = target.getY() + target.getEyeHeight() - 1.1F - web.getY() + Math.sqrt(x * x + z * z) * .2F;
        web.shoot(x, y, z, .8F, 0F);
        if (kind == MobKind.DEMON_SPIDER || isOnFire()) web.igniteForSeconds(10);
        playSound(SoundEvents.SNOWBALL_THROW, 1F, 1F / (random.nextFloat() * .4F + .8F));
        if (level().addFreshEntity(web)) webs--;
    }
    public boolean teleportToSource(double x, double y, double z) {
        if (isRemoved() || !isAlive()) return false;
        BlockPos cursor = BlockPos.containing(x, y, z);
        if (!level().hasChunkAt(cursor) || y <= level().getMinBuildHeight()) return false;
        while (cursor.getY() > level().getMinBuildHeight() && !level().getBlockState(cursor.below()).isSolid()) { cursor = cursor.below(); y--; }
        if (cursor.getY() <= level().getMinBuildHeight() || !level().getFluidState(cursor).isEmpty()) return false;
        var box = getBoundingBox().move(x - getX(), y - getY(), z - getZ());
        if (!level().noCollision(this, box) || level().containsAnyLiquid(box)) return false;
        teleportTo(x, y, z); playSound(SoundEvents.ENDERMAN_TELEPORT, 1F, 1F);
        return true;
    }
    public boolean teleportAway(Entity threat, double minimum) {
        if (isRemoved() || !isAlive()) return false;
        for (int attempt = 0; attempt < 64; attempt++) {
            int dx = random.nextInt(11) - 5, dy = random.nextInt(9) - 4, dz = random.nextInt(11) - 5;
            if (Math.abs(dx) < 3 && Math.abs(dz) < 3) continue;
            double x = blockPosition().getX() + dx + .5, y = blockPosition().getY() + dy, z = blockPosition().getZ() + dz + .5;
            double tx = threat == null ? getX() : threat.getX(), tz = threat == null ? getZ() : threat.getZ();
            if ((x - tx) * (x - tx) + (z - tz) * (z - tz) >= minimum * minimum && teleportToSource(x, y, z)) return true;
        }
        return false;
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (source.is(DamageTypeTags.IS_FALL) && amount >= getMaxHealth() * .5F) getPersistentData().putBoolean("mite_massive_fall_031", true);
        if (!level().isClientSide && kind == MobKind.PHASE_SPIDER && evasions > 0 && !source.is(DamageTypeTags.IS_FALL)
                && !source.is(DamageTypeTags.IS_FIRE) && !source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)) {
            evasions--; if (teleportAway(source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity(), 3)) return false;
        }
        return super.hurt(source, amount);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putInt("num_webs", webs); tag.putInt("max_num_evasions", maximumEvasions); tag.putInt("num_evasions", evasions);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); webs = Math.clamp(tag.getInt("num_webs"), 0, 3);
        maximumEvasions = Math.clamp(tag.getInt("max_num_evasions"), 0, 4); evasions = Math.clamp(tag.getInt("num_evasions"), 0, maximumEvasions);
    }
    @Override protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source, boolean recentlyHit) {
        if (getPersistentData().getBoolean("mite_massive_fall_031") && random.nextFloat() >= .1F) return;
        if (recentlyHit) while (webs-- > 0) spawnAtLocation(Items.STRING);
        if (recentlyHit && random.nextInt(3) == 0) spawnAtLocation(Items.SPIDER_EYE);
    }
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound() { return kind == MobKind.DEMON_SPIDER ? io.github.ningmeng123.mite.registry.MiteMobSounds.get(kind, "say") : super.getAmbientSound(); }
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) { return kind == MobKind.DEMON_SPIDER ? io.github.ningmeng123.mite.registry.MiteMobSounds.get(kind, "hurt") : super.getHurtSound(source); }
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound() { return kind == MobKind.DEMON_SPIDER ? io.github.ningmeng123.mite.registry.MiteMobSounds.get(kind, "death") : super.getDeathSound(); }
}

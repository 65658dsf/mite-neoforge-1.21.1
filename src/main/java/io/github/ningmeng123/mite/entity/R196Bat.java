package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.rules.R196MobRules;
import io.github.ningmeng123.mite.entity.animal.SourceAquatic032;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Source hunting flight, contact attacks, vampiric healing and nightwing biology. @author NingMeng123 */
public final class R196Bat extends Bat implements R196Mob {
    private static final EntityDataAccessor<Integer> HANGING_Y = SynchedEntityData.defineId(R196Bat.class, EntityDataSerializers.INT);
    private final MobKind kind;
    private int attackCooldown;
    private int feedCooldown;
    private int soundCooldown;
    private int preventHang;
    private boolean initialHangAttempted;
    private BlockPos flightTarget;
    private float waverX, waverY, waverZ;
    public R196Bat(EntityType<? extends R196Bat> type, Level level, MobKind kind) {
        super(type, level);
        if (kind.family() != MobKind.Family.BAT) throw new IllegalArgumentException("Not a R196 bat");
        this.kind = kind; this.xpReward = R196MobRules.entry(kind).experience();
        setResting(false); // Source ro starts with BatFlags=0; modern Bat's constructor starts resting.
    }
    @Override public MobKind miteKind() { return kind; }
    @Override protected int getBaseExperienceReward() { return R196MobRules.entry(kind).experience(); }
    @Override public int getMaxSpawnClusterSize() { return 8; }
    @Override protected float getSoundVolume() { return kind == MobKind.GIANT_VAMPIRE_BAT ? .2F : .1F; }
    @Override public float getVoicePitch() { return super.getVoicePitch() / (kind == MobKind.GIANT_VAMPIRE_BAT ? 2F : 1F); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(HANGING_Y, Integer.MIN_VALUE);
    }
    public int attackCooldown() { return attackCooldown; }
    public int feedCooldown() { return feedCooldown; }
    public boolean preysUpon(LivingEntity entity) {
        if (!entity.isAlive()) return false;
        if (isVehicle() && (entity instanceof Villager || entity instanceof Animal)) return false;
        if (entity instanceof Player player) return !player.isCreative() && !player.isSpectator();
        return MonsterHunting.active(level()) && feedCooldown <= 0 && (entity instanceof Villager || entity instanceof Animal
                && !(entity instanceof R196Mob mob && mob.miteKind()==MobKind.HELLHOUND));
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (kind == MobKind.NIGHTWING && !MobCombat.silver(source) && !MobCombat.magic(source) && !MobCombat.sunlight(source)) return false;
        boolean hanging = isResting();
        boolean result = super.hurt(source, amount);
        if (result && hanging) { preventHang = 60; setResting(false); }
        return result;
    }
    @Override public boolean fireImmune() { return kind == MobKind.NIGHTWING || super.fireImmune(); }
    @Override public void tick() {
        super.tick();
        if (isResting() && entityData.get(HANGING_Y) != Integer.MIN_VALUE) fixHangingPosition();
        if (level().isClientSide || !isAlive()) return;
        if (attackCooldown > 0) --attackCooldown;
        if (soundCooldown > 0) --soundCooldown;
        if (preventHang > 0) --preventHang;
        if (feedCooldown > 0) {
            if (getHealth() < getMaxHealth()) feedCooldown = 0;
            else if (--feedCooldown > 0 && getTarget() != null && !preysUpon(getTarget())) setTarget(null);
        }
        if (MobCombat.ticksWithOffset(this) % 20 == 0) {
            LivingEntity prey = closestVisiblePrey();
            setTarget(prey); MonsterHunting.remember(this, prey);
        }
        if (getTarget() != null && getBoundingBox().intersects(getTarget().getBoundingBox())) tryContactAttack(getTarget());
    }
    /** Source abw.getClosestPrey(...,32,true,false) searches the box and requires sight, but not a path. */
    public LivingEntity closestVisiblePrey() {
        LivingEntity prey=null; double closest=Double.POSITIVE_INFINITY;
        for(LivingEntity candidate:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(32),this::preysUpon)) {
            double distance=distanceToSqr(candidate);
            if(candidate!=this && distance<closest && getSensing().hasLineOfSight(candidate)) { closest=distance; prey=candidate; }
        }
        return prey;
    }
    /** A source attack attempt consumes 20 ticks even when the narrower contact box misses. */
    public boolean tryContactAttack(LivingEntity target) {
        if (level().isClientSide || attackCooldown > 0 || target != getTarget() || !MonsterHunting.mayAttackPrey(this, target)) return false;
        attackCooldown = 20;
        if (soundCooldown <= 0) {
            level().levelEvent(null, 1025, blockPosition(), 0); soundCooldown = random.nextInt(31) + 30;
        }
        AABB box = getBoundingBox();
        double xInset = box.getXsize() * .25, zInset = box.getZsize() * .25;
        if (!box.deflate(xInset, 0, zInset).intersects(target.getBoundingBox())) return false;
        float previous = target.getHealth();
        if (!doHurtTarget(target)) return false;
        if (kind == MobKind.NIGHTWING) {
            if (target instanceof Player player) VisionDimming.apply(player, 1.25F);
        } else if (target.getHealth() < previous) {
            heal(previous - target.getHealth());
            if (target instanceof Ocelot cat && cat.isAlive() && cat.getTarget() == null) cat.setTarget(this);
            if (kind == MobKind.VAMPIRE_BAT && getHealth() >= getMaxHealth()) feedCooldown = 1200;
        }
        return true;
    }
    @Override public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !isAlive() || kind != MobKind.NIGHTWING) return;
        if (R196Undead.inSunlight(this)) hurt(MobCombat.source(this, "sunlight"), 1000F);
        else if (tickCount % 40 == 0) {
            int amount = (int)((.4F - getLightLevelDependentMagicValue()) * 10F);
            if (amount > 0) heal(amount);
        }
    }
    private boolean preventedFromHanging() {
        return isVehicle() || isPassenger() || preventHang > 0 || getTarget() != null || hurtTime > 0 || level().getNearestPlayer(this, 4) != null;
    }
    public boolean canHangFrom(BlockPos ceiling) {
        var block = level().getBlockState(ceiling);
        return !block.is(Blocks.GLASS) && block.isFaceSturdy(level(), ceiling, Direction.DOWN)
                && level().isEmptyBlock(ceiling.below())
                && (kind != MobKind.GIANT_VAMPIRE_BAT || level().isEmptyBlock(ceiling.below(2)));
    }
    private BlockPos currentCeiling() { return BlockPos.containing(getX(), getY() + .12F + getBbHeight(), getZ()); }
    public void initializeHangingAt(BlockPos ceiling) {
        entityData.set(HANGING_Y, ceiling.getY()); initialHangAttempted = true;
        setResting(true); fixHangingPosition();
    }
    private void leaveHang() { if (isResting()) preventHang = 60; setResting(false); }
    private void fixHangingPosition() {
        setDeltaMovement(Vec3.ZERO);
        setPos(getX(), entityData.get(HANGING_Y) - getBbHeight()
                - (kind == MobKind.GIANT_VAMPIRE_BAT ? .1175F : .00625F), getZ());
    }
    @Override protected void customServerAiStep() {
        BlockPos ceiling = currentCeiling();
        if (isResting()) {
            if (!canHangFrom(ceiling) || preventedFromHanging() || random.nextInt(1000) == 0) {
                leaveHang(); level().levelEvent(null, 1025, blockPosition(), 0);
            } else {
                entityData.set(HANGING_Y, ceiling.getY()); fixHangingPosition();
                if (random.nextInt(200) == 0) yHeadRot = random.nextInt(360);
            }
            return;
        }
        if (flightTarget != null && (!level().isEmptyBlock(flightTarget) || flightTarget.getY() < level().getMinBuildHeight())) flightTarget = null;
        int range = kind == MobKind.GIANT_VAMPIRE_BAT ? 14 : 7;
        if (flightTarget == null || random.nextInt(30) == 0 || flightTarget.distToCenterSqr(position()) < 4)
            flightTarget = BlockPos.containing(getX() + random.nextInt(range) - random.nextInt(range),
                    Math.max(level().getMinBuildHeight() + 1, getY() + random.nextInt(6) - 2),
                    getZ() + random.nextInt(range) - random.nextInt(range));
        Vec3 destination = flightTarget.getCenter().add(0, -.4, 0);
        LivingEntity prey = getTarget();
        if (prey != null && prey.isAlive()) {
            destination = (SourceAquatic032.combatRider(this) == null ? prey.getEyePosition() : prey.position()).add(waverX, waverY, waverZ);
            if (MobCombat.ticksWithOffset(this) % 20 == 0) {
                waverX = random.nextFloat() - .5F; waverY = random.nextFloat() - .5F; waverZ = random.nextFloat() - .5F;
            }
        }
        Vec3 difference = destination.subtract(position()), velocity = getDeltaMovement();
        velocity = velocity.add((Math.signum(difference.x) * .5 - velocity.x) * .1F,
                (Math.signum(difference.y) * .7 - velocity.y) * .1F, (Math.signum(difference.z) * .5 - velocity.z) * .1F);
        setDeltaMovement(velocity); zza = .5F;
        setYRot(getYRot() + Mth.wrapDegrees((float)(Math.atan2(velocity.z, velocity.x) * 180 / Math.PI) - 90 - getYRot()));
        if (!preventedFromHanging() && random.nextInt(initialHangAttempted ? 10 : 1) == 0 && canHangFrom(ceiling)) {
            entityData.set(HANGING_Y, ceiling.getY()); setResting(true); fixHangingPosition();
        }
        initialHangAttempted = true;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (feedCooldown > 0) tag.putShort("feed_cooldown", (short)feedCooldown);
        tag.putBoolean("initial_hang_attempted", initialHangAttempted); tag.putInt("mite_hanging_y", entityData.get(HANGING_Y));
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); feedCooldown = kind == MobKind.NIGHTWING ? 0 : Math.max(0, tag.getShort("feed_cooldown"));
        initialHangAttempted = tag.getBoolean("initial_hang_attempted");
        entityData.set(HANGING_Y, tag.contains("mite_hanging_y") ? tag.getInt("mite_hanging_y") : Integer.MIN_VALUE);
    }
}

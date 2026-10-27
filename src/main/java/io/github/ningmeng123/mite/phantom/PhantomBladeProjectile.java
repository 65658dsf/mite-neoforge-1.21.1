package io.github.ningmeng123.mite.phantom;

import io.github.ningmeng123.mite.registry.MitePhantomBlades;
import io.github.ningmeng123.mite.rules.PhantomBladeRules;
import javax.annotation.Nullable;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/** One reusable sword: orbit its owner upright, attack once, then return harmlessly. @author NingMeng123 */
public final class PhantomBladeProjectile extends Projectile {
    private static final EntityDataAccessor<String> MATERIAL = SynchedEntityData.defineId(PhantomBladeProjectile.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(PhantomBladeProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(PhantomBladeProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(PhantomBladeProjectile.class, EntityDataSerializers.INT);
    private static final int DOCKED = 0, OUTBOUND = 1, RETURNING = 2;
    private static final double SPEED = .7D, RETURN_SPEED = .9D;
    @Nullable private PhantomBladeSwarm swarm;
    private int flightTicks;

    public PhantomBladeProjectile(EntityType<? extends PhantomBladeProjectile> type, Level level) {
        super(type, level); noPhysics = true; setNoGravity(true);
    }

    public void initialize(PhantomBladeSwarm swarm, ServerPlayer owner) {
        this.swarm = swarm; setOwner(owner);
        entityData.set(OWNER, owner.getId()); entityData.set(MATERIAL, swarm.material());
        dock(owner);
    }

    public void launch(LivingEntity target) {
        entityData.set(TARGET, target.getId()); entityData.set(PHASE, OUTBOUND);
        flightTicks = 0;
        setDeltaMovement(target.getBoundingBox().getCenter().subtract(position()).normalize().scale(SPEED));
        hasImpulse = true; updateRotation();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MATERIAL, "copper"); builder.define(OWNER, -1); builder.define(TARGET, -1); builder.define(PHASE, DOCKED);
    }
    public String material() { return entityData.get(MATERIAL); }
    public int ownerId() { return entityData.get(OWNER); }
    @Nullable public Entity owner() { return level().getEntity(ownerId()); }
    public int targetId() { return entityData.get(TARGET); }
    public int bladeIndex() { return 0; }
    public boolean isDocked() { return entityData.get(PHASE) == DOCKED; }
    public boolean isReturning() { return entityData.get(PHASE) == RETURNING; }
    public int flightTicks() { return flightTicks; }
    @Override public ItemStack getWeaponItem() { return new ItemStack(MitePhantomBlades.get(material())); }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (isDocked() && owner() != null) dock(owner());
            else { setPos(position().add(getDeltaMovement())); updateRotation(); }
            return;
        }
        if (!(getOwner() instanceof ServerPlayer player) || !PhantomBladeSystem.active(player)
                || swarm == null || swarm.isRemoved() || swarm.level() != level() || player.level() != level()) { discard(); return; }
        var selected = PhantomBladeSystem.selected(player);
        if (selected == null || !selected.material().equals(material())) { discard(); return; }
        if (isDocked()) { dock(player); return; }
        if (isReturning()) { returnTo(player); return; }
        Entity target = level().getEntity(targetId());
        if (++flightTicks > 60 || !(target instanceof LivingEntity living) || !PhantomBladeSystem.validTarget(player, living)) {
            beginReturn(player); return;
        }
        Vec3 movement = living.getBoundingBox().getCenter().subtract(position()).normalize().scale(SPEED);
        setDeltaMovement(movement); hasImpulse = true; updateRotation();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            // EntityHitResult may carry the entity's feet as its location. Keep the sword on its flight segment.
            Vec3 impactPosition = hit instanceof EntityHitResult entityHit
                    ? entityHit.getEntity().getBoundingBox().inflate(.3D).clip(position(), position().add(movement)).orElse(position())
                    : hit.getLocation();
            // Cancelled impacts also end the attack; the same sword returns without another damage transaction.
            if (!EventHooks.onProjectileImpact(this, hit)) hitTargetOrDeflectSelf(hit);
            // A native deflection may change the projectile's owner. This sword still returns to its carrier.
            setOwner(player);
            setPos(impactPosition);
            beginReturn(player);
            return;
        }
        setPos(position().add(movement));
    }

    private void beginReturn(ServerPlayer player) {
        entityData.set(PHASE, RETURNING); entityData.set(TARGET, -1);
        PhantomBladeState.cooldown(player, PhantomBladeRules.profile(material()));
        aimReturn(player);
    }

    private void aimReturn(Entity player) {
        setDeltaMovement(PhantomBladeSwarm.restingPosition(player).subtract(position()).normalize().scale(RETURN_SPEED));
        hasImpulse = true; updateRotation();
    }

    private void returnTo(ServerPlayer player) {
        Vec3 destination = PhantomBladeSwarm.restingPosition(player);
        Vec3 offset = destination.subtract(position());
            // Recover into the orbit after an owner teleport without tracing across the map.
        if (offset.lengthSqr() <= RETURN_SPEED * RETURN_SPEED || offset.lengthSqr() > 64D * 64D) { dock(player); return; }
        aimReturn(player);
        setPos(position().add(getDeltaMovement()));
    }

    private void dock(Entity player) {
        entityData.set(PHASE, DOCKED); entityData.set(TARGET, -1);
        setPos(PhantomBladeSwarm.restingPosition(player));
        setDeltaMovement(Vec3.ZERO); hasImpulse = true;
        setYRot(PhantomBladeSwarm.restingYaw(player, 0F)); setXRot(0F);
    }

    @Override protected boolean canHitEntity(Entity entity) {
        // Friendly living bodies block the outbound sword but are never hurt.
        return !isDocked() && !isReturning() && entity instanceof LivingEntity && entity != getOwner()
                && entity.isAlive() && super.canHitEntity(entity);
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        if (!isDocked() && !isReturning() && getOwner() instanceof ServerPlayer player
                && hit.getEntity() instanceof LivingEntity living && PhantomBladeSystem.validTarget(player, living)) {
            var profile = PhantomBladeRules.profile(material());
            if (profile != null) living.hurt(damageSources().thrown(this, player), (float) profile.damage());
        }
    }
}

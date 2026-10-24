package io.github.ningmeng123.mite.phantom;

import io.github.ningmeng123.mite.rules.PhantomBladeRules;
import javax.annotation.Nullable;
import io.github.ningmeng123.mite.registry.MitePhantomBlades;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/** Homing is limited by real block collision; damage uses the ordinary owned thrown-projectile transaction. @author NingMeng123 */
public final class PhantomBladeProjectile extends Projectile {
    private static final EntityDataAccessor<String> MATERIAL = SynchedEntityData.defineId(PhantomBladeProjectile.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(PhantomBladeProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(PhantomBladeProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BLADE = SynchedEntityData.defineId(PhantomBladeProjectile.class, EntityDataSerializers.INT);
    @Nullable private PhantomBladeSwarm swarm;

    public PhantomBladeProjectile(EntityType<? extends PhantomBladeProjectile> type, Level level) {
        super(type, level); setNoGravity(true);
    }

    public void initialize(PhantomBladeSwarm swarm, ServerPlayer owner, LivingEntity target, int bladeIndex) {
        this.swarm = swarm; setOwner(owner);
        entityData.set(OWNER, owner.getId()); entityData.set(MATERIAL, swarm.material());
        entityData.set(TARGET, target.getId()); entityData.set(BLADE, bladeIndex);
        // Launch close to the owner's upper body so a wall beside the outer visual orbit cannot be skipped.
        setPos(owner.getX(), owner.getY() + owner.getBbHeight() * .65D, owner.getZ());
        setDeltaMovement(target.getBoundingBox().getCenter().subtract(position()).normalize().scale(.7D)); updateRotation();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MATERIAL, "copper"); builder.define(OWNER, -1); builder.define(TARGET, -1); builder.define(BLADE, 0);
    }
    public String material() { return entityData.get(MATERIAL); }
    public int ownerId() { return entityData.get(OWNER); }
    @Nullable public Entity owner() { return level().getEntity(ownerId()); }
    public int targetId() { return entityData.get(TARGET); }
    public int bladeIndex() { return entityData.get(BLADE); }
    @Override public ItemStack getWeaponItem() { return new ItemStack(MitePhantomBlades.get(material())); }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) { setPos(position().add(getDeltaMovement())); updateRotation(); return; }
        Entity target = level().getEntity(targetId());
        if (!(getOwner() instanceof ServerPlayer player) || !PhantomBladeSystem.active(player)
                || swarm == null || swarm.isRemoved() || swarm.level() != level() || tickCount > 60
                || !(target instanceof LivingEntity living) || !PhantomBladeSystem.validTarget(player, living)) { discard(); return; }
        var selected = PhantomBladeSystem.selected(player);
        if (selected == null || !selected.material().equals(material())) { discard(); return; }
        Vec3 movement = living.getBoundingBox().getCenter().subtract(position()).normalize().scale(.7D);
        setDeltaMovement(movement); hasImpulse = true; updateRotation();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            if (!EventHooks.onProjectileImpact(this, hit)) hitTargetOrDeflectSelf(hit);
            discard(); return;
        }
        setPos(position().add(movement));
    }

    @Override protected boolean canHitEntity(Entity entity) {
        // Friendly living bodies block the blade but are never hurt, instead of allowing a shot through a pet or player.
        return entity instanceof LivingEntity && entity != getOwner() && entity.isAlive() && super.canHitEntity(entity);
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        if (getOwner() instanceof ServerPlayer player && hit.getEntity() instanceof LivingEntity living
                && PhantomBladeSystem.validTarget(player, living)) {
            var profile = PhantomBladeRules.profile(material());
            if (profile != null) living.hurt(damageSources().thrown(this, player), (float)profile.damage());
        }
    }
}

package io.github.ningmeng123.mite.phantom;

import io.github.ningmeng123.mite.registry.MitePhantomBlades;
import io.github.ningmeng123.mite.rules.PhantomBladeRules;
import java.util.Comparator;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** An invisible controller owns exactly one reusable, synchronized sword. @author NingMeng123 */
public final class PhantomBladeSwarm extends Entity {
    public static final int ORBIT_PERIOD_TICKS = 160;
    public static final double ORBIT_RADIUS = 1.1D, ORBIT_BOB_AMPLITUDE = .08D;
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(PhantomBladeSwarm.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> MATERIAL = SynchedEntityData.defineId(PhantomBladeSwarm.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ORBIT_MASK = SynchedEntityData.defineId(PhantomBladeSwarm.class, EntityDataSerializers.INT);
    @Nullable private PhantomBladeProjectile blade;

    public PhantomBladeSwarm(EntityType<? extends PhantomBladeSwarm> type, Level level) {
        super(type, level); noPhysics = true; setNoGravity(true);
    }

    public void initialize(ServerPlayer owner, PhantomBladeRules.Profile profile) {
        entityData.set(OWNER, owner.getId()); entityData.set(MATERIAL, profile.material());
        PhantomBladeState.current(owner, profile);
        entityData.set(ORBIT_MASK, 1); follow(owner);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1); builder.define(MATERIAL, "copper"); builder.define(ORBIT_MASK, 0);
    }
    public int ownerId() { return entityData.get(OWNER); }
    @Nullable public Entity owner() { return level().getEntity(ownerId()); }
    public String material() { return entityData.get(MATERIAL); }
    public int bladeCount() { return 1; }
    public int orbitBladeMask() { return entityData.get(ORBIT_MASK); }
    public int inFlightCount() { return blade != null && !blade.isRemoved() && !blade.isDocked() ? 1 : 0; }

    /** World time and the owner's saved identity keep the orbit continuous across turns and recreation. */
    public static double restingAngle(UUID ownerId, double gameTime) {
        double phase = (ownerId.hashCode() & 65535) / 65536D;
        return Math.PI * 2D * (gameTime % ORBIT_PERIOD_TICKS / ORBIT_PERIOD_TICKS + phase);
    }
    /** Shared by server docking, return flight and partial-tick client rendering. */
    public static Vec3 restingOffset(UUID ownerId, double ownerHeight, double gameTime) {
        double angle = restingAngle(ownerId, gameTime);
        return new Vec3(Math.sin(angle) * ORBIT_RADIUS, ownerHeight * .65D + Math.sin(angle * 2D) * ORBIT_BOB_AMPLITUDE,
                -Math.cos(angle) * ORBIT_RADIUS);
    }
    public static Vec3 restingPosition(Entity owner) {
        return owner.position().add(restingOffset(owner.getUUID(), owner.getBbHeight(), owner.level().getGameTime()));
    }
    public static Vec3 restingPosition(Entity owner, float partialTick) {
        return owner.getPosition(partialTick).add(restingOffset(owner.getUUID(), owner.getBbHeight(), owner.level().getGameTime() + (double)partialTick));
    }
    public static float restingYaw(Entity owner, float partialTick) {
        return (float)Math.toDegrees(restingAngle(owner.getUUID(), owner.level().getGameTime() + (double)partialTick));
    }
    private void follow(Entity owner) { setPos(restingPosition(owner)); }

    @Override public void tick() {
        super.tick();
        Entity owner = owner();
        if (level().isClientSide) { if (owner != null) follow(owner); return; }
        if (!(owner instanceof ServerPlayer player) || !PhantomBladeSystem.active(player)) { discard(); return; }
        var profile = PhantomBladeSystem.selected(player);
        if (profile == null || !profile.material().equals(material())) { discard(); return; }
        follow(player);
        if (blade == null || blade.isRemoved()) {
            PhantomBladeProjectile replacement = new PhantomBladeProjectile(MitePhantomBlades.PROJECTILE.get(), level());
            replacement.initialize(this, player);
            if (level().addFreshEntity(replacement)) blade = replacement;
            else { replacement.discard(); return; }
        }
        entityData.set(ORBIT_MASK, blade.isDocked() ? 1 : 0);
        if (!blade.isDocked() || !PhantomBladeState.ready(player, PhantomBladeState.current(player, profile))) return;
        LivingEntity target = level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(profile.targetRange()),
                candidate -> PhantomBladeSystem.validTarget(player, candidate) && player.hasLineOfSight(candidate))
                .stream().min(Comparator.<LivingEntity>comparingDouble(player::distanceToSqr).thenComparingInt(Entity::getId)).orElse(null);
        if (target == null) return;
        PhantomBladeState.consume(player, profile);
        blade.launch(target);
        entityData.set(ORBIT_MASK, 0);
    }

    @Override public void remove(RemovalReason reason) {
        super.remove(reason);
        if (blade != null) blade.discard();
        blade = null;
        PhantomBladeSystem.removed(this);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

package io.github.ningmeng123.mite.phantom;

import io.github.ningmeng123.mite.registry.MitePhantomBlades;
import io.github.ningmeng123.mite.rules.PhantomBladeRules;
import java.util.Comparator;
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

    /** Shared by flight logic and rendering so the waiting sword stays behind the player's facing. */
    public static Vec3 restingOffset(float yaw, double ownerHeight) {
        double radians = Math.toRadians(yaw);
        return new Vec3(Math.sin(radians) * 1.1D, ownerHeight * .65D, -Math.cos(radians) * 1.1D);
    }
    public static Vec3 restingPosition(Entity owner) {
        return owner.position().add(restingOffset(owner.getYRot(), owner.getBbHeight()));
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
        LivingEntity target = level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(PhantomBladeSystem.RANGE),
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

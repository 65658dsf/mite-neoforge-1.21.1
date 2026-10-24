package io.github.ningmeng123.mite.phantom;

import io.github.ningmeng123.mite.registry.MitePhantomBlades;
import io.github.ningmeng123.mite.rules.PhantomBladeRules;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
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

/** A single synchronized visual formation launches real, event-respecting projectiles. @author NingMeng123 */
public final class PhantomBladeSwarm extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(PhantomBladeSwarm.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> MATERIAL = SynchedEntityData.defineId(PhantomBladeSwarm.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ORBIT_MASK = SynchedEntityData.defineId(PhantomBladeSwarm.class, EntityDataSerializers.INT);
    private final Set<PhantomBladeProjectile> projectiles = new HashSet<>();

    public PhantomBladeSwarm(EntityType<? extends PhantomBladeSwarm> type, Level level) {
        super(type, level); noPhysics = true; setNoGravity(true);
    }

    public void initialize(ServerPlayer owner, PhantomBladeRules.Profile profile) {
        entityData.set(OWNER, owner.getId()); entityData.set(MATERIAL, profile.material());
        entityData.set(ORBIT_MASK, PhantomBladeState.current(owner, profile).orbitMask(profile)); follow(owner);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1); builder.define(MATERIAL, "copper"); builder.define(ORBIT_MASK, 0);
    }
    public int ownerId() { return entityData.get(OWNER); }
    @Nullable public Entity owner() { return level().getEntity(ownerId()); }
    public String material() { return entityData.get(MATERIAL); }
    public int bladeCount() {
        var profile = PhantomBladeRules.profile(material()); return profile == null ? 0 : profile.bladeCount();
    }
    public int orbitBladeMask() { return entityData.get(ORBIT_MASK); }
    public int inFlightCount() { return projectiles.size(); }

    private void follow(Entity owner) { setPos(owner.getX(), owner.getY() + owner.getBbHeight() * .6D, owner.getZ()); }

    @Override public void tick() {
        super.tick();
        Entity owner = owner();
        if (level().isClientSide) { if (owner != null) follow(owner); return; }
        if (!(owner instanceof ServerPlayer player) || !PhantomBladeSystem.active(player)) { discard(); return; }
        var profile = PhantomBladeSystem.selected(player);
        if (profile == null || !profile.material().equals(material())) { discard(); return; }
        follow(player);
        projectiles.removeIf(Entity::isRemoved);
        var state = PhantomBladeState.current(player, profile);
        entityData.set(ORBIT_MASK, state.orbitMask(profile));
        if (!PhantomBladeState.ready(player, state)) return;
        LivingEntity target = level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(PhantomBladeSystem.RANGE),
                candidate -> PhantomBladeSystem.validTarget(player, candidate) && player.hasLineOfSight(candidate))
                .stream().min(Comparator.<LivingEntity>comparingDouble(player::distanceToSqr).thenComparingInt(Entity::getId)).orElse(null);
        if (target == null) return;
        PhantomBladeProjectile projectile = new PhantomBladeProjectile(MitePhantomBlades.PROJECTILE.get(), level());
        projectile.initialize(this, player, target, profile.bladeCount()-state.charges());
        if (level().addFreshEntity(projectile)) {
            projectiles.add(projectile); entityData.set(ORBIT_MASK, PhantomBladeState.consume(player, profile).orbitMask(profile));
        } else projectile.discard();
    }

    @Override public void remove(RemovalReason reason) {
        super.remove(reason);
        for (var projectile : Set.copyOf(projectiles)) projectile.discard();
        projectiles.clear(); PhantomBladeSystem.removed(this);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

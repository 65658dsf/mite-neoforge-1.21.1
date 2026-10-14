package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.ranged.SphereCorrosion;
import io.github.ningmeng123.mite.registry.MiteWeaponContent;
import io.github.ningmeng123.mite.rules.R196WeaponRules;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Source missiles deal ordinary thrown damage; acid/pepsin applies only to blocks. @author NingMeng123 */
public final class GelatinousSphere extends ThrowableItemProjectile {
    public GelatinousSphere(EntityType<? extends GelatinousSphere> type, Level level) { super(type, level); }
    public GelatinousSphere(Level level, LivingEntity owner, ItemStack item) {
        super(MiteWeaponContent.SPHERE.get(), owner, level); setItem(item);
        setPos(getX() - (double)(Mth.cos(owner.getYRot() * Mth.DEG_TO_RAD) * .16F), getY(),
                getZ() - (double)(Mth.sin(owner.getYRot() * Mth.DEG_TO_RAD) * .16F));
    }
    @Override protected Item getDefaultItem() { return Items.SLIME_BALL; }
    public int subtype() { return Math.max(MiteWeaponContent.sphereSubtype(getItem()), 0); }
    @Override protected double getDefaultGravity() { return .07F; }
    @Override public void shootFromRotation(Entity owner, float pitch, float yaw, float offset, float speed, float spread) {
        shoot(-Mth.sin(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD),
                -Mth.sin((pitch + offset) * Mth.DEG_TO_RAD),
                Mth.cos(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD), speed, spread);
    }
    @Override public void shoot(double x, double y, double z, float speed, float spread) {
        Vec3 vector = new Vec3(x, y, z).normalize().add(random.nextGaussian() * .0075F * spread,
                random.nextGaussian() * .0075F * spread, random.nextGaussian() * .0075F * spread).scale(speed);
        setDeltaMovement(vector);
        setYRot((float)(Mth.atan2(vector.x, vector.z) * Mth.RAD_TO_DEG));
        setXRot((float)(Mth.atan2(vector.y, vector.horizontalDistance()) * Mth.RAD_TO_DEG));
        yRotO = getYRot(); xRotO = getXRot();
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level() instanceof ServerLevel server && hit.getEntity().hurt(damageSources().thrown(this, getOwner()), R196WeaponRules.sphereDamage(subtype()))) {
            server.sendParticles(ParticleTypes.CLOUD, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 8, .15, .15, .15, .02);
            server.playSound(null, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, .5F, 1F);
        }
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        if (!level().isClientSide) {
            SphereCorrosion.contact(level(), hit.getBlockPos().relative(hit.getDirection()), hit.getDirection().getOpposite(), R196WeaponRules.acidic(subtype()));
            SphereCorrosion.contact(level(), hit.getBlockPos(), hit.getDirection(), R196WeaponRules.acidic(subtype()));
        }
        super.onHitBlock(hit);
    }
    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide) { level().broadcastEntityEvent(this, (byte)3); discard(); }
    }
    @Override public void handleEntityEvent(byte event) {
        if (event == 3) for (int i = 0; i < 8; i++) level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, getItem()), getX(), getY(), getZ(), 0, 0, 0);
        else super.handleEntityEvent(event);
    }
}

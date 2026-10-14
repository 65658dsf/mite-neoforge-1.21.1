package io.github.ningmeng123.mite.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;

/** R196 abr uses independent block power and linear living damage over four times living power. @author NingMeng123 */
public final class R196ExplosionCalculator extends EntityBasedExplosionDamageCalculator {
    private final float livingPower;
    public R196ExplosionCalculator(Entity source, float livingPower) { super(source); this.livingPower = livingPower; }
    public float entityRadius() { return livingPower * 4F; }
    @Override public float getEntityDamageAmount(Explosion explosion, Entity entity) {
        double intensity = Math.max(0D, 1D - Math.sqrt(entity.distanceToSqr(explosion.center())) / entityRadius())
                * Explosion.getSeenPercent(explosion.center(), entity);
        float damage = entityRadius() * (float)intensity * 4F * .6666667F;
        return damage >= .5F ? damage : 0F;
    }
    @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) { return getEntityDamageAmount(explosion, entity) >= .5F; }
}

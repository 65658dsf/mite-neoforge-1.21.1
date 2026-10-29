package io.github.ningmeng123.mite.client.mob;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/** A faint silhouette above the usual shader alpha discard threshold. @author NingMeng123 */
public final class InvisibleMonsterVisuals {
    public static final float OPACITY = .16F;
    public static final int COLOR = 0x29FFFFFF;

    private InvisibleMonsterVisuals() {}

    public static boolean silhouette(LivingEntity entity) {
        return entity instanceof Enemy && entity.isInvisible();
    }
}

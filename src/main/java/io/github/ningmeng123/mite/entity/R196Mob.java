package io.github.ningmeng123.mite.entity;

import net.minecraft.world.damagesource.DamageSource;

/** Common identity used by rules, rendering and naturally selected source entities. @author NingMeng123 */
public interface R196Mob {
    MobKind miteKind();
    default float naturalDefense(DamageSource source) { return 0F; }
    default boolean biologicallyAlive() {
        return switch (miteKind()) {
            case WIGHT, SHADOW, NIGHTWING, HELLHOUND, FIRE_ELEMENTAL, EARTH_ELEMENTAL, CLAY_GOLEM -> false;
            default -> miteKind().family() != MobKind.Family.ZOMBIE && miteKind().family() != MobKind.Family.SKELETON;
        };
    }
    default boolean healsWithTime() {
        return switch (miteKind()) { case WIGHT, SHADOW, NIGHTWING, FIRE_ELEMENTAL -> false;
            default -> miteKind().family() != MobKind.Family.ZOMBIE && miteKind().family() != MobKind.Family.SKELETON; };
    }
}

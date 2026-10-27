package io.github.ningmeng123.mite.entity;

import net.minecraft.world.phys.AABB;

/** Read the native virtual attack volume without moving the attacking mob. @author NingMeng123 */
public interface MobAttackBoundsAccess {
    AABB mite$attackBoundingBox();
}

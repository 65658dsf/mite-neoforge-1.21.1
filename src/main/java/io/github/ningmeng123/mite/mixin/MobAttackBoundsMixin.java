package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.MobAttackBoundsAccess;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Dynamic dispatch preserves species-specific attack volumes such as the ravager's. @author NingMeng123 */
@Mixin(Mob.class)
public interface MobAttackBoundsMixin extends MobAttackBoundsAccess {
    @Override @Invoker("getAttackBoundingBox")
    AABB mite$attackBoundingBox();
}

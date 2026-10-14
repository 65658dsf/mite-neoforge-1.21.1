package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.ranged.SourceBowLaunch;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep native shot events, ammunition consumption and enchantments, replace source bow launch only. @author NingMeng123 */
@Mixin(BowItem.class)
public abstract class SourceBowLaunchMixin {
    @Redirect(method = "shootProjectile", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/Projectile;shootFromRotation(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void mite$launch(Projectile projectile, net.minecraft.world.entity.Entity shooter, float pitch, float yaw, float offset, float speed, float spread) {
        if (!(shooter instanceof LivingEntity living) || !SourceBowLaunch.launch(projectile, living, pitch + offset, yaw, speed))
            projectile.shootFromRotation(shooter, pitch, yaw, offset, speed, spread);
    }
}

package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.progression.GearProgressionEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Bow, crossbow and firework kills keep the identity of the weapon used to fire them. @author NingMeng123 */
@Mixin(ProjectileWeaponItem.class)
public abstract class GearProjectileWeaponMixin {
    @WrapOperation(method = "shoot", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/ProjectileWeaponItem;createProjectile(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/projectile/Projectile;"))
    private Projectile mite$rememberFiringWeapon(ProjectileWeaponItem item, Level level, LivingEntity shooter,
            ItemStack weapon, ItemStack ammunition, boolean critical, Operation<Projectile> original) {
        boolean eligible = shooter instanceof Player player && GearProgression.canGain(player) && GearProgression.canKill(weapon);
        if (eligible) GearProgression.ensureId(weapon);
        Projectile projectile = original.call(item, level, shooter, weapon, ammunition, critical);
        if (eligible) GearProgressionEvents.projectile(projectile, weapon);
        return projectile;
    }
}

package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.survival.DeathOrbQueue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Defers only player XP after NeoForge has resolved its drop event. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityExperienceDropMixin {
    @WrapOperation(method = "dropExperience", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"),
            require = 1, allow = 1)
    private void mite$delayOwnedDeathExperience(ServerLevel level, Vec3 position, int amount, Operation<Void> original) {
        if ((Object) this instanceof ServerPlayer player) {
            DeathOrbQueue.get(level).enqueue(player.getUUID(), position, amount, level.getGameTime());
        } else {
            original.call(level, position, amount);
        }
    }
}

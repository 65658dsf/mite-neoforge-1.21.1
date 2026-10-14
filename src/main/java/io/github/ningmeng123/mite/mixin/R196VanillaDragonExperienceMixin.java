package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** sk drops100 at each five-tick interval155..200, then200 at death200: total1200, without changing the modern dragon fight. @author NingMeng123 */
@Mixin(EnderDragon.class)
public abstract class R196VanillaDragonExperienceMixin {
    @ModifyArg(method = "tickDeath", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;getExperienceDrop(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/player/Player;I)I", ordinal = 0), index = 2)
    private int mite$sourceDragonIntervalExperience(int original) { return 100; }
    @ModifyArg(method = "tickDeath", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;getExperienceDrop(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/player/Player;I)I", ordinal = 1), index = 2)
    private int mite$sourceDragonFinalExperience(int original) { return 200; }
}

package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.BloodMoonEquipment;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Modern zombie/skeleton families share source armor generation without rerolling weapon enchantments. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class BloodMoonEquipmentMixin {
    @Inject(method = "populateDefaultEquipmentSlots", at = @At("HEAD"), cancellable = true)
    private void mite$progressiveArmor(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        if (BloodMoonEquipment.populate((Mob)(Object)this)) ci.cancel();
    }

    @Inject(method = "enchantSpawnedArmor", at = @At("HEAD"), cancellable = true)
    private void mite$alreadyGeneratedArmor(ServerLevelAccessor level, RandomSource random, EquipmentSlot slot,
            DifficultyInstance difficulty, CallbackInfo ci) {
        if (BloodMoonEquipment.generatedSlot((Mob)(Object)this, slot)) ci.cancel();
    }
}

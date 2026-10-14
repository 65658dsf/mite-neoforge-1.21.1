package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.NativeMobEquipment;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace native iron weapon generation with R196's owner-RNG weapon and armor sequence. @author NingMeng123 */
@Mixin({Zombie.class, ZombifiedPiglin.class})
public abstract class NativeMobEquipmentMixin {
    @Inject(method = "populateDefaultEquipmentSlots", at = @At("HEAD"), cancellable = true)
    private void mite$sourceNativeEquipment(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        if (NativeMobEquipment.populate((Mob)(Object)this)) ci.cancel();
    }
}

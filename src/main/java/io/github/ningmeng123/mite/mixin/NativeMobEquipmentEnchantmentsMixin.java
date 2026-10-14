package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.NativeMobEquipment;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ye.randomizeForMob already performs source enchantment and wear once per new stack. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class NativeMobEquipmentEnchantmentsMixin {
    @Inject(method = "populateDefaultEquipmentEnchantments", at = @At("HEAD"), cancellable = true)
    private void mite$sourceDoesNotEnchantEquipmentTwice(ServerLevelAccessor level, RandomSource random,
            DifficultyInstance difficulty, CallbackInfo ci) {
        if (NativeMobEquipment.handles(((Mob)(Object)this).getType())) ci.cancel();
    }
}

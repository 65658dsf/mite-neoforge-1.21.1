package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.BloodMoonEquipment;
import io.github.ningmeng123.mite.entity.MobEquipmentEnchantments;
import io.github.ningmeng123.mite.entity.PiglinArmor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shared spawn armor for native mobs, without rerolling source-generated armor. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class BloodMoonEquipmentMixin {
    @Inject(method = "populateDefaultEquipmentSlots", at = @At("HEAD"), cancellable = true)
    private void mite$progressiveArmor(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        if (BloodMoonEquipment.populate((Mob)(Object)this)) ci.cancel();
    }

    @Inject(method = "enchantSpawnedArmor", at = @At("HEAD"), cancellable = true)
    private void mite$alreadyGeneratedArmor(ServerLevelAccessor level, RandomSource random, EquipmentSlot slot,
            DifficultyInstance difficulty, CallbackInfo ci) {
        Mob mob = (Mob)(Object)this;
        if (BloodMoonEquipment.generatedSlot(mob, slot) || PiglinArmor.handles(mob)
                && MobEquipmentEnchantments.alreadyGenerated(mob, mob.getItemBySlot(slot))) ci.cancel();
    }
}

package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.item.EquipmentQuality;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Run after spawn enchantment rolls, never on equipment pickup or save restoration. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class QualityMobEquipmentMixin {
    @Inject(method = "populateDefaultEquipmentEnchantments", at = @At("RETURN"))
    private void mite$generatedQuality(ServerLevelAccessor level, RandomSource random, DifficultyInstance difficulty,
            CallbackInfo ci) {
        Mob mob = (Mob)(Object)this;
        for (EquipmentSlot slot : EquipmentSlot.values())
            EquipmentQuality.applyGeneratedMobQuality(mob.getItemBySlot(slot));
    }

    @WrapOperation(method = "dropCustomDeathLoot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;getEquipmentDropChance(Lnet/minecraft/world/entity/EquipmentSlot;)F"))
    private float mite$poorHeldDropPenalty(Mob mob, EquipmentSlot slot, Operation<Float> original) {
        float chance = original.call(mob, slot);
        if (EquipmentQuality.hasPoorHeldDropPenalty(mob.getItemBySlot(slot), slot, chance)
                && mob.getRandom().nextFloat() < 0.75F) return 0F;
        return chance;
    }
}

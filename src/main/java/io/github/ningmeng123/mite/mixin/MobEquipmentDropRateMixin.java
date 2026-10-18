package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.entity.MobEquipmentDrops;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Apply the reduction after native quality and looting rolls, preserving picked-up gear. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class MobEquipmentDropRateMixin {
    @WrapOperation(method = "dropCustomDeathLoot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextFloat()F"))
    private float mite$reduceNaturalEquipmentDrops(RandomSource random, Operation<Float> original,
            @Local EquipmentSlot slot) {
        float roll = original.call(random);
        Mob mob = (Mob)(Object)this;
        float chance = ((SourceMobDropChanceAccess)mob).mite$equipmentDropChance(slot);
        return MobEquipmentDrops.keep(mob, chance) ? roll : Float.POSITIVE_INFINITY;
    }
}

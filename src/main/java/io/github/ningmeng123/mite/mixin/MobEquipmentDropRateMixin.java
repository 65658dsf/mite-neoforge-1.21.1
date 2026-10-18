package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.entity.MobEquipmentDrops;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Apply the reduction after native quality and looting rolls, preserving picked-up gear. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class MobEquipmentDropRateMixin {
    @WrapOperation(method = "equipItemIfPossible", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;spawnAtLocation(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity mite$restrictReplacedNaturalEquipment(Mob mob, ItemStack previous, Operation<ItemEntity> original,
            @Local(ordinal = 0) double originalDropChance) {
        // The captured chance belongs to the old stack, before the incoming item receives its pickup guarantee.
        return MobEquipmentDrops.canDrop(mob, (float)originalDropChance, previous) ? original.call(mob, previous) : null;
    }

    @WrapOperation(method = "dropCustomDeathLoot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextFloat()F"))
    private float mite$reduceNaturalEquipmentDrops(RandomSource random, Operation<Float> original,
            @Local EquipmentSlot slot) {
        float roll = original.call(random);
        Mob mob = (Mob)(Object)this;
        float chance = ((SourceMobDropChanceAccess)mob).mite$equipmentDropChance(slot);
        return MobEquipmentDrops.keep(mob, chance, mob.getItemBySlot(slot)) ? roll : Float.POSITIVE_INFINITY;
    }
}

package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep stats, break notifications and other enchantments in their native paths. @author NingMeng123 */
@Mixin(ItemStack.class)
public abstract class DurabilityItemStackMixin {
    @Inject(method = "getMaxDamage", at = @At("RETURN"), cancellable = true)
    private void mite$qualityMaximum(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(EquipmentDurability.qualityAdjustedMaximum((ItemStack)(Object)this, cir.getReturnValueI()));
    }

    @WrapOperation(method = "mineBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;mineBlock(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private boolean mite$toolMiningWear(Item item, ItemStack stack, Level level, BlockState state,
            BlockPos pos, LivingEntity miner, Operation<Boolean> original) {
        return EquipmentDurability.toolProfile(stack) != null && !(item instanceof EquipmentDurability.Profiled)
                ? EquipmentDurability.mineBlock(stack, level, state, pos, miner)
                : original.call(item, stack, level, state, pos, miner);
    }

    @WrapOperation(method = "hurtEnemy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;hurtEnemy(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private boolean mite$allToolsWearOnHit(Item item, ItemStack stack, LivingEntity target,
            LivingEntity attacker, Operation<Boolean> original) {
        return EquipmentDurability.toolProfile(stack) != null && !(item instanceof EquipmentDurability.Profiled)
                || original.call(item, stack, target, attacker);
    }

    @WrapOperation(method = "postHurtEnemy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;postHurtEnemy(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void mite$toolAttackWear(Item item, ItemStack stack, LivingEntity target,
            LivingEntity attacker, Operation<Void> original) {
        if (EquipmentDurability.toolProfile(stack) != null && !(item instanceof EquipmentDurability.Profiled))
            EquipmentDurability.postHurtEnemy(stack, attacker);
        else original.call(item, stack, target, attacker);
    }

    @WrapOperation(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;processDurabilityChange(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;I)I"))
    private int mite$lastArmorPoint(ServerLevel level, ItemStack stack, int amount, Operation<Integer> original,
            @Local(argsOnly = true) LivingEntity owner) {
        return EquipmentDurability.limitArmorWear(stack, original.call(level, stack, amount), owner);
    }
}

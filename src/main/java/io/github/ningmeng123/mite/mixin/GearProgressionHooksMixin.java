package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.progression.GearProgressionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observe the committed NeoForge transaction after all cancellable listeners have finished. @author NingMeng123 */
@Mixin(CommonHooks.class)
public abstract class GearProgressionHooksMixin {
    @WrapOperation(method = "onPlaceItemIntoWorld", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;onPlace(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V"))
    private static void mite$committedEquipmentUse(BlockState state, Level level, BlockPos pos, BlockState previous,
            boolean moved, Operation<Void> original, @Local(argsOnly = true) UseOnContext context, @Local Item item) {
        // Keep the original item type: the final block in a stack may already have been consumed.
        GearProgressionEvents.beforeCommittedUse(context, pos, state, item instanceof BlockItem);
        original.call(state, level, pos, previous, moved);
        GearProgressionEvents.afterCommittedUse(context, pos, previous);
    }

    @WrapMethod(method = "onLivingDamagePost")
    private static void mite$completedArmorHit(LivingEntity entity, DamageContainer container, Operation<Void> original) {
        original.call(entity, container);
        GearProgressionEvents.damageCompleted(entity, container);
    }

    @WrapMethod(method = "onDamageBlock")
    private static LivingShieldBlockEvent mite$completedShieldBlock(LivingEntity entity, DamageContainer container,
            boolean originalBlocked, Operation<LivingShieldBlockEvent> original) {
        LivingShieldBlockEvent result = original.call(entity, container, originalBlocked);
        GearProgressionEvents.shieldBlocked(entity, result);
        return result;
    }
}

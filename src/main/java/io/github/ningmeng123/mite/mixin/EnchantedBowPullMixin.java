package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.enchantment.RangedEnchantments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve ArrowLoose event draw time while changing the actual shot and critical threshold. @author NingMeng123 */
@Mixin(BowItem.class)
public abstract class EnchantedBowPullMixin {
    @WrapOperation(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"))
    private float mite$quickness(int ticks, Operation<Float> original, ItemStack bow, Level level, LivingEntity user, int remaining) {
        return RangedEnchantments.power(bow, level, ticks);
    }
}

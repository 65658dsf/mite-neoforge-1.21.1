package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.rules.FunctionalBlockRules;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Share modern enchantment selection/payment but expose the source emerald energy formula. @author NingMeng123 */
@Mixin(EnchantmentMenu.class)
public abstract class EmeraldEnchantmentMenuMixin {
    @Shadow @Final private ContainerLevelAccess access;
    @Redirect(method = "lambda$slotsChanged$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getEnchantmentCost(Lnet/minecraft/util/RandomSource;IILnet/minecraft/world/item/ItemStack;)I"))
    private int mite$emeraldPower(RandomSource random, int slot, int shelves, ItemStack stack) {
        boolean emerald = access.evaluate((level, pos) -> level.getBlockState(pos).is(MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get()), false);
        return emerald ? FunctionalBlockRules.enchantmentLevels(shelves, true, stack.getEnchantmentValue(), slot,
                slot < 2 ? random.nextFloat() : .5F) : EnchantmentHelper.getEnchantmentCost(random, slot, shelves, stack);
    }
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void mite$validEmeraldTable(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (access.evaluate((level, pos) -> level.getBlockState(pos).is(MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get()), false))
            cir.setReturnValue(access.evaluate((level, pos) -> player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64D, false));
    }
}

package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.MobEquipmentUpgrades;
import io.github.ningmeng123.mite.entity.PiglinArmor;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Piglins share undead armor generation and replacement while retaining their weapon preferences. @author NingMeng123 */
@Mixin(Piglin.class)
public abstract class PiglinEquipmentUpgradeMixin extends AbstractPiglin {
    protected PiglinEquipmentUpgradeMixin(EntityType<? extends AbstractPiglin> type, Level level) {
        super(type, level);
    }

    @Inject(method = "populateDefaultEquipmentSlots", at = @At("HEAD"), cancellable = true)
    private void mite$progressiveArmor(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        if (!PiglinArmor.handles(this)) return;
        if (isAdult()) R196MobEquipment.randomArmor(this);
        ci.cancel();
    }

    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void mite$rejectLowTierArmor(ItemStack incoming, CallbackInfoReturnable<Boolean> cir) {
        if (PiglinArmor.rejects(this, incoming)) cir.setReturnValue(false);
    }

    @Inject(method = "canReplaceCurrentItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true)
    private void mite$preferEquipmentQuality(ItemStack incoming, ItemStack current, CallbackInfoReturnable<Boolean> cir) {
        if (PiglinArmor.handles(this) && incoming.getItem() instanceof ArmorItem) {
            cir.setReturnValue(super.canReplaceCurrentItem(incoming, current));
            return;
        }
        Boolean decision = MobEquipmentUpgrades.replacementDecision(this, incoming, current);
        if (decision != null) cir.setReturnValue(decision);
    }
}

package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import io.github.ningmeng123.mite.progression.ProgressionThrownWeapon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native approved spear hits keep their original effects and charge the actual recoverable weapon. @author NingMeng123 */
@Pseudo
@Mixin(targets = "net.sweenus.simplyswords.entity.ThrownSpearEntity", remap = false)
public abstract class SimplySwordsThrownSpearMixin extends AbstractArrow implements ProgressionThrownWeapon {
    @Shadow(remap = false) public ItemStack stack;
    @Shadow(remap = false) @Final private static EntityDataAccessor<ItemStack> ITEM_STACK;

    protected SimplySwordsThrownSpearMixin(EntityType<? extends AbstractArrow> type, Level level) { super(type, level); }

    @Override public ItemStack mite$progressionWeapon() { return stack; }
    @Override public void mite$syncProgressionWeapon() { mite$syncSpear(stack); }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void mite$removeRetiredSpearBeforeTick(CallbackInfo ci) {
        if (mite$purgeRetiredSpear()) ci.cancel();
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void mite$removeRetiredSpearBeforeHit(EntityHitResult hit, CallbackInfo ci) {
        if (mite$purgeRetiredSpear()) ci.cancel();
    }

    @Inject(method = "tryPickup", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void mite$removeRetiredSpearBeforePickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (mite$purgeRetiredSpear()) cir.setReturnValue(false);
    }

    @Unique private boolean mite$purgeRetiredSpear() {
        if (level().isClientSide || stack == null || (!stack.isEmpty() && !RemovedDiamondEquipment.isRemoved(stack))) return false;
        mite$syncSpear(ItemStack.EMPTY);
        return true;
    }

    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/SwordItem;hurtEnemy(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
            remap = false), require = 1, remap = false)
    private boolean mite$wearApprovedSpearHit(SwordItem item, ItemStack spear, LivingEntity target,
            LivingEntity owner, Operation<Boolean> original) {
        boolean result = original.call(item, spear, target, owner);
        // The original call occurs only after successful damage, once per distinct approved target.
        if (!level().isClientSide && SimplySwordsCompat.definition(spear) != null) {
            EquipmentDurability.postHurtEnemy(spear, owner);
            mite$syncSpear(spear);
        }
        return result;
    }

    @Unique private void mite$syncSpear(ItemStack spear) {
        stack = spear;
        getEntityData().set(ITEM_STACK, spear.copy(), true);
        // AbstractArrow substitutes its default weapon for an empty pickup stack; a broken spear must disappear.
        if (spear.isEmpty()) discard();
        else setPickupItemStack(spear);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"), require = 1, remap = false)
    private void mite$restoreRecoverableSpear(CompoundTag tag, CallbackInfo ci) {
        if (level().isClientSide || !tag.contains("item")) return;
        // Simply Swords 1.70 writes "item" but checks the old "Stack" key on load.
        ItemStack restored = ItemStack.parse(registryAccess(), tag.getCompound("item")).orElse(ItemStack.EMPTY);
        // Removed or invalid saved weapons must not fall back to the upstream default pickup item.
        if (restored.isEmpty() || SimplySwordsCompat.definition(restored) != null) mite$syncSpear(restored);
    }
}

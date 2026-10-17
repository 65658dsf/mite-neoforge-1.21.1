package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native approved spear hits keep their original effects and charge the actual recoverable weapon. @author NingMeng123 */
@Pseudo
@Mixin(targets = "net.sweenus.simplyswords.entity.ThrownSpearEntity", remap = false)
public abstract class SimplySwordsThrownSpearMixin extends AbstractArrow {
    @Shadow(remap = false) public ItemStack stack;
    @Shadow(remap = false) @Final private static EntityDataAccessor<ItemStack> ITEM_STACK;

    protected SimplySwordsThrownSpearMixin(EntityType<? extends AbstractArrow> type, Level level) { super(type, level); }

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
        if (SimplySwordsCompat.definition(restored) != null) mite$syncSpear(restored);
    }
}

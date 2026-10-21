package io.github.ningmeng123.mite.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ordinary dropped items expire after fifteen minutes of entity ticks. @author NingMeng123 */
@Mixin(ItemEntity.class)
public abstract class DroppedItemLifetimeMixin {
    @Unique private static final int mite$ORIGINAL_LIFETIME = 5 * 60 * 20;
    @Unique private static final int mite$DROPPED_ITEM_LIFETIME = 15 * 60 * 20;
    @Shadow private int age;
    @Shadow public int lifespan;

    @Inject(method = {
            "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V",
            "<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V",
            "<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;DDD)V",
            "<init>(Lnet/minecraft/world/entity/item/ItemEntity;)V"
    }, at = @At("RETURN"))
    private void mite$extendOrdinaryLifetime(CallbackInfo ci) {
        if (this.lifespan == mite$ORIGINAL_LIFETIME) this.lifespan = mite$DROPPED_ITEM_LIFETIME;
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void mite$migrateOrdinaryLifetime(CompoundTag tag, CallbackInfo ci) {
        // Preserve elapsed age and any explicitly customized lifespan from the save.
        if (this.lifespan == mite$ORIGINAL_LIFETIME) this.lifespan = mite$DROPPED_ITEM_LIFETIME;
    }

    @ModifyConstant(method = "isMergable", constant = @Constant(intValue = 6000))
    private int mite$mergeUntilExpiry(int original) {
        return this.lifespan;
    }

    @Inject(method = "makeFakeItem", at = @At("RETURN"))
    private void mite$keepFakeItemsShortLived(CallbackInfo ci) {
        this.age = this.lifespan - 1;
    }
}

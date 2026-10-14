package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.item.R196CoinItem;
import io.github.ningmeng123.mite.item.R196DisenchantingBottleItem;
import io.github.ningmeng123.mite.item.R196StoneBucketItem;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Material-list OR means even an adamantium bucket containing stone is lava vulnerable. @author NingMeng123 */
@Mixin(ItemStack.class)
public abstract class UtilityItemDamageMixin {
    @Inject(method = "canBeHurtBy", at = @At("HEAD"), cancellable = true)
    private void mite$sourceUtilityHazards(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = (ItemStack)(Object)this;
        if (stack.isEmpty()) return;
        boolean lava = source.is(DamageTypes.LAVA);
        if (stack.getItem() instanceof R196CoinItem coin) {
            if (lava) cir.setReturnValue(!coin.material().equals("adamantium"));
            else if (source.is(DamageTypeTags.IS_FIRE)) cir.setReturnValue(false);
        } else if (stack.getItem() instanceof R196StoneBucketItem || stack.getItem() instanceof R196DisenchantingBottleItem) {
            if (lava) cir.setReturnValue(true);
            else if (source.is(DamageTypeTags.IS_FIRE)) cir.setReturnValue(false);
        } else if (stack.getItem() instanceof MiteBucketItem bucket
                && (bucket.contents() == MiteBucketItem.Contents.EMPTY || bucket.contents() == MiteBucketItem.Contents.LAVA)) {
            // The stone-destruction remainder must retain the empty vessel's source immunity.
            if (lava) cir.setReturnValue(!bucket.metal().equals("adamantium"));
            else if (source.is(DamageTypeTags.IS_FIRE)) cir.setReturnValue(false);
        } else if (stack.is(Items.BUCKET) || stack.is(Items.LAVA_BUCKET)) {
            if (lava) cir.setReturnValue(true);
            else if (source.is(DamageTypeTags.IS_FIRE)) cir.setReturnValue(false);
        }
    }
}

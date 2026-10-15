package io.github.ningmeng123.mite.mixin.client;

import net.minecraft.client.model.SkeletonModel;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep native aiming, handedness and arm animation for every BowItem. @author NingMeng123 */
@Mixin(SkeletonModel.class)
public abstract class SkeletonBowModelMixin {
    @Redirect(method = {
            "prepareMobModel(Lnet/minecraft/world/entity/Mob;FFF)V",
            "setupAnim(Lnet/minecraft/world/entity/Mob;FFFFF)V"
    }, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean mite$materialBowPose(ItemStack stack, Item item) {
        return item == Items.BOW ? stack.getItem() instanceof BowItem : stack.is(item);
    }
}

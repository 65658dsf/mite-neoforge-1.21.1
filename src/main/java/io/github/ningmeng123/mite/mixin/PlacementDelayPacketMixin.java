package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import io.github.ningmeng123.mite.world.PlacementDelay;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;

/** Reject hungry bucket use before its contents, creature or experience can be consumed. @author NingMeng123 */
@Mixin(ItemStack.class)
public abstract class PlacementDelayPacketMixin {
    @WrapMethod(method = "use")
    private InteractionResultHolder<ItemStack> mite$limitBucketPlacement(Level level, Player player, InteractionHand hand,
            Operation<InteractionResultHolder<ItemStack>> original) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.getItem() instanceof BucketItem bucket && bucket.content != Fluids.EMPTY && PlacementDelay.placementBlocked(player))
            return InteractionResultHolder.fail(stack);
        return original.call(level, player, hand);
    }
}

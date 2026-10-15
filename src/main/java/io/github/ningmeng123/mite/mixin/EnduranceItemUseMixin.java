package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.enchantment.EnduranceActions;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.mining.MiningProgress;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Charge only after NeoForge has accepted the placement snapshots and cancellation events. @author NingMeng123 */
@Mixin(ItemStack.class)
public abstract class EnduranceItemUseMixin {
    @WrapOperation(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;onPlaceItemIntoWorld(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult mite$sourceAcceptedItemUse(UseOnContext context, Operation<InteractionResult> original) {
        Player player = context.getPlayer();
        Item used = context.getItemInHand().getItem();
        ToolFamily family = R196EnchantmentCatalog.family(context.getItemInHand());
        BlockPos clicked = context.getClickedPos();
        BlockState before = context.getLevel().getBlockState(clicked);
        BlockPos placed = used instanceof BlockItem ? new BlockPlaceContext(context).getClickedPos() : clicked;
        InteractionResult result = original.call(context);
        if (player != null && result.consumesAction()) {
            if (used instanceof BlockItem) EnduranceActions.placing(player, context.getLevel().getBlockState(placed), placed);
            else if ((family == ToolFamily.HOE || family == ToolFamily.MATTOCK)
                    && !context.getLevel().getBlockState(clicked).equals(before))
                EnduranceActions.tilling(player, MiningProgress.effectiveHardness(before, before.getDestroySpeed(context.getLevel(), clicked)));
        }
        return result;
    }
}

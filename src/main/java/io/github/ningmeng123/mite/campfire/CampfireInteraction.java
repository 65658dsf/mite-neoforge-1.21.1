package io.github.ningmeng123.mite.campfire;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Explicit fuel payments keep native ignition and block placement from bypassing the campfire reserve. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class CampfireInteraction {
    private CampfireInteraction() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getUseItem().isFalse() || event.getUseBlock().isFalse()) return;
        InteractionResult result = useOn(new UseOnContext(event.getEntity(), event.getHand(), event.getHitVec()));
        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    public static InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CampfireBlock)) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        boolean firestarter = stack.is(Items.FLINT_AND_STEEL);
        boolean torch = stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH);
        boolean wood = CampfireController.isWoodFuel(stack);
        if (!firestarter && !torch && !wood && !stack.is(Items.FIRE_CHARGE)) return InteractionResult.PASS;

        Player player = context.getPlayer();
        if (player == null || player.isSpectator() || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, context.getClickedFace(), stack)
                || player.getCooldowns().isOnCooldown(stack.getItem())) return InteractionResult.FAIL;
        // Fire charges and burning projectiles do not satisfy the deliberate relighting rule.
        if (stack.is(Items.FIRE_CHARGE)) return InteractionResult.FAIL;
        boolean ignite = firestarter || torch;
        if (ignite && (state.getValue(CampfireBlock.WATERLOGGED) || level.isRainingAt(pos.above())))
            return InteractionResult.FAIL;
        if (!(level.getBlockEntity(pos) instanceof CampfireBlockEntity campfire)) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        boolean wasLit = state.getValue(CampfireBlock.LIT);
        int added = CampfireController.refuel(campfire, firestarter ? CampfireRules.MAX_FUEL : 1, ignite);
        boolean nowLit = level.getBlockState(pos).getValue(CampfireBlock.LIT);
        if (added == 0 && (wasLit || !nowLit)) return InteractionResult.CONSUME;
        if (firestarter) stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
        else stack.consume(1, player);
        return InteractionResult.CONSUME;
    }
}

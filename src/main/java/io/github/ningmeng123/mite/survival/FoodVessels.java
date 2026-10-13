package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.MiteBowlItem;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import io.github.ningmeng123.mite.registry.MiteFoods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;

/** R196 bowl pickup, fire dousing and one-cell/three-cell farmland watering. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class FoodVessels {
    private FoodVessels() {}

    public static ItemStack empty(ItemStack stack) {
        if (stack.getItem() instanceof MiteBowlItem || stack.is(Items.MUSHROOM_STEW)) return new ItemStack(Items.BOWL);
        if (stack.getItem() instanceof MiteBucketItem bucket) return bucket.emptyStack();
        if (stack.is(Items.WATER_BUCKET) || stack.is(Items.MILK_BUCKET) || stack.is(Items.LAVA_BUCKET)) return new ItemStack(Items.BUCKET);
        return ItemStack.EMPTY;
    }

    public static boolean water(ItemStack stack) {
        return stack.is(MiteFoods.WATER_BOWL.get()) || stack.is(Items.WATER_BUCKET)
                || stack.getItem() instanceof MiteBucketItem bucket && bucket.contents() == MiteBucketItem.Contents.WATER;
    }

    public static boolean canDouse(ItemStack stack) {
        if (water(stack) || stack.is(Items.MILK_BUCKET) || stack.is(MiteFoods.ICE_CREAM.get())) return true;
        if (stack.getItem() instanceof MiteBowlItem bowl)
            return bowl.contents() == MiteBowlItem.Contents.MILK || bowl.contents() == MiteBowlItem.Contents.SOUP;
        return stack.getItem() instanceof MiteBucketItem bucket && bucket.contents() == MiteBucketItem.Contents.MILK;
    }

    public static InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (player.isSpectator() || player.getCooldowns().isOnCooldown(context.getItemInHand().getItem()))
            return InteractionResult.PASS;
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        BlockPos neighbor = pos.relative(context.getClickedFace());
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(neighbor, context.getClickedFace(), stack))
            return InteractionResult.FAIL;
        if (canDouse(stack) && (level.getBlockState(neighbor).is(Blocks.FIRE) || level.getBlockState(pos).is(Blocks.FIRE))) {
            BlockPos fire = level.getBlockState(neighbor).is(Blocks.FIRE) ? neighbor : pos;
            if (!level.isClientSide) {
                level.removeBlock(fire, false);
                level.playSound(null, fire, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1F, 1F);
                convertHeld(player, context.getHand(), empty(stack));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!water(stack)) return InteractionResult.PASS;
        BlockPos soil = level.getBlockState(pos).is(Blocks.FARMLAND) ? pos : pos.below();
        if (!level.getBlockState(soil).is(Blocks.FARMLAND)) return InteractionResult.PASS;
        if (soil.equals(pos) && context.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        int radius = stack.is(MiteFoods.WATER_BOWL.get()) ? 0 : 1;
        // wr.onItemRightClick stops here when farmland is already fully wet. Do not
        // fall through into modern bucket placement and flood the crop above it.
        if (!waterFarmland(level, soil, radius)) return InteractionResult.FAIL;
        if (!level.isClientSide) convertHeld(player, context.getHand(), empty(stack));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static boolean waterFarmland(Level level, BlockPos center, int radius) {
        if (level.dimensionType().ultraWarm()) {
            if (!level.isClientSide) level.playSound(null, center, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1F, 1F);
            return true;
        }
        boolean changed = false;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            BlockPos pos = center.offset(dx, 0, dz);
            var state = level.getBlockState(pos);
            if (state.is(Blocks.FARMLAND) && state.getValue(FarmBlock.MOISTURE) < 7) {
                if (!level.isClientSide) level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, 7), 3);
                changed = true;
            }
        }
        return changed;
    }

    public static void convertHeld(Player player, net.minecraft.world.InteractionHand hand, ItemStack result) {
        if (result.isEmpty() || player.hasInfiniteMaterials()) return;
        // Conversion is not FOOD completion: nutrition/effects are deliberately not applied.
        player.setItemInHand(hand, ItemUtils.createFilledResult(player.getItemInHand(hand), player, result));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getUseItem().isFalse()) return;
        ItemStack held = event.getItemStack();
        if (!canDouse(held)) return;
        var context = new UseOnContext(event.getEntity(), event.getHand(), event.getHitVec());
        InteractionResult result = useOn(context);
        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fillBowl(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!event.getItemStack().is(Items.BOWL)) return;
        if (player.isSpectator() || player.getCooldowns().isOnCooldown(event.getItemStack().getItem())) return;
        Level level = event.getLevel();
        var eye = player.getEyePosition();
        var end = eye.add(player.getViewVector(1F).scale(player.blockInteractionRange()));
        var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)
                || !level.mayInteract(player, hit.getBlockPos())
                || !player.mayUseItemAt(hit.getBlockPos(), hit.getDirection(), event.getItemStack())) return;
        if (!level.isClientSide) {
            convertHeld(player, event.getHand(), new ItemStack(MiteFoods.WATER_BOWL.get()));
            level.gameEvent(player, GameEvent.FLUID_PICKUP, hit.getBlockPos());
            level.playSound(null, hit.getBlockPos(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1F, 1F);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }

    @SubscribeEvent public static void fuel(FurnaceFuelBurnTimeEvent event) {
        if (event.getItemStack().is(Items.BOWL)) event.setBurnTime(200);
        else if (event.getItemStack().getItem() instanceof MiteBowlItem) event.setBurnTime(0);
    }
}

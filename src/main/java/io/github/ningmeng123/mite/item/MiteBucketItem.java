package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import io.github.ningmeng123.mite.survival.FoodVessels;
import io.github.ningmeng123.mite.survival.LavaBucketFilling;
import io.github.ningmeng123.mite.survival.WaterBucketFilling;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;

/** Modern fluid placement with source-matched metal peers, never iron-bucket replacement. @author NingMeng123 */
public final class MiteBucketItem extends BucketItem {
    public enum Contents { EMPTY, WATER, MILK, LAVA }
    private final String metal;
    private final Contents contents;

    public MiteBucketItem(Properties properties, String metal, Contents contents) {
        super(contents == Contents.WATER ? Fluids.WATER : contents == Contents.LAVA ? Fluids.LAVA : Fluids.EMPTY, properties);
        this.metal = metal;
        this.contents = contents;
    }

    public String metal() { return metal; }
    public Contents contents() { return contents; }
    public ItemStack emptyStack() { return new ItemStack(MiteFoods.get(metal + "_bucket")); }
    @Override public int getBurnTime(ItemStack stack, RecipeType<?> recipeType) {
        return contents == Contents.LAVA ? 3200 : 0;
    }

    @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return contents != Contents.EMPTY; }
    @Override public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return contents == Contents.EMPTY ? ItemStack.EMPTY : emptyStack();
    }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return contents == Contents.MILK ? UseAnim.DRINK : UseAnim.NONE; }

    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (contents != Contents.MILK) return stack;
        if (entity instanceof Player player && !FoodConsumption.canIngest(player, stack)) return stack;
        if (!level.isClientSide) entity.removeAllEffects();
        return super.finishUsingItem(stack, level, entity);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (contents == Contents.MILK) {
            if (!FoodConsumption.canIngest(player, held)) return InteractionResultHolder.fail(held);
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(held);
        }
        var hit = getPlayerPOVHitResult(level, player, contents == Contents.EMPTY ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE);
        if (contents == Contents.EMPTY) hit = WaterBucketFilling.includeFlowingWater(level, player, hit);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(held);
        BlockPos pos = hit.getBlockPos();
        BlockPos neighbor = pos.relative(hit.getDirection());
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(neighbor, hit.getDirection(), held))
            return InteractionResultHolder.fail(held);
        if (contents == Contents.EMPTY) {
            var state = level.getBlockState(pos);
            boolean lava = state.getFluidState().is(FluidTags.LAVA);
            if ((!lava && !state.getFluidState().is(FluidTags.WATER)) || !(state.getBlock() instanceof BucketPickup pickup))
                return InteractionResultHolder.fail(held);
            ItemStack picked = WaterBucketFilling.pickup(level, pos, state, () -> pickup.pickupBlock(player, level, pos, state));
            if (picked.isEmpty()) return InteractionResultHolder.fail(held);
            ItemStack filled = new ItemStack(MiteFoods.get(metal + (lava ? "_lava_bucket" : "_water_bucket")));
            pickup.getPickupSound(state).ifPresent(sound -> player.playSound(sound, 1F, 1F));
            level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
            player.awardStat(Stats.ITEM_USED.get(this));
            if (player instanceof ServerPlayer server) CriteriaTriggers.FILLED_BUCKET.trigger(server, filled);
            return InteractionResultHolder.sidedSuccess(LavaBucketFilling.convert(held, player, filled), level.isClientSide);
        }
        BlockPos target = canBlockContainFluid(player, level, pos, level.getBlockState(pos)) ? pos : neighbor;
        if (!emptyContents(player, level, target, hit, held)) return InteractionResultHolder.fail(held);
        checkExtraContent(player, level, held, target);
        if (player instanceof ServerPlayer server) CriteriaTriggers.PLACED_BLOCK.trigger(server, target, held);
        player.awardStat(Stats.ITEM_USED.get(this));
        if (player.hasInfiniteMaterials()) return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
        return InteractionResultHolder.sidedSuccess(ItemUtils.createFilledResult(held, player, emptyStack()), level.isClientSide);
    }
}

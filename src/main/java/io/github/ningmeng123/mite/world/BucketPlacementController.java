package io.github.ningmeng123.mite.world;

import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import io.github.ningmeng123.mite.registry.MiteUtilityContent;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.mite.worldgen.MantleHeat;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidUtil;

/** R196 wr: ordinary buckets expire; an explicit source spends one hundred XP points. @author NingMeng123 */
public final class BucketPlacementController {
    public static final int SOURCE_COST = 100;
    public static final int WATER_DECAY_TICKS = 16;
    public static final int LAVA_DECAY_TICKS = 48;
    private record Intent(Player player, boolean sourceRequested) {}
    private static final ThreadLocal<Intent> INTENT = new ThreadLocal<>();
    private BucketPlacementController() {}

    public static <T> T withIntent(Player player, boolean sourceRequested, Supplier<T> action) {
        Intent previous = INTENT.get();
        INTENT.set(new Intent(player, sourceRequested));
        try { return action.get(); }
        finally {
            if (previous == null) INTENT.remove();
            else INTENT.set(previous);
        }
    }

    public static boolean supports(BucketItem bucket) {
        return bucket == Items.WATER_BUCKET || bucket == Items.LAVA_BUCKET
                || bucket instanceof MobBucketItem && bucket.content == Fluids.WATER
                || bucket instanceof MiteBucketItem metal && (metal.contents() == MiteBucketItem.Contents.WATER
                || metal.contents() == MiteBucketItem.Contents.LAVA);
    }

    private static boolean sourceRequested(Player player) {
        Intent intent = INTENT.get();
        return player != null && (player.hasInfiniteMaterials()
                || intent != null && intent.player() == player && intent.sourceRequested() && player.totalExperience >= SOURCE_COST);
    }

    public static boolean empty(BucketItem bucket, Player player, Level level, BlockPos pos,
            BlockHitResult hit, ItemStack container, Supplier<Boolean> nativePlacement) {
        Fluid fluid = bucket.content;
        BlockState before = level.getBlockState(pos);
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || player != null && !level.mayInteract(player, pos)) return false;
        boolean source = sourceRequested(player);
        boolean replaceable = before.canBeReplaced(fluid);
        boolean contained = before.getBlock() instanceof LiquidBlockContainer holder
                && holder.canPlaceLiquid(player, level, pos, before, fluid);
        if (!before.isAir() && !replaceable && !(contained && source) && !(before.getBlock() instanceof LiquidBlock)) {
            return hit != null && bucket.emptyContents(player, level,
                    hit.getBlockPos().relative(hit.getDirection()), null, container);
        }
        // Keep NeoForge fluid-type evaporation hooks, Nether smoke and the existing mantle rule.
        if (fluid == Fluids.WATER && (level.dimensionType().ultraWarm() || MantleHeat.evaporatesWater(level, pos))
                || container != null && FluidUtil.getFluidContained(container)
                .filter(stack -> fluid.getFluidType().isVaporizedOnPlacement(level, pos, stack)).isPresent())
            return nativePlacement.get();

        var previousFluid = before.getFluidState();
        boolean sameLiquid = previousFluid.getType().isSame(fluid);
        var pending = level instanceof net.minecraft.server.level.ServerLevel server
                ? TemporaryFluidChanges.get(server).at(pos) : null;
        boolean temporary = pending != null && pending.water() == (fluid == Fluids.WATER);
        // Existing sources are not downgraded; repeated ordinary pours do not refresh their deadline.
        if (sameLiquid && (!source || previousFluid.isSource() && !temporary)) {
            playEmpty(bucket, player, level, pos);
            return true;
        }
        if (fluid == Fluids.WATER && previousFluid.is(FluidTags.LAVA)
                || fluid == Fluids.LAVA && previousFluid.is(FluidTags.WATER)) {
            if (!level.isClientSide) {
                BlockState stone = fluid == Fluids.WATER && previousFluid.isSource()
                        ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                if (!level.setBlock(pos, stone, 3)) return false;
                TemporaryFluidChanges.get((net.minecraft.server.level.ServerLevel) level).cancel(pos);
                level.levelEvent(1501, pos, 0);
            }
            playEmpty(bucket, player, level, pos);
            return true;
        }

        CraftingQuality.Payment payment = null;
        ServerPlayer payer = player instanceof ServerPlayer server ? server : null;
        if (source && player != null && !player.hasInfiniteMaterials() && !level.isClientSide) {
            if (payer == null || (payment = CraftingQuality.approvePayment(payer, SOURCE_COST)) == null
                    || !CraftingQuality.pay(payer, payment)) return false;
        }
        boolean placed = false;
        try {
            if (contained) {
                placed = ((LiquidBlockContainer) before.getBlock()).placeLiquid(level, pos, before, fluid.defaultFluidState());
            } else {
                if (!level.isClientSide && replaceable && !before.liquid()) level.destroyBlock(pos, true);
                placed = level.setBlock(pos, fluid.defaultFluidState().createLegacyBlock(), 11) || previousFluid.isSource();
            }
            if (!placed) return false;
            var actual = level.getFluidState(pos);
            boolean actualSource = actual.isSource() && actual.getType().isSame(fluid);
            if (!level.isClientSide) {
                var changes = TemporaryFluidChanges.get((net.minecraft.server.level.ServerLevel) level);
                if (actualSource) {
                    if (source) changes.cancel(pos);
                    else changes.schedule(pos, fluid == Fluids.WATER, level.getGameTime()
                            + (fluid == Fluids.WATER ? WATER_DECAY_TICKS : LAVA_DECAY_TICKS));
                }
            }
            // Nearby water can harden lava during placement; that did not create a paid liquid source.
            if (payment != null && !actualSource) {
                refund(payer, payment);
                payment = null;
            }
            if (payment != null) playLevelDrain(payer);
            playEmpty(bucket, player, level, pos);
            return true;
        } finally {
            if (!placed && payment != null) refund(payer, payment);
        }
    }

    private static void refund(ServerPlayer player, CraftingQuality.Payment payment) {
        PlayerExperience.setTotal(player, payment.before());
        player.increaseScore(payment.cost());
    }

    private static void playLevelDrain(ServerPlayer player) {
        var level = player.serverLevel();
        // R196 abw.playSoundAtEntity randomizes volume and pitch independently.
        float volume = .9F + (level.random.nextFloat() + level.random.nextFloat()) / 10F;
        float pitch = .9F + (level.random.nextFloat() + level.random.nextFloat()) / 10F;
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                MiteUtilityContent.LEVEL_DRAIN.get(), SoundSource.PLAYERS, volume, pitch);
    }

    private static void playEmpty(BucketItem bucket, Player player, Level level, BlockPos pos) {
        SoundEvent sound = bucket.content.getFluidType().getSound(player, level, pos, SoundActions.BUCKET_EMPTY);
        if (sound == null) sound = bucket.content == Fluids.LAVA ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
        level.playSound(player, pos, sound, SoundSource.BLOCKS, 1F, 1F);
        level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
    }
}

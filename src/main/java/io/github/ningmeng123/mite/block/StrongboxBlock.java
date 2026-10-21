package io.github.ningmeng123.mite.block;

import io.github.ningmeng123.mite.block.entity.StrongboxBlockEntity;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** R196 metal chests are owner-bound single 27-slot strongboxes. @author NingMeng123 */
public final class StrongboxBlock extends ChestBlock {
    private final String material;
    public StrongboxBlock(String material, BlockBehaviour.Properties properties) {
        super(properties, () -> MiteFunctionalBlocks.STRONGBOX_ENTITY.get());
        this.material = material;
    }
    public String material() { return material; }
    @Override public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof StrongboxBlockEntity entity && entity.isOwner(player))
            return net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new net.neoforged.neoforge.event.entity.player.PlayerEvent.HarvestCheck(player, state, level, pos, true)).canHarvest();
        return super.canHarvestBlock(state, level, pos, player);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(TYPE, ChestType.SINGLE)
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return state.setValue(TYPE, ChestType.SINGLE);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof StrongboxBlockEntity entity)
            entity.setOwner(player);
    }
    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
            boolean willHarvest, FluidState fluid) {
        var entity = level.getBlockEntity(pos);
        String ownerName = player != null && entity instanceof StrongboxBlockEntity strongbox
                && strongbox.hasOwner() && !strongbox.isOwner(player) ? strongbox.ownerName() : null;
        boolean removed = super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
        if (removed && ownerName != null && level instanceof ServerLevel serverLevel)
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
                    "mite.strongbox.destroyed_by_non_owner", player.getDisplayName(), ownerName, getName()), false);
        return removed;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StrongboxBlockEntity(pos, state); }
    @Override public DoubleBlockCombiner.NeighborCombineResult<? extends ChestBlockEntity> combine(
            BlockState state, Level level, BlockPos pos, boolean ignoreBlocked) {
        if (!ignoreBlocked && ChestBlock.isChestBlockedAt(level, pos))
            return DoubleBlockCombiner.Combiner::acceptNone;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof StrongboxBlockEntity strongbox) {
            return new DoubleBlockCombiner.NeighborCombineResult<ChestBlockEntity>() {
                @Override public <T> T apply(DoubleBlockCombiner.Combiner<? super ChestBlockEntity, T> combiner) {
                    return combiner.acceptSingle(strongbox);
                }
            };
        }
        return DoubleBlockCombiner.Combiner::acceptNone;
    }
    @Override protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return super.getMenuProvider(state, level, pos);
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockEntity entity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        var responsible = builder.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (!(entity instanceof StrongboxBlockEntity strongbox) || !(responsible instanceof Player player)
                || player.isCreative() || !strongbox.isOwner(player)) return List.of();
        return super.getDrops(state, builder);
    }
}

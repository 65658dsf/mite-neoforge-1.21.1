package io.github.ningmeng123.mite.portal;

import io.github.ningmeng123.mite.block.RunestoneBlock;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import io.github.ningmeng123.mite.rules.R196PortalRules;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.ModernUnderworldTerrain;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Exact 2x3 interior, ten required obsidian edges; corner blocks may be runestones. @author NingMeng123 */
public record MitePortalFrame(BlockPos bottomLeft, Direction.Axis axis) {
    public Direction along() { return axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH; }
    public BlockPos at(int horizontal, int vertical) { return bottomLeft.relative(along(), horizontal).above(vertical); }

    public static Optional<MitePortalFrame> find(LevelAccessor level, BlockPos inside) {
        BlockState state = level.getBlockState(inside);
        Direction.Axis preferred = state.getOptionalValue(NetherPortalBlock.AXIS).orElse(Direction.Axis.X);
        Optional<MitePortalFrame> first = find(level, inside, preferred);
        return first.isPresent() ? first : find(level, inside, preferred == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
    }

    public static Optional<MitePortalFrame> find(LevelAccessor level, BlockPos inside, Direction.Axis axis) {
        if (!axis.isHorizontal() || !empty(level.getBlockState(inside))) return Optional.empty();
        BlockPos bottom = inside;
        for (int i = 0; i < 2 && empty(level.getBlockState(bottom.below())); i++) bottom = bottom.below();
        Direction negative = axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH;
        if (empty(level.getBlockState(bottom.relative(negative)))) bottom = bottom.relative(negative);
        MitePortalFrame frame = new MitePortalFrame(bottom, axis);
        return frame.valid(level) ? Optional.of(frame) : Optional.empty();
    }

    public boolean valid(LevelAccessor level) {
        if (bottomLeft.getY() <= level.getMinBuildHeight() || bottomLeft.getY() + 3 >= level.getMaxBuildHeight()) return false;
        for (int x = -1; x <= 2; x++) {
            for (int y = -1; y <= 3; y++) {
                if ((x == -1 || x == 2) && (y == -1 || y == 3)) continue;
                BlockState state = level.getBlockState(at(x, y));
                boolean edge = x == -1 || x == 2 || y == -1 || y == 3;
                if (edge ? !state.is(Blocks.OBSIDIAN) : !empty(state)) return false;
            }
        }
        return true;
    }

    public boolean complete(LevelAccessor level) {
        if (!valid(level)) return false;
        for (int x = 0; x < 2; x++) for (int y = 0; y < 3; y++) {
            if (!level.getBlockState(at(x, y)).is(Blocks.NETHER_PORTAL)) return false;
        }
        return true;
    }

    public Optional<Rune> rune(LevelAccessor level) {
        BlockState lowerLeft = level.getBlockState(at(-1, -1));
        Block block = lowerLeft.getBlock();
        boolean adamantium = block == MiteWorldBlocks.ADAMANTIUM_RUNESTONE.get();
        if (!adamantium && block != MiteWorldBlocks.MITHRIL_RUNESTONE.get()) return Optional.empty();
        BlockState lowerRight = level.getBlockState(at(2, -1));
        BlockState upperLeft = level.getBlockState(at(-1, 3));
        BlockState upperRight = level.getBlockState(at(2, 3));
        if (lowerRight.getBlock() != block || upperLeft.getBlock() != block || upperRight.getBlock() != block) return Optional.empty();
        return Optional.of(new Rune(adamantium, R196PortalRules.runeSeed(lowerLeft.getValue(RunestoneBlock.RUNE),
                lowerRight.getValue(RunestoneBlock.RUNE), upperLeft.getValue(RunestoneBlock.RUNE), upperRight.getValue(RunestoneBlock.RUNE))));
    }

    public boolean touchingBottom(Level level) {
        int frameY = bottomLeft.getY() - 1;
        boolean underworld = level.dimension() == MiteWorldDimensions.UNDERWORLD;
        int foundationY = frameY - 1;
        boolean atPortalMantle = underworld && foundationY >= ModernUnderworldTerrain.MIN_PORTAL_MANTLE_Y
                && foundationY <= ModernUnderworldTerrain.MAX_PORTAL_MANTLE_Y;
        if (!atPortalMantle && !R196PortalRules.canTouchBottom(frameY, level.getMinBuildHeight())) return false;
        Block bottomType = underworld ? MiteWorldBlocks.MANTLE.get()
                : level.dimension() == Level.NETHER ? MiteWorldBlocks.CORE.get() : Blocks.BEDROCK;
        for (int x = -1; x <= 2; x++) if (level.getBlockState(at(x, -2)).is(bottomType)) return true;
        return false;
    }

    public static boolean empty(BlockState state) {
        return state.isAir() || state.is(BlockTags.FIRE) || state.is(Blocks.NETHER_PORTAL);
    }

    public record Rune(boolean adamantium, int seed) {}
}

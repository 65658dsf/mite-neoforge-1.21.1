package io.github.ningmeng123.mite.portal;

import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import io.github.ningmeng123.mite.rules.R196PortalRules;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import java.util.Comparator;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative ordinary gates and deterministic four-corner runegates. @author NingMeng123 */
public final class MitePortals {
    private MitePortals() {}

    public static int dimensionId(Level level) {
        if (level.dimension() == Level.OVERWORLD) return R196PortalRules.OVERWORLD;
        if (level.dimension() == MiteWorldDimensions.UNDERWORLD) return R196PortalRules.UNDERWORLD;
        if (level.dimension() == Level.NETHER) return R196PortalRules.NETHER;
        return -1;
    }

    private static ResourceKey<Level> dimensionKey(int id) {
        return id == R196PortalRules.UNDERWORLD ? MiteWorldDimensions.UNDERWORLD
                : id == R196PortalRules.NETHER ? Level.NETHER : Level.OVERWORLD;
    }

    public static void initializeRoute(Level level, MitePortalFrame frame) {
        int source = dimensionId(level);
        if (source < 0) return;
        boolean rune = frame.rune(level).isPresent();
        writeRoute(level, frame, rune ? source : R196PortalRules.ordinaryDestination(source, frame.touchingBottom(level)), rune);
    }

    public static void writeRoute(Level level, MitePortalFrame frame, int destination, boolean rune) {
        for (int x = 0; x < 2; x++) for (int y = 0; y < 3; y++) {
            BlockPos pos = frame.at(x, y);
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.NETHER_PORTAL)) level.setBlock(pos,
                    state.setValue(MitePortalProperties.DESTINATION, destination)
                            .setValue(MitePortalProperties.RUNEGATE, rune), 2);
        }
    }

    private static int route(Level level, MitePortalFrame frame) {
        BlockState state = level.getBlockState(frame.bottomLeft());
        if (state.getValue(MitePortalProperties.RUNEGATE) && frame.rune(level).isEmpty()) {
            return R196PortalRules.ordinaryDestination(dimensionId(level), frame.touchingBottom(level));
        }
        // Old worlds have the vanilla default state: Nether portals now route to the underworld.
        if (level.dimension() == Level.NETHER && !state.getValue(MitePortalProperties.RUNEGATE)) return R196PortalRules.UNDERWORLD;
        return state.getValue(MitePortalProperties.DESTINATION);
    }

    public static boolean isInstantGate(Level level, BlockPos pos) {
        return MitePortalFrame.find(level, pos).filter(frame -> frame.complete(level))
                .map(frame -> frame.rune(level).isPresent()
                        || level.dimension() == Level.OVERWORLD && route(level, frame) == R196PortalRules.OVERWORLD)
                .orElse(false);
    }

    public static boolean canEnter(Level level, BlockPos pos, Entity entity) {
        if (dimensionId(level) < 0 || !entity.isAlive() || entity.isPassenger() || entity.isVehicle()) return false;
        return !isInstantGate(level, pos) || entity instanceof Player;
    }

    @Nullable
    public static DimensionTransition destination(ServerLevel source, Entity entity, BlockPos entry) {
        if (!canEnter(source, entry, entity)) return null;
        Optional<MitePortalFrame> sourceFrame = MitePortalFrame.find(source, entry).filter(frame -> frame.complete(source));
        if (sourceFrame.isEmpty()) return null;
        MitePortalFrame frame = sourceFrame.get();
        Optional<MitePortalFrame.Rune> rune = frame.rune(source);
        BlockState state = source.getBlockState(frame.bottomLeft());
        if (rune.isPresent()) {
            if (!(entity instanceof ServerPlayer)) return null;
            if (!state.getValue(MitePortalProperties.RUNEGATE)) writeRoute(source, frame, dimensionId(source), true);
            var destination=runeDestination(source, entity, rune.get());
            if(destination==null)return null;
            return new DimensionTransition(destination.newLevel(),destination.pos(),destination.speed(),destination.yRot(),destination.xRot(),
                    destination.postDimensionTransition().then(traveler->io.github.ningmeng123.mite.advancement.MiteAchievements.award((Player)traveler,"runegate")));
        }
        if (state.getValue(MitePortalProperties.RUNEGATE)) initializeRoute(source, frame);
        int targetId = route(source, frame);
        if (dimensionId(source) == R196PortalRules.OVERWORLD && targetId == R196PortalRules.OVERWORLD) {
            if (!(entity instanceof ServerPlayer)) return null;
            Optional<Vec3> position = safeLanding(source, entity, source.getSharedSpawnPos().getX(), source.getSharedSpawnPos().getZ(), true, 8);
            return position.map(pos -> transition(source, entity, pos, Vec3.ZERO, entity.getYRot())).orElse(null);
        }
        ServerLevel target = source.getServer().getLevel(dimensionKey(targetId));
        if (target == null) return null;
        double scale = R196PortalRules.coordinateScale(dimensionId(source), targetId);
        BlockPos around = target.getWorldBorder().clampToBounds(entity.getX() * scale, entity.getY(), entity.getZ() * scale);
        Optional<MitePortalFrame> exit = findExit(target, around, dimensionId(source));
        if (exit.isEmpty()) exit = createExit(target, around, frame.axis(), dimensionId(source), entity);
        if (exit.isEmpty()) return null;
        return portalTransition(source, target, frame, exit.get(), entity);
    }

    public static Optional<MitePortalFrame> findExit(ServerLevel target, BlockPos around, int returnDimension) {
        PoiManager poi = target.getPoiManager();
        poi.ensureLoadedAndValid(target, around, R196PortalRules.SEARCH_RADIUS);
        return poi.getInSquare(type -> type.is(PoiTypes.NETHER_PORTAL), around, R196PortalRules.SEARCH_RADIUS, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos).filter(target.getWorldBorder()::isWithinBounds)
                .map(pos -> MitePortalFrame.find(target, pos)).flatMap(Optional::stream)
                .filter(frame -> frame.complete(target) && frame.rune(target).isEmpty() && route(target, frame) == returnDimension)
                .distinct().min(Comparator.<MitePortalFrame>comparingDouble(frame -> frame.bottomLeft().distSqr(around))
                        .thenComparingInt(frame -> frame.bottomLeft().getY()));
    }

    @Nullable
    private static DimensionTransition portalTransition(ServerLevel source, ServerLevel target,
            MitePortalFrame entry, MitePortalFrame exit, Entity entity) {
        var dimensions = entity.getDimensions(entity.getPose());
        if (dimensions.width() > 2 || dimensions.height() > 3) return null;
        double sourceHorizontal = entry.axis() == Direction.Axis.X ? entity.getX() - entry.bottomLeft().getX()
                : entity.getZ() - entry.bottomLeft().getZ();
        double half = dimensions.width() / 2.0;
        double horizontal = Mth.clamp(sourceHorizontal, half, 2.0 - half);
        double vertical = Mth.clamp(entity.getY() - entry.bottomLeft().getY(), 0.0, 3.0 - dimensions.height());
        BlockPos base = exit.bottomLeft();
        Vec3 position = new Vec3(base.getX() + (exit.axis() == Direction.Axis.X ? horizontal : 0.5),
                base.getY() + vertical, base.getZ() + (exit.axis() == Direction.Axis.Z ? horizontal : 0.5));
        position = PortalShape.findCollisionFreePosition(position, target, entity, dimensions);
        if (!clearForEntity(target, entity, position)) return null;
        boolean rotated = entry.axis() != exit.axis();
        Vec3 speed = entity.getDeltaMovement();
        if (rotated) speed = new Vec3(speed.z, speed.y, -speed.x);
        return transition(target, entity, position, speed, entity.getYRot() + (rotated ? 90 : 0));
    }

    private static DimensionTransition transition(ServerLevel target, Entity entity, Vec3 position, Vec3 speed, float yaw) {
        return new DimensionTransition(target, position, speed, yaw, entity.getXRot(),
                DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET).then(traveler -> {
                    traveler.fallDistance = 0F;
                    traveler.setDeltaMovement(speed);
                }));
    }

    @Nullable
    private static DimensionTransition runeDestination(ServerLevel level, Entity entity, MitePortalFrame.Rune rune) {
        int radius = R196PortalRules.runeRadius(rune.adamantium(), level.getWorldBorder().getSize() / 2.0);
        R196PortalRules.Coordinates coordinates = R196PortalRules.runeCoordinates(rune.seed(), rune.adamantium(), radius,
                (x, z) -> level.getWorldBorder().isWithinBounds(new BlockPos(x, level.getMinBuildHeight() + 64, z))
                        && level.getBiome(new BlockPos(x, level.getMinBuildHeight() + 64, z)).is(BiomeTags.IS_OCEAN));
        if (!level.getWorldBorder().isWithinBounds(new BlockPos(coordinates.x(), 0, coordinates.z()))) return null;
        return safeLanding(level, entity, coordinates.x(), coordinates.z(), level.dimension() != Level.NETHER, 8)
                .map(pos -> transition(level, entity, pos, Vec3.ZERO, entity.getYRot())).orElse(null);
    }

    /** No unconditional Y64 clearing: a blocked runegate fails atomically without modifying terrain. */
    public static Optional<Vec3> safeLanding(ServerLevel level, Entity entity, int x, int z, boolean fromTop, int radius) {
        int min = level.getMinBuildHeight() + 1;
        int max = Math.min(level.getMaxBuildHeight(), level.getMinBuildHeight() + level.getLogicalHeight()) - 2;
        BlockPos origin = new BlockPos(x, min, z);
        for (BlockPos.MutableBlockPos column : BlockPos.spiralAround(origin, radius, Direction.EAST, Direction.SOUTH)) {
            if (!level.getWorldBorder().isWithinBounds(column)) continue;
            level.getChunk(column.getX() >> 4, column.getZ() >> 4);
            for (int index = 0; index <= max - min; index++) {
                int y = fromTop ? max - index : min + index;
                BlockPos feet = new BlockPos(column.getX(), y, column.getZ());
                BlockPos below = feet.below();
                BlockState support = level.getBlockState(below);
                if (!support.isFaceSturdy(level, below, Direction.UP) || hazardous(support)
                        || support.is(Blocks.BEDROCK) || support.is(MiteWorldBlocks.MANTLE.get()) || support.is(MiteWorldBlocks.CORE.get())) continue;
                Vec3 position = Vec3.atBottomCenterOf(feet);
                if (clearForEntity(level, entity, position)) return Optional.of(position);
            }
        }
        return Optional.empty();
    }

    private static boolean clearForEntity(ServerLevel level, Entity entity, Vec3 position) {
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(position);
        if (!level.getWorldBorder().isWithinBounds(box) || !level.noCollision(entity, box)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX - 1.0E-7, box.maxY - 1.0E-7, box.maxZ - 1.0E-7))) {
            BlockState state = level.getBlockState(pos);
            if (hazardous(state) || !state.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    private static boolean hazardous(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE);
    }

    /** R196 prefers the top while travelling down and the bottom while travelling up. */
    public static Optional<MitePortalFrame> createExit(ServerLevel level, BlockPos around, Direction.Axis axis,
            int returnDimension, Entity traveler) {
        if (traveler.getDimensions(traveler.getPose()).width() > 2 || traveler.getDimensions(traveler.getPose()).height() > 3) return Optional.empty();
        int min = level.getMinBuildHeight() + 2;
        int max = Math.min(level.getMaxBuildHeight(), level.getMinBuildHeight() + level.getLogicalHeight()) - 5;
        boolean fromTop = returnDimension != R196PortalRules.NETHER
                && !(returnDimension == R196PortalRules.UNDERWORLD && level.dimension() == Level.OVERWORLD);
        MitePortalFrame best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos.MutableBlockPos column : BlockPos.spiralAround(around, 16, Direction.EAST, Direction.SOUTH)) {
            for (int y = min; y <= max; y++) {
                MitePortalFrame candidate = new MitePortalFrame(new BlockPos(column.getX(), y, column.getZ()), axis);
                if (!naturalSpace(level, candidate)) continue;
                double dy = y - (fromTop ? max : min);
                double distance = Math.pow(column.getX() - around.getX(), 2) + Math.pow(column.getZ() - around.getZ(), 2) + dy * dy;
                if (distance < bestDistance) { best = candidate; bestDistance = distance; }
            }
        }
        if (best == null) {
            int fallbackY = level.dimension().equals(MiteWorldDimensions.UNDERWORLD)
                    ? io.github.ningmeng123.mite.worldgen.ModernUnderworldTerrain.fromLegacyY(70) : 70;
            int preferred = Mth.clamp(fromTop ? max : fallbackY, min, max);
            // Inspect every block before carving. Never delete bedrock, mantle/core, existing gates or inventories.
            for (BlockPos.MutableBlockPos column : BlockPos.spiralAround(around, 16, Direction.EAST, Direction.SOUTH)) {
                for (int delta = 0; delta <= max - min; delta++) {
                    int y = fromTop ? preferred - delta : preferred + delta;
                    if (y < min || y > max) continue;
                    MitePortalFrame candidate = new MitePortalFrame(new BlockPos(column.getX(), y, column.getZ()), axis);
                    if (canCarve(level, candidate)) { best = candidate; break; }
                }
                if (best != null) break;
            }
        }
        if (best == null) return Optional.empty();
        Direction side = best.along().getClockWise();
        for (int x = -1; x <= 2; x++) for (int sideOffset = -1; sideOffset <= 1; sideOffset++) {
            for (int y = -1; y <= 3; y++) {
                BlockPos pos = best.at(x, y).relative(side, sideOffset);
                boolean frame = sideOffset == 0 && (x == -1 || x == 2 || y == -1 || y == 3);
                level.setBlock(pos, y == -1 || frame ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
        }
        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis)
                .setValue(MitePortalProperties.DESTINATION, returnDimension).setValue(MitePortalProperties.RUNEGATE, false);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 3; y++) level.setBlock(best.at(x, y), portal, 18);
        return Optional.of(best);
    }

    private static boolean naturalSpace(ServerLevel level, MitePortalFrame frame) {
        Direction side = frame.along().getClockWise();
        for (int x = -1; x <= 2; x++) for (int offset = -1; offset <= 1; offset++) for (int y = -1; y <= 3; y++) {
            BlockPos pos = frame.at(x, y).relative(side, offset);
            if (!level.getWorldBorder().isWithinBounds(pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (y == -1) {
                if (!state.isFaceSturdy(level, pos, Direction.UP) || protectedBlock(level, pos, state) || hazardous(state)) return false;
            } else if (!state.canBeReplaced() || !state.getFluidState().isEmpty() || protectedBlock(level, pos, state)) return false;
        }
        return true;
    }

    private static boolean canCarve(ServerLevel level, MitePortalFrame frame) {
        Direction side = frame.along().getClockWise();
        for (int x = -1; x <= 2; x++) for (int offset = -1; offset <= 1; offset++) for (int y = -1; y <= 3; y++) {
            BlockPos pos = frame.at(x, y).relative(side, offset);
            if (!level.getWorldBorder().isWithinBounds(pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (protectedBlock(level, pos, state) || !state.getFluidState().isEmpty()) return false;
            if (!(state.canBeReplaced() || state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.NETHERRACK)
                    || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND))) return false;
        }
        return true;
    }

    private static boolean protectedBlock(ServerLevel level, BlockPos pos, BlockState state) {
        return state.is(Blocks.BEDROCK) || state.is(MiteWorldBlocks.MANTLE.get()) || state.is(MiteWorldBlocks.CORE.get())
                || state.is(Blocks.NETHER_PORTAL) || state.is(MiteWorldBlocks.MITHRIL_RUNESTONE.get())
                || state.is(MiteWorldBlocks.ADAMANTIUM_RUNESTONE.get()) || level.getBlockEntity(pos) != null;
    }
}

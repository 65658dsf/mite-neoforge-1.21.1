package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.survival.CropLifecycle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** Source village contents inside modern layouts, restricted to actual structure placement. @author NingMeng123 */
public final class VillageTemplateAdaptation {
    private static final Set<String> VILLAGES = Set.of("village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga");
    private static final Set<String> SMITH_LOOT = Set.of("minecraft:chests/village/village_armorer",
            "minecraft:chests/village/village_toolsmith", "minecraft:chests/village/village_weaponsmith");
    private static final Set<String> TEMPLATE_RESIDENTS = Set.of("minecraft:villager", "minecraft:zombie_villager",
            "minecraft:cat", "minecraft:iron_golem");
    private static final ThreadLocal<WorldGenLevel> GENERATING = new ThreadLocal<>();

    private VillageTemplateAdaptation() {}

    @FunctionalInterface public interface Scope extends AutoCloseable { @Override void close(); }

    public static Scope generation(WorldGenLevel level, Structure structure) {
        WorldGenLevel previous = GENERATING.get();
        ResourceLocation id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure);
        if (level.getLevel().dimension().equals(Level.OVERWORLD) && id != null
                && id.getNamespace().equals("minecraft") && VILLAGES.contains(id.getPath())) GENERATING.set(level);
        else GENERATING.remove();
        return () -> { if (previous == null) GENERATING.remove(); else GENERATING.set(previous); };
    }

    public static boolean active(LevelAccessor level) { return GENERATING.get() == level; }

    public static WorldGenLevel currentLevel() { return GENERATING.get(); }

    public static boolean smithLoot(String id) { return SMITH_LOOT.contains(id); }

    public static List<StructureTemplate.StructureEntityInfo> entities(LevelAccessor level,
            List<StructureTemplate.StructureEntityInfo> source) {
        if (!active(level)) return source;
        return source.stream().filter(info -> !TEMPLATE_RESIDENTS.contains(info.nbt.getString("id"))).toList();
    }

    public static List<StructureTemplate.StructureBlockInfo> blocks(ServerLevelAccessor level,
            List<StructureTemplate.StructureBlockInfo> source) {
        return blocks(level, BlockPos.ZERO, new StructurePlaceSettings(), source, source);
    }

    public static List<StructureTemplate.StructureBlockInfo> blocks(ServerLevelAccessor level, BlockPos origin,
            StructurePlaceSettings settings, List<StructureTemplate.StructureBlockInfo> originals,
            List<StructureTemplate.StructureBlockInfo> processed) {
        if (!active(level)) return processed;
        Map<Long, List<FieldCell>> crops = deadFields(originals, origin, settings, GENERATING.get().getSeed());
        List<StructureTemplate.StructureBlockInfo> result = new ArrayList<>(processed.size());
        for (var info : processed) {
            BlockState state = info.state();
            if (farmCell(state)) {
                List<FieldCell> column = crops.get(ChunkPos.asLong(info.pos().getX(), info.pos().getZ()));
                if (column != null) {
                    FieldCell cell = column.stream().filter(candidate -> candidate.origin().equals(info.pos())).findFirst().orElse(null);
                    // Gravity can move an original field cell vertically. Its unprojected
                    // position still owns the crop choice and age across chunk boundaries.
                    if (cell == null && column.size() == 1) cell = column.getFirst();
                    if (cell != null) state = cell.deadState();
                }
            }
            if (state.is(Blocks.CRAFTING_TABLE)) state = MiteWorkbenches.TABLES.get("flint").get().defaultBlockState();
            if (info.nbt() != null && smithLoot(info.nbt().getString("LootTable"))
                    && (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST))) {
                state = MiteFunctionalBlocks.STRONGBOXES.get("iron").get().defaultBlockState()
                        .setValue(ChestBlock.FACING, state.getValue(ChestBlock.FACING))
                        .setValue(ChestBlock.WATERLOGGED, state.getValue(ChestBlock.WATERLOGGED))
                        .setValue(ChestBlock.TYPE, ChestType.SINGLE);
            }
            result.add(state == info.state() ? info : new StructureTemplate.StructureBlockInfo(info.pos(), state, info.nbt()));
        }
        return result;
    }

    private record FieldCell(BlockPos origin, BlockState deadState) {}

    /** Fruit blocks and hay are decoration; only planted crop and stem cells belong to source fields. */
    public static boolean farmCell(BlockState state) {
        return CropLifecycle.managed(state) || state.getBlock() instanceof StemBlock || state.getBlock() instanceof AttachedStemBlock;
    }

    /** Each original connected crop strip draws once, before modern species/gravity processors. */
    private static Map<Long, List<FieldCell>> deadFields(List<StructureTemplate.StructureBlockInfo> blocks,
            BlockPos origin, StructurePlaceSettings settings, long seed) {
        Map<BlockPos, BlockState> crops = new HashMap<>();
        for (var info : blocks) if (farmCell(info.state())) crops.put(info.pos(), info.state());
        Map<Long, List<FieldCell>> result = new HashMap<>();
        Set<BlockPos> remaining = new HashSet<>(crops.keySet());
        for (var first : blocks) {
            if (!remaining.remove(first.pos())) continue;
            List<BlockPos> strip = new ArrayList<>();
            ArrayDeque<BlockPos> todo = new ArrayDeque<>();
            todo.add(first.pos());
            while (!todo.isEmpty()) {
                BlockPos pos = todo.removeFirst();
                strip.add(pos);
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos adjacent = pos.relative(direction);
                    BlockState neighbor = crops.get(adjacent);
                    if (neighbor != null && remaining.remove(adjacent)) todo.addLast(adjacent);
                }
            }
            // The complete original template is inspected before per-chunk clipping. Both
            // modern random crop substitutions and vertical projection leave this identity intact.
            BlockPos localAnchor = strip.stream().min(BlockPos::compareTo).orElseThrow();
            BlockPos anchor = StructureTemplate.calculateRelativePosition(settings, localAnchor).offset(origin);
            int draw = RandomSource.create(seed ^ Mth.getSeed(anchor) ^ 0x56494c4c414745L).nextInt(6);
            CropBlock crop = switch (draw) {
                case 0 -> (CropBlock) Blocks.CARROTS;
                case 1 -> (CropBlock) Blocks.POTATOES;
                case 2 -> MiteFoodPlants.ONIONS.get();
                default -> (CropBlock) Blocks.WHEAT;
            };
            for (BlockPos pos : strip) {
                BlockPos cellOrigin = StructureTemplate.calculateRelativePosition(settings, pos).offset(origin);
                int age = 2 + RandomSource.create(seed ^ Mth.getSeed(cellOrigin) ^ 0x43524f5053L).nextInt(5);
                result.computeIfAbsent(ChunkPos.asLong(cellOrigin.getX(), cellOrigin.getZ()), ignored -> new ArrayList<>())
                        .add(new FieldCell(cellOrigin, CropLifecycle.deadState(crop.getStateForAge(age), false)));
            }
        }
        return result;
    }
}

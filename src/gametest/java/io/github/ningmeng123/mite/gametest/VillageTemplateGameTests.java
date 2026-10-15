package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.block.entity.StrongboxBlockEntity;
import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.worldgen.VillageTemplateAdaptation;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.GravityProcessor;
import net.minecraft.world.level.levelgen.Heightmap;
import io.github.ningmeng123.mite.survival.CropLifecycle;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real StructureStart and template entry points, including ordinary-placement controls. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class VillageTemplateGameTests {
    private VillageTemplateGameTests() {}

    @GameTest(template = "empty")
    public static void villageStartTransformsCropsWorkbenchesAndAllSmithChestTypes(GameTestHelper helper) {
        for (String smith : List.of("armorer", "toolsmith", "weaponsmith")) {
            var level = helper.getLevel();
            BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
            placeStart(helper, fixture(helper, smith, false), origin, "village_plains");
            helper.assertTrue(CubeCropBlight.dead(level.getBlockState(origin.above())),
                    "A crop placed through the real registered village StructureStart is dead");
            helper.assertTrue(level.getBlockState(origin.east(2)).is(MiteWorkbenches.TABLES.get("flint").get()),
                    "A village cannot expose the disabled vanilla workbench");
            BlockPos chestPos = origin.offset(2, 0, 2);
            helper.assertTrue(level.getBlockEntity(chestPos) instanceof StrongboxBlockEntity,
                    "All three modern smith roles inherit the source iron strongbox");
            var chest = (StrongboxBlockEntity) level.getBlockEntity(chestPos);
            helper.assertTrue(chest.getLootTable() == null && !chest.isEmpty(),
                    "Source rewards are materialized during generation, before any player opens or breaks the chest");
            helper.assertTrue(chest.ownerName().isEmpty(), "A natural source strongbox has no invented player owner");
            var saved = chest.saveWithFullMetadata(level.registryAccess());
            var restored = new StrongboxBlockEntity(chestPos, chest.getBlockState());
            restored.loadWithComponents(saved, level.registryAccess());
            for (int slot = 0; slot < chest.getContainerSize(); slot++)
                helper.assertTrue(net.minecraft.world.item.ItemStack.matches(chest.getItem(slot), restored.getItem(slot)),
                        "Already generated source contents and damage survive block-entity save/load");
        }
        helper.assertFalse(VillageTemplateAdaptation.active(helper.getLevel()), "Production scope is gone after placement");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void villageTemplateSuppressesResidentsButRetainsLivestockAndOrdinarySpawns(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        placeStart(helper, fixture(helper, "weaponsmith", true), origin, "village_plains");
        AABB area = new AABB(origin).inflate(3);
        helper.assertTrue(level.getEntitiesOfClass(Villager.class, area).isEmpty(), "Source villages do not create natural villagers");
        helper.assertTrue(level.getEntities(EntityType.CAT, area, cat -> true).isEmpty(), "Modern template cats do not appear in an empty source village");
        helper.assertTrue(level.getEntities(EntityType.IRON_GOLEM, area, golem -> true).isEmpty(), "Natural template golems are not source village residents");
        helper.assertTrue(!level.getEntitiesOfClass(Pig.class, area).isEmpty(), "Village livestock is retained by the modern layout adaptation");
        Villager villager = EntityType.VILLAGER.create(level);
        helper.assertTrue(villager != null, "A normal villager can be created after village placement");
        villager.moveTo(origin.getX() + 1.5, origin.getY() + 1, origin.getZ() + 1.5, 0, 0);
        helper.assertTrue(level.addFreshEntity(villager), "Ordinary player/cure/spawn operations do not inherit the template resident filter");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void standaloneAndUnrelatedStructuresKeepNativeCropsTablesAndLoot(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        StructureTemplate template = fixture(helper, "weaponsmith", false);
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(196), 2);
        nativeContents(helper, origin);
        placeStart(helper, template, origin, "stronghold");
        nativeContents(helper, origin);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceCropStripDrawIsStableWhenInputOrderChangesAndScopeUnwinds(GameTestHelper helper) {
        var level = helper.getLevel();
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var village = structures.get(ResourceLocation.withDefaultNamespace("village_plains"));
        var other = structures.get(ResourceLocation.withDefaultNamespace("stronghold"));
        List<StructureTemplate.StructureBlockInfo> strip = List.of(
                new StructureTemplate.StructureBlockInfo(new BlockPos(14, 70, 12), Blocks.WHEAT.defaultBlockState(), null),
                new StructureTemplate.StructureBlockInfo(new BlockPos(15, 70, 12), Blocks.WHEAT.defaultBlockState(), null),
                new StructureTemplate.StructureBlockInfo(new BlockPos(16, 70, 12), Blocks.WHEAT.defaultBlockState(), null));
        try (var scope = VillageTemplateAdaptation.generation(level, village)) {
            var forward = VillageTemplateAdaptation.blocks(level, strip);
            var reversed = VillageTemplateAdaptation.blocks(level, strip.reversed());
            for (int index = 0; index < forward.size(); index++) {
                helper.assertTrue(forward.get(index).state() == reversed.get(forward.size() - index - 1).state(),
                        "Each cell and whole crop strip retains its species and age across template iteration order");
                helper.assertTrue(forward.get(index).state().getBlock() == forward.getFirst().state().getBlock(),
                        "One source crop draw is shared by the complete cross-chunk strip");
            }
            try (var nested = VillageTemplateAdaptation.generation(level, other)) {
                helper.assertTrue(VillageTemplateAdaptation.blocks(level, strip) == strip,
                        "An unrelated nested structure cannot inherit village content adaptation");
            }
            helper.assertTrue(VillageTemplateAdaptation.active(level), "The enclosing village context is restored");
            try {
                try (var nested = VillageTemplateAdaptation.generation(level, other)) { throw new IllegalStateException("fixture"); }
            } catch (IllegalStateException expected) { /* Verify exception-safe generation ownership. */ }
            helper.assertTrue(VillageTemplateAdaptation.active(level), "An exceptional nested exit also restores the village");
        }
        helper.assertFalse(VillageTemplateAdaptation.active(level), "No village context survives its final close");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualModernFarmTemplatesKeepWaterChannelsAndReceiveSourceDeadFields(GameTestHelper helper) {
        var level = helper.getLevel();
        for (String biome : List.of("plains", "desert", "savanna", "snowy", "taiga")) {
            String farm = biome.equals("snowy") ? "snowy_farm_1" : biome + "_large_farm_1";
            var template = level.getStructureManager().getOrCreate(ResourceLocation.withDefaultNamespace("village/" + biome + "/houses/" + farm));
            CompoundTag saved = template.save(new CompoundTag());
            ListTag paletteTags = saved.getList("palette", 10);
            List<BlockState> palette = new ArrayList<>();
            for (int index = 0; index < paletteTags.size(); index++) palette.add(NbtUtils.readBlockState(
                    level.registryAccess().registryOrThrow(Registries.BLOCK).asLookup(), paletteTags.getCompound(index)));
            List<StructureTemplate.StructureBlockInfo> input = new ArrayList<>();
            ListTag blocks = saved.getList("blocks", 10);
            for (int index = 0; index < blocks.size(); index++) {
                var block = blocks.getCompound(index); ListTag pos = block.getList("pos", 3);
                input.add(new StructureTemplate.StructureBlockInfo(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)),
                        palette.get(block.getInt("state")), block.contains("nbt") ? block.getCompound("nbt") : null));
            }
            var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.withDefaultNamespace("village_" + biome));
            try (var scope = VillageTemplateAdaptation.generation(level, structure)) {
                var settings = new StructurePlaceSettings();
                level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).get(ResourceLocation.withDefaultNamespace("farm_" + biome))
                        .list().forEach(settings::addProcessor);
                BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
                var output = StructureTemplate.processBlockInfos(level, origin, origin, settings, input, template);
                helper.assertTrue(output.stream().anyMatch(info -> CropLifecycle.managed(info.state())), "A real " + biome + " farm supplies managed crop cells");
                for (int index = 0; index < output.size(); index++) {
                    var before = input.get(index).state(); var after = output.get(index).state();
                    if (VillageTemplateAdaptation.farmCell(before)) helper.assertTrue(CubeCropBlight.dead(after), "All actual " + biome + " farm cells become source dead crops even after the real biome farm processor");
                    if (before.is(Blocks.WATER)) helper.assertTrue(after.is(Blocks.WATER), "Modern " + biome + " irrigation channels remain intact");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realBiomeFarmProcessorsCannotLeaveLiveStemsOrSplitOriginalCropPatches(GameTestHelper helper) {
        var level = helper.getLevel();
        var village = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.withDefaultNamespace("village_plains"));
        List<StructureTemplate.StructureBlockInfo> originals = new ArrayList<>();
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) originals.add(
                new StructureTemplate.StructureBlockInfo(new BlockPos(x, 1, z), Blocks.WHEAT.defaultBlockState(), null));
        for (var decoration : List.of(Blocks.HAY_BLOCK, Blocks.MELON, Blocks.PUMPKIN)) originals.add(
                new StructureTemplate.StructureBlockInfo(new BlockPos(8, 1, originals.size() - 64), decoration.defaultBlockState(), null));
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        for (String biome : List.of("desert", "savanna", "taiga")) {
            var settings = new StructurePlaceSettings();
            level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).get(ResourceLocation.withDefaultNamespace("farm_" + biome))
                    .list().forEach(settings::addProcessor);
            var nativeOutput = StructureTemplate.processBlockInfos(level, origin, origin, settings, originals, null);
            helper.assertTrue(nativeOutput.stream().anyMatch(info -> info.state().is(Blocks.MELON_STEM) || info.state().is(Blocks.PUMPKIN_STEM)),
                    "The real " + biome + " farm processor actually produces the live stem regression control");
            try (var scope = VillageTemplateAdaptation.generation(level, village)) {
                var output = StructureTemplate.processBlockInfos(level, origin, origin, settings, originals, null);
                for (int index = 0; index < 64; index++) {
                    helper.assertTrue(CubeCropBlight.dead(output.get(index).state()), "A modern random stem substitution cannot escape source dead-field generation");
                    helper.assertTrue(output.get(index).state().getBlock() == output.getFirst().state().getBlock(),
                            "A single original crop patch draws once despite processor-created species fragments");
                }
                for (int index = 64; index < output.size(); index++) helper.assertTrue(output.get(index).state() == originals.get(index).state(),
                        "Hay and full melon/pumpkin decoration blocks remain modern village resources");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gravityProjectionAndAttachedStemsRetainTheOriginalFieldChoice(GameTestHelper helper) {
        var level = helper.getLevel();
        var village = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.withDefaultNamespace("village_plains"));
        List<StructureTemplate.StructureBlockInfo> originals = List.of(
                new StructureTemplate.StructureBlockInfo(new BlockPos(0, 1, 0), Blocks.WHEAT.defaultBlockState(), null),
                new StructureTemplate.StructureBlockInfo(new BlockPos(1, 1, 0), Blocks.MELON_STEM.defaultBlockState(), null),
                new StructureTemplate.StructureBlockInfo(new BlockPos(2, 1, 0), Blocks.ATTACHED_MELON_STEM.defaultBlockState(), null),
                new StructureTemplate.StructureBlockInfo(new BlockPos(3, 1, 0), Blocks.PUMPKIN_STEM.defaultBlockState(), null),
                new StructureTemplate.StructureBlockInfo(new BlockPos(4, 1, 0), Blocks.ATTACHED_PUMPKIN_STEM.defaultBlockState(), null));
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        try (var scope = VillageTemplateAdaptation.generation(level, village)) {
            var plain = StructureTemplate.processBlockInfos(level, origin, origin, new StructurePlaceSettings(), originals, null);
            var settings = new StructurePlaceSettings().addProcessor(new GravityProcessor(Heightmap.Types.WORLD_SURFACE_WG, 20));
            var projected = StructureTemplate.processBlockInfos(level, origin, origin, settings, originals, null);
            for (int index = 0; index < originals.size(); index++) {
                helper.assertTrue(!plain.get(index).pos().equals(projected.get(index).pos()), "The real gravity processor moves the field vertically");
                helper.assertTrue(CubeCropBlight.dead(projected.get(index).state()) && projected.get(index).state() == plain.get(index).state(),
                        "Vertical processor offsets retain each original cell's dead species and age");
                helper.assertTrue(projected.get(index).state().getBlock() == projected.getFirst().state().getBlock(),
                        "Original adjacent crop and attached-stem cells share one source field draw");
            }
        }
        helper.succeed();
    }

    private static void nativeContents(GameTestHelper helper, BlockPos origin) {
        var level = helper.getLevel();
        helper.assertTrue(level.getBlockState(origin.above()).is(Blocks.WHEAT)
                && !CubeCropBlight.dead(level.getBlockState(origin.above())), "Ordinary templates keep their living native crop");
        helper.assertTrue(level.getBlockState(origin.east(2)).is(Blocks.CRAFTING_TABLE), "Only village starts replace template workbenches");
        helper.assertTrue(level.getBlockEntity(origin.offset(2, 0, 2)) instanceof ChestBlockEntity,
                "An unrelated template retains a normal chest");
        var chest = (ChestBlockEntity) level.getBlockEntity(origin.offset(2, 0, 2));
        helper.assertTrue(chest.getLootTable() != null && chest.getLootTable().location().getPath().endsWith("village_weaponsmith"),
                "An ordinary template is not silently assigned source loot");
    }

    private static void placeStart(GameTestHelper helper, StructureTemplate template, BlockPos origin, String structureId) {
        var level = helper.getLevel();
        ResourceLocation templateId = ResourceLocation.parse("mite:gametest/village_content_" + structureId);
        level.getStructureManager().getOrCreate(templateId).load(
                level.registryAccess().registryOrThrow(Registries.BLOCK).asLookup(), template.save(new CompoundTag()));
        var element = StructurePoolElement.single(templateId.toString()).apply(StructureTemplatePool.Projection.RIGID);
        BoundingBox bounds = element.getBoundingBox(level.getStructureManager(), origin, Rotation.NONE);
        var piece = new PoolElementStructurePiece(level.getStructureManager(), element, origin, 0, Rotation.NONE,
                bounds, LiquidSettings.IGNORE_WATERLOGGING);
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.withDefaultNamespace(structureId));
        var start = new StructureStart(structure, new ChunkPos(origin), 0, new PiecesContainer(List.of(piece)));
        start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(196), bounds, new ChunkPos(origin));
    }

    private static StructureTemplate fixture(GameTestHelper helper, String smith, boolean residents) {
        CompoundTag source = new CompoundTag();
        source.put("size", integers(3, 3, 3));
        ListTag palette = new ListTag();
        for (String name : List.of("farmland", "wheat", "crafting_table", "chest", "cobblestone")) {
            CompoundTag state = new CompoundTag(); state.putString("Name", "minecraft:" + name); palette.add(state);
        }
        source.put("palette", palette);
        ListTag blocks = new ListTag();
        block(blocks, 0, 0, 0, 0, null); block(blocks, 1, 0, 1, 0, null);
        block(blocks, 2, 2, 0, 0, null); block(blocks, 4, 1, 0, 1, null);
        CompoundTag chest = new CompoundTag(); chest.putString("id", "minecraft:chest");
        chest.putString("LootTable", "minecraft:chests/village/village_" + smith);
        block(blocks, 3, 2, 0, 2, chest);
        source.put("blocks", blocks);
        ListTag entities = new ListTag();
        if (residents) for (String type : List.of("villager", "zombie_villager", "cat", "iron_golem", "pig")) {
            CompoundTag entry = new CompoundTag();
            ListTag pos = new ListTag(); pos.add(DoubleTag.valueOf(1.5)); pos.add(DoubleTag.valueOf(1)); pos.add(DoubleTag.valueOf(1.5));
            entry.put("pos", pos); entry.put("blockPos", integers(1, 1, 1));
            CompoundTag entity = new CompoundTag(); entity.putString("id", "minecraft:" + type); entry.put("nbt", entity); entities.add(entry);
        }
        source.put("entities", entities);
        StructureTemplate template = new StructureTemplate();
        template.load(helper.getLevel().registryAccess().registryOrThrow(Registries.BLOCK).asLookup(), source);
        template.setAuthor("NingMeng123");
        return template;
    }

    private static void block(ListTag list, int palette, int x, int y, int z, CompoundTag nbt) {
        CompoundTag block = new CompoundTag(); block.putInt("state", palette); block.put("pos", integers(x, y, z));
        if (nbt != null) block.put("nbt", nbt); list.add(block);
    }

    private static ListTag integers(int... values) {
        ListTag list = new ListTag(); for (int value : values) list.add(IntTag.valueOf(value)); return list;
    }
}

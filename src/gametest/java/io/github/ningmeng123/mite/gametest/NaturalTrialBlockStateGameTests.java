package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native chunk transitions and persistence preserve only genuine generated provenance. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class NaturalTrialBlockStateGameTests {
    private NaturalTrialBlockStateGameTests() {}

    @GameTest(template = "empty")
    public static void allNineCopperShapesRetainOriginThroughOxidationWaxingAndScraping(GameTestHelper helper) {
        List<List<Block>> families = List.of(
                List.of(Blocks.COPPER_BLOCK, Blocks.EXPOSED_COPPER, Blocks.WAXED_EXPOSED_COPPER,
                        Blocks.EXPOSED_COPPER, Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER, Blocks.WAXED_OXIDIZED_COPPER, Blocks.COPPER_BLOCK),
                List.of(Blocks.CUT_COPPER, Blocks.EXPOSED_CUT_COPPER, Blocks.WAXED_EXPOSED_CUT_COPPER,
                        Blocks.EXPOSED_CUT_COPPER, Blocks.WEATHERED_CUT_COPPER, Blocks.OXIDIZED_CUT_COPPER, Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.CUT_COPPER),
                List.of(Blocks.CUT_COPPER_SLAB, Blocks.EXPOSED_CUT_COPPER_SLAB, Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB,
                        Blocks.EXPOSED_CUT_COPPER_SLAB, Blocks.WEATHERED_CUT_COPPER_SLAB, Blocks.OXIDIZED_CUT_COPPER_SLAB, Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB, Blocks.CUT_COPPER_SLAB),
                List.of(Blocks.CUT_COPPER_STAIRS, Blocks.EXPOSED_CUT_COPPER_STAIRS, Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS,
                        Blocks.EXPOSED_CUT_COPPER_STAIRS, Blocks.WEATHERED_CUT_COPPER_STAIRS, Blocks.OXIDIZED_CUT_COPPER_STAIRS, Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Blocks.CUT_COPPER_STAIRS),
                List.of(Blocks.CHISELED_COPPER, Blocks.EXPOSED_CHISELED_COPPER, Blocks.WAXED_EXPOSED_CHISELED_COPPER,
                        Blocks.EXPOSED_CHISELED_COPPER, Blocks.WEATHERED_CHISELED_COPPER, Blocks.OXIDIZED_CHISELED_COPPER, Blocks.WAXED_OXIDIZED_CHISELED_COPPER, Blocks.CHISELED_COPPER),
                List.of(Blocks.COPPER_GRATE, Blocks.EXPOSED_COPPER_GRATE, Blocks.WAXED_EXPOSED_COPPER_GRATE,
                        Blocks.EXPOSED_COPPER_GRATE, Blocks.WEATHERED_COPPER_GRATE, Blocks.OXIDIZED_COPPER_GRATE, Blocks.WAXED_OXIDIZED_COPPER_GRATE, Blocks.COPPER_GRATE),
                List.of(Blocks.COPPER_BULB, Blocks.EXPOSED_COPPER_BULB, Blocks.WAXED_EXPOSED_COPPER_BULB,
                        Blocks.EXPOSED_COPPER_BULB, Blocks.WEATHERED_COPPER_BULB, Blocks.OXIDIZED_COPPER_BULB, Blocks.WAXED_OXIDIZED_COPPER_BULB, Blocks.COPPER_BULB),
                List.of(Blocks.COPPER_DOOR, Blocks.EXPOSED_COPPER_DOOR, Blocks.WAXED_EXPOSED_COPPER_DOOR,
                        Blocks.EXPOSED_COPPER_DOOR, Blocks.WEATHERED_COPPER_DOOR, Blocks.OXIDIZED_COPPER_DOOR, Blocks.WAXED_OXIDIZED_COPPER_DOOR, Blocks.COPPER_DOOR),
                List.of(Blocks.COPPER_TRAPDOOR, Blocks.EXPOSED_COPPER_TRAPDOOR, Blocks.WAXED_EXPOSED_COPPER_TRAPDOOR,
                        Blocks.EXPOSED_COPPER_TRAPDOOR, Blocks.WEATHERED_COPPER_TRAPDOOR, Blocks.OXIDIZED_COPPER_TRAPDOOR, Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR, Blocks.COPPER_TRAPDOOR));
        ProtoChunk chunk = proto(helper, new ChunkPos(-14210, 14670));
        for (int i = 0; i < families.size(); i++) {
            BlockPos pos = chunk.getPos().getWorldPosition().offset(i + 1, 70, 5);
            List<Block> family = families.get(i);
            chunk.setBlockState(pos, family.getFirst().defaultBlockState(), false);
            NaturalTrialBlocks.record(chunk, pos);
            for (Block stage : family) {
                chunk.setBlockState(pos, stage.defaultBlockState(), false);
                helper.assertTrue(NaturalTrialBlocks.isNatural(chunk, pos),
                        "An in-place copper oxidation/wax surface change keeps provenance: " + stage);
            }
        }
        helper.assertValueEqual(chunk.getData(NaturalTrialBlocks.ORIGINS.get()).size(), 9,
                "Native state transitions do not duplicate or discard any source position");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualLevelChunkKeepsPropertiesButClearsRemovalBeforeReplacement(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(pos, Blocks.COPPER_BULB.defaultBlockState(), 2);
        LevelChunk chunk = level.getChunkAt(pos);
        NaturalTrialBlocks.record(chunk, pos);
        level.setBlock(pos, level.getBlockState(pos).setValue(BlockStateProperties.LIT, true), 2);
        helper.assertTrue(NaturalTrialBlocks.isNatural(level, pos), "Changing the bulb light state preserves native origin");
        level.setBlock(pos, level.getBlockState(pos).setValue(BlockStateProperties.POWERED, true), 2);
        helper.assertTrue(NaturalTrialBlocks.isNatural(level, pos), "Changing power preserves native origin");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        helper.assertFalse(NaturalTrialBlocks.isNatural(level, pos), "Native removal immediately clears source ownership");
        level.setBlock(pos, Blocks.COPPER_BULB.defaultBlockState(), 2);
        helper.assertFalse(NaturalTrialBlocks.isNatural(level, pos), "A later player placement does not inherit the removed block's source");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void replacementWithDifferentMaterialOrCopperShapeClearsProtoOrigin(GameTestHelper helper) {
        ProtoChunk chunk = proto(helper, new ChunkPos(-14211, 14670));
        Block[][] replacements = {
                { Blocks.STONE, Blocks.DIRT },
                { Blocks.COPPER_BLOCK, Blocks.CUT_COPPER },
                { Blocks.COPPER_DOOR, Blocks.COPPER_TRAPDOOR },
                { Blocks.COPPER_GRATE, Blocks.AIR }
        };
        for (int i = 0; i < replacements.length; i++) {
            BlockPos pos = chunk.getPos().getWorldPosition().offset(i + 1, 50, 4);
            chunk.setBlockState(pos, replacements[i][0].defaultBlockState(), false);
            NaturalTrialBlocks.record(chunk, pos);
            chunk.setUnsaved(false);
            chunk.setBlockState(pos, replacements[i][1].defaultBlockState(), false);
            helper.assertFalse(NaturalTrialBlocks.isNatural(chunk, pos),
                    "A different material or physical copper shape is a replacement, not surface treatment");
            helper.assertTrue(chunk.isUnsaved(), "Cleared provenance dirties its chunk for persistence");
        }
        helper.assertFalse(chunk.hasData(NaturalTrialBlocks.ORIGINS.get()), "Removing the last source position discards its empty attachment");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeChunkPromotionAndSerializerPreserveSourcesWithIndependentCopies(GameTestHelper helper) {
        var level = helper.getLevel();
        ProtoChunk proto = proto(helper, new ChunkPos(-14212, 14670));
        List<BlockPos> positions = List.of(
                proto.getPos().getWorldPosition().offset(1, level.getMinBuildHeight() + 16, 2),
                proto.getPos().getWorldPosition().offset(8, 70, 8),
                proto.getPos().getWorldPosition().offset(14, level.getMaxBuildHeight() - 8, 14));
        for (BlockPos pos : positions) {
            proto.setBlockState(pos, Blocks.COPPER_BLOCK.defaultBlockState(), false);
            NaturalTrialBlocks.record(proto, pos);
        }
        LevelChunk promoted = new LevelChunk(level, proto, null);
        for (BlockPos pos : positions)
            helper.assertTrue(NaturalTrialBlocks.isNatural(promoted, pos), "Proto-to-full promotion retains every section's source");
        helper.assertTrue(proto.getData(NaturalTrialBlocks.ORIGINS.get()) != promoted.getData(NaturalTrialBlocks.ORIGINS.get()),
                "Chunk promotion copies mutable source data instead of sharing a mutable set");
        CompoundTag saved = ChunkSerializer.write(level, promoted);
        CompoundTag originTag = saved.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).getCompound("mite:natural_trial_blocks");
        helper.assertValueEqual(originTag.getInt("schema_version"), 1, "Native saved data has an explicit upgradeable schema");
        helper.assertValueEqual(originTag.getLongArray("positions").length, 3, "Native serializer writes compact packed position values");
        ChunkAccess loaded = ChunkSerializer.read(level, level.getPoiManager(),
                new RegionStorageInfo("mite-natural-trial-test", level.dimension(), "chunk"), promoted.getPos(), saved);
        for (BlockPos pos : positions) {
            helper.assertTrue(NaturalTrialBlocks.isNatural(loaded, pos), "Native save/reload retains source at " + pos);
            helper.assertTrue(loaded.getBlockState(pos).is(Blocks.COPPER_BLOCK), "Native block data agrees with saved source");
        }
        helper.assertFalse(NaturalTrialBlocks.isNatural(loaded, positions.getFirst().east()),
                "Serialization does not expand point provenance into a chunk or structure area");
        promoted.setBlockState(positions.getFirst(), Blocks.AIR.defaultBlockState(), false);
        helper.assertFalse(NaturalTrialBlocks.isNatural(promoted, positions.getFirst()), "The promoted native chunk clears a removed source");
        helper.assertTrue(NaturalTrialBlocks.isNatural(proto, positions.getFirst()), "Mutating the promoted attachment cannot mutate the original proto attachment");
        helper.assertTrue(NaturalTrialBlocks.isNatural(loaded, positions.getFirst()), "Mutating a live attachment cannot mutate its previously deserialized copy");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unmarkedReadsAndUnloadedQueriesDoNotAllocateOrLoad(GameTestHelper helper) {
        ProtoChunk chunk = proto(helper, new ChunkPos(-14213, 14670));
        BlockPos pos = chunk.getPos().getWorldPosition().offset(5, 70, 5);
        helper.assertFalse(NaturalTrialBlocks.isNatural(chunk, pos), "Old or unrelated chunks start without guessed provenance");
        helper.assertFalse(chunk.hasData(NaturalTrialBlocks.ORIGINS.get()), "A read does not create an empty source attachment");
        NaturalTrialBlocks.record(chunk, pos.offset(16, 0, 0));
        NaturalTrialBlocks.record(chunk, pos.atY(chunk.getMaxBuildHeight()));
        helper.assertFalse(chunk.hasData(NaturalTrialBlocks.ORIGINS.get()), "A foreign chunk coordinate or out-of-height position cannot be recorded");
        int x = -17342;
        int z = 17841;
        var source = helper.getLevel().getChunkSource();
        helper.assertTrue(source.getChunkNow(x, z) == null, "The separate query fixture starts unloaded");
        helper.assertFalse(NaturalTrialBlocks.isNatural(helper.getLevel(), new BlockPos(x * 16 + 5, 70, z * 16 + 5)),
                "An unloaded position has no available source evidence");
        helper.assertTrue(source.getChunkNow(x, z) == null, "Checking drops must not synchronously load an absent chunk");
        helper.succeed();
    }

    private static ProtoChunk proto(GameTestHelper helper, ChunkPos coordinate) {
        return new ProtoChunk(coordinate, UpgradeData.EMPTY, helper.getLevel(),
                helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME), null);
    }
}

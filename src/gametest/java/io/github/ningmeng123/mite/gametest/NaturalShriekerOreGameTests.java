package io.github.ningmeng123.mite.gametest;

import com.mojang.serialization.JsonOps;
import io.github.ningmeng123.mite.registry.MiteOreBlocks;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import io.github.ningmeng123.mite.worldgen.NaturalShriekerOres;
import io.github.ningmeng123.mite.world.PlacementDelay;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SculkBehaviour;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.SculkSpreader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.SculkPatchConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real patch and native city-piece placement exercise bounded one-time mineral rewards. @author NingMeng123 */
@GameTestHolder("mite_shrieker_ores")
@PrefixGameTestTemplate(false)
public final class NaturalShriekerOreGameTests {
    private static final long SEED = 196L;
    private static final ChunkPos ORIGIN = new ChunkPos(6520, -6520);
    private static final SculkPatchConfiguration SINGLE = new SculkPatchConfiguration(1, 1, 1, 0, 0, ConstantInt.of(1), 0F);

    private NaturalShriekerOreGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void actualNaturalPatchesProduceOneToThreeCorrectHostOresInsideTheTrueFiveBlockSphere(GameTestHelper helper) {
        Set<Block> observed = new HashSet<>();
        Set<Integer> counts = new HashSet<>();
        for (int sample = 0; sample < 32; sample++) {
            Fixture fixture = new Fixture(helper.getLevel(), new ChunkPos(ORIGIN.x + sample, ORIGIN.z), false);
            BlockPos anchor = fixture.center.getPos().getWorldPosition().offset(8, -20, 8);
            floor(fixture, anchor, sample % 2 == 0 ? Blocks.STONE.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState());
            helper.assertTrue(patch(fixture, anchor, centeredRandom()), "The native SculkPatchFeature succeeds on real unlit generation chunks");
            helper.assertTrue(fixture.state(anchor).is(Blocks.SCULK_SHRIEKER) && fixture.state(anchor).getValue(SculkShriekerBlock.CAN_SUMMON), "The actual feature creates a natural summon-capable shrieker");
            var ores = ores(fixture, anchor);
            helper.assertTrue(ores.size() >= 1 && ores.size() <= 3, "The combined four-mineral reward contains one to three blocks, not one batch per mineral");
            counts.add(ores.size());
            for (var entry : ores.entrySet()) {
                helper.assertTrue(entry.getKey().distSqr(anchor) <= 25D, "Every reward lies inside the true three-dimensional radius-five sphere");
                boolean deep = entry.getValue().is(Blocks.DEEPSLATE_DIAMOND_ORE) || entry.getValue().is(MiteOreBlocks.DEEPSLATE_MITHRIL_ORE.get())
                        || entry.getValue().is(Blocks.DEEPSLATE_LAPIS_ORE) || entry.getValue().is(Blocks.DEEPSLATE_EMERALD_ORE);
                helper.assertTrue(deep == (sample % 2 != 0), "Each ore uses the ordinary or deep variant of the rock it replaced");
                observed.add(normalized(entry.getValue()));
            }
            helper.assertValueEqual(fixture.center.getData(NaturalShriekerOres.REWARDED.get()).size(), 1, "Exactly the natural shrieker position receives a one-time persistent reward record");
            helper.assertFalse(fixture.center.isLightCorrect(), "No real world chunk is loaded or lit by the patch test");
        }
        helper.assertValueEqual(observed.size(), 4, "Independent natural positions exercise diamond, mithril, lapis and emerald selection");
        helper.assertValueEqual(counts, Set.of(1, 2, 3), "Independent position seeds exercise all three allowed total counts");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void identicalNaturalPatchInputsKeepRewardPositionsTypesAndNativeRandomConsumptionDeterministic(GameTestHelper helper) {
        Map<BlockPos, BlockState> expected = null;
        long nextRandom = 0;
        for (int repetition = 0; repetition < 2; repetition++) {
            Fixture fixture = new Fixture(helper.getLevel(), ORIGIN, false);
            BlockPos anchor = fixture.center.getPos().getWorldPosition().offset(8, -20, 8);
            floor(fixture, anchor, Blocks.DEEPSLATE.defaultBlockState());
            RandomSource random = new LegacyRandomSource(SEED);
            helper.assertTrue(patch(fixture, anchor, random), "The real feature remains reproducible");
            BlockPos shrieker = shrieker(fixture, anchor);
            var actual = ores(fixture, shrieker);
            if (expected == null) { expected = actual; nextRandom = random.nextLong(); }
            else {
                helper.assertValueEqual(actual, expected, "World seed and shrieker position determine the exact independent reward");
                helper.assertValueEqual(random.nextLong(), nextRandom, "Reward generation never advances the native feature's random source");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void nativeAncientCityAllPiecesCompleteBeforeRewardsAndRawStoneBuildingCellsAreNeverReplaced(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper.getLevel(), ORIGIN, false);
        BlockPos anchor = fixture.center.getPos().getWorldPosition().offset(8, -20, 8);
        floor(fixture, anchor, Blocks.DEEPSLATE.defaultBlockState());
        BlockPos eligible = anchor.below(4);
        StructureStart city = city(helper, anchor, eligible);
        place(fixture, city);
        helper.assertTrue(fixture.state(anchor).is(Blocks.SCULK_SHRIEKER) && fixture.state(anchor).getValue(SculkShriekerBlock.CAN_SUMMON), "Native StructureStart and template placement preserve the city's summon-capable shrieker");
        var generated = ores(fixture, anchor);
        helper.assertValueEqual(generated.keySet(), Set.of(eligible), "The only legal natural-rock cell receives one ore after the second native building piece completes");
        for (BlockPos position : BlockPos.betweenClosed(anchor.offset(-5, -5, -5), anchor.offset(5, 5, 5))) {
            if (position.distSqr(anchor) > 25D || position.equals(anchor) || position.equals(eligible)) continue;
            helper.assertTrue(fixture.state(position).is(Blocks.DEEPSLATE), "Even raw deepslate written by the ancient-city building template is protected");
        }
        var saved = NaturalShriekerOres.Rewarded.CODEC.encodeStart(JsonOps.INSTANCE,
                fixture.center.getData(NaturalShriekerOres.REWARDED.get())).getOrThrow();
        fixture.center.setData(NaturalShriekerOres.REWARDED.get(), NaturalShriekerOres.Rewarded.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
        fixture.set(eligible, Blocks.DEEPSLATE.defaultBlockState());
        place(fixture, city);
        helper.assertTrue(ores(fixture, anchor).isEmpty(), "Saved and reloaded provenance prevents a repeated native city placement from regenerating a mined reward");
        helper.assertValueEqual(fixture.center.getData(NaturalShriekerOres.REWARDED.get()).size(), 1, "Repeating the natural structure scope never appends a second claim");
        LevelChunk promoted = new LevelChunk(helper.getLevel(), fixture.center, null);
        helper.assertValueEqual(promoted.getData(NaturalShriekerOres.REWARDED.get()).size(), 1, "Native ProtoChunk-to-LevelChunk promotion retains the serialized one-time attachment");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void writableNeighborRockIsAValidFallbackButFarWriteBoundariesAndFullOldChunksAreProtected(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper.getLevel(), ORIGIN, false);
        BlockPos anchor = fixture.center.getPos().getWorldPosition().offset(15, -20, 8);
        fixture.set(anchor.below(), Blocks.SCULK.defaultBlockState());
        for (int offset = 1; offset <= 4; offset++) fixture.set(anchor.east(offset), Blocks.DEEPSLATE.defaultBlockState());
        patch(fixture, anchor, centeredRandom());
        var neighboring = ores(fixture, anchor);
        helper.assertTrue(neighboring.size() >= 1 && neighboring.size() <= 3, "Safe writable adjacent-chunk rock can complete the fixed reward budget");
        for (BlockPos position : neighboring.keySet()) helper.assertTrue(position.getX() > fixture.center.getPos().getMaxBlockX(), "The boundary fixture really exercises neighboring-chunk fallback");

        Fixture far = new Fixture(helper.getLevel(), ORIGIN, false);
        BlockPos edge = far.center.getPos().getWorldPosition().offset(31, -20, 8);
        far.set(edge.below(), Blocks.SCULK.defaultBlockState());
        far.set(edge.east(), Blocks.STONE.defaultBlockState());
        patch(far, edge, centeredRandom());
        helper.assertTrue(ores(far, edge).isEmpty() && far.state(edge.east()).is(Blocks.STONE), "Radius-five geometry cannot bypass the actual native generation write radius");

        Fixture old = new Fixture(helper.getLevel(), ORIGIN, true);
        BlockPos boundary = old.center.getPos().getWorldPosition().offset(15, -20, 8);
        old.set(boundary.below(), Blocks.SCULK.defaultBlockState());
        old.set(boundary.east(), Blocks.STONE.defaultBlockState());
        patch(old, boundary, centeredRandom());
        helper.assertTrue(ores(old, boundary).isEmpty() && old.state(boundary.east()).is(Blocks.STONE), "Already-FULL old neighboring chunks are never rewritten for a new natural reward");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void airFluidsBedrockBuildingsExistingOreAndDeniedWritesNeverBecomeRewardSubstrates(GameTestHelper helper) {
        for (BlockState protectedState : List.of(Blocks.AIR.defaultBlockState(), Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState(),
                Blocks.BEDROCK.defaultBlockState(), MiteWorldBlocks.MANTLE.get().defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState())) {
            Fixture fixture = new Fixture(helper.getLevel(), ORIGIN, false);
            BlockPos anchor = fixture.center.getPos().getWorldPosition().offset(8, -20, 8);
            for (BlockPos position : BlockPos.betweenClosed(anchor.offset(-5, -5, -5), anchor.offset(5, 5, 5)))
                if (!position.equals(anchor)) fixture.set(position, protectedState);
            fixture.set(anchor.below(), Blocks.SCULK.defaultBlockState());
            patch(fixture, anchor, centeredRandom());
            helper.assertTrue(ores(fixture, anchor).isEmpty(), "Air, fluids, existing ores and protected or constructed blocks cannot become reward ore");
            helper.assertValueEqual(fixture.state(anchor.east()), protectedState, "A protected neighboring cell remains byte-for-byte the same block state");
        }
        Fixture denied = new Fixture(helper.getLevel(), ORIGIN, false);
        BlockPos anchor = denied.center.getPos().getWorldPosition().offset(8, -20, 8);
        floor(denied, anchor, Blocks.STONE.defaultBlockState());
        for (BlockPos position : BlockPos.betweenClosed(anchor.offset(-5, -5, -5), anchor.offset(5, 5, 5)))
            if (!position.equals(anchor)) denied.region.denied.add(position.asLong());
        patch(denied, anchor, centeredRandom());
        helper.assertTrue(denied.region.deniedChecks > 0 && ores(denied, anchor).isEmpty(), "The real ensureCanWrite guard is consulted before attempting any reward replacement");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryAndRuntimeSpreaderShriekersCannotAcquireNaturalRewardProvenance(GameTestHelper helper) {
        for (boolean worldGenSpreader : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(helper.getLevel(), ORIGIN, false);
            BlockPos anchor = fixture.center.getPos().getWorldPosition().offset(8, -20, 8);
            floor(fixture, anchor, Blocks.DEEPSLATE.defaultBlockState());
            fixture.set(anchor.below(), Blocks.SCULK.defaultBlockState());
            SculkSpreader spreader = worldGenSpreader ? SculkSpreader.createWorldGenSpreader() : SculkSpreader.createLevelSpreader();
            ((SculkBehaviour)Blocks.SCULK).attemptUseCharge(new SculkSpreader.ChargeCursor(anchor.below(), 500), fixture.region,
                    anchor.east(20), zeroRandom(), spreader, true);
            helper.assertTrue(fixture.state(anchor).is(Blocks.SCULK_SHRIEKER), "The real native sculk growth path creates the negative-control shrieker");
            helper.assertTrue(fixture.state(anchor).getValue(SculkShriekerBlock.CAN_SUMMON) == worldGenSpreader, "The runtime spreader retains its original non-summoning state");
            helper.assertTrue(ores(fixture, anchor).isEmpty() && fixture.center.getExistingDataOrNull(NaturalShriekerOres.REWARDED.get()) == null,
                    "Neither runtime growth nor an unscoped worldgen-spreader invocation can claim natural rewards");
            fixture.region.setBlock(anchor, Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, true), 2);
            helper.assertTrue(ores(fixture, anchor).isEmpty(), "Even a summon-capable ordinary placement cannot substitute for verified generation provenance");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeWorldgenSpreaderGrowthInsideThePatchScopeAlsoReceivesExactlyOneReward(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper.getLevel(), ORIGIN, false);
        BlockPos anchor = fixture.center.getPos().getWorldPosition().offset(8, -20, 8);
        floor(fixture, anchor, Blocks.DEEPSLATE.defaultBlockState());
        fixture.set(anchor.below(), Blocks.SCULK.defaultBlockState());
        try (var scope = NaturalShriekerOres.patch(fixture.region)) {
            ((SculkBehaviour)Blocks.SCULK).attemptUseCharge(new SculkSpreader.ChargeCursor(anchor.below(), 500), fixture.region,
                    anchor.east(20), zeroRandom(), SculkSpreader.createWorldGenSpreader(), true);
            helper.assertTrue(ores(fixture, anchor).isEmpty(), "Native growth is recorded but its reward waits for the whole patch scope to finish");
            scope.finish();
        }
        helper.assertTrue(fixture.state(anchor).is(Blocks.SCULK_SHRIEKER) && fixture.state(anchor).getValue(SculkShriekerBlock.CAN_SUMMON), "The native worldgen growth path creates a summoning shrieker");
        int count = ores(fixture, anchor).size();
        helper.assertTrue(count >= 1 && count <= 3, "Worldgen spreader growth is not missed by a hook that only sees direct SculkPatchFeature writes");
        helper.assertValueEqual(fixture.center.getData(NaturalShriekerOres.REWARDED.get()).size(), 1, "The growth engine and direct patch path use the same one-time provenance ledger");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualSurvivalItemPlacementAndEvenAnExplicitServerLevelScopeCannotGenerateRewards(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(0, 3, 0));
        BlockPos support = target.below();
        BlockState oldTarget = level.getBlockState(target), oldSupport = level.getBlockState(support);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(Vec3.atCenterOf(target.offset(2, 0, 0)));
        var chunk = level.getChunkAt(target);
        var previous = chunk.getExistingDataOrNull(NaturalShriekerOres.REWARDED.get());
        int before = previous == null ? 0 : previous.size();
        try {
            level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            PlacementDelay.clear(player);
            ItemStack held = new ItemStack(Items.SCULK_SHRIEKER);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(support).add(0, .5, 0), Direction.UP, support, false));
            helper.assertTrue(held.useOn(context).consumesAction(), "A real survival BlockItem interaction places the negative-control shrieker");
            helper.assertTrue(level.getBlockState(target).is(Blocks.SCULK_SHRIEKER)
                    && !level.getBlockState(target).getValue(SculkShriekerBlock.CAN_SUMMON), "Player placement retains vanilla non-natural provenance");
            try (var scope = NaturalShriekerOres.patch(level)) {
                level.setBlockAndUpdate(target, Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, true));
                scope.finish();
            }
            var after = chunk.getExistingDataOrNull(NaturalShriekerOres.REWARDED.get());
            helper.assertValueEqual(after == null ? 0 : after.size(), before, "Neither player placement nor a live ServerLevel invocation can enter natural chunk-generation reward processing");
        } finally {
            PlacementDelay.clear(player);
            level.setBlockAndUpdate(target, oldTarget);
            level.setBlockAndUpdate(support, oldSupport);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void failedNativePatchUnwindsItsScopeAndCannotRewardLaterOrdinaryWrites(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper.getLevel(), ORIGIN, false);
        BlockPos anchor = fixture.center.getPos().getWorldPosition().offset(8, -20, 8);
        floor(fixture, anchor, Blocks.DEEPSLATE.defaultBlockState());
        fixture.region.failNext = true;
        boolean failed = false;
        try { patch(fixture, anchor, centeredRandom()); }
        catch (PlacementFailure expected) { failed = true; }
        helper.assertTrue(failed, "The real native patch encounters the deliberate write failure");
        fixture.region.setBlock(anchor, Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, true), 2);
        helper.assertTrue(ores(fixture, anchor).isEmpty(), "An exceptional native placement cannot leak the natural-generation scope into later writes");
        helper.assertTrue(fixture.center.getExistingDataOrNull(NaturalShriekerOres.REWARDED.get()) == null, "Failed patch placement records no consumed or pending reward");
        helper.succeed();
    }

    private static boolean patch(Fixture fixture, BlockPos anchor, RandomSource random) {
        return new ConfiguredFeature<>(Feature.SCULK_PATCH, SINGLE).place(fixture.region,
                fixture.level.getChunkSource().getGenerator(), random, anchor);
    }
    private static void floor(Fixture fixture, BlockPos anchor, BlockState rock) {
        for (BlockPos position : BlockPos.betweenClosed(anchor.offset(-6, -6, -6), anchor.offset(6, -1, 6))) fixture.set(position, rock);
    }
    private static RandomSource centeredRandom() {
        return new LegacyRandomSource(SEED) {
            @Override public int nextInt(int bound) { return bound == 5 ? 2 : super.nextInt(bound); }
        };
    }
    private static RandomSource zeroRandom() {
        return new LegacyRandomSource(SEED) { @Override public int nextInt(int bound) { return 0; } };
    }
    private static Block normalized(BlockState state) {
        if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) return Blocks.DIAMOND_ORE;
        if (state.is(MiteOreBlocks.MITHRIL_ORE.get()) || state.is(MiteOreBlocks.DEEPSLATE_MITHRIL_ORE.get())) return MiteOreBlocks.MITHRIL_ORE.get();
        if (state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)) return Blocks.LAPIS_ORE;
        if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) return Blocks.EMERALD_ORE;
        return null;
    }
    private static Map<BlockPos, BlockState> ores(Fixture fixture, BlockPos anchor) {
        Map<BlockPos, BlockState> found = new HashMap<>();
        for (BlockPos position : BlockPos.betweenClosed(anchor.offset(-6, -6, -6), anchor.offset(6, 6, 6))) {
            BlockState state = fixture.state(position);
            if (normalized(state) != null) found.put(position.immutable(), state);
        }
        return Map.copyOf(found);
    }
    private static BlockPos shrieker(Fixture fixture, BlockPos anchor) {
        for (BlockPos position : BlockPos.betweenClosed(anchor.offset(-2, 0, -2), anchor.offset(2, 0, 2)))
            if (fixture.state(position).is(Blocks.SCULK_SHRIEKER)) return position.immutable();
        throw new IllegalStateException("The actual sculk patch produced no shrieker");
    }

    private static StructureStart city(GameTestHelper helper, BlockPos anchor, BlockPos eligible) {
        var level = helper.getLevel();
        StructureTemplate shrieker = template(level, List.of(new StructureTemplate.StructureBlockInfo(BlockPos.ZERO,
                Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, true), null)), new BlockPos(1, 1, 1));
        List<StructureTemplate.StructureBlockInfo> buildings = new ArrayList<>();
        BlockPos origin = anchor.offset(-5, -5, -5);
        for (BlockPos position : BlockPos.betweenClosed(origin, anchor.offset(5, 5, 5)))
            if (position.distSqr(anchor) <= 25D && !position.equals(anchor) && !position.equals(eligible))
                buildings.add(new StructureTemplate.StructureBlockInfo(position.subtract(origin).immutable(), Blocks.DEEPSLATE.defaultBlockState(), null));
        StructureTemplate building = template(level, buildings, new BlockPos(11, 11, 11));
        var first = piece(level, shrieker, anchor, "shrieker");
        var second = piece(level, building, origin, "building");
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.withDefaultNamespace("ancient_city"));
        return new StructureStart(structure, new ChunkPos(anchor), 0, new PiecesContainer(List.of(first, second)));
    }
    private static PoolElementStructurePiece piece(ServerLevel level, StructureTemplate template, BlockPos origin, String suffix) {
        var id = ResourceLocation.parse("mite:gametest/shrieker_ores_" + suffix);
        level.getStructureManager().getOrCreate(id).load(level.registryAccess().registryOrThrow(Registries.BLOCK).asLookup(), template.save(new CompoundTag()));
        var element = StructurePoolElement.single(id.toString()).apply(StructureTemplatePool.Projection.RIGID);
        var bounds = element.getBoundingBox(level.getStructureManager(), origin, Rotation.NONE);
        return new PoolElementStructurePiece(level.getStructureManager(), element, origin, 0, Rotation.NONE, bounds, LiquidSettings.IGNORE_WATERLOGGING);
    }
    private static void place(Fixture fixture, StructureStart start) {
        var coordinate = fixture.center.getPos();
        var bounds = new BoundingBox(coordinate.getMinBlockX(), fixture.level.getMinBuildHeight(), coordinate.getMinBlockZ(),
                coordinate.getMaxBlockX(), fixture.level.getMaxBuildHeight() - 1, coordinate.getMaxBlockZ());
        start.placeInChunk(fixture.region, fixture.level.structureManager(), fixture.level.getChunkSource().getGenerator(), RandomSource.create(SEED), bounds, coordinate);
    }
    private static StructureTemplate template(ServerLevel level, List<StructureTemplate.StructureBlockInfo> blocks, BlockPos size) {
        CompoundTag source = new CompoundTag();
        source.put("size", integers(size.getX(), size.getY(), size.getZ()));
        ListTag palette = new ListTag(), entries = new ListTag();
        for (int index = 0; index < blocks.size(); index++) {
            var block = blocks.get(index);
            palette.add(net.minecraft.nbt.NbtUtils.writeBlockState(block.state()));
            CompoundTag entry = new CompoundTag();
            entry.putInt("state", index);
            entry.put("pos", integers(block.pos().getX(), block.pos().getY(), block.pos().getZ()));
            entries.add(entry);
        }
        source.put("palette", palette);
        source.put("blocks", entries);
        source.put("entities", new ListTag());
        StructureTemplate template = new StructureTemplate();
        template.load(level.registryAccess().registryOrThrow(Registries.BLOCK).asLookup(), source);
        template.setAuthor("NingMeng123");
        return template;
    }
    private static ListTag integers(int... values) {
        ListTag result = new ListTag();
        for (int value : values) result.add(IntTag.valueOf(value));
        return result;
    }

    private static final class PlacementFailure extends RuntimeException {}
    private static final class Region extends WorldGenRegion {
        final Set<Long> denied = new HashSet<>();
        int deniedChecks;
        boolean failNext;
        Region(ServerLevel level, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess center) {
            super(level, chunks, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), center);
        }
        @Override public boolean ensureCanWrite(BlockPos pos) {
            if (denied.contains(pos.asLong())) { deniedChecks++; return false; }
            return super.ensureCanWrite(pos);
        }
        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursion) {
            if (failNext) { failNext = false; throw new PlacementFailure(); }
            return super.setBlock(pos, state, flags, recursion);
        }
    }
    private static final class Fixture {
        final ServerLevel level;
        final ProtoChunk center;
        final Map<Long, ChunkAccess> chunks = new HashMap<>();
        final Region region;
        Fixture(ServerLevel level, ChunkPos coordinate, boolean oldEast) {
            this.level = level;
            var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
            StaticCache2D<GenerationChunkHolder> cache = StaticCache2D.create(coordinate.x, coordinate.z, 2, (chunkX, chunkZ) -> {
                ProtoChunk proto = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, level, biomes, null);
                proto.setPersistedStatus(ChunkStatus.CARVERS);
                ChunkAccess chunk = oldEast && chunkX == coordinate.x + 1 && chunkZ == coordinate.z ? new LevelChunk(level, proto, null) : proto;
                chunks.put(chunk.getPos().toLong(), chunk);
                return new GenerationChunkHolder(chunk.getPos()) {
                    @Override public int getTicketLevel() { return 0; }
                    @Override public int getQueueLevel() { return 0; }
                    @Override public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) { return chunk; }
                };
            });
            center = (ProtoChunk)chunks.get(coordinate.toLong());
            region = new Region(level, cache, center);
        }
        void set(BlockPos pos, BlockState state) { chunks.get(new ChunkPos(pos).toLong()).setBlockState(pos, state, false); }
        BlockState state(BlockPos pos) {
            ChunkAccess chunk = chunks.get(new ChunkPos(pos).toLong());
            return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
        }
    }
}

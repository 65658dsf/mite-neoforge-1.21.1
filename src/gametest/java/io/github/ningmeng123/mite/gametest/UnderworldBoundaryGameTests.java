package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import io.github.ningmeng123.mite.registry.MiteWorldFeatures;
import io.github.ningmeng123.mite.worldgen.MantleHeat;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Actual boundary placement, native movement/fire counters and water ticks. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class UnderworldBoundaryGameTests {
    private UnderworldBoundaryGameTests() {}

    @GameTest(template = "empty")
    public static void immutableBoundaryHasSourceLightNoItemAndNoDrops(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        for (Block block : new Block[]{MiteWorldBlocks.MANTLE.get(), MiteWorldBlocks.CORE.get()}) {
            BlockState state = block.defaultBlockState();
            helper.assertValueEqual(state.getLightEmission(helper.getLevel(), pos), 13,
                    "R196 truncates 15 * 0.9 to thirteen block light");
            helper.assertValueEqual(state.getDestroySpeed(helper.getLevel(), pos), -1F, "Boundary hardness is negative one");
            helper.assertTrue(block.asItem() == Items.AIR, "The non-carriable boundary has no registered item");
            helper.assertTrue(Block.getDrops(state, helper.getLevel(), pos, null).isEmpty(), "Boundary loot tables never yield an item");
            helper.assertTrue(block.getExplosionResistance() >= 3_600_000F,
                    "The modern equivalent of the immutable explosion exclusion withstands ordinary explosions");
            var wither = EntityType.WITHER.create(helper.getLevel());
            var skull = EntityType.WITHER_SKULL.create(helper.getLevel());
            var dragon = EntityType.ENDER_DRAGON.create(helper.getLevel());
            try {
                helper.assertFalse(state.canEntityDestroy(helper.getLevel(), pos, wither), "Actual WitherBoss destruction observes the source immutable boundary");
                helper.assertFalse(state.canEntityDestroy(helper.getLevel(), pos, skull), "Actual WitherSkull destruction cannot bypass the boundary");
                helper.assertFalse(state.canEntityDestroy(helper.getLevel(), pos, dragon), "Actual EnderDragon destruction is excluded by the boundary tag");
                helper.getLevel().setBlockAndUpdate(pos, state);
                Explosion enormous = new Explosion(helper.getLevel(), null, pos.getX(), pos.getY(), pos.getZ(), 1_000_000F, false, Explosion.BlockInteraction.DESTROY);
                List<ItemStack> drops = new ArrayList<>();
                state.onExplosionHit(helper.getLevel(), pos, enormous, (stack, at) -> drops.add(stack));
                helper.assertTrue(helper.getLevel().getBlockState(pos).equals(state) && drops.isEmpty(),
                        "Even a direct high-radius explosion callback cannot remove or drop an alwaysImmutable boundary");
            } finally { wither.discard(); skull.discard(); dragon.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualNetherFeatureChangesOnlySourceBoundaryBedrockInAnUnlitRegion(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "The modern Nether remains registered");
        GenerationFixture f = new GenerationFixture(nether, helper.absolutePos(BlockPos.ZERO));
        int x = f.center.getPos().getMinBlockX() + 7, z = f.center.getPos().getMinBlockZ() + 8;
        for (int y : new int[]{0, 1, 122, 123, 127}) f.region.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
        f.region.setBlock(new BlockPos(x + 1, 0, z), Blocks.NETHERRACK.defaultBlockState(), 2);
        f.region.setBlock(new BlockPos(x + 1, 127, z), Blocks.BASALT.defaultBlockState(), 2);
        helper.assertTrue(f.place(), "The actual feature runs in a FEATURES region before lighting");
        helper.assertTrue(f.region.getBlockState(new BlockPos(x, 0, z)).is(MiteWorldBlocks.CORE.get()), "Only lower Y zero bedrock becomes core");
        for (int y : new int[]{123, 127}) helper.assertTrue(f.region.getBlockState(new BlockPos(x, y, z)).is(MiteWorldBlocks.MANTLE.get()),
                "Old Nether roof metadata zero maps to mantle at Y " + y);
        for (int y : new int[]{1, 122}) helper.assertTrue(f.region.getBlockState(new BlockPos(x, y, z)).is(Blocks.BEDROCK),
                "Other bedrock retains the modern terrain at Y " + y);
        helper.assertTrue(f.region.getBlockState(new BlockPos(x + 1, 0, z)).is(Blocks.NETHERRACK)
                && f.region.getBlockState(new BlockPos(x + 1, 127, z)).is(Blocks.BASALT), "Non-bedrock geology is never overwritten");
        helper.assertFalse(f.center.isLightCorrect(), "The fixture stays unlit, matching generation before INITIALIZE_LIGHT");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void netherBoundaryFeatureCannotRewriteTheOverworld(GameTestHelper helper) {
        GenerationFixture f = new GenerationFixture(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        BlockPos pos = new BlockPos(f.center.getPos().getMinBlockX() + 8, 0, f.center.getPos().getMinBlockZ() + 8);
        f.region.setBlock(pos, Blocks.BEDROCK.defaultBlockState(), 2);
        helper.assertFalse(f.place(), "The feature rejects the overworld even if explicitly called there");
        helper.assertTrue(f.region.getBlockState(pos).is(Blocks.BEDROCK), "The modern overworld bedrock remains unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void heatHeightBoundariesUseTheEntityBoxAndActualDimension(GameTestHelper helper) {
        ServerLevel underworld = underworld(helper);
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO);
        try (HeatFixture u = new HeatFixture(underworld, anchor); HeatFixture n = new HeatFixture(nether, anchor);
                HeatFixture o = new HeatFixture(helper.getLevel(), anchor)) {
            u.contactBox(-59.0, -57.2, -60, MiteWorldBlocks.MANTLE.get());
            helper.assertTrue(MantleHeat.touchesHotBoundary(u.player), "Modern underworld feet at the top of its five-layer mantle permit contact");
            u.contactBox(-58.999, -57.199, -59, MiteWorldBlocks.MANTLE.get());
            helper.assertFalse(MantleHeat.touchesHotBoundary(u.player), "Above the mapped bottom band a placed boundary is not a trap");
            n.contactBox(1.0, 2.8, 0, MiteWorldBlocks.CORE.get());
            helper.assertTrue(MantleHeat.touchesHotBoundary(n.player), "Nether minY exactly one permits core contact");
            n.contactBox(1.001, 2.801, 1, MiteWorldBlocks.CORE.get());
            helper.assertFalse(MantleHeat.touchesHotBoundary(n.player), "Nether minY above one excludes artificial low core");
            n.contactBox(121.2, 123.0, 123, MiteWorldBlocks.MANTLE.get());
            helper.assertTrue(MantleHeat.touchesHotBoundary(n.player), "Nether maxY exactly 123 permits roof contact");
            n.contactBox(121.199, 122.999, 122, MiteWorldBlocks.MANTLE.get());
            helper.assertFalse(MantleHeat.touchesHotBoundary(n.player), "Nether maxY below 123 excludes artificial high mantle");
            o.contactBox(1.0, 2.8, 0, MiteWorldBlocks.MANTLE.get());
            helper.assertFalse(MantleHeat.touchesHotBoundary(o.player), "The source heat rule does not apply in the overworld");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realPlayerMovementPreservesTenContactTicksBeforeIgnitionAndDealsFireDamage(GameTestHelper helper) {
        try (HeatFixture f = new HeatFixture(underworld(helper), helper.absolutePos(BlockPos.ZERO))) {
            f.standOnMantle();
            f.player.setRemainingFireTicks(-20);
            float health = f.player.getHealth();
            for (int tick = 1; tick <= 9; tick++) {
                f.tick();
                helper.assertValueEqual(f.player.getRemainingFireTicks(), tick - 10, "Native movement must not reset accumulated negative fire counter at tick " + tick);
                helper.assertFalse(f.player.isOnFire(), "Player ignition waits for ten ticks of dry contact");
            }
            helper.assertTrue(f.player.getHealth() < health, "Contact attempts IN_FIRE damage before ignition");
            f.tick();
            helper.assertValueEqual(f.player.getRemainingFireTicks(), 170, "Tenth dry contact tick starts the source eight seconds plus ten ticks");
            helper.assertTrue(f.player.isOnFire(), "The native fire flag becomes burning after the source counter completes");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeBurningTimeAttributeAndWetMovementRemainEffective(GameTestHelper helper) {
        try (HeatFixture f = new HeatFixture(underworld(helper), helper.absolutePos(BlockPos.ZERO))) {
            f.standOnMantle();
            f.player.getAttribute(Attributes.BURNING_TIME).setBaseValue(.4);
            f.player.setRemainingFireTicks(-10);
            for (int tick = 0; tick < 10; tick++) f.tick();
            helper.assertValueEqual(f.player.getRemainingFireTicks(), 68, "Native ignition applies the same .4 burning-time attribute used by modern fire protection");
            // Administrative fixture placement supplies water without asking the bucket to violate evaporation.
            f.set(new BlockPos(f.x, f.level.getMinBuildHeight() + 1, f.z), Blocks.WATER.defaultBlockState());
            f.set(new BlockPos(f.x, f.level.getMinBuildHeight() + 2, f.z), Blocks.WATER.defaultBlockState());
            f.tick();
            helper.assertTrue(f.player.isInWaterRainOrBubble(), "Real player tick detects the wet fluid column");
            helper.assertTrue(f.player.getRemainingFireTicks() <= 0, "Native wet movement extinguishes fire even while touching mantle");
            for (int tick = 0; tick < 12; tick++) f.tick();
            helper.assertFalse(f.player.isOnFire(), "Continuous wet contact cannot restart the ignition counter");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vanillaAndMetalBucketsEvaporateAboveBothBoundariesButKeepOrdinaryWaterPlacement(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2));
        Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var buckets = new java.util.ArrayList<BucketItem>();
        buckets.add((BucketItem)Items.WATER_BUCKET);
        for (String metal : MiteFoods.METALS) buckets.add((BucketItem)MiteFoods.get(metal + "_water_bucket"));
        for (BucketItem bucket : buckets) {
            for (Block boundary : new Block[]{MiteWorldBlocks.MANTLE.get(), MiteWorldBlocks.CORE.get()}) {
                helper.getLevel().setBlockAndUpdate(target.below(), boundary.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
                helper.assertTrue(bucket.emptyContents(player, helper.getLevel(), target, null, new ItemStack(bucket)), "The real inherited bucket method accepts the evaporated action");
                helper.assertTrue(helper.getLevel().getFluidState(target).isEmpty(), "A boundary consumes placement without creating a water block");
            }
            helper.getLevel().setBlockAndUpdate(target.below(), Blocks.STONE.defaultBlockState());
            helper.assertTrue(bucket.emptyContents(player, helper.getLevel(), target, null, new ItemStack(bucket)), "The same bucket still places ordinary modern water on stone");
            helper.assertTrue(helper.getLevel().getFluidState(target).isSource(), "Ordinary pouring initially fills the whole liquid cell");
            var temporary = io.github.ningmeng123.mite.world.TemporaryFluidChanges.get(helper.getLevel());
            helper.assertValueEqual(temporary.pending().get(target.asLong()).due(), helper.getLevel().getGameTime() + 16,
                    "Ordinary pouring receives the R196 sixteen-tick deadline instead of becoming a permanent source");
            temporary.cancel(target);
            helper.getLevel().setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void actualScheduledWaterFlowSkipsTheMantleCellAndReachesAdjacentStone(GameTestHelper helper) {
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) {
            helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            boolean wall = x == 1 || x == 5 || z == 1 || z == 5;
            helper.setBlock(new BlockPos(x, 2, z), wall ? Blocks.STONE : Blocks.AIR);
            helper.setBlock(new BlockPos(x, 3, z), wall ? Blocks.STONE : Blocks.AIR);
        }
        BlockPos denied = new BlockPos(4, 2, 3), accepted = new BlockPos(3, 2, 4);
        helper.setBlock(denied.below(), MiteWorldBlocks.MANTLE.get());
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.WATER);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(denied)).isEmpty(), "Real scheduled FlowingFluid.spreadTo cannot enter directly over mantle");
            helper.assertFalse(helper.getLevel().getFluidState(helper.absolutePos(accepted)).isEmpty(), "The same actual water tick still spreads over the neighbouring stone");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void realServerBedInteractionRefusesUndergroundSleepWithoutDestroyingEitherHalf(GameTestHelper helper) {
        for (ServerLevel target : new ServerLevel[]{underworld(helper), helper.getLevel().getServer().getLevel(Level.NETHER)}) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-bed-test"), false);
            ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            EmbeddedChannel channel = new EmbeddedChannel(connection);
            var originals = new HashMap<BlockPos, BlockState>();
            try {
                player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                BlockPos foot = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX() + 8, 110,
                        helper.absolutePos(BlockPos.ZERO).getZ() + 8);
                BlockPos head = foot.east();
                for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 3; dy++) {
                    BlockPos at = foot.offset(dx, dy, dz);
                    originals.put(at, target.getBlockState(at));
                    target.setBlock(at, (dy < 0 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
                }
                BlockState footState = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.FOOT);
                BlockState headState = footState.setValue(BedBlock.PART, BedPart.HEAD);
                target.setBlock(foot, footState, 2);
                target.setBlock(head, headState, 2);
                player.teleportTo(target, foot.getX() + .5, foot.getY() + .8, foot.getZ() + .5, 0, 0);
                player.setOnGround(true);
                player.getInventory().clearContent();
                float health = player.getHealth();
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(foot), Direction.UP, foot, false);
                var result = player.gameMode.useItemOn(player, target, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
                helper.assertTrue(result.consumesAction(), "Real gameMode.useItemOn enters the NeoForge right-click denial path");
                helper.assertTrue(target.getBlockState(foot).equals(footState) && target.getBlockState(head).equals(headState),
                        "Both bed halves survive; source underground rejection never detonates the modern bed");
                helper.assertFalse(player.isSleeping(), "The actual server player never starts underground sleep");
                helper.assertValueEqual(player.getHealth(), health, "Denied bed interaction causes no explosion damage");
                helper.assertTrue(target.getBlockState(foot.below()).is(Blocks.STONE), "No surrounding floor is blasted away");

                // The native two-hand sneak condition permits ordinary item use over the bed.
                player.setShiftKeyDown(true);
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 2));
                player.setPos(foot.getX() - 1.5, foot.getY() + .8, foot.getZ() + .5);
                var placed = player.gameMode.useItemOn(player, target, player.getOffhandItem(), InteractionHand.OFF_HAND, hit);
                helper.assertTrue(placed.consumesAction() && target.getBlockState(foot.above()).is(Blocks.DIRT),
                        "Empty main hand, offhand block and sneaking use the actual native placement path");
                helper.assertValueEqual(player.getOffhandItem().getCount(), 1, "Only the placed offhand block is consumed");
                helper.assertTrue(target.getBlockState(foot).equals(footState) && target.getBlockState(head).equals(headState), "Sneak placement keeps the bed intact");
                target.setBlock(foot.above(), Blocks.AIR.defaultBlockState(), 2);
                Consumer<PlayerInteractEvent.RightClickBlock> force = event -> {
                    if (event.getEntity() == player && event.getPos().equals(foot)) event.setUseBlock(TriState.TRUE);
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PlayerInteractEvent.RightClickBlock.class, force);
                try {
                    var forced = player.gameMode.useItemOn(player, target, player.getOffhandItem(), InteractionHand.OFF_HAND, hit);
                    helper.assertTrue(forced.consumesAction() && target.getBlockState(foot.above()).isAir(),
                            "An earlier forced block-interaction event cannot bypass underground bed denial");
                    helper.assertValueEqual(player.getOffhandItem().getCount(), 1, "Denied forced interaction consumes no offhand material");
                    helper.assertTrue(target.getBlockState(foot).equals(footState) && target.getBlockState(head).equals(headState), "Forced use does not detonate either bed half");
                } finally { NeoForge.EVENT_BUS.unregister(force); }
            } finally {
                player.server.getPlayerList().remove(player);
                originals.forEach((pos, state) -> target.setBlock(pos, state, 2));
                channel.finishAndReleaseAll();
            }
        }
        helper.succeed();
    }

    private static ServerLevel underworld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(level != null, "The actual underworld dimension is loaded by server registry data");
        return level;
    }

    /** Native Player.tick and ServerLevel event dispatch; only network-free profile creation is a fixture. */
    private static final class HeatFixture implements AutoCloseable {
        final ServerLevel level;
        final Player player;
        final int x, z;
        final Map<BlockPos, BlockState> originals = new HashMap<>();
        HeatFixture(ServerLevel level, BlockPos anchor) {
            this.level = level;
            x = anchor.getX() + 8; z = anchor.getZ() + 8;
            player = new Player(level, new BlockPos(x, 1, z), 0, new GameProfile(UUID.randomUUID(), "mite-boundary-test")) {
                @Override public boolean isSpectator() { return false; }
                @Override public boolean isCreative() { return false; }
                @Override public boolean isLocalPlayer() { return true; }
            };
            player.setNoGravity(true);
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            player.setHealth(200);
        }
        void set(BlockPos pos, BlockState state) {
            originals.putIfAbsent(pos, level.getBlockState(pos));
            level.setBlockAndUpdate(pos, state);
        }
        void contactBox(double minY, double maxY, int boundaryY, Block block) {
            set(new BlockPos(x, boundaryY, z), block.defaultBlockState());
            player.setBoundingBox(new AABB(x + .2, minY, z + .2, x + .8, maxY, z + .8));
        }
        void standOnMantle() {
            int bottom = level.getMinBuildHeight();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                set(new BlockPos(x + dx, bottom, z + dz), MiteWorldBlocks.MANTLE.get().defaultBlockState());
                for (int y = bottom + 1; y <= bottom + 4; y++) set(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState());
            }
            player.setPos(x + .5, bottom + 1, z + .5);
            player.setOnGround(true);
        }
        void tick() {
            player.setDeltaMovement(Vec3.ZERO);
            player.move(MoverType.SELF, Vec3.ZERO);
            level.tickNonPassenger(player);
        }
        @Override public void close() {
            player.discard();
            originals.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
        }
    }

    /** Genuine unlit ProtoChunks, with no mutation of a Nether save or already-generated test arena. */
    private static final class GenerationFixture {
        final ServerLevel source;
        final ProtoChunk center;
        final WorldGenRegion region;
        GenerationFixture(ServerLevel source, BlockPos anchor) {
            this.source = source;
            ChunkPos origin = new ChunkPos(anchor);
            var biomes = source.registryAccess().registryOrThrow(Registries.BIOME);
            var biome = biomes.getHolderOrThrow(source.dimension().equals(Level.NETHER) ? Biomes.NETHER_WASTES : Biomes.PLAINS);
            StaticCache2D<GenerationChunkHolder> chunks = StaticCache2D.create(origin.x, origin.z, 1, (x, z) -> {
                ProtoChunk chunk = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, source, biomes, null);
                chunk.setPersistedStatus(ChunkStatus.CARVERS);
                chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> biome, source.getChunkSource().randomState().sampler());
                return new GenerationChunkHolder(chunk.getPos()) {
                    @Override public int getTicketLevel() { return 0; }
                    @Override public int getQueueLevel() { return 0; }
                    @Override public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) { return chunk.getPersistedStatus().isOrAfter(status) ? chunk : null; }
                };
            });
            center = (ProtoChunk) chunks.get(origin.x, origin.z).getChunkIfPresentUnchecked(ChunkStatus.CARVERS);
            region = new WorldGenRegion(source, chunks, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), center);
        }
        boolean place() {
            return new ConfiguredFeature<>(MiteWorldFeatures.NETHER_BOUNDARY.get(), NoneFeatureConfiguration.INSTANCE)
                    .place(region, source.getChunkSource().getGenerator(), new LegacyRandomSource(196L), center.getPos().getWorldPosition());
        }
    }
}

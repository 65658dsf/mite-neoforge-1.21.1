package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.mite.world.CollapsePhysics;
import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual player transactions, native scheduler deadlines and persistent R196 undermining. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CollapsePhysicsGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.<Integer>create("mite_collapse_test", Comparator.naturalOrder(), 200);

    private CollapsePhysicsGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void survivalRemovalImmediatelyChecksAboveAndFourSidesButNotTheDiagonal(GameTestHelper helper) {
        run(helper, f -> {
            BlockPos support = f.anchor;
            f.set(support, Blocks.STONE);
            f.set(support.above(), Blocks.DIRT);
            for (Direction direction : Direction.Plane.HORIZONTAL) f.set(support.relative(direction), Blocks.DIRT);
            BlockPos diagonal = support.north().east();
            f.set(diagonal, Blocks.DIRT);
            helper.assertTrue(f.player.gameMode.destroyBlock(support), "The actual survival destruction transaction removes stone");
            helper.assertTrue(f.state(support).isAir(), "The original supporting block is really gone");
            helper.assertTrue(f.state(support.above()).isAir(), "The dirt directly above falls immediately");
            for (Direction direction : Direction.Plane.HORIZONTAL)
                helper.assertTrue(f.state(support.relative(direction)).isAir(), "Each cardinal neighbor is checked immediately");
            helper.assertTrue(f.state(diagonal).is(Blocks.DIRT), "A diagonal neighbor is not an immediate player-break target");
            helper.assertValueEqual(f.falling().size(), 5, "Exactly the upper block and four cardinal blocks enter native falling lifecycles");
            helper.assertTrue(f.check(diagonal) != null, "The diagonal can still receive a later cardinal chain reaction");
            f.finish();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void creativeMiningAndTorchRemovalDoNotUndermineNearbySoil(GameTestHelper helper) {
        run(helper, f -> {
            f.player.setGameMode(GameType.CREATIVE);
            f.set(f.anchor, Blocks.STONE);
            f.set(f.anchor.above(), Blocks.DIRT);
            f.set(f.anchor.east(), Blocks.SOUL_SAND);
            helper.assertTrue(f.player.gameMode.destroyBlock(f.anchor), "Creative removal succeeds");
            helper.assertTrue(f.state(f.anchor.above()).is(Blocks.DIRT) && f.state(f.anchor.east()).is(Blocks.SOUL_SAND),
                    "Creative player mining preserves unsupported neighbors");
            helper.assertTrue(f.check(f.anchor.above()) == null && f.check(f.anchor.east()) == null,
                    "Creative mining does not leave delayed undermining behind");
            f.player.setGameMode(GameType.SURVIVAL);
            f.set(f.anchor.below(), Blocks.BEDROCK);
            f.set(f.anchor, Blocks.TORCH);
            helper.assertTrue(f.player.gameMode.destroyBlock(f.anchor), "Actual survival torch removal succeeds");
            helper.assertTrue(f.state(f.anchor.above()).is(Blocks.DIRT) && f.state(f.anchor.east()).is(Blocks.SOUL_SAND),
                    "The source torch-family exception also preserves nearby soil");
            helper.assertTrue(f.falling().isEmpty(), "Neither exempt operation creates a falling block");
            f.finish();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void canceledNativeBreakDoesNotProduceFallingBlocksOrPendingChecks(GameTestHelper helper) {
        run(helper, f -> {
            f.set(f.anchor, Blocks.STONE);
            f.set(f.anchor.above(), Blocks.DIRT);
            Consumer<BlockEvent.BreakEvent> cancel = event -> {
                if (event.getPlayer() == f.player && event.getPos().equals(f.anchor)) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BlockEvent.BreakEvent.class, cancel);
            try {
                helper.assertFalse(f.player.gameMode.destroyBlock(f.anchor), "NeoForge refuses the real destruction transaction");
                helper.assertTrue(f.state(f.anchor).is(Blocks.STONE) && f.state(f.anchor.above()).is(Blocks.DIRT),
                        "Canceling the event preserves both the support and its crown");
                helper.assertTrue(f.falling().isEmpty() && f.check(f.anchor.above()) == null,
                        "A refused break cannot start immediate or delayed collapse");
            } finally { NeoForge.EVENT_BUS.unregister(cancel); }
            f.finish();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void realOffsetPlacementFallsOnlyAfterItsCancellableSnapshotCommits(GameTestHelper helper) {
        run(helper, f -> {
            f.set(f.anchor, Blocks.STONE);
            BlockPos destination = f.anchor.east();
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 2));
            helper.assertTrue(f.place(f.anchor, Direction.EAST).consumesAction(), "A real side placement succeeds at the offset destination");
            helper.assertTrue(f.state(f.anchor).is(Blocks.STONE) && f.state(destination).isAir(),
                    "The clicked support survives while the actual offset dirt becomes an entity");
            helper.assertValueEqual(f.falling().size(), 1, "The committed placement creates exactly one falling dirt entity");
            helper.assertTrue(f.falling().getFirst().getStartPos().equals(destination), "The entity records the actual placement position");
            helper.assertValueEqual(f.player.getMainHandItem().getCount(), 1, "Only the committed dirt item is consumed");
            Consumer<BlockEvent.EntityPlaceEvent> cancel = event -> {
                if (event.getEntity() == f.player) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BlockEvent.EntityPlaceEvent.class, cancel);
            try {
                helper.assertFalse(f.place(f.anchor, Direction.EAST).consumesAction(), "A second real placement is rejected by NeoForge");
                helper.assertTrue(f.state(destination).isAir() && f.check(destination) == null,
                        "Snapshot rollback leaves neither dirt nor a pending check at the destination");
                helper.assertValueEqual(f.falling().size(), 1, "A rejected placement cannot publish an additional entity");
                helper.assertValueEqual(f.player.getMainHandItem().getCount(), 1, "Rollback preserves the remaining dirt item");
            } finally { NeoForge.EVENT_BUS.unregister(cancel); }
            f.finish();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void creativeNativePlacementStillFallsWithoutConsumingTheStack(GameTestHelper helper) {
        run(helper, f -> {
            f.player.setGameMode(GameType.CREATIVE);
            f.set(f.anchor, Blocks.STONE);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SOUL_SAND, 2));
            helper.assertTrue(f.place(f.anchor, Direction.WEST).consumesAction(), "Creative placement follows the actual item path");
            helper.assertTrue(f.state(f.anchor.west()).isAir(), "Creative placement has no player-mining exemption");
            helper.assertValueEqual(f.falling().size(), 1, "Placed soul sand becomes one native falling entity");
            helper.assertTrue(f.falling().getFirst().getBlockState().is(Blocks.SOUL_SAND), "The entity preserves the selected block");
            helper.assertValueEqual(f.player.getMainHandItem().getCount(), 2, "The native creative item-use transaction retains its stack");
            f.finish();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void walkingAndEntityInsideUseSourceNullPlayerContactEvenForCreativePlayers(GameTestHelper helper) {
        run(helper, f -> {
            f.player.setGameMode(GameType.CREATIVE);
            f.set(f.anchor, Blocks.DIRT);
            Blocks.DIRT.stepOn(f.level, f.anchor, f.state(f.anchor), f.player);
            helper.assertTrue(f.state(f.anchor).isAir(), "Walking uses the native block method and undermines unsupported dirt");
            BlockPos soul = f.anchor.east();
            f.set(soul, Blocks.SOUL_SAND);
            f.state(soul).entityInside(f.level, soul, f.player);
            helper.assertTrue(f.state(soul).isAir(), "The native block-state contact method covers the soul-sand override");
            helper.assertValueEqual(f.falling().size(), 2, "Both contact entry points create exactly one native entity");
            f.finish();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void naturalWritesAndOrdinaryNeighborOrBubbleUpdatesDoNotActivateFloatingTerrain(GameTestHelper helper) {
        run(helper, f -> {
            f.set(f.anchor, Blocks.DIRT);
            BlockPos soul = f.anchor.east();
            f.set(soul, Blocks.SOUL_SAND);
            f.set(f.anchor.west(), Blocks.GRASS_BLOCK);
            for (BlockPos pos : List.of(f.anchor, soul, f.anchor.west())) {
                f.state(pos).handleNeighborChanged(f.level, pos, Blocks.STONE, pos.below(), false);
                f.state(pos).tick(f.level, pos, f.level.random);
            }
            f.level.updateNeighborsAt(f.anchor.below(), Blocks.STONE);
            helper.assertTrue(f.state(f.anchor).is(Blocks.DIRT) && f.state(soul).is(Blocks.SOUL_SAND)
                            && f.state(f.anchor.west()).is(Blocks.GRASS_BLOCK),
                    "World-generation-style writes and ordinary updates preserve natural overhangs");
            helper.assertTrue(f.check(f.anchor) == null && f.check(soul) == null && f.falling().isEmpty(),
                    "An unmarked native scheduled callback does not invent undermining");
            f.finish();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeSchedulerPropagatesUpwardAndHorizontallyOnExactlyTheSecondTick(GameTestHelper helper) {
        run(helper, f -> {
            f.set(f.anchor, Blocks.DIRT);
            BlockPos upper = f.anchor.above();
            BlockPos side = f.anchor.east();
            f.set(upper, Blocks.DIRT);
            f.set(side, Blocks.SOUL_SAND);
            long start = f.level.getGameTime();
            Blocks.DIRT.stepOn(f.level, f.anchor, f.state(f.anchor), f.player);
            helper.assertTrue(f.state(f.anchor).isAir(), "Walking starts the initial native fall immediately");
            helper.assertTrue(f.check(upper).due() == start + 2 && f.check(side).due() == start + 2,
                    "The upper and horizontal source marks have independent exact two-tick deadlines");
            f.after(1, () -> helper.assertTrue(f.state(upper).is(Blocks.DIRT) && f.state(side).is(Blocks.SOUL_SAND),
                    "Neither propagation direction falls on the first native tick"));
            f.after(2, () -> {
                helper.assertTrue(f.level.getGameTime() == start + 2, "The assertion observes the exact source deadline");
                helper.assertTrue(f.state(upper).isAir() && f.state(side).isAir(), "Both queued native ticks fall on tick two");
                helper.assertTrue(f.check(upper) == null && f.check(side) == null, "Consumed checks do not remain in chunk data");
                helper.assertValueEqual(f.falling().size(), 3, "The chain creates precisely the initial, upper and side entities");
                f.finish();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void insertingSupportDuringTheTwoTickWindowPreventsTheQueuedCollapse(GameTestHelper helper) {
        run(helper, f -> {
            f.set(f.anchor, Blocks.DIRT);
            CollapsePhysics.schedule(f.level, f.anchor);
            long deadline = f.check(f.anchor).due();
            f.after(1, () -> f.set(f.anchor.below(), Blocks.STONE));
            f.after(2, () -> {
                helper.assertTrue(f.level.getGameTime() == deadline, "The real scheduler reaches the recorded deadline");
                helper.assertTrue(f.state(f.anchor).is(Blocks.DIRT) && f.state(f.anchor.below()).is(Blocks.STONE),
                        "A support added after scheduling is revalidated at execution time");
                helper.assertTrue(f.check(f.anchor) == null && f.falling().isEmpty(), "The check is consumed without a phantom falling entity");
                f.finish();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void replacingPendingDirtAndPuttingDirtBackCannotReuseTheOldMarker(GameTestHelper helper) {
        run(helper, f -> {
            f.set(f.anchor, Blocks.DIRT);
            CollapsePhysics.schedule(f.level, f.anchor);
            helper.assertTrue(f.check(f.anchor) != null, "The original dirt has a real pending mark");
            f.set(f.anchor, Blocks.STONE);
            helper.assertTrue(f.check(f.anchor) == null, "Native onRemove immediately clears the old mark");
            f.set(f.anchor, Blocks.DIRT);
            f.after(2, () -> {
                helper.assertTrue(f.state(f.anchor).is(Blocks.DIRT), "The old scheduled dirt callback cannot affect a later placement without its own mark");
                helper.assertTrue(f.check(f.anchor) == null && f.falling().isEmpty(), "A position does not retain the removed block's delayed ownership");
                f.finish();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chunkNbtAndNativeTickSerializationPreserveVersionedPendingChecksAndPriorities(GameTestHelper helper) {
        run(helper, f -> {
            try {
                f.set(f.anchor, Blocks.SOUL_SAND);
                LevelChunk chunk = f.level.getChunkAt(f.anchor);
                ticks(chunk).removeIf(t -> t.pos().equals(f.anchor));
                f.level.scheduleTick(f.anchor, Blocks.SOUL_SAND, 20, TickPriority.HIGH);
                CollapsePhysics.schedule(f.level, f.anchor);
                CollapsePhysics.Check original = f.check(f.anchor);
                helper.assertTrue(original.due() == f.level.getGameTime() + 2 && original.nativeDue() == f.level.getGameTime() + 20
                                && original.priority() == TickPriority.HIGH.getValue(),
                        "The source check also preserves the displaced native deadline and its priority");
                CompoundTag saved = ChunkSerializer.write(f.level, chunk);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                NbtIo.writeCompressed(saved, bytes);
                CompoundTag decoded = NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()), NbtAccounter.unlimitedHeap());
                CompoundTag attachment = decoded.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).getCompound("mite:pending_collapses");
                helper.assertValueEqual(attachment.getInt("version"), 1, "The actual chunk attachment records its save schema");
                CollapsePhysics.Pending codecCopy = CollapsePhysics.Pending.CODEC.parse(NbtOps.INSTANCE, attachment).getOrThrow();
                helper.assertTrue(original.equals(codecCopy.get(f.anchor)), "Codec decoding retains all four fields from real NBT bytes");
                CompoundTag futureSchema = attachment.copy();
                futureSchema.putInt("version", 2);
                helper.assertTrue(CollapsePhysics.Pending.CODEC.parse(NbtOps.INSTANCE, futureSchema).result().isEmpty(),
                        "An unsupported future schema is not silently accepted as current data");
                ChunkAccess loaded = ChunkSerializer.read(f.level, f.level.getPoiManager(),
                        new RegionStorageInfo("mite-collapse-test", f.level.dimension(), "chunk"), chunk.getPos(), decoded);
                helper.assertTrue(loaded.getBlockState(f.anchor).is(Blocks.SOUL_SAND)
                                && original.equals(loaded.getData(CollapsePhysics.PENDING.get()).get(f.anchor)),
                        "Native chunk deserialization restores block state and its pending attachment together");
                LevelChunkTicks<Block> restoredTicks = LevelChunkTicks.load(decoded.getList("block_ticks", 10),
                        key -> BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(key)), chunk.getPos());
                restoredTicks.unpack(f.level.getGameTime());
                helper.assertTrue(restoredTicks.getAll().anyMatch(t -> t.pos().equals(f.anchor) && t.type() == Blocks.SOUL_SAND
                                && t.triggerTick() == original.due()),
                        "Native tick-list deserialization also restores the two-tick callback that will consume the mark");
                f.set(f.anchor, Blocks.AIR);
                helper.assertTrue(f.check(f.anchor) == null && original.equals(codecCopy.get(f.anchor))
                                && original.equals(loaded.getData(CollapsePhysics.PENDING.get()).get(f.anchor)),
                        "Live removal cannot mutate either independently deserialized pending map");
                f.finish();
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void existingTwentyTickSoulSandCallbackCannotPostponeATwoTickCollapse(GameTestHelper helper) {
        run(helper, f -> {
            f.set(f.anchor, Blocks.SOUL_SAND);
            long start = f.level.getGameTime();
            helper.assertTrue(f.scheduled(f.anchor).triggerTick() == start + 20,
                    "Native soul-sand onPlace first schedules its ordinary twenty-tick update");
            CollapsePhysics.schedule(f.level, f.anchor);
            helper.assertTrue(f.scheduled(f.anchor).triggerTick() == start + 2 && f.check(f.anchor).nativeDue() == start + 20,
                    "The source check replaces the later callback instead of losing to native de-duplication");
            f.after(1, () -> helper.assertTrue(f.state(f.anchor).is(Blocks.SOUL_SAND), "The block remains until the exact deadline"));
            f.after(2, () -> {
                helper.assertTrue(f.state(f.anchor).isAir() && f.check(f.anchor) == null,
                        "The actual block scheduler collapses soul sand on tick two despite its original tick twenty");
                helper.assertValueEqual(f.falling().size(), 1, "The replacement schedule cannot create duplicate entities");
                f.finish();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void supportedSoulSandChecksDoNotRunTheOriginalBubbleTickEighteenTicksEarly(GameTestHelper helper) {
        run(helper, f -> {
            f.set(f.anchor.below(), Blocks.BEDROCK);
            for (int y = 1; y <= 3; y++)
                for (int x = -1; x <= 1; x++)
                    for (int z = -1; z <= 1; z++)
                        f.set(f.anchor.offset(x, y, z), x == 0 && z == 0 ? Blocks.WATER : Blocks.STONE);
            f.set(f.anchor, Blocks.SOUL_SAND);
            long start = f.level.getGameTime();
            CollapsePhysics.schedule(f.level, f.anchor);
            f.after(2, () -> {
                helper.assertTrue(f.state(f.anchor).is(Blocks.SOUL_SAND) && f.check(f.anchor) == null,
                        "The two-tick check consumes its mark while leaving supported soul sand in place");
                helper.assertTrue(f.state(f.anchor.above()).is(Blocks.WATER), "A collapse check does not execute the displaced bubble callback early");
                helper.assertTrue(f.scheduled(f.anchor).triggerTick() == start + 20, "The original native deadline is restored exactly");
            });
            f.after(19, () -> helper.assertTrue(f.state(f.anchor.above()).is(Blocks.WATER), "The real bubble column still waits at tick nineteen"));
            f.after(20, () -> {
                helper.assertTrue(f.state(f.anchor.above()).is(Blocks.BUBBLE_COLUMN), "The original callback executes through native soul-sand code at tick twenty");
                helper.assertTrue(f.falling().isEmpty(), "A supported bubble-column fixture never produces falling soil");
                f.finish();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void canceledFallingEntityJoinRestoresItsSourceAndNaturalTrialIdentity(GameTestHelper helper) {
        run(helper, f -> {
            f.set(f.anchor, Blocks.DIRT);
            NaturalTrialBlocks.record(f.level.getChunkAt(f.anchor), f.anchor);
            Consumer<EntityJoinLevelEvent> cancel = event -> {
                if (event.getLevel() == f.level && event.getEntity() instanceof FallingBlockEntity falling
                        && falling.getStartPos().equals(f.anchor)) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityJoinLevelEvent.class, cancel);
            try {
                Blocks.DIRT.stepOn(f.level, f.anchor, f.state(f.anchor), f.player);
                helper.assertTrue(f.state(f.anchor).is(Blocks.DIRT), "Refusing the native falling entity restores the original source instead of deleting it");
                helper.assertTrue(NaturalTrialBlocks.isNatural(f.level, f.anchor), "The restoration also keeps the natural trial block's no-drop identity");
                helper.assertTrue(f.check(f.anchor) == null && f.check(f.anchor.above()) == null && f.falling().isEmpty(),
                        "A canceled fall cannot publish an entity or start a chain reaction");
            } finally { NeoForge.EVENT_BUS.unregister(cancel); }
            f.finish();
        });
    }

    private static void run(GameTestHelper helper, Consumer<Fixture> scenario) {
        Fixture fixture = new Fixture(helper);
        // The real native scheduler only ticks an eligible chunk. Wait for its ticket rather than
        // changing the world's clock or invoking the controller as a substitute for scheduled ticks.
        helper.runAfterDelay(199, fixture::close);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(fixture.level.isPositionEntityTicking(fixture.anchor),
                "The fixture reaches real native entity- and block-ticking status"))
                .thenExecute(() -> fixture.guard(() -> scenario.accept(fixture)));
    }

    private static LevelChunkTicks<Block> ticks(LevelChunk chunk) { return (LevelChunkTicks<Block>)chunk.getBlockTicks(); }

    private record Previous(BlockState state, CompoundTag blockEntity, boolean natural) {}

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final BlockPos anchor;
        final Map<BlockPos, Previous> previous = new LinkedHashMap<>();
        final Map<LevelChunk, List<ScheduledTick<Block>>> previousTicks = new LinkedHashMap<>();
        final List<ChunkPos> ticketChunks = new ArrayList<>();
        final int ticketKey = IDS.incrementAndGet();
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final SpawnedEntityCapture spawned;
        private boolean closed;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
            anchor = helper.absolutePos(new BlockPos(1, 12, 1));
            for (BlockPos mutable : BlockPos.betweenClosed(anchor.offset(-1, -4, -1), anchor.offset(1, 3, 1))) {
                BlockPos pos = mutable.immutable();
                BlockEntity blockEntity = level.getBlockEntity(pos);
                previous.put(pos, new Previous(level.getBlockState(pos),
                        blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess()),
                        NaturalTrialBlocks.isNatural(level, pos)));
                LevelChunk chunk = level.getChunkAt(pos);
                if (!previousTicks.containsKey(chunk)) {
                    previousTicks.put(chunk, ticks(chunk).getAll().filter(t -> within(t.pos())).toList());
                    ticketChunks.add(chunk.getPos());
                    level.getChunkSource().addRegionTicket(TICKET, chunk.getPos(), 2, ticketKey);
                }
            }
            for (BlockPos pos : previous.keySet())
                level.setBlock(pos, pos.getY() == anchor.getY() - 4 ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            AABB bounds = new AABB(Vec3.atLowerCornerOf(anchor.offset(-1, -4, -1)), Vec3.atLowerCornerOf(anchor.offset(2, 5, 2)));
            spawned = new SpawnedEntityCapture(level, entity -> (entity instanceof FallingBlockEntity || entity instanceof ItemEntity)
                    && entity.getBoundingBox().intersects(bounds));
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "collapse-" + ticketKey), false);
            player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(Vec3.atCenterOf(anchor).add(0, 8, 0));
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            PlayerExperience.setTotal(player, 10000);
            player.getFoodData().setFoodLevel(6);
            player.removeAllEffects();
        }

        private boolean within(BlockPos pos) {
            return pos.getX() >= anchor.getX() - 1 && pos.getX() <= anchor.getX() + 1
                    && pos.getZ() >= anchor.getZ() - 1 && pos.getZ() <= anchor.getZ() + 1
                    && pos.getY() >= anchor.getY() - 4 && pos.getY() <= anchor.getY() + 3;
        }

        void set(BlockPos pos, Block block) {
            helper.assertTrue(previous.containsKey(pos), "The fixture owns each modified world position");
            level.setBlock(pos, block.defaultBlockState(), 2);
        }

        BlockState state(BlockPos pos) { return level.getBlockState(pos); }

        CollapsePhysics.Check check(BlockPos pos) {
            CollapsePhysics.Pending pending = level.getChunkAt(pos).getExistingDataOrNull(CollapsePhysics.PENDING.get());
            return pending == null ? null : pending.get(pos);
        }

        List<FallingBlockEntity> falling() { return spawned.live(FallingBlockEntity.class); }

        ScheduledTick<Block> scheduled(BlockPos pos) {
            return ticks(level.getChunkAt(pos)).getAll().filter(t -> t.pos().equals(pos) && t.type() == state(pos).getBlock())
                    .findFirst().orElseThrow(() -> new IllegalStateException("Expected an actual native scheduled tick at " + pos));
        }

        InteractionResult place(BlockPos clicked, Direction side) {
            return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(clicked), side, clicked, false)));
        }

        void after(int ticks, Runnable action) { helper.runAfterDelay(ticks, () -> { if (!closed) guard(action); }); }

        void guard(Runnable action) {
            try { action.run(); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void finish() { close(); helper.succeed(); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try {
                spawned.close();
                player.getInventory().clearContent();
                level.getServer().getPlayerList().remove(player);
            } finally {
                try {
                    // Clearing first retires every fixture-owned pending mark via native onRemove.
                    for (BlockPos pos : previous.keySet()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    for (var entry : previous.entrySet()) level.setBlock(entry.getKey(), entry.getValue().state(), 2);
                    for (var entry : previous.entrySet()) {
                        Previous value = entry.getValue();
                        if (value.blockEntity() != null) {
                            BlockEntity restored = BlockEntity.loadStatic(entry.getKey(), value.state(), value.blockEntity(), level.registryAccess());
                            if (restored != null) level.setBlockEntity(restored);
                        }
                        if (value.natural()) NaturalTrialBlocks.record(level.getChunkAt(entry.getKey()), entry.getKey());
                    }
                    for (var entry : previousTicks.entrySet()) {
                        ticks(entry.getKey()).removeIf(t -> within(t.pos()));
                        for (ScheduledTick<Block> tick : entry.getValue()) level.getBlockTicks().schedule(tick);
                    }
                } finally {
                    for (ChunkPos chunk : ticketChunks) level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticketKey);
                    channel.finishAndReleaseAll();
                }
            }
        }
    }
}

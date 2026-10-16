package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.portal.MitePortalFrame;
import io.github.ningmeng123.mite.portal.MitePortalProperties;
import io.github.ningmeng123.mite.portal.MitePortals;
import io.github.ningmeng123.mite.registry.MiteAttachments;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.ModernUnderworldTerrain;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Natural terrain, actual survival excavation/placement, native travel and negative-section NBT. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class UnderworldDepthGameTests {
    private UnderworldDepthGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void modernGeneratedColumnsHaveNegativeMantleAndNoInternalBedrockBarrier(GameTestHelper helper) {
        ServerLevel level = underworld(helper);
        ChunkPos pos = new ChunkPos(4600, -4600);
        ChunkAccess chunk = level.getChunk(pos.x, pos.z);
        helper.assertValueEqual(level.getMinBuildHeight(), -64, "The actual registered underworld includes negative height");
        helper.assertValueEqual(level.getMaxBuildHeight(), 320, "The actual registered underworld reaches modern Y319");
        helper.assertValueEqual(chunk.getData(MiteAttachments.UNDERWORLD_LAYOUT_VERSION.get()), 2,
                "Fresh generated terrain has the explicit modern layout marker");
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            BlockPos floor = pos.getWorldPosition().offset(x, -64, z);
            helper.assertTrue(chunk.getBlockState(floor).is(MiteWorldBlocks.MANTLE.get()), "The genuine generated floor is mantle at " + floor);
            helper.assertTrue(chunk.getBlockState(floor.atY(319)).is(Blocks.BEDROCK), "The ceiling remains protected bedrock");
            for (int y = ModernUnderworldTerrain.MAX_MANTLE_Y + 1; y < ModernUnderworldTerrain.MIN_ROOF_Y; y++) {
                BlockPos at = floor.atY(y);
                helper.assertFalse(chunk.getBlockState(at).is(Blocks.BEDROCK), "An unmineable old internal stratum seals survival descent at " + at);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void survivalCanExcavateNaturalMantleAndBuildANegativeHeightNetherRoundTrip(GameTestHelper helper) {
        ServerLevel underworld = underworld(helper);
        Descent descent = findDryDescent(helper, underworld, new ChunkPos(4800, -4800));
        BlockPos interior = descent.bottom();
        MitePortalFrame frame = new MitePortalFrame(interior, Direction.Axis.X);
        try (Session session = player(helper)) {
            ServerPlayer player = session.player();
            player.changeDimension(new DimensionTransition(underworld, Vec3.atBottomCenterOf(descent.start()), Vec3.ZERO,
                    0, 0, DimensionTransition.DO_NOTHING));
            helper.assertTrue(player.serverLevel() == underworld && player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
                    "The registered player performs descent in native survival mode");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteTools.get("mithril_pickaxe")));
            int excavated = 0;
            // Start in an untouched generated cave. Neither mantle nor any bedrock is replaced by the fixture.
            for (int y = descent.start().getY(); y >= interior.getY() - 1; y--) {
                BlockPos at = new BlockPos(interior.getX(), y, interior.getZ());
                excavated += mine(helper, player, underworld, at);
            }
            helper.assertTrue(excavated > 100, "Descent genuinely excavates the mineable strata between the upper cave and negative mantle");
            for (int x = -1; x <= 2; x++) for (int z = -1; z <= 1; z++) for (int y = -1; y <= 3; y++) {
                mine(helper, player, underworld, frame.at(x, y).offset(0, 0, z));
            }
            helper.assertTrue(frame.touchingBottom(underworld), "A normal-size low frame reaches a naturally generated mantle foundation");
            helper.assertTrue(interior.getY() - 1 <= underworld.getMinBuildHeight() + 8 && interior.getY() < 0,
                    "The frame is in the actual negative-height bottom portal band");
            player.setPos(interior.getX() + .5, interior.getY(), interior.getZ() - 2.5);
            // Native BlockItem.useOn placement consumes supplies; the fixture never setblocks a frame.
            for (int x = -1; x <= 2; x++) placeObsidian(helper, player, underworld, frame.at(x, -1));
            for (int y = 0; y <= 2; y++) {
                placeObsidian(helper, player, underworld, frame.at(-1, y));
                placeObsidian(helper, player, underworld, frame.at(2, y));
            }
            for (int x = -1; x <= 2; x++) placeObsidian(helper, player, underworld, frame.at(x, 3));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
            BlockPos bottomObsidian = frame.at(0, -1);
            player.setPos(interior.getX() + .5, interior.getY(), interior.getZ() - .5);
            helper.assertTrue(player.canInteractWithBlock(bottomObsidian, 1D), "Ignition fixture stays inside actual survival reach");
            var ignition = player.gameMode.useItemOn(player, underworld, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(bottomObsidian).add(0, .5, 0), Direction.UP, bottomObsidian, false));
            helper.assertTrue(ignition.consumesAction() && frame.complete(underworld), "Actual flint-and-steel interaction activates the hand-built native frame");
            helper.assertValueEqual(underworld.getBlockState(interior).getValue(MitePortalProperties.DESTINATION), 2,
                    "Natural negative mantle contact routes the gate to Nether");
            ChunkAccess original = underworld.getChunkAt(interior);
            ChunkAccess loaded = reload(underworld, original);
            helper.assertValueEqual(loaded.getData(MiteAttachments.UNDERWORLD_LAYOUT_VERSION.get()), 2, "Modern layout marker survives a native chunk save/read");
            for (int x = 0; x < 2; x++) for (int y = 0; y < 3; y++) {
                helper.assertTrue(loaded.getBlockState(frame.at(x, y)).is(Blocks.NETHER_PORTAL),
                        "The below-zero native portal survives section serialization");
            }
            helper.assertValueEqual(loaded.getBlockState(interior).getValue(MitePortalProperties.DESTINATION), 2,
                    "Below-zero portal destination survives native block-state serialization");
            helper.assertTrue(loaded.getBlockState(new BlockPos(interior.getX(), -64, interior.getZ())).is(MiteWorldBlocks.MANTLE.get()),
                    "The natural negative mantle remains in the saved lowest section");
            player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 3));
            player.setPos(Vec3.atBottomCenterOf(interior));
            var gate = (NetherPortalBlock)Blocks.NETHER_PORTAL;
            var outbound = gate.getPortalDestination(underworld, player, interior);
            ServerLevel nether = underworld.getServer().getLevel(Level.NETHER);
            helper.assertTrue(outbound != null && outbound.newLevel() == nether, "The native portal resolves Nether from the natural mantle frame");
            player.changeDimension(outbound);
            helper.assertTrue(player.serverLevel() == nether, "The actual survival player changes to Nether");
            var exit = MitePortalFrame.find(nether, player.blockPosition()).orElseThrow();
            var inbound = gate.getPortalDestination(nether, player, exit.bottomLeft());
            helper.assertTrue(inbound != null && inbound.newLevel() == underworld, "The ordinary Nether exit resolves back to underworld");
            player.changeDimension(inbound);
            helper.assertTrue(player.serverLevel() == underworld && player.getY() < 0,
                    "The same player returns through the naturally founded negative-height mantle gate");
            helper.assertTrue(player.getInventory().getItem(1).is(Items.DIAMOND) && player.getInventory().getItem(1).getCount() == 3,
                    "Neither native dimension transition loses or duplicates the inventory fixture");
            CompoundTag playerSave = player.saveWithoutId(new CompoundTag());
            helper.assertTrue(playerSave.getList("Pos", 6).getDouble(1) < 0 && playerSave.getString("Dimension").equals("mite:underworld"),
                    "Player NBT records the actual below-zero return position and underworld dimension");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void negativeSectionsAndExplicitLayoutVersionSurviveNativeChunkSerialization(GameTestHelper helper) {
        ServerLevel level = underworld(helper);
        ChunkAccess original = level.getChunk(5000, -5000);
        ChunkAccess loaded = reload(level, original);
        helper.assertValueEqual(loaded.getMinBuildHeight(), -64, "The reloaded chunk keeps negative sections");
        helper.assertValueEqual(loaded.getHeight(), 384, "The reloaded chunk keeps all twenty-four sections");
        helper.assertValueEqual(loaded.getData(MiteAttachments.UNDERWORLD_LAYOUT_VERSION.get()), 2,
                "Layout provenance survives native attachment serialization, rather than relying on guessed block shapes");
        BlockPos corner = original.getPos().getWorldPosition();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -64; y < 320; y++) {
            BlockPos at = corner.offset(x, y, z);
            helper.assertTrue(original.getBlockState(at).equals(loaded.getBlockState(at)), "Native round-trip changes a generated layer at " + at);
        }
        helper.succeed();
    }

    private static ChunkAccess reload(ServerLevel level, ChunkAccess chunk) {
        CompoundTag saved = ChunkSerializer.write(level, chunk);
        return ChunkSerializer.read(level, level.getPoiManager(), new RegionStorageInfo("mite-depth-test", level.dimension(), "chunk"),
                chunk.getPos(), saved);
    }

    private static Descent findDryDescent(GameTestHelper helper, ServerLevel level, ChunkPos chunkPos) {
        level.getChunk(chunkPos.x, chunkPos.z);
        int baseX = chunkPos.getMinBlockX(), baseZ = chunkPos.getMinBlockZ();
        for (int x = 2; x < 13; x++) for (int z = 2; z < 13; z++) {
            int centralFloor = level.getMinBuildHeight(), surroundingFloor = centralFloor;
            for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 1; dz++) {
                for (int y = level.getMinBuildHeight(); y <= ModernUnderworldTerrain.MAX_MANTLE_Y; y++) {
                    if (level.getBlockState(new BlockPos(baseX + x + dx, y, baseZ + z + dz)).is(MiteWorldBlocks.MANTLE.get())) {
                        surroundingFloor = Math.max(surroundingFloor, y);
                        if (dz == 0) centralFloor = Math.max(centralFloor, y);
                    }
                }
            }
            if (centralFloor != surroundingFloor) continue;
            BlockPos bottom = new BlockPos(baseX + x, centralFloor + 2, baseZ + z);
            for (int y = ModernUnderworldTerrain.CAVERN_TOP_EXCLUSIVE - 2; y > ModernUnderworldTerrain.SEA_LEVEL; y--) {
                BlockPos start = bottom.atY(y);
                if (!level.getBlockState(start).isAir() || !level.getBlockState(start.above()).isAir()) continue;
                boolean dry = true;
                for (int shaftY = bottom.getY() - 1; shaftY <= y; shaftY++) {
                    var state = level.getBlockState(bottom.atY(shaftY));
                    if (!(state.isAir() || state.is(Blocks.STONE))) { dry = false; break; }
                }
                if (dry) return new Descent(start, bottom);
                break;
            }
        }
        helper.fail("The untouched generated seed fixture has no dry mineable descent to a natural mantle foundation");
        throw new IllegalStateException("Unreachable after GameTest failure");
    }

    private static int mine(GameTestHelper helper, ServerPlayer player, ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir()) return 0;
        helper.assertTrue(state.getFluidState().isEmpty() && state.getDestroySpeed(level, pos) >= 0,
                "Survival excavation reaches an immutable/fluid barrier at " + pos + ": " + state);
        player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        helper.assertTrue(state.getDestroyProgress(player, level, pos) > 0, "The actual held tool must make normal survival progress at " + pos);
        helper.assertTrue(player.gameMode.destroyBlock(pos) && level.getBlockState(pos).isAir(), "Native survival excavation cannot remove " + pos);
        return 1;
    }

    private static void placeObsidian(GameTestHelper helper, ServerPlayer player, ServerLevel level, BlockPos target) {
        if (!player.getMainHandItem().is(Items.OBSIDIAN) || player.getMainHandItem().isEmpty())
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OBSIDIAN, 8));
        player.setPos(target.getX() + .5, target.getY(), target.getZ() - .5);
        int before = player.getMainHandItem().getCount();
        for (Direction towardSupport : Direction.values()) {
            BlockPos support = target.relative(towardSupport);
            if (level.getBlockState(support).isAir()) continue;
            helper.assertTrue(player.canInteractWithBlock(support, 1D), "Placement fixture approaches the real native interaction face inside survival reach");
            Direction face = towardSupport.getOpposite();
            var result = player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(support).add(face.getStepX() * .5, face.getStepY() * .5, face.getStepZ() * .5), face, support, false));
            if (!result.consumesAction()) continue;
            helper.assertTrue(level.getBlockState(target).is(Blocks.OBSIDIAN), "Native placement does not fill the required frame position " + target);
            helper.assertValueEqual(player.getMainHandItem().getCount(), before - 1, "Survival placement consumes exactly one obsidian");
            return;
        }
        helper.fail("No native placement face can fill the excavated frame position " + target);
    }

    private static ServerLevel underworld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(level != null, "The actual underworld dimension must load before testing negative terrain");
        return level;
    }

    private static Session player(GameTestHelper helper) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-depth-test"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            return new Session(player, channel);
        } catch (RuntimeException | Error failure) {
            player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }

    private record Descent(BlockPos start, BlockPos bottom) {}
    private record Session(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() { player.server.getPlayerList().remove(player); channel.finishAndReleaseAll(); }
    }
}

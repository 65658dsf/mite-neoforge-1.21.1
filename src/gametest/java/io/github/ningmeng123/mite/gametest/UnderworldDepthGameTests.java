package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.world.PlacementDelay;
import io.github.ningmeng123.mite.config.MiteConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.portal.MitePortalFrame;
import io.github.ningmeng123.mite.portal.MitePortalProperties;
import io.github.ningmeng123.mite.registry.MiteAttachments;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import io.github.ningmeng123.mite.rules.R196PortalRules;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.ModernUnderworldTerrain;
import io.github.ningmeng123.mite.worldgen.R196UnderworldTerrain;
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
import net.minecraft.world.level.block.Block;
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
                var state = chunk.getBlockState(at);
                helper.assertFalse(state.is(Blocks.BEDROCK), "An unmineable old internal stratum seals survival descent at " + at);
                if (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)) {
                    helper.assertTrue(state.is(baseRock(y)), "Natural rock changes to deepslate below Y=0 at " + at);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void survivalCanExcavateNaturalMantleAndBuildANegativeHeightNetherRoundTrip(GameTestHelper helper) {
        ServerLevel underworld = underworld(helper);
        Descent descent;
        // The original terrain/portal fixture needs an undecorated rock shaft under the historical ore rates.
        // Richer user presets are independently tested; they must not make this terrain seed reject mineable ores.
        try (var ignored = new LegacyOreRates()) {
            descent = findDryDescent(helper, underworld, new ChunkPos(4800, -4800));
        }
        BlockPos interior = descent.bottom();
        MitePortalFrame frame = new MitePortalFrame(interior, Direction.Axis.X);
        for (int y = ModernUnderworldTerrain.MIN_PORTAL_MANTLE_Y; y <= ModernUnderworldTerrain.MAX_PORTAL_MANTLE_Y; y++) {
            helper.assertTrue(underworld.getBlockState(interior.atY(y)).is(baseRock(y)),
                    "The descent uses a continuous natural rock opening through the shallow mantle layers at Y=" + y);
        }
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
            excavatePortalRoom(helper, player, underworld, frame);
            helper.assertTrue(frame.touchingBottom(underworld), "A normal-size low frame reaches a naturally generated mantle foundation");
            helper.assertTrue(interior.getY() - 1 <= underworld.getMinBuildHeight() + 8 && interior.getY() < 0,
                    "The frame is in the actual negative-height bottom portal band");
            buildAndLightPortal(helper, player, underworld, frame);
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

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void survivalCanLightNetherGatesAtAllShallowMantleHeights(GameTestHelper helper) {
        ServerLevel underworld = underworld(helper);
        ModernUnderworldTerrain terrain = new ModernUnderworldTerrain(underworld.getSeed());
        List<MitePortalFrame> frames = new ArrayList<>();
        for (int mantleY = ModernUnderworldTerrain.MIN_PORTAL_MANTLE_Y;
                mantleY <= ModernUnderworldTerrain.MAX_PORTAL_MANTLE_Y; mantleY++) {
            MitePortalFrame frame = findShallowPortalFoundation(helper, terrain, new ChunkPos(4900 + 24 * mantleY, -4900), mantleY);
            helper.assertTrue(underworld.getBlockState(frame.at(0, -2)).is(MiteWorldBlocks.MANTLE.get()),
                    "The foundation under the bottom obsidian generates as mantle at Y=" + mantleY);
            helper.assertTrue(frame.touchingBottom(underworld), "A natural mantle foundation is valid at Y=" + mantleY);
            frames.add(frame);
        }
        MitePortalFrame highestFrame = frames.getLast();
        assertGapDoesNotRoute(helper, underworld, highestFrame);
        MitePortalFrame higherFrame = new MitePortalFrame(highestFrame.bottomLeft().above(), highestFrame.axis());
        BlockPos higherFoundation = higherFrame.at(0, -2);
        var original = underworld.getBlockState(higherFoundation);
        try {
            underworld.setBlock(higherFoundation, MiteWorldBlocks.MANTLE.get().defaultBlockState(), 2);
            helper.assertFalse(higherFrame.touchingBottom(underworld), "Mantle above Y=1 cannot route to the Nether");
        } finally {
            underworld.setBlock(higherFoundation, original, 2);
        }

        try (Session session = player(helper)) {
            ServerPlayer player = session.player();
            player.changeDimension(new DimensionTransition(underworld, Vec3.atBottomCenterOf(frames.getFirst().bottomLeft()), Vec3.ZERO,
                    0, 0, DimensionTransition.DO_NOTHING));
            for (MitePortalFrame frame : frames) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteTools.get("mithril_pickaxe")));
                excavatePortalRoom(helper, player, underworld, frame);
                buildAndLightPortal(helper, player, underworld, frame);
                helper.assertValueEqual(underworld.getBlockState(frame.bottomLeft()).getValue(MitePortalProperties.DESTINATION),
                        R196PortalRules.NETHER, "The gate stores the Nether route at mantle Y=" + frame.at(0, -2).getY());
            }

            MitePortalFrame frame = frames.get(-ModernUnderworldTerrain.MIN_PORTAL_MANTLE_Y);
            BlockPos interior = frame.bottomLeft();
            player.setPos(Vec3.atBottomCenterOf(interior));
            var gate = (NetherPortalBlock) Blocks.NETHER_PORTAL;
            var outbound = gate.getPortalDestination(underworld, player, interior);
            ServerLevel nether = underworld.getServer().getLevel(Level.NETHER);
            helper.assertTrue(outbound != null && outbound.newLevel() == nether,
                    "The Y=0 foundation sends the native portal to the Nether");
            player.changeDimension(outbound);
            helper.assertTrue(player.serverLevel() == nether, "The player arrives in the Nether");
            var exit = MitePortalFrame.find(nether, player.blockPosition()).orElseThrow();
            var inbound = gate.getPortalDestination(nether, player, exit.bottomLeft());
            helper.assertTrue(inbound != null && inbound.newLevel() == underworld,
                    "The Nether exit resolves back to the underworld");
            player.changeDimension(inbound);
            helper.assertTrue(player.serverLevel() == underworld && frame.equals(MitePortalFrame.find(underworld, player.blockPosition()).orElse(null)),
                    "The player returns through the original frame on the Y=0 foundation");
        }
        helper.succeed();
    }

    private static void assertGapDoesNotRoute(GameTestHelper helper, ServerLevel level, MitePortalFrame supportedFrame) {
        BlockPos corner = new ChunkPos(supportedFrame.bottomLeft()).getWorldPosition().atY(supportedFrame.bottomLeft().getY());
        for (int x = 1; x <= 13; x++) for (int z = 0; z < 16; z++) {
            MitePortalFrame frame = new MitePortalFrame(corner.offset(x, 0, z), Direction.Axis.X);
            boolean onMantle = false;
            for (int offset = -1; offset <= 2; offset++) {
                onMantle |= level.getBlockState(frame.at(offset, -2)).is(MiteWorldBlocks.MANTLE.get());
            }
            if (onMantle) continue;
            helper.assertFalse(frame.touchingBottom(level), "A frame at an eligible height without any mantle contact cannot route to the Nether");
            return;
        }
        helper.fail("No natural opening under a possible frame at the top shallow mantle height");
    }

    private static MitePortalFrame findShallowPortalFoundation(GameTestHelper helper, ModernUnderworldTerrain terrain,
            ChunkPos anchor, int mantleY) {
        for (int radius = 0; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                ChunkPos chunk = new ChunkPos(anchor.x + dx, anchor.z + dz);
                byte[] blocks = terrain.generate(chunk.x, chunk.z);
                for (int x = 1; x <= 13; x++) for (int z = 1; z <= 14; z++) {
                    if (blocks[ModernUnderworldTerrain.index(x, mantleY, z)] != R196UnderworldTerrain.MANTLE) continue;
                    if (hasMantleAboveFoundation(blocks, x, z, mantleY)) continue;
                    return new MitePortalFrame(chunk.getWorldPosition().offset(x, mantleY + 2, z), Direction.Axis.X);
                }
            }
        }
        helper.fail("No natural mantle foundation fits a survival obsidian frame at Y=" + mantleY);
        throw new IllegalStateException("Unreachable after GameTest failure");
    }

    private static boolean hasMantleAboveFoundation(byte[] blocks, int x, int z, int mantleY) {
        for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 1; dz++) {
            for (int y = mantleY + 1; y <= ModernUnderworldTerrain.MAX_PORTAL_MANTLE_Y; y++) {
                if (blocks[ModernUnderworldTerrain.index(x + dx, y, z + dz)] == R196UnderworldTerrain.MANTLE) return true;
            }
        }
        return false;
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
        for (int x = 1; x <= 13; x++) for (int z = 1; z <= 14; z++) {
            BlockPos column = new BlockPos(baseX + x, 0, baseZ + z);
            if (!level.getBlockState(column).is(Blocks.STONE)) continue;
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
            BlockPos bottom = column.atY(centralFloor + 2);
            for (int y = ModernUnderworldTerrain.CAVERN_TOP_EXCLUSIVE - 2; y > ModernUnderworldTerrain.SEA_LEVEL; y--) {
                BlockPos start = bottom.atY(y);
                if (!level.getBlockState(start).isAir() || !level.getBlockState(start.above()).isAir()) continue;
                boolean dry = true;
                for (int shaftY = bottom.getY() - 1; shaftY <= y; shaftY++) {
                    var state = level.getBlockState(bottom.atY(shaftY));
                    if (!(state.isAir() || state.is(baseRock(shaftY)))) { dry = false; break; }
                }
                if (dry) return new Descent(start, bottom);
                break;
            }
        }
        helper.fail("The untouched generated seed fixture has no dry mineable descent to a natural mantle foundation");
        throw new IllegalStateException("Unreachable after GameTest failure");
    }

    private static Block baseRock(int y) {
        return y < 0 ? Blocks.DEEPSLATE : Blocks.STONE;
    }

    private static void excavatePortalRoom(GameTestHelper helper, ServerPlayer player, ServerLevel level, MitePortalFrame frame) {
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 1; z++) for (int y = -1; y <= 3; y++) {
            mine(helper, player, level, frame.at(x, y).offset(0, 0, z));
        }
    }

    private static void buildAndLightPortal(GameTestHelper helper, ServerPlayer player, ServerLevel level, MitePortalFrame frame) {
        // Native BlockItem.useOn places the frame and consumes the obsidian.
        for (int x = -1; x <= 2; x++) placeObsidian(helper, player, level, frame.at(x, -1));
        for (int y = 0; y <= 2; y++) {
            placeObsidian(helper, player, level, frame.at(-1, y));
            placeObsidian(helper, player, level, frame.at(2, y));
        }
        for (int x = -1; x <= 2; x++) placeObsidian(helper, player, level, frame.at(x, 3));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        BlockPos bottomObsidian = frame.at(0, -1);
        BlockPos interior = frame.bottomLeft();
        player.setPos(interior.getX() + .5, interior.getY(), interior.getZ() - .5);
        helper.assertTrue(player.canInteractWithBlock(bottomObsidian, 1D), "Ignition fixture stays inside actual survival reach");
        var ignition = player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(bottomObsidian).add(0, .5, 0), Direction.UP, bottomObsidian, false));
        helper.assertTrue(ignition.consumesAction() && frame.complete(level),
                "Actual flint-and-steel interaction activates the hand-built native frame");
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
        PlacementDelay.clear(player);
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

    private static final class LegacyOreRates implements AutoCloseable {
        private final Map<ModConfigSpec.DoubleValue, Double> original = new HashMap<>();
        private LegacyOreRates() {
            for (var settings : List.of(MiteConfig.UNDERWORLD_ORES, MiteConfig.UNDERWORLD_DEEPSLATE_ORES))
                settings.values().forEach(setting -> original.put(setting, setting.get()));
            MiteConfig.UNDERWORLD_ORES.values().forEach(setting -> setting.set(1D));
            // Modern coal/emerald were absent from the source terrain fixture.
            MiteConfig.UNDERWORLD_ORES.get(io.github.ningmeng123.mite.rules.R196OreRules.Kind.COAL).set(0D);
            MiteConfig.UNDERWORLD_ORES.get(io.github.ningmeng123.mite.rules.R196OreRules.Kind.EMERALD).set(0D);
            MiteConfig.UNDERWORLD_DEEPSLATE_ORES.values().forEach(setting -> setting.set(-1D));
            MiteConfig.refreshEnchantmentRules();
        }
        @Override public void close() {
            original.forEach(ModConfigSpec.DoubleValue::set);
            MiteConfig.refreshEnchantmentRules();
        }
    }

    private record Descent(BlockPos start, BlockPos bottom) {}
    private record Session(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() { player.server.getPlayerList().remove(player); channel.finishAndReleaseAll(); }
    }
}

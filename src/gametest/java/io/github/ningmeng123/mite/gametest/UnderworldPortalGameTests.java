package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.block.RunestoneBlock;
import io.github.ningmeng123.mite.portal.MitePortalFrame;
import io.github.ningmeng123.mite.portal.MitePortalProperties;
import io.github.ningmeng123.mite.portal.MitePortals;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual frames, mixin state serialization, rune updates, traveler restrictions and dimension routes. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class UnderworldPortalGameTests {
    private UnderworldPortalGameTests() {}

    @GameTest(template = "empty")
    public static void standardFrameActivatesBothAxesWithSourceSpawnRoute(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            MitePortalFrame frame = frame(level, helper.absolutePos(new BlockPos(axis == Direction.Axis.X ? 1 : 7, 2, 1)), axis);
            var shape = PortalShape.findEmptyPortalShape(level, frame.bottomLeft(), axis);
            helper.assertTrue(shape.isPresent(), "The exact R196 two by three frame activates on " + axis);
            shape.orElseThrow().createPortalBlocks();
            helper.assertTrue(frame.complete(level), "All six blocks are the native portal");
            helper.assertValueEqual(level.getBlockState(frame.bottomLeft()).getValue(MitePortalProperties.DESTINATION), 0, "Surface overworld portal routes to overworld spawn");
            helper.assertTrue(MitePortals.isInstantGate(level, frame.bottomLeft()), "Spawn route is immediate");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oversizedAndCryingObsidianFramesCannotActivate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MitePortalFrame frame = frame(level, helper.absolutePos(new BlockPos(1, 2, 1)), Direction.Axis.X);
        level.setBlock(frame.at(-1, 1), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
        helper.assertTrue(PortalShape.findEmptyPortalShape(level, frame.bottomLeft(), frame.axis()).isEmpty(), "Crying obsidian is not a R196 edge");
        level.setBlock(frame.at(-1, 1), Blocks.OBSIDIAN.defaultBlockState(), 2);
        for (int y = 0; y < 3; y++) level.setBlock(frame.at(2, y), Blocks.AIR.defaultBlockState(), 2);
        for (int y = -1; y <= 3; y++) level.setBlock(frame.at(3, y), Blocks.OBSIDIAN.defaultBlockState(), 2);
        level.setBlock(frame.at(2, -1), Blocks.OBSIDIAN.defaultBlockState(), 2);
        level.setBlock(frame.at(2, 3), Blocks.OBSIDIAN.defaultBlockState(), 2);
        helper.assertTrue(PortalShape.findEmptyPortalShape(level, frame.bottomLeft(), frame.axis()).isEmpty(), "Three-wide interior does not activate");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void runeCornersMustShareMetalAndPreserveNibbleOrderAfterActivation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MitePortalFrame frame = frame(level, helper.absolutePos(new BlockPos(1, 2, 1)), Direction.Axis.X);
        PortalShape.findEmptyPortalShape(level, frame.bottomLeft(), frame.axis()).orElseThrow().createPortalBlocks();
        MitePortals.writeRoute(level, frame, 1, false);
        corners(level, frame, false);
        helper.assertValueEqual(frame.rune(level).orElseThrow().seed(), 0x4321, "Four source corner nibbles retain their order");
        helper.assertTrue(level.getBlockState(frame.bottomLeft()).getValue(MitePortalProperties.RUNEGATE), "Corner edits immediately synchronize the runegate appearance");
        helper.assertValueEqual(level.getBlockState(frame.at(1, 2)).getValue(MitePortalProperties.DESTINATION), 0, "All runegate blocks immediately use their source dimension's color");
        helper.assertTrue(MitePortals.isInstantGate(level, frame.bottomLeft()), "Adding corners to an existing frame enables runegate behavior");
        level.setBlock(frame.at(2, 3), MiteWorldBlocks.ADAMANTIUM_RUNESTONE.get().defaultBlockState(), 2);
        helper.assertTrue(frame.rune(level).isEmpty(), "Mixed-metal corners do not form a runegate");
        helper.assertFalse(level.getBlockState(frame.bottomLeft()).getValue(MitePortalProperties.RUNEGATE), "Removing a matching corner immediately restores ordinary gate appearance");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyNetherRoutesSynchronizeTheirActualDestinationOnChunkLoad(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        int x = 240000 + helper.absolutePos(BlockPos.ZERO).getX();
        MitePortalFrame frame = frame(nether, new BlockPos(x, 70, 0), Direction.Axis.Z);
        PortalShape.findEmptyPortalShape(nether, frame.bottomLeft(), frame.axis()).orElseThrow().createPortalBlocks();
        MitePortals.writeRoute(nether, frame, 0, false);
        var state = nether.getBlockState(frame.bottomLeft());
        helper.assertValueEqual(MitePortals.destinationId(nether, state), 1, "A legacy default-state Nether gate already travels to the underworld");
        MitePortals.load(new net.neoforged.neoforge.event.level.ChunkEvent.Load(nether.getChunkAt(frame.bottomLeft()), false));
        helper.runAfterDelay(2, () -> {
            for (int column = 0; column < 2; column++) for (int row = 0; row < 3; row++) {
                helper.assertValueEqual(nether.getBlockState(frame.at(column, row)).getValue(MitePortalProperties.DESTINATION), 1,
                        "Chunk loading synchronizes the legacy gate's purple underworld appearance");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyRuneCornerStatesReconcileAcrossChunkBordersWithoutChangingSavedOrdinaryRoutes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int chunkX = Math.floorDiv(480000 + helper.absolutePos(BlockPos.ZERO).getX(), 16) * 16;
        int chunkZ = Math.floorDiv(helper.absolutePos(BlockPos.ZERO).getZ(), 16) * 16;
        MitePortalFrame addedCorners = frame(level, new BlockPos(chunkX + 14, 70, chunkZ + 8), Direction.Axis.X);
        MitePortalFrame removedCorners = frame(level, new BlockPos(chunkX + 38, 70, chunkZ + 8), Direction.Axis.X);
        MitePortalFrame ordinary = frame(level, new BlockPos(chunkX + 45, 70, chunkZ + 8), Direction.Axis.X);
        for (MitePortalFrame frame : new MitePortalFrame[]{addedCorners, removedCorners, ordinary}) {
            PortalShape.findEmptyPortalShape(level, frame.bottomLeft(), frame.axis()).orElseThrow().createPortalBlocks();
        }
        corners(level, addedCorners, false);
        var cornerChunk = level.getChunkAt(addedCorners.at(2, -1));
        helper.assertValueEqual(cornerChunk.getPos().x, (addedCorners.bottomLeft().getX() >> 4) + 1,
                "The load event belongs to the corner-only chunk east of the portal interior");

        // Let setup-generated chunk-load tasks finish before reproducing states saved by the old implementation.
        helper.runAfterDelay(2, () -> {
            MitePortals.writeRoute(level, addedCorners, 1, false);
            MitePortals.writeRoute(level, removedCorners, 0, true);
            MitePortals.writeRoute(level, ordinary, 1, false);
            helper.assertTrue(addedCorners.rune(level).isPresent(), "The legacy ordinary state already has complete rune corners");
            helper.assertTrue(removedCorners.rune(level).isEmpty(), "The legacy runegate state has already lost its corners");
            MitePortals.load(new net.neoforged.neoforge.event.level.ChunkEvent.Load(cornerChunk, false));
            helper.runAfterDelay(2, () -> {
                for (int column = 0; column < 2; column++) for (int row = 0; row < 3; row++) {
                    var added = level.getBlockState(addedCorners.at(column, row));
                    helper.assertTrue(added.getValue(MitePortalProperties.RUNEGATE),
                            "Loading only the adjacent corner chunk updates every runegate interior block");
                    helper.assertValueEqual(added.getValue(MitePortalProperties.DESTINATION), 0,
                            "Existing rune corners restore the blue overworld runegate route");
                    var removed = level.getBlockState(removedCorners.at(column, row));
                    helper.assertFalse(removed.getValue(MitePortalProperties.RUNEGATE),
                            "Missing rune corners restore the ordinary gate state");
                    helper.assertValueEqual(removed.getValue(MitePortalProperties.DESTINATION), 0,
                            "The former runegate follows the ordinary surface return-home route");
                    var saved = level.getBlockState(ordinary.at(column, row));
                    helper.assertFalse(saved.getValue(MitePortalProperties.RUNEGATE),
                            "A saved ordinary return gate keeps its ordinary state");
                    helper.assertValueEqual(saved.getValue(MitePortalProperties.DESTINATION), 1,
                            "Loading preserves the explicit underworld destination of an ordinary return gate");
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void generatedReturnRouteSurvivesNativeBlockStateNbt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MitePortalFrame frame = frame(level, helper.absolutePos(new BlockPos(1, 2, 1)), Direction.Axis.X);
        PortalShape.findEmptyPortalShape(level, frame.bottomLeft(), frame.axis()).orElseThrow().createPortalBlocks();
        MitePortals.writeRoute(level, frame, 1, false);
        var state = level.getBlockState(frame.bottomLeft());
        var decoded = NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), NbtUtils.writeBlockState(state));
        helper.assertValueEqual(decoded.getValue(MitePortalProperties.DESTINATION), 1, "Reverse destination is stored with the block, independent of natural floor rules");
        helper.assertFalse(decoded.getValue(MitePortalProperties.RUNEGATE), "Native state serialization preserves ordinary/rune status");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeProcessorUsesR196WaitAndCooldownForPlayersAndOrdinaryEntities(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MitePortalFrame frame = frame(level, helper.absolutePos(new BlockPos(1, 2, 1)), Direction.Axis.X);
        PortalShape.findEmptyPortalShape(level, frame.bottomLeft(), frame.axis()).orElseThrow().createPortalBlocks();
        MitePortals.writeRoute(level, frame, 1, false);
        var gate = (NetherPortalBlock)Blocks.NETHER_PORTAL;
        var survival = helper.makeMockPlayer(GameType.SURVIVAL);
        survival.setPos(Vec3.atBottomCenterOf(frame.bottomLeft()));
        survival.portalProcess = new PortalProcessor(gate, frame.bottomLeft());
        for (int tick = 0; tick < 80; tick++) {
            survival.portalProcess.setAsInsidePortalThisTick(true);
            helper.assertFalse(survival.portalProcess.processPortalTeleportation(level, survival, true), "Survival wait has not reached source threshold at " + tick);
        }
        survival.portalProcess.setAsInsidePortalThisTick(true);
        helper.assertTrue(survival.portalProcess.processPortalTeleportation(level, survival, true), "Postincrement eighty threshold uses the same R196 portal loop");
        survival.setPortalCooldown();
        helper.assertValueEqual(survival.getPortalCooldown(), 10, "Player cooldown remains ten ticks");
        Pig pig = EntityType.PIG.create(level);
        pig.portalProcess = new PortalProcessor(gate, frame.bottomLeft());
        helper.assertTrue(pig.portalProcess.processPortalTeleportation(level, pig, true), "Ordinary entities have no transition wait");
        pig.setPortalCooldown();
        helper.assertValueEqual(pig.getPortalCooldown(), 900, "Ordinary entity cooldown uses nine hundred source ticks");
        pig.setPortalCooldown(899);
        pig.setAsInsidePortal(gate, frame.bottomLeft());
        helper.assertValueEqual(pig.getPortalCooldown(), 900, "Touching the exit gate continually refreshes the source cooldown");
        MitePortals.writeRoute(level, frame, 0, false);
        helper.assertValueEqual(gate.getPortalTransitionTime(level, survival), 0, "World-spawn route bypasses the ordinary eighty-tick wait");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spawnAndRuneGatesRejectMobsAndAllGatesRejectRiding(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MitePortalFrame frame = frame(level, helper.absolutePos(new BlockPos(1, 2, 1)), Direction.Axis.X);
        PortalShape.findEmptyPortalShape(level, frame.bottomLeft(), frame.axis()).orElseThrow().createPortalBlocks();
        Pig pig = EntityType.PIG.create(level);
        Pig mount = EntityType.PIG.create(level);
        helper.assertFalse(MitePortals.canEnter(level, frame.bottomLeft(), pig), "Overworld-spawn gates are player-only");
        MitePortals.writeRoute(level, frame, 1, false);
        helper.assertTrue(MitePortals.canEnter(level, frame.bottomLeft(), pig), "Ordinary inter-dimension gates accept unmounted non-player entities");
        pig.startRiding(mount, true);
        helper.assertFalse(MitePortals.canEnter(level, frame.bottomLeft(), pig), "Passenger cannot enter");
        helper.assertFalse(MitePortals.canEnter(level, frame.bottomLeft(), mount), "Entity carrying a passenger cannot enter");
        pig.stopRiding();
        corners(level, frame, false);
        helper.assertFalse(MitePortals.canEnter(level, frame.bottomLeft(), pig), "Rune gates are player-only");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void actualUnderworldAndNetherAndEndActivateTheirSourceRoutes(GameTestHelper helper) {
        ServerLevel server = helper.getLevel();
        ServerLevel underworld = server.getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        ServerLevel nether = server.getServer().getLevel(Level.NETHER);
        ServerLevel end = server.getServer().getLevel(Level.END);
        helper.assertTrue(underworld != null, "The registered underworld dimension loads");
        int x = 200000 + helper.absolutePos(BlockPos.ZERO).getX();
        MitePortalFrame uw = frame(underworld, new BlockPos(x, 20, 0), Direction.Axis.X);
        underworld.setBlockAndUpdate(uw.bottomLeft(), Blocks.FIRE.defaultBlockState());
        helper.assertTrue(uw.complete(underworld), "Actual fire onPlace activates a standard frame in the underworld");
        helper.assertValueEqual(underworld.getBlockState(uw.bottomLeft()).getValue(MitePortalProperties.DESTINATION), 0, "Non-bottom underworld gate returns to the overworld");
        MitePortalFrame low = frame(underworld, new BlockPos(x + 8, underworld.getMinBuildHeight() + 6, 0), Direction.Axis.X);
        underworld.setBlock(low.at(0, -2), MiteWorldBlocks.MANTLE.get().defaultBlockState(), 2);
        PortalShape.findEmptyPortalShape(underworld, low.bottomLeft(), low.axis()).orElseThrow().createPortalBlocks();
        helper.assertValueEqual(underworld.getBlockState(low.bottomLeft()).getValue(MitePortalProperties.DESTINATION), 2, "Bottom mantle contact routes to Nether");
        MitePortalFrame nf = frame(nether, new BlockPos(x, 50, 0), Direction.Axis.X);
        PortalShape.findEmptyPortalShape(nether, nf.bottomLeft(), nf.axis()).orElseThrow().createPortalBlocks();
        helper.assertValueEqual(nether.getBlockState(nf.bottomLeft()).getValue(MitePortalProperties.DESTINATION), 1, "Every ordinary Nether gate returns to underworld");
        MitePortalFrame ef = frame(end, new BlockPos(x, 80, 0), Direction.Axis.X);
        helper.assertTrue(PortalShape.findEmptyPortalShape(end, ef.bottomLeft(), ef.axis()).isEmpty(), "End cannot activate obsidian portals");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyLowOverworldFramesTouchingRealBedrockEnterUnderworld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int x = 100000 + helper.absolutePos(BlockPos.ZERO).getX();
        int floor = level.getMinBuildHeight();
        MitePortalFrame low = frame(level, new BlockPos(x, floor + 2, 0), Direction.Axis.X);
        for (int side = -1; side <= 2; side++) level.setBlock(low.at(side, -2), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(low.at(1, -2), Blocks.BEDROCK.defaultBlockState(), 2);
        PortalShape.findEmptyPortalShape(level, low.bottomLeft(), low.axis()).orElseThrow().createPortalBlocks();
        helper.assertValueEqual(level.getBlockState(low.bottomLeft()).getValue(MitePortalProperties.DESTINATION), 1, "Modern minY-relative floor with one bedrock contact enters underworld");
        MitePortals.writeRoute(level, low, 0, true);
        helper.assertFalse(MitePortals.isInstantGate(level, low.bottomLeft()), "Removing rune corners restores the ordinary low gate's wait before teleportation");
        MitePortals.initializeRoute(level, low);
        MitePortalFrame high = frame(level, new BlockPos(x + 8, floor + 10, 0), Direction.Axis.X);
        level.setBlock(high.at(1, -2), Blocks.BEDROCK.defaultBlockState(), 2);
        PortalShape.findEmptyPortalShape(level, high.bottomLeft(), high.axis()).orElseThrow().createPortalBlocks();
        helper.assertValueEqual(level.getBlockState(high.bottomLeft()).getValue(MitePortalProperties.DESTINATION), 0, "Placed bedrock above the eight-block band does not bypass the floor rule");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void poiSearchSkipsWrongRouteAndRunegatesAndIncludesExtendedStates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(10000000 + helper.absolutePos(BlockPos.ZERO).getX(), 80, 0);
        MitePortalFrame wrong = frame(level, origin, Direction.Axis.X);
        PortalShape.findEmptyPortalShape(level, wrong.bottomLeft(), wrong.axis()).orElseThrow().createPortalBlocks();
        MitePortalFrame rune = frame(level, origin.east(8), Direction.Axis.X);
        PortalShape.findEmptyPortalShape(level, rune.bottomLeft(), rune.axis()).orElseThrow().createPortalBlocks();
        MitePortals.writeRoute(level, rune, 1, true);
        corners(level, rune, false);
        MitePortalFrame good = frame(level, origin.east(16), Direction.Axis.X);
        PortalShape.findEmptyPortalShape(level, good.bottomLeft(), good.axis()).orElseThrow().createPortalBlocks();
        MitePortals.writeRoute(level, good, 1, false);
        helper.runAfterDelay(3, () -> {
            helper.assertValueEqual(MitePortals.findExit(level, origin, 1).orElseThrow().bottomLeft(), good.bottomLeft(),
                    "Search sees the extended native POI states and chooses a reverse-route ordinary portal only");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void safeLandingRejectsLiquidAndProtectedFloorsWithoutCarving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(1, 2, 1));
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) level.setBlock(new BlockPos(base.getX(), y, base.getZ()), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(base.below(), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
        Pig pig = EntityType.PIG.create(level);
        helper.assertTrue(MitePortals.safeLanding(level, pig, base.getX(), base.getZ(), true, 0).isEmpty(), "Magma and an empty void do not provide a safe landing");
        helper.assertTrue(level.getBlockState(base.below()).is(Blocks.MAGMA_BLOCK), "Failure does not clear terrain");
        level.setBlock(base.below(), Blocks.STONE.defaultBlockState(), 2);
        helper.assertValueEqual(MitePortals.safeLanding(level, pig, base.getX(), base.getZ(), true, 0).orElseThrow(), Vec3.atBottomCenterOf(base), "Solid dry two-block space is accepted");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    @SuppressWarnings("removal")
    public static void realServerPlayerUsesNativePortalMixinForRoundTripWithoutInventoryLoss(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel underworld = overworld.getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        int x = 1000000 + helper.absolutePos(BlockPos.ZERO).getX();
        MitePortalFrame entrance = frame(overworld, new BlockPos(x, 80, 0), Direction.Axis.X);
        PortalShape.findEmptyPortalShape(overworld, entrance.bottomLeft(), entrance.axis()).orElseThrow().createPortalBlocks();
        MitePortals.writeRoute(overworld, entrance, 1, false);
        MitePortalFrame exit = frame(underworld, new BlockPos(x, 160, 0), Direction.Axis.X);
        PortalShape.findEmptyPortalShape(underworld, exit.bottomLeft(), exit.axis()).orElseThrow().createPortalBlocks();
        MitePortals.writeRoute(underworld, exit, 0, false);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        player.setPos(x + 0.5, 80, 0.5);
        helper.runAfterDelay(3, () -> {
            try {
                var gate = (NetherPortalBlock)Blocks.NETHER_PORTAL;
                var outbound = gate.getPortalDestination(overworld, player, entrance.bottomLeft());
                helper.assertTrue(outbound != null && outbound.newLevel() == underworld, "The actual vanilla block Mixin chooses underworld");
                player.changeDimension(outbound);
                helper.assertTrue(player.serverLevel() == underworld, "The server player actually changes dimension");
                helper.assertTrue(Math.abs(player.getX() - (x + 0.5)) < 0.01, "Overworld-underworld travel has no Nether scaling");
                var inbound = gate.getPortalDestination(underworld, player, exit.bottomLeft());
                helper.assertTrue(inbound != null && inbound.newLevel() == overworld, "Reverse route finds the original ordinary gate");
                player.changeDimension(inbound);
                helper.assertTrue(player.serverLevel() == overworld, "The same server player returns to overworld");
                helper.assertTrue(player.getInventory().getItem(0).is(Items.DIAMOND) && player.getInventory().getItem(0).getCount() == 3,
                        "Inventory count survives both network dimension transitions");
                helper.succeed();
            } finally {
                overworld.getServer().getPlayerList().remove(player);
            }
        });
    }

    @GameTest(template = "empty", batch = "underworld_entities", timeoutTicks = 1600)
    public static void ordinaryEntityChangesDimensionOnceAndKeepsSavedStateAndCooldown(GameTestHelper helper) {
        ServerLevel source = helper.getLevel();
        ServerLevel target = source.getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        int x = 2000000 + helper.absolutePos(BlockPos.ZERO).getX();
        MitePortalFrame entrance = frame(source, new BlockPos(x, 80, 0), Direction.Axis.X);
        PortalShape.findEmptyPortalShape(source, entrance.bottomLeft(), entrance.axis()).orElseThrow().createPortalBlocks();
        MitePortals.writeRoute(source, entrance, 1, false);
        MitePortalFrame exit = frame(target, new BlockPos(x, 160, 0), Direction.Axis.X);
        PortalShape.findEmptyPortalShape(target, exit.bottomLeft(), exit.axis()).orElseThrow().createPortalBlocks();
        MitePortals.writeRoute(target, exit, 0, false);
        Pig original = EntityType.PIG.create(source);
        original.setPos(x + 0.5, 80, 0.5);
        original.setNoAi(true);
        original.getPersistentData().putString("mite_portal_test", "kept");
        original.setPortalCooldown();
        helper.runAfterDelay(3, () -> {
            Entity transferred = null;
            try {
                source.addFreshEntity(original);
                original.setPos(x + 0.5, 80, 0.5);
                original.setPortalCooldown();
                helper.assertTrue(entrance.complete(source) && exit.complete(target), "Both ordinary entity test frames remain complete");
                helper.assertTrue(MitePortals.canEnter(source, entrance.bottomLeft(), original), "Fresh unmounted alive ordinary entity is eligible");
                helper.assertValueEqual(MitePortals.findExit(target, original.blockPosition(), 0).orElseThrow().bottomLeft(), exit.bottomLeft(),
                        "Reverse portal is visible before ordinary entity transition");
                var transition = ((NetherPortalBlock)Blocks.NETHER_PORTAL).getPortalDestination(source, original, entrance.bottomLeft());
                helper.assertTrue(transition != null && transition.newLevel() == target, "Native portal resolves underworld for ordinary mob; alive="
                        + original.isAlive() + " removed=" + original.isRemoved() + " pos=" + original.position());
                transferred = original.changeDimension(transition);
                helper.assertTrue(transferred instanceof Pig && transferred != original, "Entity.changeDimension constructs exactly the destination entity");
                helper.assertTrue(original.isRemoved(), "The original entity is marked removed by the native transfer");
                helper.assertValueEqual(transferred.getUUID(), original.getUUID(), "Native save/restore retains the source UUID");
                helper.assertValueEqual(transferred.getPersistentData().getString("mite_portal_test"), "kept", "Persistent mob data survives the native save/restore path");
                helper.assertValueEqual(transferred.getPortalCooldown(), 900, "The source nine-hundred-tick cooldown survives transfer");
                Pig loaded = EntityType.PIG.create(target);
                loaded.load(transferred.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
                helper.assertValueEqual(loaded.getPortalCooldown(), 900, "Cooldown survives entity NBT save/reload");
                Entity destinationEntity = transferred;
                // The headless GT loop has no 20TPS sleep. Keep the test-only portal ticket alive
                // while asynchronous neighbouring terrain promotes this initially hidden entity section.
                helper.testInfo.addListener(new GameTestListener() {
                    @Override public void testStructureLoaded(GameTestInfo info) {}
                    @Override public void testAddedForRerun(GameTestInfo originalInfo, GameTestInfo rerun, GameTestRunner runner) {}
                    @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { clean(); }
                    @Override public void testFailed(GameTestInfo info, GameTestRunner runner) {
                        com.mojang.logging.LogUtils.getLogger().error("Ordinary portal entity fixture: {}", entityDiagnostic(target, destinationEntity));
                        clean();
                    }
                    private void clean() {
                        original.discard();
                        destinationEntity.discard();
                    }
                });
                helper.succeedWhen(() -> {
                    destinationEntity.placePortalTicket(destinationEntity.blockPosition());
                    helper.assertTrue(source.getEntity(original.getUUID()) == null, "No original entity remains in the source UUID index");
                    helper.assertTrue(target.getEntity(original.getUUID()) == destinationEntity,
                            "Exactly the transferred entity must become visible; " + entityDiagnostic(target, destinationEntity));
                });
            } catch (RuntimeException | Error failure) {
                original.discard();
                if (transferred != null) transferred.discard();
                throw failure;
            }
        });
    }

    private static String entityDiagnostic(ServerLevel target, Entity entity) {
        var chunk = target.getChunkSource().getChunkNow(entity.blockPosition().getX() >> 4, entity.blockPosition().getZ() >> 4);
        var indexed = target.getEntity(entity.getUUID());
        return "removed=" + entity.isRemoved() + " reason=" + entity.getRemovalReason() + " added=" + entity.isAddedToLevel()
                + " level=" + entity.level().dimension().location() + " pos=" + entity.position()
                + " chunk=" + (chunk == null ? "not-loaded" : chunk.getFullStatus())
                + " lookup=" + (indexed == null ? "null" : indexed == entity ? "same" : indexed.getId())
                + " cooldown=" + entity.getPortalCooldown();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void automaticExitCreationStaysInsideTheRealUnderworldAndStoresReturnRoute(GameTestHelper helper) {
        ServerLevel underworld = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        int x = 3000000 + helper.absolutePos(BlockPos.ZERO).getX();
        Pig traveler = EntityType.PIG.create(helper.getLevel());
        MitePortalFrame created = MitePortals.createExit(underworld, new BlockPos(x, 80, 0), Direction.Axis.X, 0, traveler).orElseThrow();
        helper.assertTrue(created.complete(underworld), "The actual generator and automatic exit creator produce a complete native portal");
        helper.assertTrue(created.bottomLeft().getY() >= 152 && created.bottomLeft().getY() + 3 < 320,
                "The entrance from overworld lands in the dry upper cave layer, below the underworld roof");
        helper.assertValueEqual(underworld.getBlockState(created.bottomLeft()).getValue(MitePortalProperties.DESTINATION), 0,
                "Automatic exit keeps its explicit return-to-overworld state");
        helper.assertValueEqual(MitePortals.findExit(underworld, new BlockPos(x, 80, 0), 0).orElseThrow().bottomLeft(), created.bottomLeft(),
                "A second same-tick traveler immediately reuses the automatic exit through native POI updates");
        helper.assertTrue(underworld.getBlockState(new BlockPos(x, -64, 0)).is(MiteWorldBlocks.MANTLE.get()),
                "Exit creation keeps the mantle floor intact");
        helper.assertTrue(underworld.getBlockState(new BlockPos(x, 319, 0)).is(Blocks.BEDROCK),
                "Exit creation keeps the ceiling bedrock intact");
        helper.succeed();
    }

    private static MitePortalFrame frame(ServerLevel level, BlockPos bottom, Direction.Axis axis) {
        MitePortalFrame frame = new MitePortalFrame(bottom, axis);
        for (int x = -1; x <= 2; x++) for (int y = -1; y <= 3; y++) level.setBlock(frame.at(x, y),
                x == -1 || x == 2 || y == -1 || y == 3 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        return frame;
    }

    private static void corners(ServerLevel level, MitePortalFrame frame, boolean adamantium) {
        var block = adamantium ? MiteWorldBlocks.ADAMANTIUM_RUNESTONE.get() : MiteWorldBlocks.MITHRIL_RUNESTONE.get();
        level.setBlock(frame.at(-1, -1), block.defaultBlockState().setValue(RunestoneBlock.RUNE, 1), 2);
        level.setBlock(frame.at(2, -1), block.defaultBlockState().setValue(RunestoneBlock.RUNE, 2), 2);
        level.setBlock(frame.at(-1, 3), block.defaultBlockState().setValue(RunestoneBlock.RUNE, 3), 2);
        level.setBlock(frame.at(2, 3), block.defaultBlockState().setValue(RunestoneBlock.RUNE, 4), 2);
    }
}

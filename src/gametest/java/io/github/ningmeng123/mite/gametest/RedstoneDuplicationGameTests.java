package io.github.ningmeng123.mite.gametest;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression checks for stale state during vanilla redstone updates. @author NingMeng123 */
@GameTestHolder("mite_gameplay")
@PrefixGameTestTemplate(false)
public final class RedstoneDuplicationGameTests {
    private RedstoneDuplicationGameTests() {}

    @GameTest(template = "empty")
    public static void disarmedTripwireStaysReplacedByWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos wire = placeTripwire(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHEARS));
        Blocks.TRIPWIRE.playerWillDestroy(level, wire, level.getBlockState(wire), player);
        helper.assertTrue(level.getBlockState(wire).getValue(TripWireBlock.DISARMED), "Shears disarm the tripwire");

        level.setBlock(wire, Fluids.FLOWING_WATER.getFlowing(7, true).createLegacyBlock(), Block.UPDATE_ALL);

        helper.assertTrue(level.getBlockState(wire).is(Blocks.WATER), "The hook must leave the replacement water in place");
        for (BlockPos hook : new BlockPos[] {wire.west(), wire.east()}) {
            BlockState state = level.getBlockState(hook);
            helper.assertFalse(state.getValue(TripWireHookBlock.ATTACHED), "Both hooks detach when the wire is gone");
            helper.assertFalse(state.getValue(TripWireHookBlock.POWERED), "Cutting with shears does not power the hooks");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryTripwireStillPowersBothHooks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos wire = placeTripwire(helper);
        var pig = helper.spawn(EntityType.PIG, new Vec3(2.5, 2.0625, 2.5));
        level.getBlockState(wire).entityInside(level, wire, pig);

        helper.assertTrue(level.getBlockState(wire.west()).getValue(TripWireHookBlock.POWERED), "The west hook gives a signal");
        helper.assertTrue(level.getBlockState(wire.east()).getValue(TripWireHookBlock.POWERED), "The east hook gives a signal");
        pig.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tripwireDoesNotRestoreAHookBrokenDuringNeighborUpdates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos wire = placeTripwire(helper);
        BlockPos nearHook = wire.west();
        BlockPos farHook = wire.east();
        BlockState poweredWire = level.getBlockState(wire).setValue(TripWireBlock.POWERED, true);
        level.setBlock(wire, poweredWire, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);

        // A neighbor update from the far hook can break the near hook before its turn to update.
        Consumer<BlockEvent.NeighborNotifyEvent> breakHook = event -> {
            if (event.getLevel() == level && event.getPos().equals(farHook)
                    && event.getState().is(Blocks.TRIPWIRE_HOOK) && event.getState().getValue(TripWireHookBlock.POWERED)) {
                level.destroyBlock(nearHook, true);
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.NeighborNotifyEvent.class, breakHook);
        try {
            TripWireHookBlock.calculateState(level, nearHook, level.getBlockState(nearHook), false, true, 1, poweredWire);
        } finally {
            NeoForge.EVENT_BUS.unregister(breakHook);
        }

        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(nearHook).inflate(0.5),
                item -> item.getItem().is(Items.TRIPWIRE_HOOK));
        helper.assertValueEqual(drops.stream().mapToInt(item -> item.getItem().getCount()).sum(), 1,
                "The broken hook drops exactly one item");
        drops.forEach(ItemEntity::discard);
        helper.assertTrue(level.getBlockState(nearHook).isAir(), "The broken hook must stay removed");
        helper.assertFalse(level.getBlockState(farHook).getValue(TripWireHookBlock.ATTACHED), "The surviving hook detaches");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pistonDoesNotCarryTntThatIgnitesDuringMovement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos piston = placePistonWithTnt(helper);
        BlockPos source = piston.east();
        BlockPos destination = piston.east(2);
        level.setBlockAndUpdate(destination.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(destination, Blocks.TORCH.defaultBlockState());

        // Piston break drops occur after the push list is captured and before movement starts.
        Consumer<BlockDropsEvent> igniteDuringMove = event -> {
            if (event.getLevel() == level && event.getPos().equals(destination) && event.getState().is(Blocks.TORCH)) {
                BlockPos power = source.north();
                level.setBlock(power, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE);
                level.neighborChanged(source, Blocks.REDSTONE_BLOCK, power);
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockDropsEvent.class, igniteDuringMove);
        try {
            extendPiston(helper, piston);
        } finally {
            NeoForge.EVENT_BUS.unregister(igniteDuringMove);
        }

        PrimedTnt primed = onlyPrimedTnt(helper, source);
        helper.assertTrue(level.getBlockState(destination).isAir() && level.getBlockEntity(destination) == null,
                "An already consumed TNT block leaves no moving placeholder");
        helper.runAfterDelay(4, () -> {
            primed.discard();
            helper.assertTrue(level.getBlockState(destination).isAir(), "The consumed TNT must not reappear at the piston destination");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void pistonMovesUnlitTntAndRedstoneStillIgnitesIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos piston = placePistonWithTnt(helper);
        BlockPos destination = piston.east(2);
        extendPiston(helper, piston);

        helper.runAfterDelay(4, () -> {
            helper.assertTrue(level.getBlockState(destination).is(Blocks.TNT), "An unlit TNT block moves to its destination");
            level.setBlockAndUpdate(destination.north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.assertTrue(level.getBlockState(destination).isAir(), "Normal redstone ignition consumes the TNT block");
            onlyPrimedTnt(helper, destination).discard();
            helper.succeed();
        });
    }

    private static BlockPos placeTripwire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos wire = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(wire.west(2), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(wire.east(2), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(wire.west(), Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, Direction.EAST));
        level.setBlockAndUpdate(wire.east(), Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, Direction.WEST));
        level.setBlockAndUpdate(wire, Blocks.TRIPWIRE.defaultBlockState());
        helper.assertTrue(level.getBlockState(wire).getValue(TripWireBlock.ATTACHED), "The wire starts attached to both hooks");
        return wire;
    }

    private static BlockPos placePistonWithTnt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos piston = helper.absolutePos(new BlockPos(1, 2, 2));
        level.setBlockAndUpdate(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        level.setBlockAndUpdate(piston.east(), Blocks.TNT.defaultBlockState());
        return piston;
    }

    private static void extendPiston(GameTestHelper helper, BlockPos piston) {
        ServerLevel level = helper.getLevel();
        level.setBlock(piston.south(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE);
        helper.assertTrue(level.getBlockState(piston).triggerEvent(level, piston, 0, Direction.EAST.get3DDataValue()),
                "The piston starts extending");
    }

    private static PrimedTnt onlyPrimedTnt(GameTestHelper helper, BlockPos pos) {
        var primed = helper.getLevel().getEntitiesOfClass(PrimedTnt.class, new AABB(pos).inflate(1));
        helper.assertValueEqual(primed.size(), 1, "One block produces exactly one primed TNT entity");
        return primed.getFirst();
    }
}

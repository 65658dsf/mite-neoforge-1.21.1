package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteDecorativeContent;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real survival item interactions include NeoForge's deferred placement snapshots. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class MaterialDoorPlacementGameTests {
    private static final BlockPos SITE = new BlockPos(4, 2, 4);
    private MaterialDoorPlacementGameTests() {}

    @GameTest(template = "ranged_027") public static void copperDoorItemPlacesAndOpensByHand(GameTestHelper h) { allDirections(h, Blocks.COPPER_DOOR); }
    @GameTest(template = "ranged_027") public static void exposedCopperDoorItemPlacesAndOpensByHand(GameTestHelper h) { allDirections(h, Blocks.EXPOSED_COPPER_DOOR); }
    @GameTest(template = "ranged_027") public static void weatheredCopperDoorItemPlacesAndOpensByHand(GameTestHelper h) { allDirections(h, Blocks.WEATHERED_COPPER_DOOR); }
    @GameTest(template = "ranged_027") public static void oxidizedCopperDoorItemPlacesAndOpensByHand(GameTestHelper h) { allDirections(h, Blocks.OXIDIZED_COPPER_DOOR); }
    @GameTest(template = "ranged_027") public static void waxedCopperDoorItemPlacesAndOpensByHand(GameTestHelper h) { allDirections(h, Blocks.WAXED_COPPER_DOOR); }
    @GameTest(template = "ranged_027") public static void waxedExposedCopperDoorItemPlacesAndOpensByHand(GameTestHelper h) { allDirections(h, Blocks.WAXED_EXPOSED_COPPER_DOOR); }
    @GameTest(template = "ranged_027") public static void waxedWeatheredCopperDoorItemPlacesAndOpensByHand(GameTestHelper h) { allDirections(h, Blocks.WAXED_WEATHERED_COPPER_DOOR); }
    @GameTest(template = "ranged_027") public static void waxedOxidizedCopperDoorItemPlacesAndOpensByHand(GameTestHelper h) { allDirections(h, Blocks.WAXED_OXIDIZED_COPPER_DOOR); }
    @GameTest(template = "ranged_027") public static void silverDoorItemPlacesAndUsesRedstone(GameTestHelper h) { allDirections(h, sourceDoor("silver")); }
    @GameTest(template = "ranged_027") public static void goldenDoorItemPlacesAndUsesRedstone(GameTestHelper h) { allDirections(h, sourceDoor("gold")); }
    @GameTest(template = "ranged_027") public static void ancientMetalDoorItemPlacesAndUsesRedstone(GameTestHelper h) { allDirections(h, sourceDoor("ancient_metal")); }
    @GameTest(template = "ranged_027") public static void mithrilDoorItemPlacesAndUsesRedstone(GameTestHelper h) { allDirections(h, sourceDoor("mithril")); }
    @GameTest(template = "ranged_027") public static void adamantiumDoorItemPlacesAndUsesRedstone(GameTestHelper h) { allDirections(h, sourceDoor("adamantium")); }

    private static Block sourceDoor(String material) { return MiteDecorativeContent.DOORS.get(material).get(); }

    private static void allDirections(GameTestHelper h, Block door) {
        Player player = player(h);
        boolean copper = MiteDecorativeContent.isCopperDoor(door);
        h.assertValueEqual(((DoorBlock) door).type(), copper ? BlockSetType.COPPER : BlockSetType.IRON,
                "Copper keeps its native pathfinding identity while source metal doors retain iron identity");
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Direction side : new Direction[]{facing.getCounterClockWise(), facing.getClockWise()}) {
                BlockPos pos = prepare(h, facing, side);
                player.setYRot(facing.toYRot());
                ItemStack held = new ItemStack(door);
                player.setItemInHand(InteractionHand.MAIN_HAND, held);
                var result = held.useOn(context(player, pos));
                h.assertTrue(result.consumesAction(), "A framed survival door item places: " + door + "/" + facing + "/" + side);
                assertHalves(h, door, pos, false);
                h.assertTrue(held.isEmpty(), "Accepted placement consumes exactly one door");
                h.assertTrue(!h.getLevel().captureBlockSnapshots && h.getLevel().capturedBlockSnapshots.isEmpty(), "Placement finishes its NeoForge snapshot transaction");
                var lower = h.getLevel().getBlockState(pos);
                h.assertValueEqual(lower.getValue(DoorBlock.FACING), facing, "Facing survives the item placement transaction");
                lower.useWithoutItem(h.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(pos), facing, pos, false));
                assertHalves(h, door, pos, copper);
                h.getLevel().getBlockState(pos.above()).useWithoutItem(h.getLevel(), player,
                        new BlockHitResult(Vec3.atCenterOf(pos.above()), facing, pos.above(), false));
                assertHalves(h, door, pos, false);
                triggerWind(h, pos);
                assertHalves(h, door, pos, copper);
                if (copper) {
                    triggerWind(h, pos);
                    assertHalves(h, door, pos, false);
                }
                BlockPos power = pos.relative(facing);
                h.getLevel().setBlockAndUpdate(power, Blocks.REDSTONE_BLOCK.defaultBlockState());
                assertHalves(h, door, pos, true);
                triggerWind(h, pos);
                assertHalves(h, door, pos, true);
                h.getLevel().removeBlock(power, false);
                assertHalves(h, door, pos, false);
                h.getLevel().removeBlock(pos.relative(side).above(), false);
                h.assertTrue(h.getLevel().getBlockState(pos).isAir() && h.getLevel().getBlockState(pos.above()).isAir(), "Losing the upper hinge support removes both halves");
            }
        }
        h.succeed();
    }

    private static void triggerWind(GameTestHelper h, BlockPos pos) {
        Explosion explosion = new Explosion(h.getLevel(), null, pos.getX() + .5D, pos.getY() + .5D,
                pos.getZ() + .5D, 1F, false, Explosion.BlockInteraction.TRIGGER_BLOCK);
        h.getLevel().getBlockState(pos).onExplosionHit(h.getLevel(), pos, explosion, (stack, at) -> {
            h.assertTrue(stack.isEmpty(), "A wind-charge interaction must not drop or destroy a door");
        });
    }

    @GameTest(template = "ranged_027")
    public static void rejectedAndCancelledMetalDoorPlacementKeepsCarriedItem(GameTestHelper h) {
        Player player = player(h);
        BlockPos pos = prepare(h, Direction.NORTH, Direction.WEST);
        h.getLevel().removeBlock(pos.west(), false);
        h.getLevel().removeBlock(pos.west().above(), false);
        ItemStack held = new ItemStack(Blocks.COPPER_DOOR);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        h.assertTrue(!held.useOn(context(player, pos)).consumesAction(), "R196 metal doors still reject a missing hinge frame");
        h.assertValueEqual(held.getCount(), 1, "Rejected frame consumes no item");
        h.assertTrue(h.getLevel().getBlockState(pos).isAir() && h.getLevel().getBlockState(pos.above()).isAir(), "Rejected placement leaves neither half");
        prepare(h, Direction.NORTH, Direction.WEST);
        Consumer<BlockEvent.EntityMultiPlaceEvent> cancel = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, cancel);
        try {
            h.assertTrue(!held.useOn(context(player, pos)).consumesAction(), "NeoForge can cancel the real two-half placement");
            h.assertValueEqual(held.getCount(), 1, "Cancelled placement restores the carried door");
            h.assertTrue(h.getLevel().getBlockState(pos).isAir() && h.getLevel().getBlockState(pos.above()).isAir(), "Cancelled placement restores both snapshots");
        } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void modernWoodDoorStillPlacesWithoutMetalFrameAndOpensByHand(GameTestHelper h) {
        Player player = player(h);
        BlockPos pos = prepare(h, Direction.NORTH, Direction.WEST);
        h.getLevel().removeBlock(pos.west(), false);
        h.getLevel().removeBlock(pos.west().above(), false);
        player.setYRot(Direction.NORTH.toYRot());
        ItemStack held = new ItemStack(Blocks.OAK_DOOR);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        h.assertTrue(held.useOn(context(player, pos)).consumesAction(), "Modern wood door placement does not inherit source metal framing restrictions");
        assertHalves(h, Blocks.OAK_DOOR, pos, false);
        h.getLevel().getBlockState(pos).useWithoutItem(h.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        assertHalves(h, Blocks.OAK_DOOR, pos, true);
        h.succeed();
    }

    private static Player player(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(h.absolutePos(SITE)).add(0D, 0D, 3D));
        player.getFoodData().setFoodLevel(6);
        return player;
    }

    private static BlockPos prepare(GameTestHelper h, Direction facing, Direction side) {
        BlockPos pos = h.absolutePos(SITE);
        for (int y = 0; y < 3; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            h.getLevel().setBlock(pos.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.relative(side), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.relative(side).above(), Blocks.STONE.defaultBlockState());
        return pos;
    }

    private static void assertHalves(GameTestHelper h, Block door, BlockPos pos, boolean open) {
        var lower = h.getLevel().getBlockState(pos);
        var upper = h.getLevel().getBlockState(pos.above());
        h.assertTrue(lower.is(door) && upper.is(door), "Both placed door halves must survive: " + door);
        h.assertValueEqual(lower.getValue(DoorBlock.HALF), DoubleBlockHalf.LOWER, "Lower half identity");
        h.assertValueEqual(upper.getValue(DoorBlock.HALF), DoubleBlockHalf.UPPER, "Upper half identity");
        h.assertValueEqual(lower.getValue(DoorBlock.OPEN), open, "Lower opening follows the door interaction");
        h.assertValueEqual(upper.getValue(DoorBlock.OPEN), open, "Upper opening follows the door interaction");
        h.assertTrue(lower.canSurvive(h.getLevel(), pos) && upper.canSurvive(h.getLevel(), pos.above()), "Both halves retain legal floor and frame support");
    }

    private static UseOnContext context(Player player, BlockPos pos) {
        BlockPos base = pos.below();
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(base).add(0D, .5D, 0D), Direction.UP, base, false));
    }
}

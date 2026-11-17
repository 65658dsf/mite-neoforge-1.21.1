package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.crafting.RemovedCrafter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Retirement covers acquisition, native item placement and saved non-ticking chunks. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class RemovedCrafterGameTests {
    @GameTest(template = "empty")
    public static void retiredCrafterHasNoRecipeOrCreativeEntries(GameTestHelper helper) {
        helper.assertFalse(helper.getLevel().getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("crafter")).isPresent(),
                "The automatic crafter recipe is disabled in the loaded recipe manager");
        helper.assertTrue(Block.getDrops(Blocks.CRAFTER.defaultBlockState(), helper.getLevel(),
                        helper.absolutePos(new BlockPos(2, 2, 2)), null).isEmpty(),
                "Breaking a retired block cannot recreate a crafter item");
        for (boolean permissions : new boolean[]{false, true}) {
            CreativeModeTabs.tryRebuildTabContents(helper.getLevel().enabledFeatures(), permissions, helper.getLevel().registryAccess());
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                helper.assertFalse(tab.getDisplayItems().stream().anyMatch(stack -> stack.is(Items.CRAFTER)),
                        "Creative category entries omit the retired crafter");
                helper.assertFalse(tab.getSearchTabDisplayItems().stream().anyMatch(stack -> stack.is(Items.CRAFTER)),
                        "Creative search entries omit the retired crafter");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oldCrafterItemsCannotBePlacedThroughNativeItemUse(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
        ItemStack stack = new ItemStack(Items.CRAFTER);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(floor.getCenter().add(0, .5D, 0), Direction.UP, floor, false);
        var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, floor, hit);
        NeoForge.EVENT_BUS.post(event);
        helper.assertTrue(event.isCanceled(), "Normal right-click placement is denied");
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertFalse(helper.getLevel().getBlockState(floor.above()).is(Blocks.CRAFTER),
                "The native placement transaction also rejects direct item use");
        helper.assertValueEqual(stack.getCount(), 1, "Rejected placement preserves the old held item");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void savedNonTickingCrafterIsRemovedWhenItsChunkLoads(GameTestHelper helper) {
        var level = helper.getLevel();
        var chunk = level.getChunk(85000, 85100);
        BlockPos position = new BlockPos(chunk.getPos().getMiddleBlockX(), 80, chunk.getPos().getMiddleBlockZ());
        helper.assertFalse(level.isPositionEntityTicking(position), "The saved-world fixture has no ticking ticket");
        level.setBlockAndUpdate(position, Blocks.CRAFTER.defaultBlockState());
        RemovedCrafter.load(new ChunkEvent.Load(chunk, false));
        helper.succeedWhen(() -> helper.assertTrue(level.getBlockState(position).isAir(),
                "Chunk-load migration removes old machines even outside simulation distance"));
    }
}

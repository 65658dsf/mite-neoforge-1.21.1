package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.mining.ToolHarvestRules;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual mining progress and server removal eligibility, rather than only drop flags. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class SourceMiningGameTests {
    @GameTest(template = "empty")
    public static void iceAndSnowAllowHandBreakingWhileSnowKeepsItsDropToolRequirement(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(true);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        List<BlockState> states = new ArrayList<>(List.of(
                Blocks.ICE.defaultBlockState(),
                Blocks.FROSTED_ICE.defaultBlockState(),
                Blocks.PACKED_ICE.defaultBlockState(),
                Blocks.BLUE_ICE.defaultBlockState(),
                Blocks.SNOW_BLOCK.defaultBlockState(),
                Blocks.POWDER_SNOW.defaultBlockState()));
        for (int layers = 1; layers <= 8; layers++) {
            states.add(Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers));
        }

        for (ItemStack held : List.of(ItemStack.EMPTY, new ItemStack(Items.STICK))) {
            player.setItemSlot(EquipmentSlot.MAINHAND, held);
            for (BlockState state : states) {
                helper.getLevel().setBlock(pos, state, 2);
                helper.assertFalse(ToolHarvestRules.isBlocked(player, state),
                        "Hands and ordinary items can break ice and snow: " + state);
                helper.assertTrue(state.getDestroyProgress(player, helper.getLevel(), pos) > 0F,
                        "Ice and snow allow actual mining progress: " + state);
                var event = new BlockEvent.BreakEvent(helper.getLevel(), pos, state, player);
                ToolHarvestRules.harvest(event);
                helper.assertFalse(event.isCanceled(), "The final ice or snow break is accepted: " + state);
            }
        }

        for (BlockState state : states) {
            if (!state.is(Blocks.SNOW) && !state.is(Blocks.SNOW_BLOCK)) continue;
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            helper.assertFalse(player.hasCorrectToolForDrops(state),
                    "Bare hands retain the native snow drop requirement: " + state);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SHOVEL));
            helper.assertTrue(player.hasCorrectToolForDrops(state),
                    "A shovel retains native snow drop eligibility: " + state);
        }
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void bareHandsAndUnrelatedItemsCannotRemoveRequiredMaterialsAtAnyLevel(GameTestHelper h) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setOnGround(true);p.experienceLevel=500;
        BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
        for(var item:new net.minecraft.world.item.Item[]{Items.AIR,Items.STICK,Items.STONE_SHOVEL}) {
            p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(item));
            for(var block:new net.minecraft.world.level.block.Block[]{Blocks.STONE,Blocks.COBBLESTONE,Blocks.NETHERRACK,Blocks.IRON_BARS}) {
                var state=block.defaultBlockState();h.getLevel().setBlock(pos,state,2);
                h.assertTrue(ToolHarvestRules.isBlocked(p,state),"An unrelated item cannot destroy the required material: "+block);
                h.assertValueEqual(state.getDestroyProgress(p,h.getLevel(),pos),0F,"Both mining sides predict zero progress");
                var event=new BlockEvent.BreakEvent(h.getLevel(),pos,state,p);ToolHarvestRules.harvest(event);
                h.assertTrue(event.isCanceled(),"Final server removal cannot bypass a zero-progress tool gate");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void requiredGlassAndSandstoneAcceptOnlyAnEffectiveSufficientSourceTool(GameTestHelper h) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setOnGround(true);BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.GLASS,Blocks.RED_STAINED_GLASS,Blocks.TINTED_GLASS,Blocks.SANDSTONE}) {
            var state=block.defaultBlockState();p.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            h.assertTrue(ToolHarvestRules.isBlocked(p,state),"Whole glass and sandstone need a tool even without vanilla drop flags");
            p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mite:flint_axe"))));
            h.assertTrue(!ToolHarvestRules.isBlocked(p,state) && state.getDestroyProgress(p,h.getLevel(),pos)>0F,"Flint axe satisfies source level one and material effectiveness");
        }
        p.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        h.assertTrue(!ToolHarvestRules.isBlocked(p,Blocks.GLASS_PANE.defaultBlockState()),"Thin glass retains its source zero minimum");
        h.assertTrue(!ToolHarvestRules.isBlocked(p,Blocks.DIRT.defaultBlockState()),"Soil remains hand-breakable");
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.PISTON,Blocks.STONE_BUTTON,Blocks.RAIL,Blocks.COAL_BLOCK,Blocks.GLOWSTONE})
            h.assertTrue(!ToolHarvestRules.isBlocked(p,block.defaultBlockState()) && p.hasCorrectToolForDrops(block.defaultBlockState()),
                    "A modern pickaxe tag cannot invent a source tool requirement for "+block);
        p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(!ToolHarvestRules.isBlocked(p,Blocks.STONE.defaultBlockState()),"Modern tools retain explicit native Tool compatibility");h.succeed();
    }
}

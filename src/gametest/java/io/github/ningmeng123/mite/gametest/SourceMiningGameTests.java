package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.mining.ToolHarvestRules;
import io.github.ningmeng123.mite.registry.MiteTools;
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
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual mining progress and server removal eligibility, rather than only drop flags. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class SourceMiningGameTests {
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

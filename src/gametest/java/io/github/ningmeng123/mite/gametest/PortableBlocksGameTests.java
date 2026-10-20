package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.block.entity.StrongboxBlockEntity;
import io.github.ningmeng123.mite.mining.FunctionalBlockMining;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Portable inheritance, UUID ownership, actual native mining progress and tool callback. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class PortableBlocksGameTests {
    @GameTest(template="empty")
    public static void portableFamiliesHandleWithoutToolsButDoNotIncludeEveryBlockEntity(GameTestHelper h) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setOnGround(true);p.experienceLevel=0;
        var pos=h.absolutePos(new BlockPos(2,2,2));
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.RED_BED,Blocks.LADDER,Blocks.CAULDRON,Blocks.CHEST,Blocks.FURNACE,
                Blocks.HOPPER,Blocks.DISPENSER,Blocks.BREWING_STAND,Blocks.ENCHANTING_TABLE,Blocks.DAYLIGHT_DETECTOR,Blocks.JUKEBOX}) {
            var s=block.defaultBlockState();h.getLevel().setBlock(pos,s,2);
            h.assertTrue(FunctionalBlockMining.portable(p,s,h.getLevel(),pos),"Source portable identity: "+block);
            h.assertTrue(p.hasCorrectToolForDrops(s),"Portability grants hand harvesting independent of native tool tag");
            h.assertValueEqual(s.getDestroyProgress(p,h.getLevel(),pos),1F/128F,"Zero-level positive-hardness handling takes128 ticks");
        }
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.SPAWNER,Blocks.TRIAL_SPAWNER,Blocks.VAULT,Blocks.SHULKER_BOX,Blocks.BARREL})
            h.assertTrue(!FunctionalBlockMining.portableWithoutOwner(block.defaultBlockState()),"BlockEntity existence does not imply source portability");
        h.assertTrue(FunctionalBlockMining.portableWithoutOwner(Blocks.TNT.defaultBlockState()),"Zero-hardness TNT is portable too");h.succeed();
    }
    @GameTest(template="empty")
    public static void portableHandlingIgnoresHeldEfficiencyAndSparesModernToolDurability(GameTestHelper h) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setOnGround(true);var pos=h.absolutePos(new BlockPos(2,2,2));var s=Blocks.HOPPER.defaultBlockState();
        h.getLevel().setBlock(pos,s,2);p.experienceLevel=50;
        float empty=s.getDestroyProgress(p,h.getLevel(),pos);
        var tool=new ItemStack(Items.STONE_PICKAXE);p.setItemSlot(EquipmentSlot.MAINHAND,tool);
        h.assertValueEqual(s.getDestroyProgress(p,h.getLevel(),pos),empty,"Held efficiency cannot accelerate portable handling");
        h.assertValueEqual(empty,1F/64F,"Level fifty doubles source handling speed exactly once");
        int before=tool.getDamageValue();tool.mineBlock(h.getLevel(),s,pos,p);
        h.assertValueEqual(tool.getDamageValue(),before,"Even a modern non-source tool receives no portable wear");
        p.setOnGround(false);p.experienceLevel=-40;
        h.assertValueEqual(s.getDestroyProgress(p,h.getLevel(),pos),1F/128F,"Air and negative level cannot go below portable minimum");h.succeed();
    }
    @GameTest(template="empty")
    public static void onlyTheRecordedStrongboxOwnerReceivesPortableExemption(GameTestHelper h) {
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var other=h.makeMockPlayer(GameType.SURVIVAL);
        var pos=h.absolutePos(new BlockPos(2,2,2));var s=MiteFunctionalBlocks.STRONGBOXES.get("mithril").get().defaultBlockState();h.getLevel().setBlock(pos,s,2);
        var entity=(StrongboxBlockEntity)h.getLevel().getBlockEntity(pos);entity.setOwner(owner);
        h.assertTrue(FunctionalBlockMining.portable(owner,s,h.getLevel(),pos),"Recorded owner handles their box without a tool");
        h.assertTrue(!FunctionalBlockMining.portable(other,s,h.getLevel(),pos),"Another player cannot inherit owner portability");
        h.assertTrue(FunctionalBlockMining.blocked(other,s,pos),"Non-owner still needs the exact strongbox tool tier");
        h.assertTrue(!FunctionalBlockMining.portableWithoutOwner(s),"Generic chest inheritance never bypasses box ownership");h.succeed();
    }
}

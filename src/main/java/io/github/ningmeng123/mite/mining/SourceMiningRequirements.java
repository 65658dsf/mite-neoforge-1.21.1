package io.github.ningmeng123.mite.mining;

import io.github.ningmeng123.mite.registry.MiteDecorativeContent;
import io.github.ningmeng123.mite.registry.MiteFurnaces;
import io.github.ningmeng123.mite.registry.MiteMetalBlocks;
import io.github.ningmeng123.mite.rules.OreBlockRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** akc.requiresTool and aqz minimum harvest levels, with explicit modern material families. @author NingMeng123 */
public final class SourceMiningRequirements {
    private SourceMiningRequirements() {}
    public static int minimumHarvestLevel(BlockState state) {
        if (sourceHandBreakable(state)) return 0;
        if (OreBlockRules.isManaged(state)) return OreBlockRules.minimumHarvestLevel(state);
        if (state.getBlock() instanceof net.minecraft.world.level.block.AnvilBlock) return 0;
        if (state.getBlock() instanceof io.github.ningmeng123.mite.block.StrongboxBlock box) return FunctionalBlockMining.requiredHarvest(box);
        if (state.getBlock() instanceof io.github.ningmeng123.mite.block.RunestoneBlock) return 3;
        if (state.is(MiteFurnaces.CLAY_OVEN.get()) || state.is(MiteFurnaces.LARGE_CLAY_OVEN.get()) || state.is(MiteFurnaces.SANDSTONE_FURNACE.get())) return 0;
        if (state.is(MiteFurnaces.OBSIDIAN_FURNACE.get())) return 3;
        if (state.is(Blocks.IRON_DOOR) || state.is(Blocks.IRON_BARS) || state.is(Blocks.GOLD_BLOCK)) return 3;
        if (state.is(Blocks.IRON_BLOCK) || state.is(Blocks.EMERALD_BLOCK)) return 4;
        if (state.is(Blocks.DIAMOND_BLOCK)) return 5;
        int decorative = MiteDecorativeContent.requiredHarvest(state.getBlock());
        if (decorative > 0) return decorative;
        String storage = MiteMetalBlocks.material(state);
        if (storage != null) return MiteMetalBlocks.requiredHarvest(storage);
        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)) return 3;
        if (state.is(BlockTags.LOGS) || state.getBlock() instanceof net.minecraft.world.level.block.TransparentBlock
                || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) return 1;
        if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("sandstone") || state.is(BlockTags.TERRACOTTA)) return 1;
        if (state.is(Blocks.CLAY) || state.is(Blocks.GLASS_PANE) || state.getBlock() instanceof net.minecraft.world.level.block.StainedGlassPaneBlock
                || state.is(Blocks.PUMPKIN) || state.is(Blocks.CARVED_PUMPKIN)
                || state.is(Blocks.JACK_O_LANTERN) || state.is(Blocks.MELON) || state.is(BlockTags.WOOL) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.SNOW) || state.is(Blocks.SPONGE) || state.is(Blocks.WET_SPONGE)) return 0;
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL) || state.is(BlockTags.NEEDS_IRON_TOOL)) return 3;
        if (state.is(BlockTags.NEEDS_STONE_TOOL) || state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return 2;
        return 0;
    }
    public static boolean requiresTool(BlockState state) {
        // Ice and snow allow hand destruction independently of tool requirements for drops.
        if (state.is(BlockTags.ICE) || state.is(BlockTags.SNOW) || sourceHandBreakable(state)) return false;
        return minimumHarvestLevel(state) > 0 || state.requiresCorrectToolForDrops();
    }
    /** These R196 materials have neither requires_tool nor a positive block minimum, despite modern pickaxe tags. */
    public static boolean sourceHandBreakable(BlockState state) {
        var block=state.getBlock();
        return block instanceof net.minecraft.world.level.block.BaseRailBlock
                || block instanceof net.minecraft.world.level.block.ButtonBlock
                || block instanceof net.minecraft.world.level.block.LeverBlock
                || block instanceof net.minecraft.world.level.block.FlowerPotBlock
                || block instanceof net.minecraft.world.level.block.DiodeBlock
                || block instanceof net.minecraft.world.level.block.piston.PistonBaseBlock
                || block instanceof net.minecraft.world.level.block.piston.PistonHeadBlock
                || block instanceof net.minecraft.world.level.block.InfestedBlock
                || state.is(Blocks.COAL_BLOCK) || state.is(Blocks.GLOWSTONE) || state.is(Blocks.REDSTONE_LAMP);
    }
}

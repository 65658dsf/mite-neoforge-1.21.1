package io.github.ningmeng123.mite.progression;

import io.github.ningmeng123.mite.block.StrongboxBlock;
import io.github.ningmeng123.mite.block.RunestoneBlock;
import io.github.ningmeng123.mite.mining.SourceMiningRequirements;
import io.github.ningmeng123.mite.registry.MiteDecorativeContent;
import io.github.ningmeng123.mite.registry.MiteMetalBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/** Raw akc material levels used by tool XP; ore/tool harvest requirements are a different lookup. @author NingMeng123 */
public final class SourceBlockMaterialExperience {
    private SourceBlockMaterialExperience() {}

    public static int minimumHarvestLevel(BlockState state) {
        if (state.getBlock() instanceof AnvilBlock || state.is(BlockTags.TERRACOTTA)
                || state.is(Blocks.CLAY) || state.is(Blocks.COAL_BLOCK)
                || SourceMiningRequirements.sourceHandBreakable(state)) return 0;
        if (state.is(Tags.Blocks.ORES)) return 2; // The old ore blocks have stone material, even adamantium/diamond ore.
        if (state.getBlock() instanceof RunestoneBlock) return 3; // R196 runestones inherit obsidian, regardless of rune metal.
        if (state.getBlock() instanceof StrongboxBlock box) return GearModifierRules.materialHarvestLevel(box.material());
        String metal = MiteMetalBlocks.material(state);
        if (metal == null) metal = MiteDecorativeContent.material(state.getBlock());
        if (metal != null) return GearModifierRules.materialHarvestLevel(metal);
        if (state.is(Blocks.IRON_BLOCK) || state.is(Blocks.IRON_BARS) || state.is(Blocks.IRON_DOOR)
                || state.is(Blocks.IRON_TRAPDOOR) || state.is(Blocks.CHAIN)) return 3;
        if (state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE)) return 2;
        if (state.is(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE)) return 3;
        if (state.is(Blocks.EMERALD_BLOCK)) return 3;
        if (state.is(Blocks.DIAMOND_BLOCK)) return 4;
        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)) return 3;
        if (state.is(Blocks.REDSTONE_BLOCK)) return 2;
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (name.contains("copper") || name.equals("raw_iron_block") || name.equals("raw_gold_block"))
            return name.contains("iron") ? 3 : 2;
        // Modern pickaxe rock families follow stone; wood, plants, glass, earth and clay retain material level zero.
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? 2 : 0;
    }
}

package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteTags;
import io.github.ningmeng123.mite.registry.MiteResourceBlocks;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.registry.MiteOreBlocks;
import io.github.ningmeng123.mite.registry.MiteFurnaces;
import net.minecraft.tags.BlockTags;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Fixed vanilla membership; other mods opt in through the MITE tag. @author NingMeng123 */
public final class MiteBlockTagsProvider extends BlockTagsProvider {
    public MiteBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
            ExistingFileHelper existingFiles) {
        super(output, lookup, Mite.MOD_ID, existingFiles);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookup) {
        var decorative = io.github.ningmeng123.mite.registry.MiteDecorativeContent.DOORS;
        decorative.values().forEach(block -> {
            tag(BlockTags.DOORS).add(block.get());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block.get());
            tag(BlockTags.NEEDS_STONE_TOOL).add(block.get());
        });
        io.github.ningmeng123.mite.registry.MiteDecorativeContent.BARS.values().forEach(block -> {
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block.get());
            tag(BlockTags.NEEDS_STONE_TOOL).add(block.get());
        });
        for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            if (!io.github.ningmeng123.mite.registry.MiteDecorativeContent.isCopperDoor(block)) continue;
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block);
            tag(BlockTags.NEEDS_STONE_TOOL).add(block);
            tag(BlockTags.MOB_INTERACTABLE_DOORS).remove(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block));
        }
        tag(BlockTags.SMALL_FLOWERS).add(io.github.ningmeng123.mite.registry.MiteDecorativeContent.DAHLIA.get());
        tag(BlockTags.FLOWER_POTS).add(io.github.ningmeng123.mite.registry.MiteDecorativeContent.POTTED_DAHLIA.get());
        io.github.ningmeng123.mite.registry.MiteMetalBlocks.STORAGE.forEach((material, block) -> {
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block.get());
            if (material.equals("silver")) tag(BlockTags.NEEDS_IRON_TOOL).add(block.get());
            else tag(BlockTags.NEEDS_DIAMOND_TOOL).add(block.get());
        });
        tag(BlockTags.NEEDS_IRON_TOOL).add(Blocks.COPPER_BLOCK);
        io.github.ningmeng123.mite.registry.MiteFunctionalBlocks.ANVILS.values().forEach(block -> {
            tag(BlockTags.ANVIL).add(block.get());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block.get());
        });
        io.github.ningmeng123.mite.registry.MiteFunctionalBlocks.STRONGBOXES.forEach((material, block) -> {
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block.get());
            if (material.equals("mithril") || material.equals("adamantium")) tag(BlockTags.NEEDS_DIAMOND_TOOL).add(block.get());
            else tag(BlockTags.NEEDS_IRON_TOOL).add(block.get());
        });
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(io.github.ningmeng123.mite.registry.MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get());
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(MiteFurnaces.CLAY_OVEN.get(), MiteFurnaces.LARGE_CLAY_OVEN.get(),
                MiteFurnaces.OBSIDIAN_FURNACE.get(), MiteFurnaces.NETHERRACK_FURNACE.get());
        tag(BlockTags.MINEABLE_WITH_AXE).add(MiteFurnaces.CLAY_OVEN.get(), MiteFurnaces.LARGE_CLAY_OVEN.get());
        tag(BlockTags.MINEABLE_WITH_SHOVEL).add(MiteFurnaces.CLAY_OVEN.get(), MiteFurnaces.SANDSTONE_FURNACE.get());
        tag(BlockTags.NEEDS_IRON_TOOL).add(MiteFurnaces.OBSIDIAN_FURNACE.get());
        tag(BlockTags.NEEDS_STONE_TOOL).add(Blocks.FURNACE, MiteFurnaces.NETHERRACK_FURNACE.get());
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(MiteOreBlocks.SILVER_ORE.get(), MiteOreBlocks.DEEPSLATE_SILVER_ORE.get(),
                MiteOreBlocks.MITHRIL_ORE.get(), MiteOreBlocks.DEEPSLATE_MITHRIL_ORE.get(),
                MiteOreBlocks.ADAMANTIUM_ORE.get(), MiteOreBlocks.DEEPSLATE_ADAMANTIUM_ORE.get(), MiteOreBlocks.INFESTED_NETHERRACK.get());
        // Copper material is source level 2, iron 3 and mithril 4; the native names are modern compatibility only.
        tag(BlockTags.NEEDS_STONE_TOOL).add(MiteOreBlocks.SILVER_ORE.get(), MiteOreBlocks.DEEPSLATE_SILVER_ORE.get());
        tag(BlockTags.NEEDS_IRON_TOOL).add(MiteOreBlocks.MITHRIL_ORE.get(), MiteOreBlocks.DEEPSLATE_MITHRIL_ORE.get());
        tag(BlockTags.NEEDS_DIAMOND_TOOL).add(MiteOreBlocks.ADAMANTIUM_ORE.get(), MiteOreBlocks.DEEPSLATE_ADAMANTIUM_ORE.get());
        tag(BlockTags.WITHER_IMMUNE).add(io.github.ningmeng123.mite.registry.MiteWorldBlocks.MANTLE.get(),
                io.github.ningmeng123.mite.registry.MiteWorldBlocks.CORE.get());
        tag(BlockTags.DRAGON_IMMUNE).add(io.github.ningmeng123.mite.registry.MiteWorldBlocks.MANTLE.get(),
                io.github.ningmeng123.mite.registry.MiteWorldBlocks.CORE.get());
        tag(MiteTags.RUNESTONES).add(io.github.ningmeng123.mite.registry.MiteWorldBlocks.MITHRIL_RUNESTONE.get(),
                io.github.ningmeng123.mite.registry.MiteWorldBlocks.ADAMANTIUM_RUNESTONE.get());
        tag(BlockTags.MINEABLE_WITH_PICKAXE).addTag(MiteTags.RUNESTONES);
        tag(BlockTags.NEEDS_IRON_TOOL).addTag(MiteTags.RUNESTONES);
        tag(MiteTags.UNDERWORLD_ORE_REPLACEABLES).add(Blocks.STONE, Blocks.DEEPSLATE);
        tag(BlockTags.MINEABLE_WITH_SHOVEL).add(MiteResourceBlocks.NETHER_GRAVEL.get());
        MiteWorkbenches.TABLES.values().forEach(block -> tag(BlockTags.MINEABLE_WITH_AXE).add(block.get()));
        // The R196 material match alone is insufficient: whole glass/ice require harvest tier 1.
        tag(MiteTags.WOODEN_CLUB_EFFICIENT).add(
                Blocks.CAKE, Blocks.PUMPKIN, Blocks.CARVED_PUMPKIN, Blocks.JACK_O_LANTERN,
                Blocks.GLASS_PANE, Blocks.WHITE_STAINED_GLASS_PANE, Blocks.ORANGE_STAINED_GLASS_PANE,
                Blocks.MAGENTA_STAINED_GLASS_PANE, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE,
                Blocks.YELLOW_STAINED_GLASS_PANE, Blocks.LIME_STAINED_GLASS_PANE,
                Blocks.PINK_STAINED_GLASS_PANE, Blocks.GRAY_STAINED_GLASS_PANE,
                Blocks.LIGHT_GRAY_STAINED_GLASS_PANE, Blocks.CYAN_STAINED_GLASS_PANE,
                Blocks.PURPLE_STAINED_GLASS_PANE, Blocks.BLUE_STAINED_GLASS_PANE,
                Blocks.BROWN_STAINED_GLASS_PANE, Blocks.GREEN_STAINED_GLASS_PANE,
                Blocks.RED_STAINED_GLASS_PANE, Blocks.BLACK_STAINED_GLASS_PANE);
        // Recursively verified against minecraft_1.21.1_client.jar's data/minecraft/tags/block/logs.json.
        // These are its 40 concrete members, not a live reference to other mods' minecraft:logs additions.
        tag(MiteTags.LOGS_REQUIRING_AXE).add(
                Blocks.ACACIA_LOG, Blocks.ACACIA_WOOD,
                Blocks.BIRCH_LOG, Blocks.BIRCH_WOOD,
                Blocks.CHERRY_LOG, Blocks.CHERRY_WOOD,
                Blocks.CRIMSON_HYPHAE, Blocks.CRIMSON_STEM,
                Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_WOOD,
                Blocks.JUNGLE_LOG, Blocks.JUNGLE_WOOD,
                Blocks.MANGROVE_LOG, Blocks.MANGROVE_WOOD,
                Blocks.OAK_LOG, Blocks.OAK_WOOD,
                Blocks.SPRUCE_LOG, Blocks.SPRUCE_WOOD,
                Blocks.STRIPPED_ACACIA_LOG, Blocks.STRIPPED_ACACIA_WOOD,
                Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_BIRCH_WOOD,
                Blocks.STRIPPED_CHERRY_LOG, Blocks.STRIPPED_CHERRY_WOOD,
                Blocks.STRIPPED_CRIMSON_HYPHAE, Blocks.STRIPPED_CRIMSON_STEM,
                Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_WOOD,
                Blocks.STRIPPED_JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_WOOD,
                Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_WOOD,
                Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_OAK_WOOD,
                Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_WOOD,
                Blocks.STRIPPED_WARPED_HYPHAE, Blocks.STRIPPED_WARPED_STEM,
                Blocks.WARPED_HYPHAE, Blocks.WARPED_STEM);
    }
}

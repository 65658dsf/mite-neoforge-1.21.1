package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteTags;
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

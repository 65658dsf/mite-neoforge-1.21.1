package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteTags;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Explicit tools below the current log-harvesting stage. @author NingMeng123 */
public final class MiteItemTagsProvider extends ItemTagsProvider {
    public MiteItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
            CompletableFuture<TagsProvider.TagLookup<Block>> blockTags, ExistingFileHelper existingFiles) {
        super(output, lookup, blockTags, Mite.MOD_ID, existingFiles);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookup) {
        tag(MiteTags.BELOW_LOG_MINING_TIER).add(Items.WOODEN_AXE);
    }
}

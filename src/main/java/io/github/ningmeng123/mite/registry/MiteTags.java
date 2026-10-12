package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Explicit, reloadable compatibility boundaries for MITE rules. @author NingMeng123 */
public final class MiteTags {
    public static final TagKey<Block> LOGS_REQUIRING_AXE =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "logs_requiring_axe"));
    public static final TagKey<Item> BELOW_LOG_MINING_TIER =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "below_log_mining_tier"));

    private MiteTags() {
    }
}

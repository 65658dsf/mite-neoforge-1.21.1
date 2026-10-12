package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteResourceBlocks;
import io.github.ningmeng123.mite.registry.MiteShards;
import net.minecraft.world.level.ItemLike;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Reuses modern item silhouettes with stable material colors, without copying old textures. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class MaterialColors {
    private MaterialColors() {}

    @SubscribeEvent
    public static void items(RegisterColorHandlersEvent.Item event) {
        color(event, 0xE7A779, MiteMaterials.COPPER_NUGGET.get());
        color(event, 0xE2EFFF, MiteMaterials.SILVER_NUGGET.get(), MiteMaterials.SILVER_INGOT.get());
        color(event, 0xC3A170, MiteMaterials.ANCIENT_METAL_NUGGET.get(), MiteMaterials.ANCIENT_METAL_INGOT.get());
        color(event, 0x8FDCEB, MiteMaterials.MITHRIL_NUGGET.get(), MiteMaterials.MITHRIL_INGOT.get());
        color(event, 0x70BC85, MiteMaterials.ADAMANTIUM_NUGGET.get(), MiteMaterials.ADAMANTIUM_INGOT.get());
        color(event, 0x8A679F, MiteShards.OBSIDIAN_SHARD.get());
        color(event, 0x58F59D, MiteShards.EMERALD_SHARD.get());
        color(event, 0x76E9ED, MiteShards.DIAMOND_SHARD.get());
        color(event, 0xFFF0D9, MiteShards.QUARTZ_SHARD.get());
        color(event, 0xCFEAF9, MiteShards.GLASS_SHARD.get());
        color(event, 0xCE9A90, MiteResourceBlocks.NETHER_GRAVEL.get());
    }

    private static void color(RegisterColorHandlersEvent.Item event, int rgb, ItemLike... items) {
        event.register((stack, tint) -> 0xFF000000 | rgb, items);
    }

    @SubscribeEvent
    public static void blocks(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> 0xCE9A90, MiteResourceBlocks.NETHER_GRAVEL.get());
    }
}

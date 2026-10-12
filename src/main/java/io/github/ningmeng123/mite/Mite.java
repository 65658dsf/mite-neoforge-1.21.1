package io.github.ningmeng123.mite;

import com.mojang.logging.LogUtils;
import io.github.ningmeng123.mite.item.WoodenClubBehavior;
import io.github.ningmeng123.mite.mining.GrassMining;
import io.github.ningmeng123.mite.registry.MiteAttachments;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteShards;
import io.github.ningmeng123.mite.registry.MiteResourceBlocks;
import io.github.ningmeng123.mite.registry.MiteLootModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * MITE: Modern entry point.
 *
 * @author NingMeng123
 */
@Mod(Mite.MOD_ID)
public final class Mite {
    public static final String MOD_ID = "mite";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Mite(IEventBus modBus) {
        MiteAttachments.register(modBus);
        MiteItems.register(modBus);
        MiteMaterials.register(modBus);
        MiteShards.register(modBus);
        MiteResourceBlocks.register(modBus);
        MiteLootModifiers.register(modBus);
        modBus.addListener(WoodenClubBehavior::modifyComponents);
        modBus.addListener(GrassMining::modifyShears);
        LOGGER.info("MITE: Modern initialized for Minecraft 1.21.1");
    }
}

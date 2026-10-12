package io.github.ningmeng123.mite;

import com.mojang.logging.LogUtils;
import io.github.ningmeng123.mite.registry.MiteAttachments;
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
        LOGGER.info("MITE: Modern initialized for Minecraft 1.21.1");
    }
}

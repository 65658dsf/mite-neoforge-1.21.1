package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Enables Punchy's own Better Combat support when both mods are installed. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PunchyBetterCombatSupport {
    private PunchyBetterCombatSupport() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("punchy") && BetterCombatBridge.enabled()) {
            event.enqueueWork(PunchyBetterCombatSupport::enableSupport);
        }
    }

    private static void enableSupport() {
        try {
            Class<?> config = Class.forName("punchy.config.PunchyConfig");
            if (Boolean.TRUE.equals(config.getMethod("isBetterCombatCompatEnabled").invoke(null))) return;

            Object settings = config.getMethod("snapshot").invoke(null);
            Class<?> settingsType = settings.getClass();
            settingsType.getField("bettercombatCompat").setBoolean(settings, true);
            config.getMethod("applyAndSave", settingsType).invoke(null, settings);
            Mite.LOGGER.info("Enabled Punchy's Better Combat support");
        } catch (ReflectiveOperationException | LinkageError failure) {
            Mite.LOGGER.warn("Cannot enable Punchy's Better Combat support", failure);
        }
    }
}

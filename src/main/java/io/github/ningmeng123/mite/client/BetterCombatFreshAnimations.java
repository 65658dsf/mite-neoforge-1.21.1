package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Keeps optional EMF and animation classes out of normal and dedicated-server loading. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BetterCombatFreshAnimations {
    private BetterCombatFreshAnimations() {}

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("entity_model_features") && ModList.get().isLoaded("bettercombat")
                && ModList.get().isLoaded("playeranimator")) {
            event.enqueueWork(() -> {
                try {
                    FreshAnimationsCombatHook.install();
                } catch (Exception | LinkageError failure) {
                    Mite.LOGGER.warn("Cannot install the optional EMF / Better Combat animation hook", failure);
                }
            });
        }
    }

    public static boolean active(LivingEntity entity) {
        if (!ModList.get().isLoaded("bettercombat") || !ModList.get().isLoaded("playeranimator")) return false;
        return entity instanceof Mob mob ? BetterCombatMobAnimations.active(mob)
                : BetterCombatPlayerAnimationState.active(entity);
    }
}

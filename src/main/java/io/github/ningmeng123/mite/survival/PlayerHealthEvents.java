package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.ProgressionRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Applies the R196 base health curve on the logical server for every game mode.
 *
 * @author NingMeng123
 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class PlayerHealthEvents {
    private PlayerHealthEvents() {
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        applyHealthCap(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        applyHealthCap(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        applyHealthCap(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // Also covers commands and other changes that bypass XP change events.
        applyHealthCap(event.getEntity());
    }

    private static void applyHealthCap(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        double baseHealth = io.github.ningmeng123.mite.api.MiteExtensions.maximumHealth(player);
        if (maxHealth.getBaseValue() != baseHealth) {
            // Change only the base; health modifiers from effects or other mods remain.
            maxHealth.setBaseValue(baseHealth);
        }
        float effectiveMaxHealth = player.getMaxHealth();
        if (player.getHealth() > effectiveMaxHealth) {
            player.setHealth(effectiveMaxHealth);
        }
    }
}

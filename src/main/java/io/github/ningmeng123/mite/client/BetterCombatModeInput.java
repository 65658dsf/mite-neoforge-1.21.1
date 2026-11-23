package io.github.ningmeng123.mite.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.network.BetterCombatModePayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** The rebindable MITE shortcut for the player's Better Combat mode. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class BetterCombatModeInput {
    private static KeyMapping toggle;

    private BetterCombatModeInput() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) {
        if (toggle == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        while (toggle.consumeClick()) {
            if (minecraft.player == null || minecraft.screen != null || minecraft.isPaused()) continue;
            var connection = minecraft.getConnection();
            if (connection != null && connection.hasChannel(BetterCombatModePayload.TYPE)
                    && Loaded.serverHasBetterCombat()) {
                PacketDistributor.sendToServer(new BetterCombatModePayload());
            } else {
                minecraft.player.displayClientMessage(
                        Component.translatable("message.mite.better_combat.unavailable"), true);
            }
        }
    }

    @EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            if (!BetterCombatBridge.enabled()) return;
            toggle = new KeyMapping("key.mite.toggle_better_combat", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.mite");
            event.register(toggle);
        }
    }

    private static final class Loaded {
        private static boolean serverHasBetterCombat() {
            return net.bettercombat.client.BetterCombatClientMod.ENABLED;
        }
    }
}

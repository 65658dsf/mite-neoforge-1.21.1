package io.github.ningmeng123.mite.client.config;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.MiteConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** NeoForge's extension point is also used by Mod Menu (NeoForge Edition). @author NingMeng123 */
public final class MiteConfigClient {
    private MiteConfigClient() {}
    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, MiteSettingsScreen::new);
    }
    public static boolean editable() {
        var mc = Minecraft.getInstance();
        return mc.getConnection() == null || mc.hasSingleplayerServer();
    }
    public static void receive(MiteConfig.Snapshot snapshot) {
        // Integrated server and client share the same config values. Never shadow live edits with an old packet.
        var mc = Minecraft.getInstance();
        if (mc.getConnection() != null && !mc.hasSingleplayerServer()) MiteConfig.receiveServerSnapshot(snapshot);
    }
    @EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
    public static final class ConnectionEvents {
        private ConnectionEvents() {}
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            MiteConfig.clearServerSnapshot();
        }
    }
}

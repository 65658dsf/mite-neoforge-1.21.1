package io.github.ningmeng123.mite.client.config;

import com.google.gson.Gson;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.network.GameplayConfigEditPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.PacketDistributor;

/** NeoForge's extension point is also used by Mod Menu (NeoForge Edition). @author NingMeng123 */
public final class MiteConfigClient {
    private static final Gson GSON = new Gson();
    private record Pending(long deadline, Consumer<Component> completion) {}
    private static final Map<Integer, Pending> PENDING = new HashMap<>();
    private static Map<String, Object> gameplayValues = Map.of();
    private static Map<String, Object> spawningValues = Map.of();
    private static int nextRequest;
    private MiteConfigClient() {}
    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, MiteSettingsScreen::new);
    }
    public static boolean editable() {
        var mc = Minecraft.getInstance();
        return localEditable() || mc.player != null && mc.player.hasPermissions(2)
                && mc.getConnection().hasChannel(GameplayConfigEditPayload.TYPE);
    }
    public static boolean localEditable() {
        return !remoteServer();
    }
    public static boolean remoteServer() {
        var mc = Minecraft.getInstance();
        return mc.getConnection() != null && !mc.hasSingleplayerServer();
    }
    static boolean netherOreSettingsAvailable() {
        if (!remoteServer()) return true;
        var snapshot = MiteConfig.effectiveSnapshot();
        return snapshot != MiteConfig.localSnapshot() && !snapshot.netherOres().isEmpty();
    }
    public static Map<String, Object> gameplayValues() { return gameplayValues; }
    public static Map<String, Object> spawningControlValues() {
        return remoteServer() && !spawningValues.isEmpty() ? spawningValues : MiteExtensions.policy().spawningControlValues();
    }
    /** Complete with null only after the server confirms a save; never write a remote draft locally. */
    public static void saveRemote(String section, Object values, Consumer<Component> completion) {
        if (!remoteServer() || !editable()) {
            completion.accept(Component.translatable("mite.config.remote.denied"));
            return;
        }
        int requestId = ++nextRequest;
        PENDING.put(requestId, new Pending(System.nanoTime() + 20_000_000_000L, completion));
        try {
            PacketDistributor.sendToServer(new GameplayConfigEditPayload(requestId, section, GSON.toJson(values)));
        } catch (RuntimeException exception) {
            PENDING.remove(requestId);
            completion.accept(Component.translatable("mite.config.remote.send_failed"));
        }
    }
    public static void receiveEditResult(int requestId, boolean success, String message) {
        var pending = PENDING.remove(requestId);
        if (pending != null) pending.completion().accept(success ? null
                : message.startsWith("mite.config.remote.") ? Component.translatable(message) : Component.literal(message));
    }
    public static void receive(MiteConfig.Snapshot snapshot) {
        receive(snapshot, Map.of(), Map.of());
    }
    public static void receive(MiteConfig.Snapshot snapshot, Map<String, Object> general, Map<String, Object> spawning) {
        // Integrated server and client share the same config values. Never shadow live edits with an old packet.
        var mc = Minecraft.getInstance();
        if (mc.getConnection() != null && !mc.hasSingleplayerServer()) {
            MiteConfig.receiveServerSnapshot(snapshot);
            gameplayValues = Map.copyOf(general);
            spawningValues = Map.copyOf(spawning);
        }
    }
    @EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
    public static final class ConnectionEvents {
        private ConnectionEvents() {}
        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            long now = System.nanoTime();
            var expired = PENDING.entrySet().stream().filter(entry -> now >= entry.getValue().deadline()).toList();
            for (var entry : expired) {
                PENDING.remove(entry.getKey());
                entry.getValue().completion().accept(Component.translatable("mite.config.remote.timeout"));
            }
        }
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            MiteConfig.clearServerSnapshot();
            gameplayValues = Map.of();
            spawningValues = Map.of();
            var pending = java.util.List.copyOf(PENDING.values());
            PENDING.clear();
            pending.forEach(request -> request.completion().accept(Component.translatable("mite.config.remote.disconnected")));
        }
    }
}

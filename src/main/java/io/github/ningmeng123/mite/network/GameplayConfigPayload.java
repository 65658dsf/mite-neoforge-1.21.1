package io.github.ningmeng123.mite.network;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.config.GameplayConfigEdits;
import io.github.ningmeng123.mite.config.MiteConfig;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server settings including both ore dimensions, sent on login and config reload. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record GameplayConfigPayload(MiteConfig.Snapshot snapshot, Map<String, Object> gameplayValues,
        Map<String, Object> addonSpawning) implements CustomPacketPayload {
    private static final Gson GSON = new Gson();
    public GameplayConfigPayload {
        gameplayValues = Map.copyOf(gameplayValues);
        addonSpawning = Map.copyOf(addonSpawning);
    }
    public GameplayConfigPayload(MiteConfig.Snapshot snapshot) { this(snapshot, Map.of(), Map.of()); }
    public static final Type<GameplayConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "gameplay_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GameplayConfigPayload> CODEC = new StreamCodec<>() {
        @Override public GameplayConfigPayload decode(RegistryFriendlyByteBuf buffer) {
            var json = JsonParser.parseString(buffer.readUtf(262144)).getAsJsonObject();
            java.lang.reflect.Type valuesType = new TypeToken<Map<String, Object>>() {}.getType();
            return new GameplayConfigPayload(GSON.fromJson(json, MiteConfig.Snapshot.class),
                    json.has("gameplayValues") ? GSON.fromJson(json.get("gameplayValues"), valuesType) : Map.of(),
                    json.has("addonSpawning") ? GSON.fromJson(json.get("addonSpawning"), valuesType) : Map.of());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, GameplayConfigPayload payload) {
            // Extra fields retain the original snapshot shape for older read-only clients.
            var json = GSON.toJsonTree(payload.snapshot()).getAsJsonObject();
            json.add("gameplayValues", GSON.toJsonTree(payload.gameplayValues()));
            json.add("addonSpawning", GSON.toJsonTree(payload.addonSpawning()));
            buffer.writeUtf(json.toString(), 262144);
        }
    };
    @Override public Type<GameplayConfigPayload> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("5").playToClient(TYPE, CODEC, (payload, context) -> context.enqueueWork(() ->
                io.github.ningmeng123.mite.client.config.MiteConfigClient.receive(payload.snapshot(), payload.gameplayValues(), payload.addonSpawning())));
    }
    public static void send(ServerPlayer player) {
        if (player.connection != null && player.connection.hasChannel(TYPE))
            PacketDistributor.sendToPlayer(player, new GameplayConfigPayload(MiteConfig.localSnapshot(),
                    GameplayConfigEdits.gameplayValues(), MiteExtensions.policy().spawningControlValues()));
    }
}

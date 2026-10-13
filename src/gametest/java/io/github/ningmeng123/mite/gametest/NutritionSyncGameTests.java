package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.network.NutritionPayload;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.NutritionState;
import io.github.ningmeng123.mite.survival.PlayerFoodEvents;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Observe the actual sync-to-packet path outside periodic ticks. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class NutritionSyncGameTests {
    private NutritionSyncGameTests() {}

    @GameTest(template = "empty")
    public static void nonzeroSaturationChangesSendImmediateSnapshotsWithoutDuplicatePackets(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "mite-food-sync"), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        CapturingListener listener = new CapturingListener(server, connection, player, cookie);
        try {
            helper.assertTrue(connection.isConnected(), "The packet observer uses a connected embedded channel");
            player.tickCount = 1; // None of these checks can be explained by the twenty-tick heartbeat.
            player.getFoodData().setFoodLevel(4);
            ((DualFoodData) player.getFoodData()).mite$setNutrition(3);
            NutritionState.set(player, NutritionState.INITIAL);
            float unchangedHealth = player.getHealth();
            float[] saturationSequence = {1F, 2F, 3F, 2F};
            for (int index = 0; index < saturationSequence.length; index++) {
                player.getFoodData().setSaturation(saturationSequence[index]);
                PlayerFoodEvents.sync(player, false);
                helper.assertValueEqual(listener.received.size(), index + 1,
                        "Each nonzero saturation change immediately emits one custom snapshot");
                NutritionPayload actual = listener.received.get(index);
                helper.assertValueEqual(actual.saturation(), saturationSequence[index],
                        "The real outbound packet carries the newly authoritative saturation amount");
                helper.assertValueEqual(actual.entityId(), player.getId(), "The snapshot belongs to the tested player");
                helper.assertValueEqual(actual.satiation(), 4, "Satiation stays fixed while saturation changes");
                helper.assertValueEqual(actual.nutrition(), 3, "Nutrition stays fixed while saturation changes");
                helper.assertValueEqual(actual.limit(), 6, "The source level-zero capacity stays six");
                helper.assertValueEqual(actual.protein(), 160_000, "Protein does not trigger the packet");
                helper.assertValueEqual(actual.fat(), 160_000, "Essential fats do not trigger the packet");
                helper.assertValueEqual(actual.plants(), 160_000, "Plant nutrients do not trigger the packet");
                helper.assertValueEqual(actual.resistance(), 0, "Sugar resistance does not trigger the packet");
                helper.assertValueEqual(actual.resistanceLevel(), -1, "Resistance state remains absent");
                helper.assertValueEqual(player.getHealth(), unchangedHealth, "Health does not trigger the snapshot");
                PlayerFoodEvents.sync(player, false);
                helper.assertValueEqual(listener.received.size(), index + 1,
                        "A second call with unchanged resources sends no duplicate packet outside the heartbeat");
            }
        } finally {
            // Exercise the public production cleanup hook; leave no UUID entry in the snapshot cache.
            PlayerFoodEvents.loggedOut(new PlayerEvent.PlayerLoggedOutEvent(player));
            player.getTextFilter().leave();
            channel.finishAndReleaseAll();
        }
        helper.succeed();
    }

    /** The transport observer supplies channel negotiation and records actual payload packets. */
    private static final class CapturingListener extends ServerGamePacketListenerImpl {
        private final List<NutritionPayload> received = new ArrayList<>();

        private CapturingListener(MinecraftServer server, Connection connection, ServerPlayer player,
                                  CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override public boolean hasChannel(ResourceLocation payloadId) {
            return NutritionPayload.TYPE.id().equals(payloadId);
        }

        @Override public void send(Packet<?> packet) {
            if (packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof NutritionPayload payload) received.add(payload);
        }
    }
}

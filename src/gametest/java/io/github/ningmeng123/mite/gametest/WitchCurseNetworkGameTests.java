package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.network.CurseNotificationPayload;
import io.github.ningmeng123.mite.network.CurseStatePayload;
import io.github.ningmeng123.mite.network.RevealCursePayload;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent wire round trips for unknown, discovered and lifted curses. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class WitchCurseNetworkGameTests {
    @GameTest(template = "empty")
    public static void curseWireMessagesPreserveEveryTypeAndDiscoveryWithoutCombiningNotices(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            for (int id = 0; id <= 16; id++) {
                for (boolean known : new boolean[]{false, true}) {
                    var state = new CurseStatePayload(id, known);
                    CurseStatePayload.STREAM_CODEC.encode(buffer, state);
                    helper.assertTrue(state.equals(CurseStatePayload.STREAM_CODEC.decode(buffer)),
                            "Every curse type and discovery bit survive the private state message");
                    helper.assertValueEqual(buffer.readableBytes(), 0, "The state message consumes exactly its own bytes");
                }
                var request = new RevealCursePayload(id);
                RevealCursePayload.STREAM_CODEC.encode(buffer, request);
                helper.assertTrue(request.equals(RevealCursePayload.STREAM_CODEC.decode(buffer)), "Discovery identifies only the sender's current type");
            }
            for (boolean active : new boolean[]{false, true}) {
                var notice = new CurseNotificationPayload(active);
                CurseNotificationPayload.STREAM_CODEC.encode(buffer, notice);
                helper.assertTrue(notice.equals(CurseNotificationPayload.STREAM_CODEC.decode(buffer)),
                        "A realization and a lifting notice remain distinguishable from state synchronization");
            }
            helper.assertValueEqual(buffer.readableBytes(), 0, "No message consumes another message's bytes");
        } finally { buffer.release(); }
        helper.succeed();
    }
}

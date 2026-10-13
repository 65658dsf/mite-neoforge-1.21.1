package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.network.NutritionPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Verifies signed resistance levels and independently ordered food resources on the wire. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class NutritionNetworkGameTests {
    @GameTest(template = "empty")
    public static void nutritionPayloadKeepsAllServerResourcesAndNegativeNoResistanceLevel(GameTestHelper helper) {
        var samples = new NutritionPayload[]{
                new NutritionPayload(31, 6, 0, 6, 160000, 120000, 80000, 0, -1, 1F),
                new NutritionPayload(31, 6, 0, 6, 160000, 120000, 80000, 0, -1, 2F),
                new NutritionPayload(31, 6, 0, 6, 160000, 120000, 80000, 0, -1, 3F),
                new NutritionPayload(2048, 0, 13, 20, 0, 32000, 1200, 9000, 0, 0F),
                new NutritionPayload(8193, 12, 19, 20, 160000, 0, 160000, 160000, 2, 3.25F)
        };
        for (NutritionPayload sample : samples) {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                NutritionPayload.STREAM_CODEC.encode(buffer, sample);
                NutritionPayload decoded = NutritionPayload.STREAM_CODEC.decode(buffer);
                helper.assertTrue(decoded.equals(sample), "Every S/N, saturation, micronutrient and sugar field survives network serialization");
                helper.assertValueEqual(Float.floatToRawIntBits(decoded.saturation()), Float.floatToRawIntBits(sample.saturation()),
                        "Nonzero-to-nonzero saturation changes and fractional amounts keep their exact float bits");
                helper.assertValueEqual(buffer.readableBytes(), 0, "Decoding consumes exactly one authoritative snapshot");
            } finally {
                buffer.release();
            }
        }
        helper.succeed();
    }
}

package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.client.config.MiteConfigClient;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.network.VisualPackBlockedPayload;
import io.github.ningmeng123.mite.network.VisualPackBlockedPayload.Reason;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client rules and notices shared by the Iris and Minecraft pack loaders. @author NingMeng123 */
public final class VisualPackPolicy {
    private VisualPackPolicy() {}

    public static boolean blocks(Minecraft minecraft, Reason reason) {
        // Use the restrictive policy at startup and before the world's rule has arrived.
        if (minecraft == null || minecraft.getConnection() == null) return true;
        return switch (reason) {
            case NIGHT_VISION_SHADER -> MiteConfigClient.blockNightVisionShaders();
            case NIGHT_VISION_RESOURCE_PACK -> MiteConfigClient.blockNightVisionResourcePacks();
            case XRAY_RESOURCE_PACK -> MiteConfigClient.blockXrayResourcePacks();
        };
    }

    public static boolean report(Minecraft minecraft, Reason reason) {
        var connection = minecraft.getConnection();
        if (connection == null || minecraft.player == null || !blocks(minecraft, reason)) return false;
        if (connection.hasChannel(VisualPackBlockedPayload.TYPE)) {
            if (MiteConfigClient.remoteServer() && !MiteConfigClient.gameplayValues().containsKey(setting(reason))) {
                return false;
            }
            PacketDistributor.sendToServer(new VisualPackBlockedPayload(reason));
        } else {
            minecraft.player.displayClientMessage(reason.blockedMessage(minecraft.player.getGameProfile().getName()), false);
            minecraft.player.displayClientMessage(VisualPackBlockedPayload.settingsHint(), false);
        }
        return true;
    }

    private static String setting(Reason reason) {
        return switch (reason) {
            case NIGHT_VISION_SHADER -> MiteConfig.BLOCK_NIGHT_VISION_SHADERS_PATH;
            case NIGHT_VISION_RESOURCE_PACK -> MiteConfig.BLOCK_NIGHT_VISION_RESOURCE_PACKS_PATH;
            case XRAY_RESOURCE_PACK -> MiteConfig.BLOCK_XRAY_RESOURCE_PACKS_PATH;
        };
    }
}

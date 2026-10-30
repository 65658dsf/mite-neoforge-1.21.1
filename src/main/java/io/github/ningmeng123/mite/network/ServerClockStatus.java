package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.rules.WorldClock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.ServerStatus;

/** Publish the Overworld clock through the ordinary pre-login status response. @author NingMeng123 */
public final class ServerClockStatus {
    private ServerClockStatus() {}

    public static ServerStatus withClock(ServerStatus original, long dayTime) {
        var clock = Component.translatableWithFallback("gui.mite.server_time", "Day %s · %s",
                WorldClock.displayDay(dayTime), WorldClock.displayTime(dayTime)).withStyle(ChatFormatting.GOLD);
        var description = Component.empty().append(clock).append("\n").append(original.description().copy());
        return new ServerStatus(description, original.players(), original.version(), original.favicon(),
                original.enforcesSecureChat(), original.isModded());
    }
}

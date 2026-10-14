package io.github.ningmeng123.mite.utility;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Server drink completion extension for future R196 world/player curses; does not remove item enchantments. @author NingMeng123 */
public final class PlayerDisenchantingEvent extends Event {
    private final ServerPlayer player;
    public PlayerDisenchantingEvent(ServerPlayer player) { this.player = player; }
    public ServerPlayer player() { return player; }
}

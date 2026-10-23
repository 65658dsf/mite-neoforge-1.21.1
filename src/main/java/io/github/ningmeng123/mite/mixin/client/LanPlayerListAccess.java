package io.github.ningmeng123.mite.mixin.client;

import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Updates the integrated server's actual admission limit. @author NingMeng123 */
@Mixin(PlayerList.class)
public interface LanPlayerListAccess {
    @Mutable
    @Accessor("maxPlayers")
    void mite$setMaxPlayers(int maxPlayers);
}

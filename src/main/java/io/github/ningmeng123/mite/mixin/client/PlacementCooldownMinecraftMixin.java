package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.client.PlacementClickTiming;
import io.github.ningmeng123.mite.world.PlacementDelay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Keeps held placement at six ticks without changing ordinary interactions. @author NingMeng123 */
@Mixin(Minecraft.class)
public abstract class PlacementCooldownMinecraftMixin {
    @Shadow public LocalPlayer player;
    @Shadow private int rightClickDelay;

    @WrapMethod(method = "startUseItem")
    private void mite$synchronizePlacementRepeat(Operation<Void> original) {
        int previousRemaining = PlacementDelay.remainingTicks(player);
        PlacementClickTiming.begin();
        try {
            original.call();
        } finally {
            boolean blocked = PlacementClickTiming.consumeBlocked() && (player == null || !player.isUsingItem());
            rightClickDelay = PlacementClickTiming.rightClickDelay(rightClickDelay, previousRemaining,
                    PlacementDelay.remainingTicks(player), blocked);
        }
    }
}

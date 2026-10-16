package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.rules.MovementHungerRules;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;

/** Source movement effort is decided once on the server, independently of modern movement statistics. @author NingMeng123 */
public final class MovementHunger {
    private MovementHunger() {}
    public static void travel(Player player, double x, double y, double z) {
        if (player.level().isClientSide || !player.isAlive() || player.isPassenger() || player.isSpectator()) return;
        float effort = MovementHungerRules.exhaustion(x, y, z, player.isEyeInFluid(FluidTags.WATER), player.isInWater(),
                player.onClimbable(), player.onGround(), player.isSleeping(), player.isSprinting());
        if (effort > 0F) player.causeFoodExhaustion(effort);
    }
    public static void rowing(Boat boat) {
        if (boat.level().isClientSide || !(boat.getControllingPassenger() instanceof Player player) || !player.isAlive() || player.isSpectator()) return;
        // Modern input is transmitted with the native player-input packet. Coasting/other passengers do not row.
        float effort = MovementHungerRules.rowing(player.zza);
        if (effort > 0F) player.causeFoodExhaustion(effort);
    }
}

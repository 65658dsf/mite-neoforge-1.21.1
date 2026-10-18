package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.combat.ShieldGuard;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** The native axe interruption keeps its behavior while clearing persistent guard history. @author NingMeng123 */
@Mixin(Player.class)
public abstract class ShieldGuardPlayerMixin {
    @WrapMethod(method = "disableShield")
    private void mite$shareNativeShieldCooldown(Operation<Void> original) {
        Player self = (Player)(Object)this;
        ItemStack shield = self.getUseItem();
        original.call();
        ShieldGuard.nativeDisabled(self, shield);
    }
}

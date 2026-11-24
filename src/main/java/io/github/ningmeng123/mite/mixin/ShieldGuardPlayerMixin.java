package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.combat.ShieldGuard;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Apply the shared material cooldown inside the native shield interruption. @author NingMeng123 */
@Mixin(Player.class)
public abstract class ShieldGuardPlayerMixin {
    @WrapOperation(method = "disableShield", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V"))
    private void mite$shareNativeShieldCooldown(ItemCooldowns cooldowns, Item item, int ticks, Operation<Void> original) {
        Player self = (Player)(Object)this;
        if (!ShieldGuard.nativeDisabled(self, self.getUseItem())) original.call(cooldowns, item, ticks);
    }
}

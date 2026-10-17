package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.survival.AutoRestock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/** A weapon destroyed by an actual melee hit uses the same source refill path. @author NingMeng123 */
@Mixin(Player.class)
public abstract class AutoRestockAttackMixin {
    @WrapMethod(method = "attack")
    private void mite$brokenWeapon(Entity target, Operation<Void> original) {
        var before = AutoRestock.before((Player)(Object)this, InteractionHand.MAIN_HAND);
        original.call(target);
        AutoRestock.after(before);
    }
}

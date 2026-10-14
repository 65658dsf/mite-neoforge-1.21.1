package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.item.SourceArmorDefense;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/** Each real player damage transaction gets a nested-safe marker; exceptions cannot leak it into later hits. @author NingMeng123 */
@Mixin(Player.class)
public abstract class SourceArmorDefenseScopeMixin {
    @WrapMethod(method = "actuallyHurt")
    private void mite$sourceDefenseScope(DamageSource source, float incoming, Operation<Void> original) {
        try (var scope = SourceArmorDefense.enter((Player)(Object)this)) { original.call(source, incoming); }
    }
}

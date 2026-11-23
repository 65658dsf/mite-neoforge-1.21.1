package io.github.ningmeng123.mite.mixin;

import net.bettercombat.api.AttackHand;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access to the last weapon cached by Better Combat's Player mixin. @author NingMeng123 */
@Mixin(Player.class)
public interface BetterCombatLastAttackAccess {
    @Accessor(value = "lastAttack", remap = false)
    void mite$setLastAttack(AttackHand attack);
}

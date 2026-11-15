package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.rules.EnchantmentCombatRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** R196 webs and paralysis slow both mouse axes using the net movement penalty. @author NingMeng123 */
@Mixin(MouseHandler.class)
public abstract class SourceWebLookMixin {
    @Shadow @Final private Minecraft minecraft;

    @ModifyArgs(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void mite$slowLook(Args args) {
        var player = minecraft.player;
        if (player == null) return;
        var slow = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        var haste = player.getEffect(MobEffects.MOVEMENT_SPEED);
        // nn:992-1009 checks the whole body and uf:1586-1589 exempts flying players.
        boolean inWeb = !player.getAbilities().flying && !player.isSpectator()
                && player.level().getBlockStates(player.getBoundingBox().deflate(.001D))
                        .anyMatch(state -> state.is(Blocks.COBWEB));
        if (slow == null && !inWeb) return;
        double factor = EnchantmentCombatRules.lookSensitivityFactor(slow == null ? -1 : slow.getAmplifier(),
                haste == null ? -1 : haste.getAmplifier(), inWeb, R196Enchantments.freeActionResistance(player));
        args.set(0, (double)args.get(0) * factor);
        args.set(1, (double)args.get(1) * factor);
    }
}

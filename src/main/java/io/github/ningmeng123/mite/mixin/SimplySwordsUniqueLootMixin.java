package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exclude unique loot added directly after chest generation while preserving tablet rolls. @author NingMeng123 */
@Pseudo
@Mixin(targets = "net.sweenus.simplyswords.loot.PityLootManager", remap = false)
public abstract class SimplySwordsUniqueLootMixin {
    @Inject(method = "chooseUnique(Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/item/Item;",
            at = @At("RETURN"), cancellable = true, require = 1, remap = false)
    private static void mite$removeUniqueLoot(RandomSource random, CallbackInfoReturnable<Item> cir) {
        Item chosen = cir.getReturnValue();
        // A null selection skips only the unique reward; the independent tablet roll still runs.
        if (chosen != null && SimplySwordsCompat.uniqueWeapon(chosen)) cir.setReturnValue(null);
    }
}

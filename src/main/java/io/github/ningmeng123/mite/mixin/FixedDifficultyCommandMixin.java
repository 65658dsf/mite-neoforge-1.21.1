package io.github.ningmeng123.mite.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.DifficultyCommand;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R196 aj disables difficulty changes; the useful modern query remains available. @author NingMeng123 */
@Mixin(DifficultyCommand.class)
public abstract class FixedDifficultyCommandMixin {
    @Inject(method = "setDifficulty", at = @At("HEAD"), cancellable = true)
    private static void mite$rejectDifficultyChanges(CommandSourceStack source, Difficulty requested,
            CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        throw new SimpleCommandExceptionType(Component.translatable("commands.mite.difficulty.locked")).create();
    }
}

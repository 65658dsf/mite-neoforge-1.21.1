package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** New and recreated local worlds use Extreme without changing survival, creative or hardcore. @author NingMeng123 */
@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationDifficultyMixin {
    @ModifyExpressionValue(method = "<init>", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/Difficulty;NORMAL:Lnet/minecraft/world/Difficulty;"))
    private Difficulty mite$initialExtremeDifficulty(Difficulty original) {
        return Difficulty.HARD;
    }

    @ModifyVariable(method = "setDifficulty", at = @At("HEAD"), argsOnly = true)
    private Difficulty mite$fixedExtremeDifficulty(Difficulty requested) {
        return Difficulty.HARD;
    }
}

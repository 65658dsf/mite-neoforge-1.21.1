package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.world.LunarWeather;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Blood-night thunder retains its native strength when the rain stops. @author NingMeng123 */
@Mixin(Level.class)
public abstract class LunarThunderMixin {
    @ModifyExpressionValue(method = "getThunderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getRainLevel(F)F"))
    private float mite$dryBloodThunder(float rainMultiplier) {
        return LunarWeather.dryBloodStorm((Level)(Object)this) ? 1F : rainMultiplier;
    }
}

package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.world.LunarWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Blood-period dry biomes retain native sky and height checks before receiving real rain. @author NingMeng123 */
@Mixin(Level.class)
public abstract class LunarPrecipitationMixin {
    @WrapOperation(method = "isRainingAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation mite$desertRain(Biome biome, BlockPos pos, Operation<Biome.Precipitation> original) {
        return LunarWeather.precipitation((Level)(Object)this, biome, pos, original.call(biome, pos));
    }
}

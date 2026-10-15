package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.rules.LunarCalendar;
import io.github.ningmeng123.mite.world.LunarWeather;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Blood-period rain reaches normally arid biomes in both precipitation quads and rain particles/sounds. @author NingMeng123 */
@Mixin(LevelRenderer.class)
public abstract class LunarPrecipitationMixin {
    @Shadow private ClientLevel level;

    @ModifyExpressionValue(method = "renderSnowAndRain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;hasPrecipitation()Z"))
    private boolean mite$bloodPeriodRainGate(boolean original) {
        return original || level != null && LunarCalendar.state(level.getDayTime(), level.dimension().equals(Level.OVERWORLD)).bloodPeriod();
    }

    @WrapOperation(method = {"renderSnowAndRain", "tickRain"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation mite$bloodPeriodPrecipitation(Biome biome, BlockPos pos, Operation<Biome.Precipitation> original) {
        return LunarWeather.precipitation(level, biome, pos, original.call(biome, pos));
    }
}

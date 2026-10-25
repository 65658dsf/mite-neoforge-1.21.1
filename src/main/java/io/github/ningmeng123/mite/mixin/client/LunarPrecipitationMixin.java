package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.client.BloodMoonAtmosphere;
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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Daytime blood rain includes arid biomes; blood nights never render precipitation or rain sounds. @author NingMeng123 */
@Mixin(LevelRenderer.class)
public abstract class LunarPrecipitationMixin {
    @Shadow private ClientLevel level;

    @Inject(method = {"renderSnowAndRain", "tickRain"}, at = @At("HEAD"), cancellable = true)
    private void mite$dryBloodNight(CallbackInfo ci) {
        // Native weather packets and rain interpolation can still contain the preceding day's rain.
        if (level != null && LunarWeather.dryBloodStorm(level)) ci.cancel();
    }

    @ModifyArgs(method = "renderSnowAndRain", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;RAIN_LOCATION:Lnet/minecraft/resources/ResourceLocation;"),
                    to = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;SNOW_LOCATION:Lnet/minecraft/resources/ResourceLocation;")),
            require = 4, allow = 4)
    private void mite$bloodRainColor(Args args) {
        if (level == null) return;
        var color = BloodMoonAtmosphere.rainColor(level);
        args.set(0, (float)args.get(0) * color.red());
        args.set(1, (float)args.get(1) * color.green());
        args.set(2, (float)args.get(2) * color.blue());
    }

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

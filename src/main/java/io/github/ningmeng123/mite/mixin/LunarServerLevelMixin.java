package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.rules.LunarWeatherRules;
import io.github.ningmeng123.mite.world.LunarWeather;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep native weather interpolation, packets, lightning rods and sleep accounting. @author NingMeng123 */
@Mixin(ServerLevel.class)
public abstract class LunarServerLevelMixin {
    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"))
    private void mite$lunarWeather(CallbackInfo ci) { LunarWeather.updateNaturalWeather((ServerLevel)(Object)this); }

    @ModifyConstant(method = "tickChunk", constant = @Constant(intValue = 100000))
    private int mite$bloodMoonLightning(int original) {
        return LunarWeatherRules.lightningRarity(LunarWeather.bloodPeriod((ServerLevel)(Object)this));
    }

    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation mite$desertPrecipitation(Biome biome, BlockPos pos, Operation<Biome.Precipitation> original) {
        return LunarWeather.precipitation((ServerLevel)(Object)this, biome, pos, original.call(biome, pos));
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/SleepStatus;areEnoughDeepSleeping(ILjava/util/List;)Z"))
    private boolean mite$bloodMoonCannotSkip(SleepStatus status, int percentage, List<ServerPlayer> players, Operation<Boolean> original) {
        return !LunarWeather.bloodPeriod((ServerLevel)(Object)this) && original.call(status, percentage, players);
    }
}

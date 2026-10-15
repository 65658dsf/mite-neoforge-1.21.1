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
import net.minecraft.world.level.levelgen.Heightmap;
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

    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
    private boolean mite$dryBloodLightning(ServerLevel level, Operation<Boolean> original) {
        return LunarWeather.dryBloodStorm(level) || original.call(level);
    }

    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRainingAt(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean mite$dryBloodLightningAt(ServerLevel level, BlockPos pos, Operation<Boolean> original) {
        if (!LunarWeather.dryBloodStorm(level)) return original.call(level, pos);
        // Only the lightning branch bypasses wet weather. Covered blocks, snow biomes and all
        // ordinary rain consumers keep the native sky, height and precipitation restrictions.
        if (!level.canSeeSky(pos) || level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY()) return false;
        Biome biome = level.getBiome(pos).value();
        return LunarWeather.precipitation(level, biome, pos, biome.getPrecipitationAt(pos)) == Biome.Precipitation.RAIN;
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

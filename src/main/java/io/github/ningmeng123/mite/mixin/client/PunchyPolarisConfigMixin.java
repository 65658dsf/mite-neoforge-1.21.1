package io.github.ningmeng123.mite.mixin.client;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.client.PolarisWeaponAnimations;
import java.io.Reader;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Scope the original pack before Punchy's priority and tuning-profile merging. @author NingMeng123 */
@Pseudo
@Mixin(targets = "punchy.config.PunchyTuningConfig", remap = false)
public abstract class PunchyPolarisConfigMixin {
    // Punchy 3.1b delegates JSON parsing to its original$ resource reader.
    @WrapOperation(method = {"readCompatResource", "original$readCompatResource"}, at = @At(value = "INVOKE",
            target = "Lcom/google/gson/Gson;fromJson(Ljava/io/Reader;Ljava/lang/Class;)Ljava/lang/Object;"), require = 1)
    private static Object mite$scopePolaris(Gson gson, Reader reader, Class<?> type, Operation<Object> original,
                                            @Local(argsOnly = true) Resource resource) {
        if (!PolarisWeaponAnimations.polarisPack(resource.sourcePackId())) return original.call(gson, reader, type);
        return gson.fromJson(PolarisWeaponAnimations.scopeConfig(
                JsonParser.parseReader(reader).getAsJsonObject(), resource.sourcePackId()), type);
    }
}

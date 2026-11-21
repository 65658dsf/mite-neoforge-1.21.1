package io.github.ningmeng123.mite.mixin;

import java.util.Properties;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keep dedicated server settings consistent with the world's fixed difficulty. @author NingMeng123 */
@Mixin(DedicatedServerProperties.class)
public abstract class FixedDifficultyPropertiesMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/dedicated/Settings;<init>(Ljava/util/Properties;)V"))
    private static Properties mite$fixedDifficultyProperty(Properties properties) {
        properties.setProperty("difficulty", Difficulty.HARD.getKey());
        return properties;
    }
}

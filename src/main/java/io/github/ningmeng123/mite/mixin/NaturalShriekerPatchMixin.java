package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.worldgen.NaturalShriekerOres;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.SculkPatchFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SculkPatchConfiguration;
import org.spongepowered.asm.mixin.Mixin;

/** Includes both patch extras and the patch's native world-generation sculk spreader. @author NingMeng123 */
@Mixin(SculkPatchFeature.class)
public abstract class NaturalShriekerPatchMixin {
    @WrapMethod(method = "place")
    private boolean mite$naturalPatchOres(FeaturePlaceContext<SculkPatchConfiguration> context, Operation<Boolean> original) {
        try (var scope = NaturalShriekerOres.patch(context.level())) {
            boolean placed = original.call(context);
            if (placed) scope.finish();
            return placed;
        }
    }
}

package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.worldgen.NetherOrePlacement;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep the registered feature identity for biome filters and the vanilla decoration seed. @author NingMeng123 */
@Mixin(PlacedFeature.class)
public abstract class NetherOrePlacementMixin {
    @WrapMethod(method = "placeWithContext")
    private boolean mite$applyNetherSettings(PlacementContext context, RandomSource random, BlockPos origin, Operation<Boolean> original) {
        return NetherOrePlacement.place((PlacedFeature)(Object)this, context, () -> original.call(context, random, origin));
    }

    @ModifyExpressionValue(method = "placeWithContext", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;placement:Ljava/util/List;"))
    private List<PlacementModifier> mite$netherPlacementModifiers(List<PlacementModifier> original) {
        return NetherOrePlacement.modifiers((PlacedFeature)(Object)this, original);
    }
}

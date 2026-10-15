package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** R196's early mines consume both torch rolls without placing lights before day ten. @author NingMeng123 */
@Mixin(MineshaftPieces.MineShaftCorridor.class)
public abstract class MineshaftTorchMixin {
    @WrapOperation(method = "placeSupport", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/levelgen/structure/structures/MineshaftPieces$MineShaftCorridor;maybeGenerateBlock(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/util/RandomSource;FIIILnet/minecraft/world/level/block/state/BlockState;)V"))
    private void mite$dayTenTorchRoll(MineshaftPieces.MineShaftCorridor corridor, WorldGenLevel level,
            BoundingBox bounds, RandomSource random, float chance, int x, int y, int z, BlockState torch,
            Operation<Void> original) {
        if (R196StructureGeneration.displayDay(level.getLevel().getDayTime()) < 10L) {
            random.nextFloat();
            return;
        }
        original.call(corridor, level, bounds, random, chance, x, y, z, torch);
    }
}

package io.github.ningmeng123.mite.mining;

import io.github.ningmeng123.mite.rules.BlockHardnessRules;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/** R196 state hardness adapted without changing registered block properties. @author NingMeng123 */
public final class SourceBlockHardness {
    private SourceBlockHardness() {}

    public static float effectiveHardness(BlockState state, float fallback) {
        if (fallback < 0F) return fallback;
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals("minecraft")) return fallback;
        if (id.getPath().equals("snow"))
            return .05F * state.getValue(SnowLayerBlock.LAYERS);
        if (id.getPath().equals("vine")) {
            int faces = 0;
            for (Direction direction : Direction.Plane.HORIZONTAL)
                if (state.getValue(VineBlock.getPropertyForFace(direction))) faces++;
            return .2F * Math.max(faces, 1);
        }
        boolean doubleSlab = state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
        return effectiveHardness(id.getNamespace(), id.getPath(), doubleSlab, fallback);
    }

    public static float effectiveHardness(String namespace, String path, boolean doubleSlab, float fallback) {
        return BlockHardnessRules.effectiveHardness(namespace, path, doubleSlab, fallback);
    }

    public static Map<String, Float> registeredHardness() { return BlockHardnessRules.registeredHardness(); }
    public static Map<String, Float> slabHardness() { return BlockHardnessRules.slabHardness(); }
}

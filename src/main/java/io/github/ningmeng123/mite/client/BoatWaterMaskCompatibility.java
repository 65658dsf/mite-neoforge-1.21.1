package io.github.ningmeng123.mite.client;

import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.geom.ModelPart;

/** Keep animated CEM depth masks from erasing boat passengers. @author NingMeng123 */
public final class BoatWaterMaskCompatibility {
    private BoatWaterMaskCompatibility() {}

    public static ModelPart select(ModelPart original, boolean playerPassenger) {
        // Vanilla has one cuboid and no children. CEM packs such as FA+Objects
        // replace it with an animated hierarchy whose depth-only volume can
        // extend above the seat and occlude the rider's legs.
        if (!playerPassenger || original.getAllParts().skip(1).findAny().isEmpty()) return original;
        return VanillaMask.PART;
    }

    private static final class VanillaMask {
        // Bake directly: EntityModelSet.bakeLayer would apply the CEM override again.
        private static final ModelPart PART = BoatModel.createBodyModel().bakeRoot().getChild("water_patch");
    }
}

package io.github.ningmeng123.mite.client;

import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.fml.ModList;
import traben.entity_model_features.models.parts.EMFModelPart;

/** Preserve CEM child textures when a recursive mesh fast path skips their render calls. @author NingMeng123 */
public final class EmfPartTextureCompatibility {
    private static final ThreadLocal<Integer> FALLBACK_DEPTH = new ThreadLocal<>();

    private EmfPartTextureCompatibility() {}

    /** Returns the previous depth, or -1 when this render needs no compatibility scope. */
    public static int begin(ModelPart root) {
        Integer previous = FALLBACK_DEPTH.get();
        if (previous != null) {
            FALLBACK_DEPTH.set(previous + 1);
            return previous;
        }
        if (!OptionalMods.ACCELERATED_RENDERING || !OptionalMods.EMF || !Loaded.hasTextureOverride(root)) return -1;
        FALLBACK_DEPTH.set(1);
        return 0;
    }

    public static void end(int previousDepth) {
        if (previousDepth < 0) return;
        if (previousDepth == 0) FALLBACK_DEPTH.remove();
        else FALLBACK_DEPTH.set(previousDepth);
    }

    public static boolean useVanillaPipeline() {
        return FALLBACK_DEPTH.get() != null;
    }

    // Keep optional EMF symbols out of the class called by Accelerated Rendering
    // when EMF is absent. Inspect the active tree rather than caching variants:
    // EMF can replace children, and a resource reload replaces model roots.
    private static final class Loaded {
        private static boolean hasTextureOverride(ModelPart root) {
            return root.getAllParts().anyMatch(part ->
                    part instanceof EMFModelPart emf && emf.textureOverride != null);
        }
    }

    private static final class OptionalMods {
        private static final boolean ACCELERATED_RENDERING = ModList.get().isLoaded("acceleratedrendering");
        private static final boolean EMF = ModList.get().isLoaded("entity_model_features");
    }
}

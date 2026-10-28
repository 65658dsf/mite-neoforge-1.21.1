package io.github.ningmeng123.mite.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.fml.ModList;
import traben.entity_model_features.models.EMFModel_ID;
import traben.entity_model_features.models.jem_objects.EMFJemData;
import traben.entity_model_features.models.jem_objects.EMFPartData;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;

/** Bridge separately named dragon neck/tail segments onto 1.21.1's reused spine. @author NingMeng123 */
public final class EnderDragonModelCompatibility {
    private static final String MARKER = "mite_dragon_segment";
    private static final String PREPARED_MARKER = "EMF_" + MARKER;
    private static final String SEGMENT = "mite_dragon_segment_index_";
    private static final String PREPARED_SEGMENT = "EMF_" + SEGMENT;

    private EnderDragonModelCompatibility() {}

    /** Called only by the optional EMF mixin, before its normal JEM preparation. */
    public static void prepare(Object data, Object modelId) {
        Loaded.prepare((EMFJemData)data, (EMFModel_ID)modelId);
    }

    /** Scope child visibility to one repeated vanilla draw, including exceptional exits. */
    public static void renderSegment(ModelPart spine, int segment, Runnable render) {
        if (!OptionalMods.EMF) {
            render.run();
            return;
        }
        Loaded.renderSegment(spine, segment, render);
    }

    private static final class Loaded {
        private static void prepare(EMFJemData data, EMFModel_ID modelId) {
            if (!"dragon".equals(modelId.getMapId())) return;
            // Complete newer rigs have five independent neck pieces and twelve tail
            // pieces. Leave legacy `spine` packs and incomplete rigs untouched.
            EMFPartData[] segments = new EMFPartData[17];
            for (EMFPartData part : data.models) {
                if ("spine".equals(part.part)) return;
                int segment = segment(part.part);
                if (segment >= 0) {
                    if (segments[segment] != null) return;
                    segments[segment] = part;
                }
            }
            for (EMFPartData part : segments) if (part == null) return;
            for (int i = 0; i < segments.length; i++) {
                EMFPartData part = segments[i];
                part.part = "spine";
                // A geometry-free child carries the segment identity through EMF's
                // preparation and model variants without renaming animated IDs.
                EMFPartData marker = new EMFPartData();
                marker.id = MARKER;
                EMFPartData index = new EMFPartData();
                index.id = SEGMENT + i;
                marker.submodels.add(index);
                part.submodels.add(marker);
            }
        }

        private static int segment(String part) {
            for (int i = 1; i <= 5; i++) if (("neck" + i).equals(part)) return i - 1;
            for (int i = 1; i <= 12; i++) if (("tail" + i).equals(part)) return i + 4;
            return -1;
        }

        private static void renderSegment(ModelPart spine, int segment, Runnable render) {
            if (!(spine instanceof EMFModelPartVanilla emf)) {
                render.run();
                return;
            }
            List<ModelPart> changed = new ArrayList<>();
            List<Boolean> visibility = new ArrayList<>();
            for (ModelPart child : emf.getAllEMFCustomChildren()) {
                if (!child.hasChild(PREPARED_MARKER)) continue;
                changed.add(child);
                visibility.add(child.visible);
                child.visible = child.visible && child.getChild(PREPARED_MARKER).hasChild(PREPARED_SEGMENT + segment);
            }
            try {
                render.run();
            } finally {
                for (int i = 0; i < changed.size(); i++) changed.get(i).visible = visibility.get(i);
            }
        }
    }

    private static final class OptionalMods {
        private static final boolean EMF = ModList.get().isLoaded("entity_model_features");
    }
}

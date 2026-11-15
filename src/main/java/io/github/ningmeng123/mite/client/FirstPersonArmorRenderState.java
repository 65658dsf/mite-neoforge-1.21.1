package io.github.ningmeng123.mite.client;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Preserve shared armor parts while a first-person pass draws one arm. @author NingMeng123 */
public final class FirstPersonArmorRenderState implements AutoCloseable {
    private static final ThreadLocal<FirstPersonArmorRenderState> CURRENT = new ThreadLocal<>();
    private final FirstPersonArmorRenderState previous;
    private final Map<ModelPart, PartState> parts = new IdentityHashMap<>();

    private FirstPersonArmorRenderState() {
        previous = CURRENT.get();
        CURRENT.set(this);
    }

    public static FirstPersonArmorRenderState open() {
        return new FirstPersonArmorRenderState();
    }

    /** Capture the final armor model selected by NeoForge before ISMAH hides its descendants. */
    public static void capture(HumanoidModel<?> model) {
        FirstPersonArmorRenderState scope = CURRENT.get();
        if (scope == null) return;
        for (ModelPart part : List.of(model.head, model.hat, model.body,
                model.rightArm, model.leftArm, model.rightLeg, model.leftLeg)) {
            part.getAllParts().forEach(child -> scope.parts.computeIfAbsent(child, PartState::new));
        }
    }

    @Override
    public void close() {
        try {
            parts.forEach((part, state) -> state.restore(part));
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    private record PartState(float x, float y, float z, float xRot, float yRot, float zRot,
            float xScale, float yScale, float zScale, boolean visible, boolean skipDraw) {
        private PartState(ModelPart part) {
            this(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
                    part.xScale, part.yScale, part.zScale, part.visible, part.skipDraw);
        }

        private void restore(ModelPart part) {
            part.x = x;
            part.y = y;
            part.z = z;
            part.xRot = xRot;
            part.yRot = yRot;
            part.zRot = zRot;
            part.xScale = xScale;
            part.yScale = yScale;
            part.zScale = zScale;
            part.visible = visible;
            part.skipDraw = skipDraw;
        }
    }
}

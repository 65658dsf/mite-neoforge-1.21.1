package io.github.ningmeng123.mite.client;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/** FA still evaluates variables and faces; active weapon keyframes own the shared combat bones. @author NingMeng123 */
final class FreshAnimationsCombatHook extends EMFAnimationApi.EMFAnimationHook {
    private static final Set<String> BONES = Set.of("root", "head", "hat", "body", "right_arm", "left_arm",
            "right_leg", "left_leg", "jacket", "right_sleeve", "left_sleeve", "right_pants", "left_pants", "arms");
    private final Map<EMFModelPartRoot, Map<ModelPart, Pose>> pending = new IdentityHashMap<>();

    static void install() throws Exception {
        if (EMFAnimationApi.getApiVersion() >= 11) EMFAnimationApi.registerAnimationHook(new FreshAnimationsCombatHook());
    }

    @Override public boolean onAnimationStart(AnimationContext context, boolean cancelled) {
        EMFModelPartRoot root = context.animatingModelRoot();
        pending.remove(root);
        if (!(EMFAnimationApi.getCurrentEntity() instanceof LivingEntity entity)
                || !BetterCombatFreshAnimations.active(entity)) return true;
        var parts = root.getAllVanillaPartsByNameEMF();
        // Exclude cape, elytra, skull and other feature models with their own local coordinate system.
        if (!parts.containsKey("body") || !parts.containsKey("right_arm") || !parts.containsKey("left_arm")) return true;
        Map<ModelPart, Pose> poses = new IdentityHashMap<>();
        parts.forEach((name, part) -> {
            if (BONES.contains(name)) poses.put(part, new Pose(part, entity instanceof AbstractIllager
                    && (name.equals("arms") || name.equals("right_arm") || name.equals("left_arm"))));
        });
        pending.put(root, poses);
        return true;
    }

    @Override public void onAnimationEnd(AnimationContext context, boolean cancelled) {
        Map<ModelPart, Pose> poses = pending.remove(context.animatingModelRoot());
        if (poses != null) poses.forEach((part, pose) -> pose.restore(part));
    }

    // Preserve skin/first-person visibility. Illager melee explicitly unfolds the arms during its attack.
    private record Pose(PartPose pose, float scaleX, float scaleY, float scaleZ, Boolean visible) {
        Pose(ModelPart part, boolean keepVisibility) {
            this(part.storePose(), part.xScale, part.yScale, part.zScale, keepVisibility ? part.visible : null);
        }
        void restore(ModelPart part) {
            part.loadPose(pose);
            part.xScale = scaleX;
            part.yScale = scaleY;
            part.zScale = scaleZ;
            if (visible != null) part.visible = visible;
        }
    }
}

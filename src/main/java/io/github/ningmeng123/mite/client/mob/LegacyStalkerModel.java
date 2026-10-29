package io.github.ningmeng123.mite.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ningmeng123.mite.client.FreshAnimationsModelDriver;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Borrows zombie motion while retaining ModelInvisibleStalker's geometry, 64x32 UVs and opacity. @author NingMeng123 */
public final class LegacyStalkerModel<T extends Mob> extends HumanoidModel<T> {
    private final float opacity;
    private final HumanoidModel<T> animationModel;
    private final ModelPart animationRoot;
    private final Map<String, NativeBone> nativeBones = new HashMap<>();
    private boolean freshAnimationsApplied;
    private boolean freshHeadMapped;

    public LegacyStalkerModel(ModelPart root, float opacity) { this(root, opacity, null); }

    public LegacyStalkerModel(ModelPart root, float opacity, Supplier<ModelPart> animationRoot) {
        super(root);
        this.opacity = opacity;
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            if (index != 0) return;
            String name = path.substring(path.lastIndexOf('/') + 1);
            ModelPart part = nativePart(name);
            if (part != null) nativeBones.put(name, new NativeBone(part, cube));
        });
        // EMF binds the latest baked root to the next Model constructor. Bake this
        // hidden driver only after the visible 64x32 model has finished construction.
        if (animationRoot == null || !ModList.get().isLoaded("entity_model_features")) {
            this.animationRoot = null;
            animationModel = null;
        } else {
            ModelPart candidateRoot = animationRoot.get();
            HumanoidModel<T> candidateModel = new HumanoidModel<>(candidateRoot);
            boolean animated = FreshAnimationsModelDriver.hasAnimation(candidateModel);
            this.animationRoot = animated ? candidateRoot : null;
            animationModel = animated ? candidateModel : null;
        }
    }

    @Override public void setupAnim(T mob, float swing, float speed, float age, float yaw, float pitch) {
        // Native interpolation is unchanged when no resource-pack pose was borrowed.
        if (freshAnimationsApplied) nativeBones.values().forEach(bone -> bone.part().resetPose());
        super.setupAnim(mob, swing, speed, age, yaw, pitch);
        float attack = Mth.sin(attackTime * Mth.PI), eased = Mth.sin((1 - (1 - attackTime) * (1 - attackTime)) * Mth.PI);
        rightArm.zRot = 0; leftArm.zRot = 0;
        rightArm.yRot = -(.1F - attack * .6F); leftArm.yRot = .1F - attack * .6F;
        rightArm.xRot = -Mth.HALF_PI - (attack * 1.2F - eased * .4F);
        leftArm.xRot = rightArm.xRot;
        rightArm.zRot += Mth.cos(age * .09F) * .05F + .05F; leftArm.zRot -= Mth.cos(age * .09F) * .05F + .05F;
        rightArm.xRot += Mth.sin(age * .067F) * .05F; leftArm.xRot -= Mth.sin(age * .067F) * .05F;
        freshAnimationsApplied = false;
        freshHeadMapped = false;
        if (animationModel == null) return;
        copyPropertiesTo(animationModel);
        animationModel.swimAmount = swimAmount;
        animationRoot.getAllParts().forEach(ModelPart::resetPose);
        animationModel.setupAnim(mob, swing, speed, age, yaw, pitch);
        // The optional API returns false unless this model has actual CEM animation.
        freshAnimationsApplied = FreshAnimationsModelDriver.animate(mob, animationModel);
        if (!freshAnimationsApplied) return;

        animationModel.copyPropertiesTo(this);
        mapAnimatedGeometry();
        hat.copyFrom(head);
        // BetterCombatMobRendererMixin applies armed attacks after setupAnim, so
        // its weapon keyframes retain final control of the visible combat bones.
    }

    public boolean freshAnimationsApplied() { return freshAnimationsApplied; }
    public boolean freshHeadMapped() { return freshHeadMapped; }

    private void mapAnimatedGeometry() {
        Map<String, Integer> mapped = new HashMap<>();
        animationRoot.visit(new PoseStack(), (pose, path, index, cube) -> {
            if (index != 0 || !activeGeometry(path)) return;
            String name = boneName(path);
            NativeBone target = nativeBones.get(name);
            if (target == null || !target.sameSize(cube)) return;
            // FA's canonical head is empty: its real head is headwear/head2. A
            // cumulative matrix also handles packs that parent the head to body.
            int priority = path.matches(".*(?:^|/)(?:EMF_)?head2(?:/.*)?") ? 2 : 1;
            if (mapped.getOrDefault(name, 0) >= priority) return;
            Matrix4f transform = new Matrix4f(pose.pose()).translate(
                    ((cube.minX + cube.maxX) * .5F - target.centerX()) / 16F,
                    ((cube.minY + cube.maxY) * .5F - target.centerY()) / 16F,
                    ((cube.minZ + cube.maxZ) * .5F - target.centerZ()) / 16F);
            setTransform(target.part(), transform);
            mapped.put(name, priority);
            if (name.equals("head")) freshHeadMapped = true;
        });
    }

    private boolean activeGeometry(String path) {
        ModelPart part = animationRoot;
        if (!part.visible) return false;
        for (String name : path.split("/")) {
            if (name.isEmpty()) continue;
            part = part.getChild(name);
            if (!part.visible) return false;
        }
        // ModelPart.visit includes EMF's retained vanilla cubes even when their
        // wrapper skips drawing them. Only the active CEM geometry may drive us.
        return !part.skipDraw;
    }

    private ModelPart nativePart(String name) {
        return switch (name) {
            case "head" -> head;
            case "hat" -> hat;
            case "body" -> body;
            case "right_arm" -> rightArm;
            case "left_arm" -> leftArm;
            case "right_leg" -> rightLeg;
            case "left_leg" -> leftLeg;
            default -> null;
        };
    }

    private static String boneName(String path) {
        String[] chain = path.split("/");
        for (int i = chain.length - 1; i >= 0; i--) {
            String name = chain[i].replaceFirst("^EMF_", "");
            if (name.equals("head2")) return "head";
            if (name.equals("head") || name.equals("body") || name.equals("right_arm")
                    || name.equals("left_arm") || name.equals("right_leg") || name.equals("left_leg")) return name;
        }
        return "";
    }

    private static void setTransform(ModelPart part, Matrix4f matrix) {
        Vector3f position = matrix.getTranslation(new Vector3f());
        Vector3f scale = matrix.getScale(new Vector3f());
        // ModelPart uses Rz * Ry * Rx. Extract that order directly: the bundled
        // JOML quaternion Euler helper does not exactly invert rotationZYX.
        float sinY = Mth.clamp(-matrix.m02() / scale.x, -1F, 1F);
        float y = (float)Math.asin(sinY);
        float x, z;
        if (Math.abs(sinY) < .9999999F) {
            x = (float)Math.atan2(matrix.m12() / scale.y, matrix.m22() / scale.z);
            z = (float)Math.atan2(matrix.m01() / scale.x, matrix.m00() / scale.x);
        } else {
            x = 0F;
            z = (float)Math.atan2(-matrix.m10() / scale.y, matrix.m11() / scale.y);
        }
        part.setPos(position.x * 16F, position.y * 16F, position.z * 16F);
        part.setRotation(x, y, z);
        part.xScale = scale.x; part.yScale = scale.y; part.zScale = scale.z;
    }

    private record NativeBone(ModelPart part, float centerX, float centerY, float centerZ,
            float width, float height, float depth) {
        NativeBone(ModelPart part, ModelPart.Cube cube) {
            this(part, (cube.minX + cube.maxX) * .5F, (cube.minY + cube.maxY) * .5F,
                    (cube.minZ + cube.maxZ) * .5F, cube.maxX - cube.minX, cube.maxY - cube.minY, cube.maxZ - cube.minZ);
        }
        boolean sameSize(ModelPart.Cube cube) {
            return Math.abs(cube.maxX - cube.minX - width) < .001F
                    && Math.abs(cube.maxY - cube.minY - height) < .001F
                    && Math.abs(cube.maxZ - cube.minZ - depth) < .001F;
        }
    }

    @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, int color) {
        int alpha = Math.min(color >>> 24, Math.round(255F * opacity));
        super.renderToBuffer(pose, vertices, light, overlay, color & 0x00FFFFFF | alpha << 24);
    }
}

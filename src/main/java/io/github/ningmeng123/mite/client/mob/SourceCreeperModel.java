package io.github.ningmeng123.mite.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.FreshAnimationsModelDriver;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Borrows the base creeper's motion without applying random CEM skins to source UVs. @author NingMeng123 */
public final class SourceCreeperModel extends CreeperModel<Creeper> {
    private static final Set<String> BONES = Set.of("head", "body", "right_hind_leg", "left_hind_leg",
            "right_front_leg", "left_front_leg");
    private final Map<String, NativeBone> nativeBones = new HashMap<>();
    private final ModelPart animationRoot;
    private final CreeperModel<Creeper> animationModel;
    private boolean freshAnimationsApplied;
    private int freshMappedParts;

    public SourceCreeperModel(ModelPart root, Supplier<ModelPart> animationRootSupplier) {
        super(root);
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (index == 0 && BONES.contains(name)) nativeBones.put(name, new NativeBone(root.getChild(name), cube));
        });
        ModelPart driverRoot = null;
        CreeperModel<Creeper> driver = null;
        if (animationRootSupplier != null && ModList.get().isLoaded("entity_model_features")) {
            try {
                // Bake only after this visible model's constructor has bound its own source root.
                ModelPart candidate = animationRootSupplier.get();
                Loaded.baseVariant(candidate);
                CreeperModel<Creeper> candidateModel = new CreeperModel<>(candidate);
                if (FreshAnimationsModelDriver.hasAnimation(candidateModel)) {
                    driverRoot = candidate;
                    driver = candidateModel;
                }
            } catch (Exception | LinkageError failure) {
                Mite.LOGGER.warn("Cannot borrow optional resource-pack creeper animation; using source motion", failure);
            }
        }
        animationRoot = driverRoot;
        animationModel = driver;
    }

    @Override public void setupAnim(Creeper entity, float swing, float speed, float age, float yaw, float pitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        super.setupAnim(entity, swing, speed, age, yaw, pitch);
        freshAnimationsApplied = false;
        freshMappedParts = 0;
        if (animationModel == null) return;
        copyPropertiesTo(animationModel);
        animationRoot.getAllParts().forEach(ModelPart::resetPose);
        animationModel.setupAnim(entity, swing, speed, age, yaw, pitch);
        if (!FreshAnimationsModelDriver.animate(entity, animationModel)) return;
        Map<String, Integer> mapped = new HashMap<>();
        animationRoot.visit(new PoseStack(), (pose, path, index, cube) -> {
            if (index != 0 || !activeGeometry(path)) return;
            String name = boneName(path);
            NativeBone target = nativeBones.get(name);
            if (target == null || !target.sameShape(cube)) return;
            int priority = path.matches(".*(?:^|/)(?:EMF_)?head2(?:/.*)?") ? 2 : 1;
            if (mapped.getOrDefault(name, 0) >= priority) return;
            Matrix4f transform = new Matrix4f(pose.pose()).translate(
                    ((cube.minX + cube.maxX) * .5F - target.centerX()) / 16F,
                    ((cube.minY + cube.maxY) * .5F - target.centerY()) / 16F,
                    ((cube.minZ + cube.maxZ) * .5F - target.centerZ()) / 16F);
            setTransform(target.part(), transform);
            mapped.put(name, priority);
        });
        freshMappedParts = mapped.size();
        freshAnimationsApplied = freshMappedParts == BONES.size();
        if (!freshAnimationsApplied) {
            // Unsupported silhouettes must not leave a partly borrowed, disconnected source body.
            root().getAllParts().forEach(ModelPart::resetPose);
            super.setupAnim(entity, swing, speed, age, yaw, pitch);
        }
    }

    public boolean freshAnimationsApplied() { return freshAnimationsApplied; }
    public boolean freshHeadMapped() { return freshAnimationsApplied; }
    public int freshMappedParts() { return freshMappedParts; }

    private boolean activeGeometry(String path) {
        ModelPart part = animationRoot;
        if (!part.visible) return false;
        for (String name : path.split("/")) {
            if (name.isEmpty()) continue;
            part = part.getChild(name);
            if (!part.visible) return false;
        }
        return !part.skipDraw;
    }

    private static String boneName(String path) {
        String[] chain = path.split("/");
        for (int i = chain.length - 1; i >= 0; i--) {
            String name = chain[i].replaceFirst("^EMF_", "");
            if (name.equals("head2")) return "head";
            if (BONES.contains(name)) return name;
        }
        return "";
    }

    private static void setTransform(ModelPart part, Matrix4f matrix) {
        Vector3f position = matrix.getTranslation(new Vector3f());
        Vector3f scale = matrix.getScale(new Vector3f());
        if (Math.min(scale.x, Math.min(scale.y, scale.z)) < .000001F) {
            part.setPos(position.x * 16F, position.y * 16F, position.z * 16F);
            part.setRotation(0, 0, 0);
            part.xScale = scale.x; part.yScale = scale.y; part.zScale = scale.z;
            return;
        }
        float sinY = Mth.clamp(-matrix.m02() / scale.x, -1F, 1F);
        float x, z;
        if (Math.abs(sinY) < .9999999F) {
            x = (float)Math.atan2(matrix.m12() / scale.y, matrix.m22() / scale.z);
            z = (float)Math.atan2(matrix.m01() / scale.x, matrix.m00() / scale.x);
        } else {
            x = 0F;
            z = (float)Math.atan2(-matrix.m10() / scale.y, matrix.m11() / scale.y);
        }
        part.setPos(position.x * 16F, position.y * 16F, position.z * 16F);
        part.setRotation(x, (float)Math.asin(sinY), z);
        part.xScale = scale.x; part.yScale = scale.y; part.zScale = scale.z;
    }

    private record NativeBone(ModelPart part, float centerX, float centerY, float centerZ,
            float width, float height, float depth) {
        NativeBone(ModelPart part, ModelPart.Cube cube) {
            this(part, (cube.minX + cube.maxX) * .5F, (cube.minY + cube.maxY) * .5F,
                    (cube.minZ + cube.maxZ) * .5F, cube.maxX - cube.minX, cube.maxY - cube.minY, cube.maxZ - cube.minZ);
        }
        boolean sameShape(ModelPart.Cube cube) {
            // The source charged overlay keeps its original two-pixel inflation while
            // following the same body driver, so uniform cube padding may differ.
            float padding = cube.maxX - cube.minX - width;
            return Math.abs(cube.maxY - cube.minY - height - padding) < .001F
                    && Math.abs(cube.maxZ - cube.minZ - depth - padding) < .001F;
        }
    }

    private static final class Loaded {
        private static void baseVariant(ModelPart root) {
            if (root instanceof traben.entity_model_features.models.parts.EMFModelPartRoot emf) {
                // Only this hidden driver is pinned. Vanilla creepers retain their pack's
                // random rock/mud/skeleton forms and their corresponding custom textures.
                emf.variantTester = null;
                emf.setVariantStateTo(1);
            }
        }
    }
}

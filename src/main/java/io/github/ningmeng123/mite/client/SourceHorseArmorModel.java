package io.github.ningmeng123.mite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ningmeng123.mite.rules.HorseArmorUvRules;
import io.github.ningmeng123.mite.rules.HorseArmorUvRules.Box;
import io.github.ningmeng123.mite.rules.HorseArmorUvRules.Face;
import io.github.ningmeng123.mite.rules.HorseArmorUvRules.Rect;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

/** Keeps modern geometry, inflation, poses and animation; changes only the source atlas coordinates. @author NingMeng123 */
public final class SourceHorseArmorModel {
    private SourceHorseArmorModel() {}
    public static ModelPart bakeRoot() {
        ModelPart original = LayerDefinition.create(HorseModel.createBodyMesh(new CubeDeformation(.1F)), 64, 64).bakeRoot();
        Map<String, List<ModelPart.Cube>> cubes = new LinkedHashMap<>();
        original.visit(new PoseStack(), (pose, path, index, cube) -> cubes.computeIfAbsent(path, key -> new ArrayList<>()).add(cube));
        return copy(original, "", cubes);
    }
    private static ModelPart copy(ModelPart original, String path, Map<String, List<ModelPart.Cube>> cubes) {
        Map<String, ModelPart> children = new LinkedHashMap<>();
        for (String candidate : cubes.keySet()) {
            if (!candidate.startsWith(path + "/")) continue;
            String remainder = candidate.substring(path.length() + 1);
            String name = remainder.split("/", 2)[0];
            if (!children.containsKey(name)) children.put(name, copy(original.getChild(name), path + "/" + name, cubes));
        }
        List<ModelPart.Cube> mapped = cubes.getOrDefault(path, List.of()).stream()
                .map(cube -> (ModelPart.Cube)new AtlasCube(cube, HorseArmorUvRules.part(path))).toList();
        ModelPart result = new ModelPart(mapped, children);
        result.setInitialPose(original.getInitialPose());
        result.loadPose(original.getInitialPose());
        result.copyFrom(original);
        result.visible = original.visible;
        result.skipDraw = original.skipDraw;
        return result;
    }
    private record Quad(Face face, float[][] vertices, Rect texture) {}
    private static final class AtlasCube extends ModelPart.Cube {
        private final List<Quad> quads;
        AtlasCube(ModelPart.Cube original, List<Box> parts) {
            // ModelPart handles all transforms. Keep its public bounds, while using our precomputed source-UV faces.
            super(0, 0, original.minX, original.minY, original.minZ, original.maxX - original.minX,
                    original.maxY - original.minY, original.maxZ - original.minZ, 0, 0, 0, false, 64, 64, Set.of());
            Bounds bounds = new Bounds();
            original.compile(new PoseStack().last(), bounds, 0, 0, -1);
            quads = faces(bounds, parts);
        }
        @Override public void compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color) {
            Vector3f normal = new Vector3f(), position = new Vector3f();
            for (Quad quad : quads) {
                Vector3f axis = Direction.valueOf(quad.face().name()).step();
                pose.transformNormal(axis, normal);
                for (int corner = 0; corner < 4; corner++) {
                    float[] vertex = quad.vertices()[corner];
                    pose.pose().transformPosition(vertex[0], vertex[1], vertex[2], position);
                    consumer.addVertex(position.x(), position.y(), position.z(), color, quad.texture().u(corner),
                            quad.texture().v(corner), overlay, light, normal.x(), normal.y(), normal.z());
                }
            }
        }
    }
    private static List<Quad> faces(Bounds b, List<Box> parts) {
        List<Quad> result = new ArrayList<>();
        result.add(new Quad(Face.DOWN, vertices(b, Face.DOWN, b.minY, b.maxY), parts.getFirst().face(Face.DOWN)));
        result.add(new Quad(Face.UP, vertices(b, Face.UP, b.minY, b.maxY), parts.getLast().face(Face.UP)));
        int total = parts.stream().mapToInt(Box::weight).sum(), elapsed = 0;
        for (Box source : parts) {
            float from = b.minY + (b.maxY - b.minY) * (float)elapsed / total;
            elapsed += source.weight();
            float to = b.minY + (b.maxY - b.minY) * (float)elapsed / total;
            for (Face face : List.of(Face.WEST, Face.NORTH, Face.EAST, Face.SOUTH))
                result.add(new Quad(face, vertices(b, face, from, to), source.face(face)));
        }
        return List.copyOf(result);
    }
    private static float[][] vertices(Bounds b, Face face, float from, float to) {
        float x1 = b.minX, x2 = b.maxX, z1 = b.minZ, z2 = b.maxZ;
        return switch (face) {
            case DOWN -> new float[][]{{x2, from, z2}, {x1, from, z2}, {x1, from, z1}, {x2, from, z1}};
            case UP -> new float[][]{{x2, to, z1}, {x1, to, z1}, {x1, to, z2}, {x2, to, z2}};
            case WEST -> new float[][]{{x1, from, z1}, {x1, from, z2}, {x1, to, z2}, {x1, to, z1}};
            case NORTH -> new float[][]{{x2, from, z1}, {x1, from, z1}, {x1, to, z1}, {x2, to, z1}};
            case EAST -> new float[][]{{x2, from, z2}, {x2, from, z1}, {x2, to, z1}, {x2, to, z2}};
            case SOUTH -> new float[][]{{x1, from, z2}, {x2, from, z2}, {x2, to, z2}, {x1, to, z2}};
        };
    }
    /** Read the already-baked cube vertices once, preserving exact modern inflation including baby legs. */
    private static final class Bounds implements VertexConsumer {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            return this;
        }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}

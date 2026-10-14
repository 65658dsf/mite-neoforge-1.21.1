package io.github.ningmeng123.mite.rules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Original 1.6.4 bbp/bcp atlas faces, projected onto the corresponding modern horse parts. @author NingMeng123 */
public final class HorseArmorUvRules {
    public static final int ATLAS_SIZE = 128;
    public enum Face { DOWN, UP, WEST, NORTH, EAST, SOUTH }
    public record Rect(float u1, float v1, float u2, float v2) {
        public float u(int corner) { return (corner == 0 || corner == 3 ? u2 : u1) / ATLAS_SIZE; }
        public float v(int corner) { return (corner < 2 ? v1 : v2) / ATLAS_SIZE; }
    }
    public record Box(int u, int v, int width, int height, int depth, int weight) {
        public Box(int u, int v, int width, int height, int depth) { this(u, v, width, height, depth, height); }
        public Rect face(Face face) {
            return switch (face) {
                case DOWN -> new Rect(u + depth, v, u + depth + width, v + depth);
                case UP -> new Rect(u + depth + width, v + depth, u + depth + width * 2, v);
                case WEST -> new Rect(u, v + depth, u + depth, v + depth + height);
                case NORTH -> new Rect(u + depth, v + depth, u + depth + width, v + depth + height);
                case EAST -> new Rect(u + depth + width, v + depth, u + depth * 2 + width, v + depth + height);
                case SOUTH -> new Rect(u + depth * 2 + width, v + depth, u + depth * 2 + width * 2, v + depth + height);
            };
        }
    }
    private static final Map<String, List<Box>> PARTS = mappings();
    private HorseArmorUvRules() {}
    public static Map<String, List<Box>> parts() { return PARTS; }
    public static List<Box> part(String path) {
        List<Box> result = PARTS.get(path);
        if (result == null) throw new IllegalArgumentException("Missing source horse UV part: " + path);
        return result;
    }
    private static Map<String, List<Box>> mappings() {
        Map<String, List<Box>> parts = new LinkedHashMap<>();
        parts.put("/body", List.of(new Box(0, 34, 10, 10, 24)));
        parts.put("/body/tail", List.of(new Box(44, 0, 2, 2, 3, 3), new Box(38, 7, 3, 4, 7, 7), new Box(24, 3, 3, 4, 7, 7)));
        parts.put("/body/saddle", List.of(new Box(80, 0, 10, 1, 8)));
        parts.put("/head_parts", List.of(new Box(0, 12, 4, 14, 8)));
        parts.put("/head_parts/head", List.of(new Box(0, 0, 5, 5, 7)));
        parts.put("/head_parts/mane", List.of(new Box(58, 0, 2, 16, 4)));
        parts.put("/head_parts/upper_mouth", List.of(new Box(24, 18, 4, 3, 6), new Box(24, 27, 4, 2, 5)));
        parts.put("/head_parts/head/left_ear", List.of(new Box(0, 0, 2, 3, 1)));
        parts.put("/head_parts/head/right_ear", List.of(new Box(0, 0, 2, 3, 1)));
        parts.put("/head_parts/left_saddle_mouth", List.of(new Box(74, 13, 1, 2, 2)));
        parts.put("/head_parts/right_saddle_mouth", List.of(new Box(74, 13, 1, 2, 2)));
        parts.put("/head_parts/left_saddle_line", List.of(new Box(44, 10, 0, 3, 16)));
        parts.put("/head_parts/right_saddle_line", List.of(new Box(44, 5, 0, 3, 16)));
        parts.put("/head_parts/head_saddle", List.of(new Box(80, 12, 5, 5, 12)));
        parts.put("/head_parts/mouth_saddle_wrap", List.of(new Box(80, 12, 5, 5, 12)));
        List<Box> leftHind = List.of(new Box(78, 29, 4, 9, 5), new Box(78, 43, 3, 5, 3), new Box(78, 51, 4, 3, 4));
        List<Box> rightHind = List.of(new Box(96, 29, 4, 9, 5), new Box(96, 43, 3, 5, 3), new Box(96, 51, 4, 3, 4));
        List<Box> leftFront = List.of(new Box(44, 29, 3, 8, 4), new Box(44, 41, 3, 5, 3), new Box(44, 51, 4, 3, 4));
        List<Box> rightFront = List.of(new Box(60, 29, 3, 8, 4), new Box(60, 41, 3, 5, 3), new Box(60, 51, 4, 3, 4));
        for (String age : List.of("", "_baby")) {
            parts.put("/left_hind" + age + "_leg", leftHind);
            parts.put("/right_hind" + age + "_leg", rightHind);
            parts.put("/left_front" + age + "_leg", leftFront);
            parts.put("/right_front" + age + "_leg", rightFront);
        }
        return Map.copyOf(parts);
    }
}

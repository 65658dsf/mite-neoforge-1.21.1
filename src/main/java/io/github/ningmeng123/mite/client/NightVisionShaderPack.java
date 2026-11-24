package io.github.ningmeng123.mite.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Recognizes terrain programs that send albedo directly to the screen without light. @author NingMeng123 */
public final class NightVisionShaderPack {
    private static final int MAX_SOURCE_BYTES = 16_384;
    private static final int MAX_INCLUDE_DEPTH = 4;
    private static final String IDENTIFIER = "[a-zA-Z_]\\w*";
    private static final String SWIZZLE = "(?:\\s*\\.\\s*(?:rgb|rgba|xyz|xyzw))?";
    private static final String LEGACY_OUTPUT = "(?:gl_FragColor|gl_FragData\\s*\\[\\s*0\\s*])";
    private static final Pattern COMMENTS = Pattern.compile("(?s:/\\*.*?\\*/)|//[^\\r\\n]*");
    private static final Pattern INCLUDE = Pattern.compile("#\\s*include\\s+\"([^\"\\r\\n]+)\"");
    private static final Pattern PREPROCESSOR = Pattern.compile("(?m)^[ \\t]*#[ \\t]*(?!(?:version|extension)\\b)\\w+");
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{([^{}]*)}");
    private static final Pattern VERTEX_COLOR = Pattern.compile("\\b(" + IDENTIFIER
            + ")\\s*=\\s*(?:gl_Color|vaColor)" + SWIZZLE + "\\s*;");
    private static final Pattern COLOR_OUTPUT = Pattern.compile("\\bout\\s+vec[34]\\s+(" + IDENTIFIER + ")\\s*;");
    private static final Pattern SAMPLER = Pattern.compile("\\buniform\\s+sampler2D\\s+(" + IDENTIFIER + ")\\s*;");
    private static final Pattern LIGHT_INPUT = Pattern.compile("\\b(?:lightmap|iris_Lightmap|vaUV2|gl_MultiTexCoord[12]"
            + "|mc_midTexCoord|Sampler2|UV2)\\b|\\bgl_TexCoord\\s*\\[\\s*[12]\\s*]", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORLD_FOLDER = Pattern.compile("world-?\\d+");
    private static final Pattern DEFERRED_LIGHTING = Pattern.compile("(?:deferred\\d*|shadow)\\.(?:fsh|csh)");

    private NightVisionShaderPack() {}

    public static boolean matches(Path shaderRoot) {
        try {
            if (matchesTerrain(shaderRoot, shaderRoot)) return true;
            try (var children = Files.list(shaderRoot)) {
                for (Path dimension : children.filter(Files::isDirectory).toList()) {
                    if (WORLD_FOLDER.matcher(dimension.getFileName().toString()).matches()
                            && matchesTerrain(shaderRoot, dimension)) return true;
                }
            }
        } catch (IOException | InvalidPathException exception) {
            // Let Iris handle unreadable packs through its normal loader.
        }
        return false;
    }

    private static boolean matchesTerrain(Path root, Path dimension) throws IOException {
        Path vertex = dimension.resolve("gbuffers_terrain.vsh");
        Path fragment = dimension.resolve("gbuffers_terrain.fsh");
        if (!Files.isRegularFile(vertex) || !Files.isRegularFile(fragment)
                || Files.exists(dimension.resolve("gbuffers_terrain.gsh"))) return false;

        String vertexSource = readStage(root, vertex, 0);
        String fragmentSource = readStage(root, fragment, 0);
        if (vertexSource == null || fragmentSource == null
                || LIGHT_INPUT.matcher(vertexSource).find() || LIGHT_INPUT.matcher(fragmentSource).find()
                || PREPROCESSOR.matcher(vertexSource).find() || PREPROCESSOR.matcher(fragmentSource).find()) return false;

        var vertexMain = MAIN.matcher(vertexSource);
        var fragmentMain = MAIN.matcher(fragmentSource);
        if (!vertexMain.find() || !fragmentMain.find()) return false;

        var tint = VERTEX_COLOR.matcher(vertexMain.group(1));
        while (tint.find()) {
            String varying = tint.group(1);
            if (!declaresTint(vertexSource, "varying|out", varying)
                    || !declaresTint(fragmentSource, "varying|in", varying)) continue;
            var assignments = Pattern.compile("\\b" + varying + "(?:\\s*\\.\\s*" + IDENTIFIER
                    + ")?\\s*(?:[+*/-]?=|\\+\\+|--)").matcher(vertexMain.group(1));
            if (assignments.results().count() == 1 && outputsTextureTint(fragmentSource, fragmentMain.group(1), varying)
                    && !hasDeferredLighting(dimension) && (dimension.equals(root) || !hasDeferredLighting(root))) {
                return true;
            }
        }
        return false;
    }

    private static boolean declaresTint(String source, String qualifiers, String varying) {
        return Pattern.compile("\\b(?:" + qualifiers + ")\\s+vec[34]\\s+" + varying + "\\s*;").matcher(source).find();
    }

    private static boolean outputsTextureTint(String source, String body, String varying) {
        var sampler = SAMPLER.matcher(source);
        if (!sampler.find()) return false;
        String texture = sampler.group(1);
        if (sampler.find()) return false;
        String textureSample = "(?:texture2D|texture)\\s*\\(\\s*" + texture + "\\s*,\\s*"
                + IDENTIFIER + "(?:\\s*\\.\\s*[stxy]{2})?\\s*\\)" + SWIZZLE;
        String tint = "\\b" + varying + "\\b" + SWIZZLE;

        var fragmentOutput = COLOR_OUTPUT.matcher(source);
        String output = LEGACY_OUTPUT;
        if (fragmentOutput.find()) {
            output = "\\b" + fragmentOutput.group(1);
            if (fragmentOutput.find()) return false;
        }
        String multiply = "(?:" + textureSample + "\\s*\\*\\s*" + tint
                + "|" + tint + "\\s*\\*\\s*" + textureSample + ")";
        if (body.matches("\\s*" + output + "\\s*=\\s*" + multiply + "\\s*;\\s*")) return true;

        // Also recognize a sampled local whose RGB is tinted before a single output write.
        return body.matches("\\s*vec[34]\\s+(" + IDENTIFIER + ")\\s*=\\s*" + textureSample + "\\s*;\\s*"
                + "\\1" + SWIZZLE + "\\s*\\*=\\s*" + tint + "\\s*;\\s*"
                + output + "\\s*=\\s*\\1\\s*;\\s*");
    }

    private static boolean hasDeferredLighting(Path directory) throws IOException {
        try (var children = Files.list(directory)) {
            return children.anyMatch(path -> DEFERRED_LIGHTING.matcher(path.getFileName().toString()).matches());
        }
    }

    private static String readStage(Path root, Path path, int depth) throws IOException {
        if (depth > MAX_INCLUDE_DEPTH || !path.normalize().startsWith(root.normalize()) || !Files.isRegularFile(path)) return null;
        String source;
        try (var stream = Files.newInputStream(path)) {
            source = readSource(stream);
        }
        if (source == null) return null;
        var include = INCLUDE.matcher(source.strip());
        if (!include.matches()) return source;
        String file = include.group(1);
        Path included = file.startsWith("/") ? root.resolve(file.substring(1)) : path.getParent().resolve(file);
        return readStage(root, included.normalize(), depth + 1);
    }

    static String readSource(InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(MAX_SOURCE_BYTES + 1);
        if (bytes.length > MAX_SOURCE_BYTES) return null;
        return COMMENTS.matcher(new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "")).replaceAll(" ");
    }
}

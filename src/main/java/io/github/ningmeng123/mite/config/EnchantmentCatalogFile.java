package io.github.ningmeng123.mite.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/** Metadata persistence is separate from the player's editable gameplay overrides. @author NingMeng123 */
final class EnchantmentCatalogFile {
    record Definition(String id, int defaultMaxLevel, String descriptionJson) {
        Definition {
            if (id == null || id.length() > 512 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Invalid enchantment registry ID");
            if (defaultMaxLevel < 1 || defaultMaxLevel > 255)
                throw new IllegalArgumentException("Invalid enchantment default maximum level");
            if (descriptionJson == null || descriptionJson.length() > 65536)
                throw new IllegalArgumentException("Invalid enchantment description");
            String json = descriptionJson.stripLeading();
            if (json.isEmpty() || json.charAt(0) != '{' && json.charAt(0) != '[' && json.charAt(0) != '"')
                throw new IllegalArgumentException("Expected a JSON chat component");
            JsonParser.parseString(descriptionJson);
        }
    }

    private EnchantmentCatalogFile() {}

    static Map<String, Definition> read(Reader source) throws IOException {
        try {
            JsonObject root = JsonParser.parseReader(source).getAsJsonObject();
            if (root.get("schema").getAsBigDecimal().intValueExact() != 1)
                throw new IllegalArgumentException("Unsupported enchantment catalog schema");
            Map<String, Definition> definitions = new TreeMap<>();
            for (var value : root.getAsJsonArray("enchantments")) {
                JsonObject entry = value.getAsJsonObject();
                Definition definition = new Definition(entry.get("id").getAsString(),
                        entry.get("defaultMaxLevel").getAsBigDecimal().intValueExact(), entry.get("descriptionJson").getAsString());
                definitions.put(definition.id(), definition);
            }
            return Map.copyOf(definitions);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid enchantment catalog document", exception);
        }
    }

    /** Retain entries absent from this world; update only observed default metadata. */
    static Map<String, Definition> merge(Map<String, Definition> previous, Collection<Definition> observed) {
        Map<String, Definition> merged = new TreeMap<>(previous);
        for (Definition definition : observed) merged.put(definition.id(), definition);
        return Map.copyOf(merged);
    }

    static void write(Path destination, Collection<Definition> definitions) throws IOException {
        Path file = destination.toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        var root = new JsonObject();
        root.addProperty("schema", 1);
        var entries = new JsonArray();
        definitions.stream().sorted(java.util.Comparator.comparing(Definition::id)).forEach(definition -> {
            var entry = new JsonObject();
            entry.addProperty("id", definition.id());
            entry.addProperty("defaultMaxLevel", definition.defaultMaxLevel());
            entry.addProperty("descriptionJson", definition.descriptionJson());
            entries.add(entry);
        });
        root.add("enchantments", entries);
        Path temporary = Files.createTempFile(file.getParent(), ".mite-enchantment-catalog-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n", StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}

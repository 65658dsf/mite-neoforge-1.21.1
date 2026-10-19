package io.github.ningmeng123.mite.config;

import io.github.ningmeng123.mite.Mite;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

/** Bundled defaults plus persistent registry discoveries allow editing before opening a world. @author NingMeng123 */
public final class EnchantmentCatalog {
    public static final String RESOURCE = "/mite/enchantment-defaults.json";
    public static final String FILE_NAME = "mite-enchantment-catalog.json";
    private static Map<String, EnchantmentCatalogFile.Definition> known;
    private static List<Entry> published = List.of();

    private EnchantmentCatalog() {}

    public record Entry(String id, int defaultMaxLevel, String descriptionJson) {
        public Entry {
            // Use the same validation for imported registry records and saved documents.
            new EnchantmentCatalogFile.Definition(id, defaultMaxLevel, descriptionJson);
        }
        public Component name() {
            try {
                Component component = Component.Serializer.fromJson(descriptionJson, RegistryAccess.EMPTY);
                if (component != null) return component;
            } catch (RuntimeException ignored) {
                // An unavailable optional registry in a rich description must not prevent offline editing.
            }
            return Component.literal(id);
        }
    }

    public static synchronized List<Entry> entries() {
        load();
        return published;
    }

    /** Raw definitions bypass configured getMaxLevel(), so a player's overrides cannot become defaults. */
    public static void observe(RegistryAccess access) {
        var discovered = new ArrayList<Entry>();
        access.registry(Registries.ENCHANTMENT).ifPresent(registry -> registry.entrySet().forEach(entry -> {
            var enchantment = entry.getValue();
            String id = entry.getKey().location().toString();
            String description;
            try { description = Component.Serializer.toJson(enchantment.description(), access); }
            catch (RuntimeException exception) { description = "{\"text\":\"" + id + "\"}"; }
            discovered.add(new Entry(id, enchantment.definition().maxLevel(), description));
        }));
        mergeDefaults(discovered);
    }

    /** Never writes MiteConfig caps or disabled IDs; discoveries only enrich the directory. */
    public static synchronized void mergeDefaults(Collection<Entry> discovered) {
        load();
        var definitions = discovered.stream().map(entry -> new EnchantmentCatalogFile.Definition(
                entry.id(), entry.defaultMaxLevel(), entry.descriptionJson())).toList();
        var merged = EnchantmentCatalogFile.merge(known, definitions);
        if (merged.equals(known)) return;
        known = merged;
        publish();
        persist();
    }

    public static Path file() { return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME); }

    private static void load() {
        if (known != null) return;
        known = Map.of();
        try (var source = EnchantmentCatalog.class.getResourceAsStream(RESOURCE)) {
            if (source != null) known = EnchantmentCatalogFile.read(new InputStreamReader(source, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            Mite.LOGGER.warn("Cannot read bundled enchantment defaults; registry discovery remains available", exception);
        }
        boolean readable = true;
        boolean needsWrite = true;
        Path file = file();
        if (Files.isRegularFile(file)) {
            try (var source = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                var cached = EnchantmentCatalogFile.read(source);
                known = EnchantmentCatalogFile.merge(known, cached.values());
                needsWrite = !known.equals(cached);
            } catch (IOException | RuntimeException exception) {
                readable = false;
                Mite.LOGGER.warn("Cannot read enchantment catalog {}; using bundled metadata without changing gameplay overrides", file, exception);
            }
        }
        publish();
        // A corrupt cache is retained for recovery until a live registry can rebuild its metadata.
        if (readable && needsWrite) persist();
    }

    private static void publish() {
        published = known.values().stream().sorted(java.util.Comparator.comparing(EnchantmentCatalogFile.Definition::id))
                .map(entry -> new Entry(entry.id(), entry.defaultMaxLevel(), entry.descriptionJson())).toList();
    }

    private static void persist() {
        try { EnchantmentCatalogFile.write(file(), known.values()); }
        catch (IOException | RuntimeException exception) { Mite.LOGGER.warn("Cannot save enchantment catalog {}; keeping discovered metadata in memory", file(), exception); }
    }
}

package io.github.ningmeng123.mite.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import io.github.ningmeng123.mite.rules.MonsterGrowthRules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Exports validated MITE and optional ITE common configurations for server installation. @author NingMeng123 */
public final class GameplayConfigExport {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final String ITE_MOD_ID = "modern_ite";

    public record ExportedBundle(Path primaryFile, List<Path> files) {
        public ExportedBundle { files = List.copyOf(files); }
    }

    private record Document(String fileName, ModConfigSpec spec, CommentedConfig content) {}

    private GameplayConfigExport() {}

    /**
     * Returns a new exports/mite-timestamp/mite-common.toml containing the local gameplay settings.
     * The active configuration and any earlier export are never overwritten.
     */
    public static Path export(Path gameDirectory) throws IOException {
        return exportBundle(gameDirectory).primaryFile();
    }

    /** Exports MITE and every registered ITE common configuration as one complete server package. */
    public static ExportedBundle exportBundle(Path gameDirectory) throws IOException {
        Path directory = null;
        var createdFiles = new ArrayList<Path>();
        try {
            MiteConfig.refreshEnchantmentRules();
            var content = captureLocalSettings();
            validate(content);
            var documents = new ArrayList<Document>();
            documents.add(new Document("mite-common.toml", MiteConfig.SPEC, content));
            documents.addAll(captureIteSettings());
            var names = new HashSet<String>();
            for (var document : documents) {
                String name = document.fileName();
                // COMMON files are installed directly into config; never let a registration escape
                // this invocation's fresh directory or overwrite another document in the package.
                if (name.isBlank() || name.contains("/") || name.contains("\\") || name.contains(":")
                        || !name.endsWith(".toml") || !names.add(name.toLowerCase(java.util.Locale.ROOT)))
                    throw new IOException("Unsupported or duplicate common configuration filename: " + name);
                validate(document);
            }
            Path exports = gameDirectory.toAbsolutePath().normalize().resolve("exports");
            Files.createDirectories(exports);
            directory = createExportDirectory(exports);
            for (var document : documents) {
                Path file = directory.resolve(document.fileName());
                try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    createdFiles.add(file);
                    new TomlWriter().write(document.content(), writer);
                }
                CommentedConfig written;
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    written = new TomlParser().parse(reader);
                }
                validate(new Document(document.fileName(), document.spec(), written));
                requireSameValues(document.content(), written, List.of());
            }
            return new ExportedBundle(createdFiles.getFirst(), createdFiles);
        } catch (IOException | RuntimeException exception) {
            IOException failure = exception instanceof IOException io ? io
                    : new IOException("Unable to export gameplay configuration", exception);
            // Only remove files and the directory created by this invocation; never recurse into exports.
            for (int i = createdFiles.size() - 1; i >= 0; i--) removeIncomplete(createdFiles.get(i), failure);
            if (directory != null) removeIncomplete(directory, failure);
            throw failure;
        }
    }

    private static List<Document> captureIteSettings() throws IOException {
        var registered = List.copyOf(ModConfigs.getModConfigs(ITE_MOD_ID));
        boolean installed = ModList.get().isLoaded(ITE_MOD_ID) || !registered.isEmpty();
        if (!installed) return List.of();
        var common = registered.stream().filter(config -> config.getType() == ModConfig.Type.COMMON)
                .sorted(Comparator.comparing(ModConfig::getFileName)).toList();
        if (common.isEmpty()) throw new IOException("ITE is installed, but no ITE common configuration is registered");
        var documents = new ArrayList<Document>();
        for (var config : common) {
            if (!(config.getSpec() instanceof ModConfigSpec spec) || !spec.isLoaded()
                    || config.getLoadedConfig() == null)
                throw new IOException("ITE common configuration is unavailable: " + config.getFileName());
            if (!spec.isCorrect(config.getLoadedConfig().config()))
                throw new IOException("ITE common configuration failed validation: " + config.getFileName());
            var content = TomlFormat.newConfig(LinkedHashMap::new);
            copyDefinitions(spec, spec.getValues(), content, List.of(), true);
            documents.add(new Document(config.getFileName(), spec, content));
        }
        return documents;
    }

    private static CommentedConfig captureLocalSettings() {
        var content = TomlFormat.newConfig(LinkedHashMap::new);
        copyDefinitions(MiteConfig.SPEC, MiteConfig.SPEC.getValues(), content, List.of(), false);
        var local = MiteConfig.localSnapshot();
        var growth = local.monsterSettings();
        // Invalid reloads retain this complete last-valid growth revision at runtime. Export that
        // revision rather than publishing rejected formula text to a server with a different history.
        put(content, MiteConfig.MONSTER_GROWTH, local.growth());
        put(content, MiteConfig.MONSTER_HEALTH_GROWTH, local.healthGrowth());
        put(content, MiteConfig.MONSTER_DAMAGE_GROWTH, local.damageGrowth());
        put(content, MiteConfig.MONSTER_HEALTH_BASE, growth.healthBase());
        put(content, MiteConfig.MONSTER_DAMAGE_BASE, growth.damageBase());
        put(content, MiteConfig.MONSTER_SKELETON_REFERENCE, growth.skeletonReference());
        put(content, MiteConfig.MONSTER_HEALTH_DAILY_PERCENT, growth.healthRate() * 100D);
        put(content, MiteConfig.MONSTER_DAMAGE_DAILY_PERCENT, growth.damageRate() * 100D);
        put(content, MiteConfig.MONSTER_HEALTH_FORMULA, growth.healthFormula());
        put(content, MiteConfig.MONSTER_DAMAGE_FORMULA, growth.damageFormula());
        put(content, MiteConfig.MONSTER_HEALTH_BLOOD_MOON_FORMULA, growth.healthBloodMoonFormula());
        put(content, MiteConfig.MONSTER_DAMAGE_BLOOD_MOON_FORMULA, growth.damageBloodMoonFormula());
        return content;
    }

    private static void copyDefinitions(ModConfigSpec spec, UnmodifiableConfig definitions, CommentedConfig destination,
                                        List<String> parent, boolean raw) {
        for (var entry : definitions.valueMap().entrySet()) {
            var path = new ArrayList<>(parent);
            path.add(entry.getKey());
            if (entry.getValue() instanceof UnmodifiableConfig child) {
                copyDefinitions(spec, child, destination, path, raw);
                String comment = spec.getLevelComment(path);
                if (comment != null) destination.setComment(path, comment);
            } else if (entry.getValue() instanceof ModConfigSpec.ConfigValue<?> setting) {
                Object value = spec.isLoaded() ? (raw ? setting.getRaw() : setting.get()) : setting.getDefault();
                destination.set(setting.getPath(), copyValue(value));
                String comment = setting.getSpec().getComment();
                if (comment != null) destination.setComment(setting.getPath(), comment);
            } else {
                throw new IllegalStateException("Unsupported configuration definition at " + String.join(".", path));
            }
        }
    }

    private static Object copyValue(Object value) {
        if (value instanceof Enum<?> constant) return constant.name();
        return value instanceof List<?> list ? list.stream().map(GameplayConfigExport::copyValue).toList() : value;
    }

    private static <T> void put(CommentedConfig content, ModConfigSpec.ConfigValue<T> setting, T value) {
        content.set(setting.getPath(), value);
    }

    private static void validate(CommentedConfig content) throws IOException {
        if (!MiteConfig.SPEC.isCorrect(content))
            throw new IOException("Exported gameplay configuration does not match its configuration specification");
        var settings = new MonsterGrowthRules.Settings(
                number(content, MiteConfig.MONSTER_HEALTH_BASE), number(content, MiteConfig.MONSTER_DAMAGE_BASE),
                number(content, MiteConfig.MONSTER_SKELETON_REFERENCE),
                number(content, MiteConfig.MONSTER_HEALTH_DAILY_PERCENT) / 100D,
                number(content, MiteConfig.MONSTER_DAMAGE_DAILY_PERCENT) / 100D,
                content.get(MiteConfig.MONSTER_HEALTH_FORMULA.getPath()),
                content.get(MiteConfig.MONSTER_DAMAGE_FORMULA.getPath()),
                content.get(MiteConfig.MONSTER_HEALTH_BLOOD_MOON_FORMULA.getPath()),
                content.get(MiteConfig.MONSTER_DAMAGE_BLOOD_MOON_FORMULA.getPath()));
        double overall = number(content, MiteConfig.MONSTER_GROWTH);
        var validation = MonsterGrowthRules.validate(settings,
                overall * number(content, MiteConfig.MONSTER_HEALTH_GROWTH),
                overall * number(content, MiteConfig.MONSTER_DAMAGE_GROWTH));
        if (!validation.valid()) throw new IOException("Exported growth formulas failed validation: " + validation.message());
    }

    private static void validate(Document document) throws IOException {
        if (!document.spec().isCorrect(document.content()))
            throw new IOException("Exported configuration failed validation: " + document.fileName());
        if (document.spec() == MiteConfig.SPEC) validate(document.content());
    }

    private static double number(UnmodifiableConfig content, ModConfigSpec.DoubleValue setting) {
        return ((Number)content.get(setting.getPath())).doubleValue();
    }

    private static void requireSameValues(UnmodifiableConfig expected, UnmodifiableConfig actual,
                                          List<String> parent) throws IOException {
        for (var entry : expected.valueMap().entrySet()) {
            var path = new ArrayList<>(parent);
            path.add(entry.getKey());
            Object written = actual.valueMap().get(entry.getKey());
            if (entry.getValue() instanceof UnmodifiableConfig child && written instanceof UnmodifiableConfig writtenChild) {
                requireSameValues(child, writtenChild, path);
            } else if (!sameValue(entry.getValue(), written)) {
                throw new IOException("Export changed configuration value " + String.join(".", path));
            }
        }
    }

    private static boolean sameValue(Object expected, Object actual) {
        if (expected instanceof Number left && actual instanceof Number right)
            return new java.math.BigDecimal(left.toString()).compareTo(new java.math.BigDecimal(right.toString())) == 0;
        if (expected instanceof List<?> left && actual instanceof List<?> right) {
            if (left.size() != right.size()) return false;
            for (int i = 0; i < left.size(); i++) if (!sameValue(left.get(i), right.get(i))) return false;
            return true;
        }
        return Objects.equals(expected, actual);
    }

    private static Path createExportDirectory(Path exports) throws IOException {
        String stem = "mite-" + TIMESTAMP.format(LocalDateTime.now());
        for (int collision = 0; ; collision++) {
            Path candidate = exports.resolve(collision == 0 ? stem : stem + "-" + collision);
            try {
                return Files.createDirectory(candidate);
            } catch (FileAlreadyExistsException ignored) {
                // A second click or another instance must get its own export.
            }
        }
    }

    private static void removeIncomplete(Path path, IOException failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException | RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}

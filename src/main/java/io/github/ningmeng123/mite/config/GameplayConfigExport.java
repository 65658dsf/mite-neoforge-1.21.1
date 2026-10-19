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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Exports a standalone, validated common configuration for server installation. @author NingMeng123 */
public final class GameplayConfigExport {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private GameplayConfigExport() {}

    /**
     * Returns a new exports/mite-timestamp/mite-common.toml containing the local gameplay settings.
     * The active configuration and any earlier export are never overwritten.
     */
    public static Path export(Path gameDirectory) throws IOException {
        Path directory = null;
        Path file = null;
        boolean fileCreated = false;
        try {
            MiteConfig.refreshEnchantmentRules();
            var content = captureLocalSettings();
            validate(content);
            Path exports = gameDirectory.toAbsolutePath().normalize().resolve("exports");
            Files.createDirectories(exports);
            directory = createExportDirectory(exports);
            file = directory.resolve("mite-common.toml");
            try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                fileCreated = true;
                new TomlWriter().write(content, writer);
            }
            CommentedConfig written;
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                written = new TomlParser().parse(reader);
            }
            validate(written);
            requireSameValues(content, written, List.of());
            return file;
        } catch (IOException | RuntimeException exception) {
            IOException failure = exception instanceof IOException io ? io
                    : new IOException("Unable to export gameplay configuration", exception);
            // Only remove the file and directory created by this invocation; never recurse into exports.
            if (fileCreated) removeIncomplete(file, failure);
            if (directory != null) removeIncomplete(directory, failure);
            throw failure;
        }
    }

    private static CommentedConfig captureLocalSettings() {
        var content = TomlFormat.newConfig(LinkedHashMap::new);
        copyDefinitions(MiteConfig.SPEC.getValues(), content, List.of());
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

    private static void copyDefinitions(UnmodifiableConfig definitions, CommentedConfig destination,
                                        List<String> parent) {
        for (var entry : definitions.valueMap().entrySet()) {
            var path = new ArrayList<>(parent);
            path.add(entry.getKey());
            if (entry.getValue() instanceof UnmodifiableConfig child) {
                copyDefinitions(child, destination, path);
                String comment = MiteConfig.SPEC.getLevelComment(path);
                if (comment != null) destination.setComment(path, comment);
            } else if (entry.getValue() instanceof ModConfigSpec.ConfigValue<?> setting) {
                Object value = MiteConfig.SPEC.isLoaded() ? setting.get() : setting.getDefault();
                destination.set(setting.getPath(), copyValue(value));
                String comment = setting.getSpec().getComment();
                if (comment != null) destination.setComment(setting.getPath(), comment);
            } else {
                throw new IllegalStateException("Unsupported configuration definition at " + String.join(".", path));
            }
        }
    }

    private static Object copyValue(Object value) {
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
            } else if (!Objects.equals(entry.getValue(), written)) {
                throw new IOException("Export changed configuration value " + String.join(".", path));
            }
        }
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

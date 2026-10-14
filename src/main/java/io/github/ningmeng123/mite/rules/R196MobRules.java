package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.entity.MobKind;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;

/** Inherited effective source baselines; size, taming and material transitions remain entity-owned. @author NingMeng123 */
public final class R196MobRules {
    public record Entry(double maxHealth, double attackDamage, double movementSpeed, double followRange,
                        double knockbackResistance, int experience, float width, float height) {}
    private static final Map<MobKind, Entry> ENTRIES = load();
    private R196MobRules() {}
    public static Entry entry(MobKind kind) { return Objects.requireNonNull(ENTRIES.get(kind), "Unmapped source mob " + kind); }
    public static Map<MobKind, Entry> entries() { return ENTRIES; }
    private static final JsonObject SOURCE_DATA = readSource();
    public static JsonObject sourceData() { return SOURCE_DATA; }
    public static JsonObject sourceRow(MobKind kind) {
        for (var value : SOURCE_DATA.getAsJsonArray("entities")) {
            JsonObject row = value.getAsJsonObject();
            if (row.get("legacy_id").getAsInt() == kind.sourceId()) return row;
        }
        throw new IllegalArgumentException("Missing source identity " + kind);
    }

    public static AttributeSupplier.Builder attributes(MobKind kind) {
        Entry value = entry(kind);
        AttributeSupplier.Builder builder = kind.family() == MobKind.Family.ZOMBIE ? Zombie.createAttributes()
                : kind.family() == MobKind.Family.WOLF ? Wolf.createAttributes() : Monster.createMonsterAttributes();
        return builder.add(Attributes.MAX_HEALTH, value.maxHealth()).add(Attributes.ATTACK_DAMAGE, value.attackDamage())
                .add(Attributes.MOVEMENT_SPEED, value.movementSpeed()).add(Attributes.FOLLOW_RANGE, value.followRange())
                .add(Attributes.KNOCKBACK_RESISTANCE, value.knockbackResistance()).add(Attributes.ARMOR, 0);
    }

    private static Map<MobKind, Entry> load() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(R196MobRules.class.getResourceAsStream(
                "/mite/mobs-031.json")), StandardCharsets.UTF_8)) {
            Map<MobKind, Entry> result = new EnumMap<>(MobKind.class);
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            for (var element : data.getAsJsonArray("entities")) {
                JsonObject row = element.getAsJsonObject();
                int id = row.get("legacy_id").getAsInt();
                if (id < 512 || id > 540) continue;
                MobKind kind = java.util.Arrays.stream(MobKind.values()).filter(k -> k.sourceId() == id).findFirst().orElseThrow();
                JsonObject base = row.getAsJsonObject("base_attributes");
                Entry value = new Entry(number(base, "max_health"), number(base, "attack_damage"), number(base, "movement_speed"),
                        number(base, "follow_range"), number(base, "knockback_resistance"), row.get("experience").getAsInt(),
                        row.get("width").getAsFloat(), row.get("height").getAsFloat());
                if (value.maxHealth() <= 0 || value.width() <= 0 || value.height() <= 0 || result.put(kind, value) != null)
                    throw new IllegalArgumentException("Invalid source mob " + id);
            }
            if (result.size() != MobKind.values().length) throw new IllegalArgumentException("Incomplete R196 living registry");
            return Collections.unmodifiableMap(result);
        } catch (Exception failure) { throw new IllegalStateException("Cannot load R196 mob rules", failure); }
    }
    private static double number(JsonObject object, String key) { return object.get(key).getAsDouble(); }
    private static JsonObject readSource() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(R196MobRules.class.getResourceAsStream("/mite/mobs-031.json")), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception failure) { throw new IllegalStateException("Cannot read source entity registry", failure); }
    }
}

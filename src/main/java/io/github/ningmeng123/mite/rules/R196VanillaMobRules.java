package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonObject;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;

/** Constructor baselines and exact getters for registered source identities retained by modern Minecraft. @author NingMeng123 */
public final class R196VanillaMobRules {
    public record Entry(String sourceClass, double health, double attack, double speed, double follow, double knockback,
                        float width, float height, int experience, boolean sourceAttackAttribute) {}
    private static final class Data { static final Map<EntityType<?>, Entry> VALUES = load(); }
    private static final Set<EntityType<?>> UNDEAD = Set.of(EntityType.SKELETON, EntityType.WITHER_SKELETON,
            EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN, EntityType.WITHER, EntityType.ZOMBIE_HORSE, EntityType.SKELETON_HORSE);
    private R196VanillaMobRules() {}
    public static Map<EntityType<?>, Entry> entries() { return Data.VALUES; }
    public static Entry entry(EntityType<?> type) { return Data.VALUES.get(type); }
    public static boolean managed(EntityType<?> type) { return entry(type) != null; }
    public static boolean healsWithTime(EntityType<?> type) { return managed(type) && !UNDEAD.contains(type) && type != EntityType.SNOW_GOLEM; }
    public static int experience(Mob mob) {
        if (mob instanceof Slime slime) return slime.getSize() * (slime instanceof MagmaCube ? 3 : 1);
        return entry(mob.getType()).experience();
    }
    private static Map<EntityType<?>, Entry> load() {
        Map<EntityType<?>, Entry> result = new IdentityHashMap<>(); Map<Integer, Entry> byId = new java.util.HashMap<>();
        JsonObject data = R196MobRules.sourceData();
        for (var element : data.getAsJsonArray("entities")) {
            JsonObject row = element.getAsJsonObject();
            if (!row.has("base_attributes") || row.get("base_attributes").isJsonNull()
                    || !row.has("modern_id") || row.get("modern_id").isJsonNull()
                    || !row.get("modern_id").getAsString().startsWith("minecraft:")
                    || !row.has("variants") || row.getAsJsonArray("variants").isEmpty()) continue;
            JsonObject attributes = row.getAsJsonObject("base_attributes"); boolean sourceAttack = false;
            for (var attribute : row.getAsJsonArray("variants").get(0).getAsJsonObject().getAsJsonArray("attributes"))
                sourceAttack |= attribute.getAsJsonObject().get("symbol").getAsString().equals("tp.e");
            Entry entry = new Entry(row.get("source_class").getAsString(), attributes.get("max_health").getAsDouble(),
                    attributes.get("attack_damage").getAsDouble(), attributes.get("movement_speed").getAsDouble(),
                    attributes.get("follow_range").getAsDouble(), attributes.get("knockback_resistance").getAsDouble(),
                    row.get("width").getAsFloat(), row.get("height").getAsFloat(), row.get("experience").getAsInt(), sourceAttack);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(row.get("modern_id").getAsString()));
            result.put(type, entry); byId.put(row.get("legacy_id").getAsInt(), entry);
        }
        for (var value : data.getAsJsonArray("modern_split_identities")) {
            JsonObject split = value.getAsJsonObject(); Entry from = byId.get(split.get("legacy_id").getAsInt());
            for (var id : split.getAsJsonArray("modern_extra_ids")) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id.getAsString()));
                float width = type == EntityType.WITHER_SKELETON ? .72F : from.width(), height = type == EntityType.WITHER_SKELETON ? 2.34F : from.height();
                boolean undeadHorse = type == EntityType.ZOMBIE_HORSE || type == EntityType.SKELETON_HORSE;
                double health = undeadHorse ? 15 : from.health(), speed = undeadHorse ? .2F : type == EntityType.DONKEY || type == EntityType.MULE ? .175F : from.speed();
                result.put(type, new Entry(from.sourceClass(), health, from.attack(), speed, from.follow(), from.knockback(), width, height, from.experience(), from.sourceAttackAttribute()));
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }
}

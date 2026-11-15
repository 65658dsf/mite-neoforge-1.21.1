package io.github.ningmeng123.mite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Limits the original Polaris resource pack to daggers and sai. @author NingMeng123 */
public final class PolarisWeaponAnimations {
    private static final List<String> CLIPS = List.of("sword_in", "sword_1", "sword_2", "sword_3", "Polaris");
    private static final String PREFIX = "mite_polaris_";

    private PolarisWeaponAnimations() {}

    public static boolean polarisPack(String source) {
        return source != null && source.toLowerCase(Locale.ROOT).contains("polaris");
    }

    private static boolean shortWeapon(ItemStack stack) {
        var simply = SimplySwordsCompat.definition(stack);
        if (simply != null) return simply.family().equals("sai");
        var profile = EquipmentDurability.toolProfile(stack);
        return profile != null && profile.family() == ToolFamily.DAGGER;
    }

    /** Discard Polaris 1.4's copied global template before Punchy merges pack priorities. */
    public static JsonObject scopeConfig(JsonObject json, String source) {
        if (!polarisPack(source)) return json;
        JsonObject scoped = new JsonObject();
        JsonObject groups = json.getAsJsonObject("item");
        if (groups == null || !groups.has("SWORD")) return scoped;
        JsonObject sword = groups.getAsJsonObject("SWORD").deepCopy();
        sword.addProperty("kind", "SWORD");
        renameReferences(sword);
        JsonArray animations = sword.getAsJsonArray("customAnimation");
        if (animations != null) for (JsonElement animation : animations) {
            JsonObject entry = animation.getAsJsonObject();
            if (entry.has("type") && "attack".equals(entry.get("type").getAsString()))
                entry.addProperty("critical_hit", PREFIX + "sword_3");
        }
        // An empty named combo selects Punchy's custom attack path and retains its support hand.
        JsonObject combo = new JsonObject();
        combo.addProperty("type", "melee_combo");
        JsonArray combat = new JsonArray();
        combat.add(combo);
        sword.add("betterCombat", combat);
        JsonObject specific = new JsonObject();
        for (Item item : BuiltInRegistries.ITEM) {
            if (shortWeapon(new ItemStack(item)))
                specific.add(BuiltInRegistries.ITEM.getKey(item).toString(), sword.deepCopy());
        }
        scoped.add("itemSpecific", specific);
        return scoped;
    }

    /** Polaris also ships a full player.animation.json which would override default tool clips. */
    public static void scopeClips(JsonObject json, String source) {
        if (!polarisPack(source)) return;
        JsonObject animations = json.getAsJsonObject("animations");
        if (animations == null) return;
        JsonObject scoped = new JsonObject();
        for (String name : CLIPS) {
            if (animations.has(name)) scoped.add(PREFIX + name, animations.get(name));
        }
        json.add("animations", scoped);
    }

    private static void renameReferences(JsonElement value) {
        if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                JsonElement child = entry.getValue();
                if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()
                        && CLIPS.contains(child.getAsString()))
                    entry.setValue(new JsonPrimitive(PREFIX + child.getAsString()));
                else renameReferences(child);
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) renameReferences(child);
        }
    }
}

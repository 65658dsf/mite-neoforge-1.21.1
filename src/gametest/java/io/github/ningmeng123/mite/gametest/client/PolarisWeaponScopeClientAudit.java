package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

/** Real resource-stack and weapon-selector regression for the built-in Polaris restriction. @author NingMeng123 */
final class PolarisWeaponScopeClientAudit {
    private static final Gson GSON = new Gson();
    private static final Set<String> CORE_CLIPS = Set.of("mite_polaris_sword_in", "mite_polaris_sword_1",
            "mite_polaris_sword_2", "mite_polaris_sword_3", "mite_polaris_Polaris");

    private PolarisWeaponScopeClientAudit() {}

    static void audit(Minecraft mc, JsonObject report) throws ReflectiveOperationException {
        check(ModList.get().isLoaded("punchy"), "The actual Punchy mod must be loaded");
        boolean enabled = mc.options.resourcePacks.stream().anyMatch(PolarisWeaponScopeClientAudit::polaris);
        report.addProperty("punchy_loaded", true);
        report.addProperty("punchy_version", ModList.get().getModContainerById("punchy").orElseThrow().getModInfo().getVersion().toString());
        report.addProperty("standalone_pack_enabled", false);
        report.addProperty("polaris_enabled", enabled);
        report.addProperty("scope", "built-in mod restriction with the original Polaris resource pack, actual parsed overlays and loaded weapon selectors");
        report.add("selected_resource_packs", GSON.toJsonTree(mc.options.resourcePacks));

        Class<?> custom = Class.forName("punchy.client.state.CustomAnimationResolver");
        Class<?> entryType = Class.forName("punchy.config.PunchyTuningConfig$CustomAnimationEntry");
        Class<?> combatType = Class.forName("punchy.config.PunchyTuningConfig$BetterCombatEntry");
        Class<?> state = Class.forName("punchy.client.state.AttackStateMachine");
        Method find = accessible(custom, "findEntry", ItemStack.class, String.class);
        Method names = accessible(custom, "resolveComboNames", entryType);
        Method combat = accessible(custom, "findBetterCombatEntry", ItemStack.class, String.class);
        Method combatNames = accessible(custom, "resolveBetterCombatComboNames", combatType);
        Method select = accessible(state, "resolveCustomAttackClip", ItemStack.class, InteractionHand.class, Minecraft.class, entryType);
        Method selectCombat = accessible(state, "resolveBetterCombatClip", ItemStack.class, InteractionHand.class, Minecraft.class);
        Method namedClip = Class.forName("punchy.client.animation.PunchyAnimationManager")
                .getMethod("resolveNamedClip", String.class, Minecraft.class, InteractionHand.class);
        Method allEntries = Class.forName("punchy.client.render.ToolTuning").getMethod("getCustomAnimation", ItemStack.class);
        verifyResourceStacks(mc, report, enabled);

        ItemStack originalMain = mc.player.getMainHandItem(), originalOff = mc.player.getOffhandItem();
        JsonArray cases = new JsonArray();
        report.add("polaris_scope_cases", cases);
        int shortWeapons = 0, otherWeapons = 0;
        try {
            mc.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            for (Item item : BuiltInRegistries.ITEM) {
                if (RemovedDiamondEquipment.isRemoved(item) || !weapon(item)) continue;
                boolean scoped = shortWeapon(new ItemStack(item));
                if (scoped) shortWeapons++; else otherWeapons++;
                for (boolean saved : new boolean[] {false, true}) {
                    ItemStack stack = new ItemStack(item);
                    if (saved) stack = ItemStack.parse(mc.level.registryAccess(), stack.save(mc.level.registryAccess())).orElseThrow();
                    mc.player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    Object attack = find.invoke(null, stack, "attack");
                    check(attack != null, "The actual default or scoped attack entry exists " + item);
                    List<?> configured = (List<?>) names.invoke(null, attack);
                    check(!configured.isEmpty(), "The actual attack contains combo names " + item);
                    Object machine = state.getConstructor().newInstance();
                    List<String> clips = new ArrayList<>();
                    for (int index = 0; index < configured.size(); ++index) {
                        Object clip = select.invoke(machine, stack, InteractionHand.MAIN_HAND, mc, attack);
                        check(clip != null, "Every actual selected combo clip is loaded " + item);
                        clips.add((String) clip.getClass().getMethod("getName").invoke(clip));
                    }
                    if (enabled && scoped) {
                        check(clips.size() == 3 && clips.stream().allMatch(name -> name.startsWith("mite_polaris_sword_")),
                                "Only daggers and sai select the three actual Polaris combo clips " + item + " " + clips);
                        String critical = (String) entryType.getField("critical_hit").get(attack);
                        check("mite_polaris_sword_3".equals(critical), "Short weapon critical attacks retain the scoped Polaris clip " + item);
                        check(namedClip.invoke(null, critical, mc, InteractionHand.MAIN_HAND) != null, "The scoped critical clip is loaded");
                        Object bc = combat.invoke(null, stack, "melee_combo");
                        check(bc != null && ((List<?>) combatNames.invoke(null, bc)).isEmpty(),
                                "Scoped short weapons use the intentional empty Better Combat definition " + item);
                        check(selectCombat.invoke(machine, stack, InteractionHand.MAIN_HAND, mc) == null,
                                "Better Combat falls through to the actual scoped short-weapon animation " + item);
                    } else {
                        check(clips.stream().noneMatch(PolarisWeaponScopeClientAudit::polarisClip),
                                "Other weapons and disabled-pack short weapons retain their default clips " + item + " " + clips);
                        check(!containsPolaris(allEntries.invoke(null, stack)), "Every non-attack action also stays outside Polaris " + item);
                    }
                    JsonObject fact = new JsonObject();
                    fact.addProperty("item", BuiltInRegistries.ITEM.getKey(item).toString());
                    fact.addProperty("saved_stack", saved);
                    fact.addProperty("short_weapon", scoped);
                    fact.addProperty("polaris_active", enabled && scoped);
                    fact.add("actual_attack_clips", GSON.toJsonTree(clips));
                    fact.addProperty("passed", true);
                    cases.add(fact);
                }
            }
            for (Item item : List.of(Items.APPLE, Items.IRON_PICKAXE, Items.IRON_SHOVEL, Items.IRON_AXE, Items.BOW))
                check(!containsPolaris(allEntries.invoke(null, new ItemStack(item))), "Non-weapon item tuning stays default " + item);
        } finally {
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, originalMain);
            mc.player.setItemInHand(InteractionHand.OFF_HAND, originalOff);
        }
        check(shortWeapons > 0 && otherWeapons > 0, "The regression contains both short weapons and other weapons");
        report.addProperty("short_weapons_checked", shortWeapons);
        report.addProperty("other_weapons_checked", otherWeapons);
        report.addProperty("registered_weapons_checked", shortWeapons + otherWeapons);
        report.addProperty("original_polaris_scoped_by_main_mod", enabled);
        report.addProperty("polaris_limited_to_daggers_and_sai", true);
        report.addProperty("new_and_saved_weapon_animation_entries_passed", true);
        report.addProperty("visual_appearance_tested", false);
    }

    static void verifyResourceStacks(Minecraft mc, JsonObject report, boolean enabled) throws ReflectiveOperationException {
        Method readClips = accessible(Class.forName("punchy.client.animation.io.PunchyAnimationLoader"), "readClips", JsonObject.class, String.class);
        Method readConfig = accessible(Class.forName("punchy.config.PunchyTuningConfig"), "readCompatResource", net.minecraft.server.packs.resources.Resource.class);
        report.addProperty("polaris_config_reader", readConfig.getName());
        JsonArray resources = new JsonArray();
        report.add("polaris_resource_stack_cases", resources);
        int animationFiles = 0, configFiles = 0;
        try {
            for (var stack : mc.getResourceManager().listResourceStacks("punchy/animations", id -> id.getPath().endsWith(".json")).entrySet()) {
                for (var resource : stack.getValue()) {
                    if (!polaris(resource.sourcePackId())) continue;
                    JsonObject json;
                    try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                        json = JsonParser.parseReader(reader).getAsJsonObject();
                    }
                    Map<?, ?> clips = (Map<?, ?>) readClips.invoke(null, json, resource.sourcePackId());
                    if (stack.getKey().getPath().endsWith("/player.animation.json"))
                        check(clips.isEmpty(), "Polaris' bundled default player clips cannot override other items");
                    if (stack.getKey().getPath().endsWith("/polaris.animation.json"))
                        check(clips.keySet().equals(CORE_CLIPS), "Only the five private Polaris clips are exposed");
                    JsonObject fact = new JsonObject();
                    fact.addProperty("resource", stack.getKey().toString());
                    fact.addProperty("source_pack", resource.sourcePackId());
                    fact.addProperty("resource_type", "animation");
                    fact.add("loaded_clip_names", GSON.toJsonTree(clips.keySet()));
                    fact.addProperty("passed", true); resources.add(fact); animationFiles++;
                }
            }
            for (var stack : mc.getResourceManager().listResourceStacks("punchy/compat", id -> id.getPath().endsWith(".json")).entrySet()) {
                for (var resource : stack.getValue()) {
                    if (!polaris(resource.sourcePackId())) continue;
                    JsonObject data = GSON.toJsonTree(readConfig.invoke(null, resource)).getAsJsonObject();
                    check(!data.has("item") || data.getAsJsonObject("item").isEmpty(), "The full Polaris SWORD group is removed before merging");
                    JsonObject specific = data.getAsJsonObject("itemSpecific");
                    check(specific != null && !specific.isEmpty(), "The real original Polaris config supplies scoped short-weapon entries");
                    for (String id : specific.keySet()) {
                        Item item = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id));
                        check(shortWeapon(new ItemStack(item)), "Every original Polaris overlay entry targets a dagger or sai " + id);
                    }
                    JsonObject fact = new JsonObject();
                    fact.addProperty("resource", stack.getKey().toString());
                    fact.addProperty("source_pack", resource.sourcePackId());
                    fact.addProperty("resource_type", "compat");
                    fact.addProperty("scoped_short_weapon_entries", specific.size());
                    fact.addProperty("global_sword_group_removed", true);
                    fact.addProperty("passed", true);
                    resources.add(fact);
                    configFiles++;
                }
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read the actual Polaris resource stack", exception);
        }
        check(!enabled || animationFiles >= 2 && configFiles >= 2, "Both original Polaris animation files and config files are exercised");
        report.addProperty("polaris_animation_resources_checked", animationFiles);
        report.addProperty("polaris_config_resources_checked", configFiles);
    }

    private static boolean weapon(Item item) {
        ItemStack stack = new ItemStack(item);
        if (SimplySwordsCompat.definition(stack) != null || item == Items.TRIDENT || item == Items.MACE) return true;
        var profile = EquipmentDurability.toolProfile(stack);
        return profile != null && switch (profile.family()) {
            case SWORD, DAGGER, KNIFE, CLUB, CUDGEL, BATTLE_AXE, WAR_HAMMER, SCYTHE -> true;
            default -> false;
        };
    }

    private static boolean shortWeapon(ItemStack stack) {
        var simply = SimplySwordsCompat.definition(stack);
        if (simply != null) return simply.family().equals("sai");
        var profile = EquipmentDurability.toolProfile(stack);
        return profile != null && profile.family() == io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily.DAGGER;
    }

    private static boolean containsPolaris(Object entries) {
        String json = GSON.toJson(entries);
        return json.contains("mite_polaris_") || json.contains("\"Polaris\"")
                || json.contains("\"sword_1\"") || json.contains("\"sword_2\"") || json.contains("\"sword_3\"")
                || json.contains("\"sword_in\"");
    }

    private static boolean polarisClip(String name) {
        return name.startsWith("mite_polaris_") || Set.of("sword_1", "sword_2", "sword_3", "Polaris", "sword_in").contains(name);
    }

    private static boolean polaris(String source) {
        return source.toLowerCase(Locale.ROOT).contains("polaris");
    }

    private static Method accessible(Class<?> owner, String name, Class<?>... parameters) throws ReflectiveOperationException {
        Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}

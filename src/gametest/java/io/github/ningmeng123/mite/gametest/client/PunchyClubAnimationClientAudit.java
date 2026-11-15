package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import org.joml.Vector3f;

/** Actual loaded Punchy weapon entries and combo clips in a disposable client world. @author NingMeng123 */
final class PunchyClubAnimationClientAudit {
    private PunchyClubAnimationClientAudit() {}

    static void audit(Minecraft mc, JsonObject report) throws ReflectiveOperationException {
        boolean standalone = mc.options.resourcePacks.stream()
                .anyMatch(pack -> pack.toLowerCase(java.util.Locale.ROOT).contains("mite-weapon-animations"));
        report.addProperty("standalone_pack_enabled", standalone);
        if (!standalone) {
            PolarisWeaponScopeClientAudit.audit(mc, report);
            return;
        }
        check(ModList.get().isLoaded("punchy"), "The actual Punchy mod must be loaded");
        report.addProperty("punchy_loaded", true);
        report.addProperty("punchy_version", ModList.get().getModContainerById("punchy").orElseThrow().getModInfo().getVersion().toString());
        report.addProperty("scope", "actual Punchy weapon classification, loaded family combo clips and saved-stack round trips");
        report.add("selected_resource_packs", new GsonBuilder().create().toJsonTree(mc.options.resourcePacks));
        boolean polarisEnabled = mc.options.resourcePacks.stream().anyMatch(pack -> {
            String name = pack.toLowerCase(java.util.Locale.ROOT);
            return name.contains("polaris") || name.contains("mite-weapon-animations");
        });
        report.addProperty("polaris_enabled", polarisEnabled);
        boolean originalPolarisEnabled = mc.options.resourcePacks.stream()
                .anyMatch(pack -> pack.toLowerCase(java.util.Locale.ROOT).contains("polaris"));
        PolarisWeaponScopeClientAudit.verifyResourceStacks(mc, report, originalPolarisEnabled);
        Class<?> entries = Class.forName("punchy.config.PunchyTuningConfig$CustomAnimationEntry");
        Class<?> state = Class.forName("punchy.client.state.AttackStateMachine");
        Class<?> custom = Class.forName("punchy.client.state.CustomAnimationResolver");
        Method entry = custom.getDeclaredMethod("findEntry", ItemStack.class, String.class);
        entry.setAccessible(true);
        Method names = custom.getDeclaredMethod("resolveComboNames", entries);
        names.setAccessible(true);
        Method select = state.getDeclaredMethod("resolveCustomAttackClip", ItemStack.class, InteractionHand.class, Minecraft.class, entries);
        select.setAccessible(true);
        Class<?> combatEntries = Class.forName("punchy.config.PunchyTuningConfig$BetterCombatEntry");
        Method combatEntry = custom.getDeclaredMethod("findBetterCombatEntry", ItemStack.class, String.class);
        combatEntry.setAccessible(true);
        Method combatNames = custom.getDeclaredMethod("resolveBetterCombatComboNames", combatEntries);
        combatNames.setAccessible(true);
        Method selectCombat = state.getDeclaredMethod("resolveBetterCombatClip", ItemStack.class, InteractionHand.class, Minecraft.class);
        selectCombat.setAccessible(true);
        Method namedClip = Class.forName("punchy.client.animation.PunchyAnimationManager")
                .getMethod("resolveNamedClip", String.class, Minecraft.class, InteractionHand.class);
        Method idleClip = custom.getMethod("resolveIdleClip", Minecraft.class, InteractionHand.class);
        ItemStack originalMain = mc.player.getMainHandItem(), originalOff = mc.player.getOffhandItem();
        JsonArray cases = new JsonArray();
        report.add("punchy_weapon_cases", cases);
        try {
            mc.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            ItemStack sword = new ItemStack(MiteTools.get("mite:mithril_sword"));
            check(attackFamily(sword) == null, "Ordinary swords retain their default attack family");
            Object swordEntry = entry.invoke(null, sword, "attack");
            check(swordEntry != null, "The active default sword attack entry exists");
            List<?> swordNames = (List<?>) names.invoke(null, swordEntry);
            List<String> reference = resolveCombo(mc, sword, swordEntry, state, select, swordNames.size());
            check(!reference.isEmpty(), "Default sword combo contains loaded clips");
            check(reference.stream().noneMatch(name -> name.startsWith("mite_polaris_") || List.of("sword_1", "sword_2", "sword_3").contains(name)),
                    "Polaris cannot replace the ordinary sword's default attack");
            if (mc.options.resourcePacks.stream().anyMatch(pack -> pack.toLowerCase(java.util.Locale.ROOT).contains("mite-weapon-animations"))) {
                check(reference.stream().allMatch(name -> name.startsWith("mite_default_sword_")),
                        "The standalone pack supplies its private copies of Punchy's default sword attacks");
            }
            report.add("active_sword_resolved_combo_clips", new GsonBuilder().create().toJsonTree(reference));
            JsonArray defaultCombatClips = new JsonArray();
            Object defaultCombat = combatEntry.invoke(null, sword, "melee_combo");
            check(defaultCombat != null, "Ordinary swords retain their default Better Combat combo");
            List<?> defaultCombatNames = (List<?>) combatNames.invoke(null, defaultCombat);
            check(defaultCombatNames.size() == 6, "Only the ordinary sword keeps the full default Better Combat combo");
            for (Object name : defaultCombatNames) {
                Object clip = namedClip.invoke(null, name.toString(), mc, InteractionHand.MAIN_HAND);
                check(clip != null, "The default Better Combat sword combo contains loaded clips");
                defaultCombatClips.add((String) clip.getClass().getMethod("getName").invoke(clip));
            }
            if (ModList.get().isLoaded("bettercombat")) {
                Object machine = state.getConstructor().newInstance();
                for (int index = 0; index < 2; ++index) {
                    Object clip = selectCombat.invoke(machine, sword, InteractionHand.MAIN_HAND, mc);
                    if (clip != null) defaultCombatClips.add((String) clip.getClass().getMethod("getName").invoke(clip));
                }
            }
            report.add("active_sword_bettercombat_combo_clips", defaultCombatClips);
            Map<Item, String> expected = weapons();
            var covered = new LinkedHashSet<String>();
            for (var weapon : expected.entrySet()) {
                Item item = weapon.getKey();
                String family = weapon.getValue();
                for (boolean saved : new boolean[] {false, true}) {
                    ItemStack stack = new ItemStack(item);
                    if (saved) stack = ItemStack.parse(mc.level.registryAccess(), stack.save(mc.level.registryAccess())).orElseThrow();
                    String actualFamily = attackFamily(stack);
                    check(java.util.Objects.equals(family, actualFamily), "Expected attack family " + family + " for " + item + ", got " + actualFamily);
                    Object attack = entry.invoke(null, stack, "attack");
                    check(attack != null, "The actual Punchy attack entry exists for " + item);
                    List<?> comboNames = (List<?>) names.invoke(null, attack);
                    check(!comboNames.isEmpty(), "The weapon has combo variations " + item);
                    List<String> clips = resolveCombo(mc, stack, attack, state, select, comboNames.size());
                    if (family == null || family.equals("polaris") && !polarisEnabled) {
                        check(clips.equals(reference), "Ordinary swords retain the active default sword combo " + item);
                    } else {
                        String prefix = family.equals("polaris") ? "mite_polaris_sword_" : "mite_" + family + "_";
                        check(clips.stream().allMatch(name -> name.startsWith(prefix)), "Every combo uses the expected family " + family + " for " + item + ": " + clips);
                        covered.add(family);
                    }
                    String criticalName = (String) entries.getField("critical_hit").get(attack);
                    String idleName = null;
                    if (family != null && (!family.equals("polaris") || polarisEnabled)) {
                        String prefix = family.equals("polaris") ? "mite_polaris_sword_" : "mite_" + family + "_";
                        check(criticalName != null && criticalName.startsWith(prefix), "The critical attack uses its weapon family " + item);
                        Object criticalClip = namedClip.invoke(null, criticalName, mc, InteractionHand.MAIN_HAND);
                        check(criticalClip != null, "The critical attack clip is actually loaded " + criticalName);
                        if (!family.equals("polaris")) {
                            Object idle = idleClip.invoke(null, mc, InteractionHand.MAIN_HAND);
                            check(idle != null, "Every customized weapon supplies its configured guard idle " + item);
                            idleName = (String) idle.getClass().getMethod("getName").invoke(idle);
                            check(!((Map<?, ?>) idle.getClass().getMethod("getTracks").invoke(idle)).isEmpty(),
                                    "The configured idle contains loaded bone tracks " + item);
                            for (String clipName : clips)
                                verifyIdleContinuity(idle, namedClip.invoke(null, clipName, mc, InteractionHand.MAIN_HAND), item);
                            verifyIdleContinuity(idle, criticalClip, item);
                            Method dual = Class.forName("punchy.client.animation.PunchyAnimationManager").getMethod("isDualHandedActive",
                                    Minecraft.class, Class.forName("punchy.client.animation.data.AnimationClip"), float.class);
                            check(Boolean.TRUE.equals(dual.invoke(null, mc, idle, 0F)) == twoHandedFamily(family),
                                    "The actual idle begins with the configured one-handed or two-handed grip " + item);
                        }
                        Object combat = combatEntry.invoke(null, stack, "melee_combo");
                        check(combat != null, "The intentional empty Better Combat combo definition exists " + item);
                        check(((List<?>) combatNames.invoke(null, combat)).isEmpty(), "The empty Better Combat definition has no inherited attack names " + item);
                        for (int index = 1; index <= 6; ++index) {
                            String configured = (String) combatEntries.getField("attack_" + index).get(combat);
                            check(configured == null || configured.isBlank(), "Default Better Combat attack fields stay empty " + item);
                        }
                        Object machine = state.getConstructor().newInstance();
                        check(selectCombat.invoke(machine, stack, InteractionHand.MAIN_HAND, mc) == null,
                                "The actual Better Combat visual selector falls through to the loaded custom attack " + item);
                        if (twoHandedFamily(family)) {
                            Object firstClip = namedClip.invoke(null, clips.getFirst(), mc, InteractionHand.MAIN_HAND);
                            check(Boolean.TRUE.equals(firstClip.getClass().getMethod("hasEffectAny", String.class).invoke(firstClip, "dual_handed")),
                                    "Two-handed custom attack clips retain their supporting-hand timeline " + item);
                        }
                        if (!saved) {
                            var signatures = new LinkedHashSet<String>();
                            for (String clipName : clips) signatures.add(curve(namedClip.invoke(null, clipName, mc, InteractionHand.MAIN_HAND)));
                            check(signatures.size() == clips.size(), "Combo variations contain distinct bone curves " + item);
                            String resolvedCriticalName = (String) criticalClip.getClass().getMethod("getName").invoke(criticalClip);
                            if (!clips.contains(resolvedCriticalName)) {
                                check(signatures.add(curve(criticalClip)), "Critical attacks contain a distinct heavy-attack curve " + item);
                            }
                        }
                    }
                    preserveNonAttackEntries(stack);
                    JsonObject fact = new JsonObject();
                    fact.addProperty("item", BuiltInRegistries.ITEM.getKey(item).toString());
                    fact.addProperty("saved_stack", saved);
                    fact.addProperty("attack_family", family == null ? "default_sword" : family);
                    fact.addProperty("critical_clip", criticalName);
                    fact.addProperty("idle_clip", idleName);
                    fact.addProperty("attack_endpoints_match_idle", idleName != null);
                    fact.addProperty("bettercombat_visual_route", customAttackInBetterCombat(family) ? "empty_melee_combo_falls_back_to_custom_attack" : "melee_combo");
                    fact.addProperty("non_attack_entries_preserved", true);
                    fact.add("actual_attack_clips", new GsonBuilder().create().toJsonTree(clips));
                    fact.addProperty("passed", true);
                    cases.add(fact);
                }
            }
            for (Item item : List.of(Items.APPLE, Items.IRON_PICKAXE, Items.IRON_SHOVEL)) {
                check(attackFamily(new ItemStack(item)) == null, "Ordinary items and mining tools retain default animation selection " + item);
            }
            check(covered.containsAll(polarisEnabled ? List.of("polaris", "knife", "club", "battleaxe", "hammer", "scythe", "trident") : List.of("knife", "club", "battleaxe", "hammer", "scythe", "trident")), "All native melee weapon families are covered");
            if (SimplySwordsCompat.enabled()) {
                check(covered.containsAll(List.of("longsword", "rapier", "spear", "katana", "cutlass", "twinblade", "warglaive", "claymore", "greataxe", "greathammer", "glaive", "halberd", "chakram")),
                        "Every Simply Swords special weapon family is covered");
            }
            report.add("covered_attack_families", new GsonBuilder().create().toJsonTree(covered));
            report.addProperty("registered_weapons_checked", expected.size());
            report.addProperty("new_and_saved_weapon_animation_entries_passed", true);
            report.addProperty("polaris_limited_to_daggers_and_sai", polarisEnabled);
            report.addProperty("disabled_polaris_short_weapons_use_default_clips", !polarisEnabled);
            report.addProperty("critical_bettercombat_combos_and_two_handed_custom_fallback_clips_loaded", true);
            report.addProperty("distinct_combo_and_critical_bone_curves", true);
            report.addProperty("ordinary_swords_keep_default_attacks", true);
            report.addProperty("visual_appearance_tested", false);
        } finally {
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, originalMain);
            mc.player.setItemInHand(InteractionHand.OFF_HAND, originalOff);
        }
    }

    private static boolean customAttackInBetterCombat(String family) {
        return family != null;
    }

    static boolean twoHandedFamily(String family) {
        return family != null && List.of("longsword", "twinblade", "katana", "spear", "trident", "glaive", "claymore",
                "greataxe", "greathammer", "scythe", "halberd").contains(family);
    }

    static String attackFamily(ItemStack stack) {
        var simply = SimplySwordsCompat.definition(stack);
        if (simply != null) return simply.family().equals("sai") ? "polaris" : simply.family();
        if (stack.is(Items.TRIDENT)) return "trident";
        if (stack.is(Items.MACE)) return "hammer";
        var profile = EquipmentDurability.toolProfile(stack);
        if (profile == null) return null;
        return switch (profile.family()) {
            case DAGGER -> "polaris";
            case KNIFE -> "knife";
            case CLUB, CUDGEL -> "club";
            case BATTLE_AXE -> "battleaxe";
            case WAR_HAMMER -> "hammer";
            case SCYTHE -> "scythe";
            default -> null;
        };
    }

    private static Map<Item, String> weapons() {
        Map<Item, String> result = new LinkedHashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (RemovedDiamondEquipment.isRemoved(item)) continue;
            ItemStack stack = new ItemStack(item);
            var swords = SimplySwordsCompat.definition(stack);
            if (swords != null) {
                result.put(item, switch (swords.family()) {
                    case "sai" -> "polaris";
                    default -> swords.family();
                });
                continue;
            }
            var profile = EquipmentDurability.toolProfile(stack);
            if (profile == null) continue;
            switch (profile.family()) {
                case SWORD -> result.put(item, null);
                case DAGGER -> result.put(item, "polaris");
                case KNIFE -> result.put(item, "knife");
                case CLUB, CUDGEL -> result.put(item, "club");
                case BATTLE_AXE -> result.put(item, "battleaxe");
                case WAR_HAMMER -> result.put(item, "hammer");
                case SCYTHE -> result.put(item, "scythe");
                default -> { }
            }
        }
        result.put(Items.TRIDENT, "trident");
        result.put(Items.MACE, "hammer");
        return result;
    }

    private static String curve(Object clip) throws ReflectiveOperationException {
        Map<?, ?> tracks = (Map<?, ?>) clip.getClass().getMethod("getTracks").invoke(clip);
        var curve = new java.util.TreeMap<String, String>();
        for (var track : tracks.entrySet()) {
            curve.put(track.getKey().toString(), track.getValue().getClass().getMethod("getKeyframes").invoke(track.getValue()).toString());
        }
        return curve.toString();
    }

    private static void verifyIdleContinuity(Object idle, Object attack, Item item) throws ReflectiveOperationException {
        Map<?, ?> idleTracks = (Map<?, ?>) idle.getClass().getMethod("getTracks").invoke(idle);
        Map<?, ?> attackTracks = (Map<?, ?>) attack.getClass().getMethod("getTracks").invoke(attack);
        for (String bone : List.of("right_arm", "left_arm", "itemgrip_right", "itemgrip_left")) {
            for (String channel : List.of("sampledPosition", "sampledRotationEuler", "sampledScale")) {
                Vector3f guard = endpoint(idleTracks.get(bone), false, channel);
                Vector3f start = endpoint(attackTracks.get(bone), false, channel);
                Vector3f end = endpoint(attackTracks.get(bone), true, channel);
                check(guard.distanceSquared(start) <= .0004F && guard.distanceSquared(end) <= .0004F,
                        "Attack starts and ends in its actual idle grip " + item + " " + bone + " " + channel
                                + " idle=" + guard + " start=" + start + " end=" + end);
            }
        }
    }

    private static Vector3f endpoint(Object track, boolean last, String channel) throws ReflectiveOperationException {
        if (track == null) return new Vector3f(channel.equals("sampledScale") ? 1F : 0F);
        List<?> frames = (List<?>) track.getClass().getMethod("getKeyframes").invoke(track);
        check(!frames.isEmpty(), "Loaded grip tracks contain keyframes");
        Object frame = last ? frames.getLast() : frames.getFirst();
        float time = ((Number) frame.getClass().getMethod("timeStamp").invoke(frame)).floatValue();
        return (Vector3f) frame.getClass().getMethod(channel, float.class).invoke(frame, time);
    }

    private static void preserveNonAttackEntries(ItemStack stack) throws ReflectiveOperationException {
        Class<?> tuning = Class.forName("punchy.client.render.ToolTuning");
        List<?> original = (List<?>) tuning.getMethod("getSpecificCustomAnimation", ItemStack.class).invoke(null, stack);
        if (original == null) {
            Class<?> kind = Class.forName("punchy.client.renderer.layer.VanillaFirstPersonItemLayer$ToolKind");
            Object resolved = Class.forName("punchy.client.render.ToolKindResolver").getMethod("resolveItemKind", ItemStack.class, kind).invoke(null, stack, null);
            var groups = tuning.getDeclaredField("GROUP_ANIMATIONS");
            groups.setAccessible(true);
            original = (List<?>) ((Map<?, ?>) groups.get(null)).get(resolved);
        }
        var defaults = tuning.getDeclaredField("DEFAULT_ANIMATIONS");
        defaults.setAccessible(true);
        Method merge = tuning.getDeclaredMethod("mergeAnimationEntries", List.class, List.class);
        merge.setAccessible(true);
        original = (List<?>) merge.invoke(null, original, defaults.get(null));
        List<?> actual = (List<?>) tuning.getMethod("getCustomAnimation", ItemStack.class).invoke(null, stack);
        if (original == null) return;
        for (Object preserved : original) {
            if (preserved == null || "attack".equals(preserved.getClass().getField("type").get(preserved))) continue;
            check(actual.contains(preserved), "Actual item tuning retains its non-attack entry " + stack);
        }
    }

    private static List<String> resolveCombo(Minecraft mc, ItemStack stack, Object entry, Class<?> state, Method select, int count)
            throws ReflectiveOperationException {
        mc.player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Object machine = state.getConstructor().newInstance();
        List<String> result = new ArrayList<>();
        for (int index = 0; index < count; ++index) {
            Object clip = select.invoke(machine, stack, InteractionHand.MAIN_HAND, mc, entry);
            check(clip != null, "The real Punchy attack selector resolves a loaded combo clip for " + stack);
            check(!((Map<?, ?>) clip.getClass().getMethod("getTracks").invoke(clip)).isEmpty(), "Loaded weapon clips contain actual bone tracks " + stack);
            result.add((String) clip.getClass().getMethod("getName").invoke(clip));
        }
        return result;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}

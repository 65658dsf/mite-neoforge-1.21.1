package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatPlayerMode;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatPlayerReach;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.config.MiteConfig;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in real client collision audit, exclusively in a newly created disposable flat world. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class BetterCombatPlayerReachClientAudit {
    private static boolean started;
    private static boolean done;
    private static boolean auditWindowHidden;
    private static int waiting;
    private static int readyTicks;
    private static int nextEntityId = -42000;
    private static boolean punchyClassificationDone;
    private static int modePhase;
    private static JsonObject report;

    private BetterCombatPlayerReachClientAudit() {}

    @SubscribeEvent public static void afterTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mite.betterCombatPlayerReachAudit")) return;
        Minecraft mc = Minecraft.getInstance();
        if (!auditWindowHidden && Boolean.getBoolean("mite.punchyHideWindow")) {
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            auditWindowHidden = true;
        }
        if (done) {
            PunchyClubAttackInputAudit.previewTick(mc);
            return;
        }
        if (mc.getOverlay() != null) return;
        try {
            if (!started) {
                if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) {
                    onboarding.onClose();
                    return;
                }
                if (!(mc.screen instanceof TitleScreen)) return;
                started = true;
                report = new JsonObject();
                report.addProperty("author", "NingMeng123");
                report.addProperty("bettercombat_loaded", BetterCombatBridge.enabled());
                report.addProperty("simplyswords_loaded", SimplySwordsCompat.enabled());
                report.addProperty("existing_worlds_opened", false);
                report.addProperty("user_configuration_files_written", false);
                report.addProperty("damage_or_attack_packets_sent", false);
                report.addProperty("scope", "real client TargetFinder candidate lists and production MITE melee guard; in-memory config snapshots and disposable local targets");
                report.add("cases", new JsonArray());
                report.add("failures", new JsonArray());
                String world = "bettercombat-player-reach-audit-" + System.currentTimeMillis();
                report.addProperty("disposable_world", world);
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings(world, GameType.CREATIVE, false, Difficulty.NORMAL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(123456789L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), mc.screen);
                return;
            }
            int tickBudget = Boolean.getBoolean("mite.punchyClubInputAudit") ? 7200 : 2400;
            check(++waiting < tickBudget, "Timed out loading the disposable player reach audit world");
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.getConnection() == null) return;
            if (mc.screen != null) return;
            // Let the new world's client-requested chunks finish generation before shutdown unloads them.
            if (++readyTicks < 200) return;
            if (readyTicks == 200 && BetterCombatBridge.enabled() && ModList.get().isLoaded("punchy")
                    && !Boolean.getBoolean("mite.punchyClubAudit")) {
                mc.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                return;
            }
            if (Boolean.getBoolean("mite.punchyClubAudit")) {
                if (!punchyClassificationDone) {
                    PunchyClubAnimationClientAudit.audit(mc, report);
                    punchyClassificationDone = true;
                }
                if (Boolean.getBoolean("mite.punchyClubInputAudit")) {
                    if (report.get("standalone_pack_enabled").getAsBoolean()) {
                        if (!PunchyClubAttackInputAudit.tick(mc, report)) return;
                    } else {
                        report.addProperty("native_input_audit_skipped",
                                "Full weapon frame capture requires the optional external animation pack; Polaris scope was audited.");
                    }
                }
            } else {
                if (BetterCombatBridge.enabled() && !auditModeKey(mc)) return;
                audit(mc);
            }
            report.addProperty("status", Boolean.getBoolean("mite.punchyClubAudit")
                    ? report.get("standalone_pack_enabled").getAsBoolean()
                    ? "passed_standalone_punchy_resource_pack_all_weapon_families_default_sword_and_polaris"
                    : "passed_main_mod_polaris_scope_original_resource_pack_and_default_weapons"
                    : BetterCombatBridge.enabled()
                    ? "passed_real_client_targetfinder_and_mite_guard"
                    : "passed_optional_bettercombat_absent_and_original_mite_guard");
            finish(mc);
        } catch (Throwable failure) {
            fail(mc, failure);
        }
    }

    private static boolean auditModeKey(Minecraft mc) throws ReflectiveOperationException {
        if (!net.bettercombat.client.BetterCombatClientMod.ENABLED) return false;
        var toggle = Arrays.stream(mc.options.keyMappings)
                .filter(mapping -> mapping.getName().equals("key.mite.toggle_better_combat"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing MITE combat mode key mapping"));
        if (modePhase == 0) {
            check(!BetterCombatPlayerMode.attacksDisabled(mc.player), "Better Combat starts enabled");
            check(toggle.getDefaultKey().getValue() == org.lwjgl.glfw.GLFW.GLFW_KEY_V, "The default toggle is V");
            if (ModList.get().isLoaded("punchy")) {
                Object enabled = Class.forName("punchy.config.PunchyConfig")
                        .getMethod("isBetterCombatCompatEnabled").invoke(null);
                check(Boolean.TRUE.equals(enabled), "Punchy compatibility starts enabled with both mods installed");
                check(net.bettercombat.client.compat.FirstPersonAnimationCompatibility.firstPersonMode()
                                == dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode.NONE,
                        "Punchy takes over Better Combat's first-person renderer");
                report.addProperty("punchy_owns_first_person_combat_rendering", true);
            }
            KeyMapping.click(toggle.getKey());
            modePhase++;
            return false;
        }
        if (modePhase == 1) {
            if (!BetterCombatPlayerMode.isVanilla(mc.player)) return false;
            check(BetterCombatPlayerMode.attacksDisabled(mc.player), "Better Combat sees the synchronized vanilla mode");
            KeyMapping.click(toggle.getKey());
            modePhase++;
            return false;
        }
        if (BetterCombatPlayerMode.isVanilla(mc.player)) return false;
        check(!BetterCombatPlayerMode.attacksDisabled(mc.player), "The second key press restores Better Combat");
        report.addProperty("combat_mode_key_synced_both_directions", true);
        return true;
    }

    private static void audit(Minecraft mc) throws ReflectiveOperationException {
        Player player = mc.player;
        MiteConfig.Snapshot savedSettings = MiteConfig.effectiveSnapshot();
        ItemStack savedMain = player.getMainHandItem(), savedOff = player.getOffhandItem();
        boolean savedCreative = player.getAbilities().instabuild;
        double savedScale = player.getAttribute(Attributes.SCALE).getBaseValue();
        double savedRange = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getBaseValue();
        var savedRangeModifiers = List.copyOf(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getModifiers());
        Vec3 savedPosition = player.position();
        float savedYaw = player.getYRot(), savedPitch = player.getXRot();
        Object bcConfig = null;
        float savedScaleInfluence = 1F;
        Integer savedCombo = null;
        try {
            player.getAbilities().instabuild = false;
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            // Keep the audit inside the chunks loaded around this new world's spawn. Moving to
            // absolute 0,0 can put client-only entities in an untracked chunk, hiding all candidates.
            player.setPos(savedPosition.x, 120D, savedPosition.z);
            player.setYRot(0F); player.yRotO = 0F;
            player.setXRot(0F); player.xRotO = 0F;
            player.setOldPosAndRot();
            report.add("fixture_spawn_position", vector(savedPosition));
            report.add("fixture_audit_position", vector(player.position()));
            report.addProperty("fixture_player_chunk_loaded", mc.level.hasChunkAt(player.blockPosition()));
            scale(player, 1D);
            settings(savedSettings, 1D, 0D, true);
            equip(player, new ItemStack(Items.IRON_SWORD));
            if (!BetterCombatBridge.enabled()) {
                nativeCase(mc, "without_bettercombat_in_range", 2.15D, true);
                nativeCase(mc, "without_bettercombat_outside_mite_boundary", 2.35D, false);
                nativeCase(mc, "without_bettercombat_outside_mite", 2.45D, false);
                report.addProperty("optional_bettercombat_classes_resolved", false);
                return;
            }

            bcConfig = Class.forName("net.bettercombat.BetterCombatMod").getMethod("getConfig").invoke(null);
            savedScaleInfluence = bcConfig.getClass().getField("entity_scale_attack_range_influence").getFloat(bcConfig);
            bcConfig.getClass().getField("entity_scale_attack_range_influence").setFloat(bcConfig, 1F);
            savedCombo = ((Number)api().getCombo.invoke(player)).intValue();
            api().setCombo.invoke(player, 0);
            Attack sword = attack(player);
            near(sword.rawRange(), 3D, "The real installed ordinary sword has a three-block Better Combat range");
            near(BetterCombatPlayerReach.reach(player), 3D, "Better Combat retains its native sword range behind the source cap");

            sample(mc, "sword_inside_mite_range", sword, 2.1D, 0D, 2.25D, true);
            sample(mc, "sword_outside_mite_boundary", sword, 2.4D, 0D, 2.25D, false);
            sample(mc, "sword_keeps_legacy_boundary", sword, 2.2D, 0D, 2.25D, true);
            sample(mc, "sword_outside_native_bettercombat_range", sword, 3.2D, 0D, 2.25D, false);
            sample(mc, "raised_target_keeps_mite_height_penalty", sword, 1.7D, 1.25D, 1.875D, true);
            sample(mc, "raised_target_outside_mite_height_limit", sword, 2D, 1.25D, 1.875D, false);
            for (int yaw : new int[] {0, 90, 180, 270, 45, 135}) {
                player.setYRot(yaw); player.yRotO = yaw; player.setOldPosAndRot();
                sample(mc, "sword_yaw_" + yaw + "_inside_mite_range", sword, 2.1D, 0D, 2.25D, true);
                sample(mc, "sword_yaw_" + yaw + "_outside_mite_range", sword, 2.4D, 0D, 2.25D, false);
            }
            player.setYRot(0F); player.yRotO = 0F; player.setOldPosAndRot();

            if (SimplySwordsCompat.enabled()) {
                ItemStack polearm = new ItemStack(SimplySwordsCompat.weapon("iron", "glaive"));
                check(!polearm.isEmpty() && !polearm.is(Items.AIR), "The installed Simply Swords iron glaive exists");
                equip(player, polearm);
                Attack glaive = attack(player);
                near(glaive.rawRange(), 3.5D, "The installed Simply Swords glaive declares its own three-and-a-half-block range");
                sample(mc, "simplyswords_glaive_inside_declared_weapon_range", glaive, 3.3D, 0D, 3.5D, true);
                sample(mc, "simplyswords_glaive_outside_declared_weapon_range", glaive, 3.7D, 0D, 3.5D, false);
                ItemStack scytheStack = new ItemStack(SimplySwordsCompat.weapon("copper", "scythe"));
                check(!scytheStack.isEmpty() && !scytheStack.is(Items.AIR), "The MITE-registered Simply Swords copper scythe exists");
                equip(player, scytheStack);
                Attack scythe = attack(player);
                near(scythe.rawRange(), 3.5D, "The MITE Simply Swords scythe inherits the glaive's declared range");
                sample(mc, "simplyswords_scythe_inside_declared_weapon_range", scythe, 3.3D, 0D, 3.5D, true);
                sample(mc, "simplyswords_scythe_outside_declared_weapon_range", scythe, 3.7D, 0D, 3.5D, false);
                simplySwordsLengthCases(mc, savedSettings);
            } else {
                report.addProperty("simplyswords_polearm_case", "skipped_optional_simplyswords_absent");
            }

            dualWieldCases(mc);
            equip(player, new ItemStack(Items.IRON_SWORD));
            sword = attack(player);
            settings(savedSettings, 4D, 0D, true);
            near(BetterCombatPlayerReach.reach(player), 12D, "The native BC range still receives the configured multiplier");
            // With raw range 3, the unmodified initial candidate box reaches only 7.3 blocks forward.
            // A null cursor prevents the direct-target exception from hiding a stale broad-phase radius.
            sample(mc, "multiplier_expands_initial_candidate_search", sword, 8.8D, 0D, 9D, true);
            sample(mc, "multiplied_range_keeps_mite_upper_bound", sword, 9.2D, 0D, 9D, false);

            settings(savedSettings, 1.5D, 1D, false);
            scale(player, 2D);
            double alignedY = api().origin(player).y - player.getY() - .5D;
            near(BetterCombatPlayerReach.reach(player), 8D, "BC scale precedes the configured multiplier and reduction");
            // BC keeps (3 * 2) * 1.5 - 1 = 8, while the source cap remains 2.25 * 1.5 - 1.
            // Align both origins within the target box and disable height adjustment to isolate scale.
            sample(mc, "scale_preserves_source_cap_after_multiplier_and_reduction", sword, 2.2D, alignedY, 2.375D, true);
            sample(mc, "scaled_bettercombat_range_cannot_exceed_source_cap", sword, 2.6D, alignedY, 2.375D, false);

            settings(savedSettings, 1D, 0D, false);
            scale(player, .5D);
            near(BetterCombatPlayerReach.reach(player), 1.5D, "Smaller BC scale retains the native shorter range");
            sample(mc, "smaller_bettercombat_scale_keeps_native_limit", sword, 1.3D, 0D, 1.5D, true);
            sample(mc, "source_cap_does_not_bypass_smaller_native_range", sword, 1.8D, 0D, 1.5D, false);

            scale(player, 1D);
            settings(savedSettings, 1D, 16D, true);
            sample(mc, "reduction_clamps_range_to_zero", sword, .5D, 0D, 0D, false);
            settings(savedSettings, 0D, 0D, true);
            sample(mc, "zero_multiplier_has_no_candidate_or_melee_hit", sword, .5D, 0D, 0D, false);
            report.addProperty("native_targetfinder_invoked", true);
            report.addProperty("null_cursor_used_for_every_candidate_search", true);
            report.addProperty("client_only_entities_added_to_disposable_world", true);
        } finally {
            MiteConfig.receiveServerSnapshot(savedSettings);
            if (bcConfig != null) bcConfig.getClass().getField("entity_scale_attack_range_influence").setFloat(bcConfig, savedScaleInfluence);
            if (savedCombo != null) api().setCombo.invoke(player, savedCombo);
            player.setItemInHand(InteractionHand.MAIN_HAND, savedMain);
            player.setItemInHand(InteractionHand.OFF_HAND, savedOff);
            player.getAbilities().instabuild = savedCreative;
            scale(player, savedScale);
            var range = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
            range.removeModifiers(); range.setBaseValue(savedRange);
            savedRangeModifiers.forEach(range::addTransientModifier);
            player.setPos(savedPosition);
            player.setYRot(savedYaw); player.setXRot(savedPitch);
            player.setOldPosAndRot();
            report.addProperty("in_memory_settings_restored", true);
        }
    }

    /** Reproduce the two photographed weapons, including the point where their reach differs. */
    private static void simplySwordsLengthCases(Minecraft mc, MiteConfig.Snapshot base) throws ReflectiveOperationException {
        Player player = mc.player;
        ItemStack glaiveStack = new ItemStack(SimplySwordsCompat.weapon("vibranium", "glaive"));
        ItemStack cutlassStack = new ItemStack(SimplySwordsCompat.weapon("vibranium", "cutlass"));
        check(!glaiveStack.isEmpty() && !cutlassStack.isEmpty(), "Both photographed vibranium weapons are registered");
        equip(player, glaiveStack);
        Attack glaive = attack(player);
        near(glaive.rawRange(), 3.5D, "The photographed vibranium glaive declares 3.5 blocks");
        sample(mc, "vibranium_glaive_hits_at_2_6", glaive, 2.6D, 0D, 3.5D, true);
        sample(mc, "vibranium_glaive_reaches_beyond_cutlass_at_2_9", glaive, 2.9D, 0D, 3.5D, true);
        sample(mc, "vibranium_glaive_hits_near_its_3_5_boundary", glaive, 3.3D, 0D, 3.5D, true);
        sample(mc, "vibranium_glaive_rejects_beyond_its_3_5_boundary", glaive, 3.7D, 0D, 3.5D, false);

        equip(player, cutlassStack);
        Attack cutlass = attack(player);
        near(cutlass.rawRange(), 2.75D, "The photographed vibranium cutlass retains the inherited sickle range");
        sample(mc, "vibranium_cutlass_hits_at_1_95", cutlass, 1.95D, 0D, 2.1D, true);
        sample(mc, "vibranium_cutlass_cannot_reach_glaive_target_at_2_9", cutlass, 2.9D, 0D, 2.1D, false);

        String[] balancedFamilies = {"sai", "rapier", "claymore", "greataxe", "greathammer", "spear", "halberd", "chakram", "twinblade", "longsword"};
        double[] balancedReaches = {2D, 2.1D, 3.25D, 3.25D, 3.25D, 3.75D, 4D, 2.8D, 3.5D, 2.5D};
        for (int index = 0; index < balancedFamilies.length; index++) {
            String family = balancedFamilies[index];
            double reach = balancedReaches[index];
            equip(player, new ItemStack(SimplySwordsCompat.weapon("vibranium", family)));
            Attack balanced = attack(player);
            sample(mc, "balanced_" + family + "_inside_family_boundary", balanced, reach - .15D, 0D, reach, true);
            sample(mc, "balanced_" + family + "_outside_family_boundary", balanced, reach + .15D, 0D, reach, false);
        }

        equip(player, glaiveStack);
        settings(base, 1.5D, .25D, true);
        glaive = attack(player);
        sample(mc, "simplyswords_declared_range_receives_multiplier_then_reduction", glaive, 4.8D, 0D, 5D, true);
        sample(mc, "simplyswords_configured_range_still_has_a_boundary", glaive, 5.2D, 0D, 5D, false);
        settings(base, 1D, 0D, true);

        // An explicit interaction-range component replaces BC's +.5 glaive metadata bonus.
        // The source guard must consume the same resulting attribute, not add either bonus again.
        equip(player, weaponWithRangeBonus(glaiveStack.copy(), "client_audit_simplyswords_range"));
        Attack componentGlaive = attack(player);
        near(componentGlaive.rawRange(), 5D, "The glaive's explicit range component replaces its metadata range bonus");
        sample(mc, "simplyswords_explicit_range_component_is_preserved", componentGlaive, 4.8D, 0D, 5D, true);
        sample(mc, "simplyswords_explicit_range_component_does_not_double_metadata", componentGlaive, 5.2D, 0D, 5D, false);
    }

    /** Keep the equipped hand's component modifiers real while BC temporarily reads the attacking hand. */
    private static void dualWieldCases(Minecraft mc) throws ReflectiveOperationException {
        Player player = mc.player;
        ItemStack normal = new ItemStack(Items.IRON_SWORD);
        ItemStack extended = swordWithRangeBonus();
        equip(player, normal, extended);
        api().setCombo.invoke(player, 1);
        Attack off = attack(player);
        check(off.offHand(), "Dual-wield combo 1 selects the real offhand sword");
        near(off.rawRange(), 5D, "Offhand mainhand-only range components participate in the actual BC attack");
        sample(mc, "dual_wield_extended_offhand_inside_source_range", off, 4.05D, 0D, 4.25D, true);
        sample(mc, "dual_wield_extended_offhand_outside_source_range", off, 4.45D, 0D, 4.25D, false);
        api().setCombo.invoke(player, 0);
        Attack main = attack(player);
        check(!main.offHand(), "Dual-wield combo 0 selects the real mainhand sword");
        near(main.rawRange(), 3D, "The normal mainhand cannot borrow the offhand's range component");
        sample(mc, "dual_wield_normal_mainhand_inside_source_range", main, 2.1D, 0D, 2.25D, true);
        sample(mc, "dual_wield_normal_mainhand_cannot_borrow_offhand_range", main, 2.4D, 0D, 2.25D, false);

        equip(player, extended, normal);
        api().setCombo.invoke(player, 0);
        main = attack(player);
        check(!main.offHand(), "Reversed fixture combo 0 still selects the mainhand");
        near(main.rawRange(), 5D, "The extended mainhand retains its own explicit range component");
        sample(mc, "dual_wield_extended_mainhand_inside_source_range", main, 4.05D, 0D, 4.25D, true);
        sample(mc, "dual_wield_extended_mainhand_outside_source_range", main, 4.45D, 0D, 4.25D, false);
        api().setCombo.invoke(player, 1);
        off = attack(player);
        check(off.offHand(), "Reversed fixture combo 1 still selects the offhand");
        near(off.rawRange(), 3D, "The normal offhand cannot borrow the mainhand's range component");
        sample(mc, "dual_wield_normal_offhand_inside_source_range", off, 2.1D, 0D, 2.25D, true);
        sample(mc, "dual_wield_normal_offhand_cannot_borrow_mainhand_range", off, 2.4D, 0D, 2.25D, false);
        api().setCombo.invoke(player, 0);
        equip(player, new ItemStack(Items.IRON_SWORD));
    }

    private static ItemStack swordWithRangeBonus() {
        return weaponWithRangeBonus(new ItemStack(Items.IRON_SWORD), "client_audit_dual_wield_range");
    }

    private static ItemStack weaponWithRangeBonus(ItemStack weapon, String modifierId) {
        var modifiers = ItemAttributeModifiers.builder();
        weapon.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) ->
                modifiers.add(attribute, modifier, EquipmentSlotGroup.MAINHAND));
        modifiers.add(Attributes.ENTITY_INTERACTION_RANGE,
                new AttributeModifier(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, modifierId),
                        2D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        weapon.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers.build());
        return weapon;
    }

    private static void sample(Minecraft mc, String name, Attack attack, double faceDistance, double feetOffset,
            double expectedRange, boolean expectedHit) throws ReflectiveOperationException {
        Player player = mc.player;
        Mob target = target(mc, player, faceDistance, feetOffset);
        try {
            List<?> candidates = (List<?>)api().find.invoke(null, player, null, attack.move(), attack.rawRange());
            boolean found = candidates.contains(target);
            boolean allowed = SourcePlayerReach.canMelee(player, target);
            double reach = SourcePlayerReach.meleeReach(player, target);
            JsonObject fact = fact(name, player, target, faceDistance, feetOffset, reach, expectedRange, expectedHit);
            fact.addProperty("raw_bettercombat_range", attack.rawRange());
            fact.addProperty("configured_bettercombat_range", BetterCombatPlayerReach.reach(player));
            fact.addProperty("attacking_weapon", BuiltInRegistries.ITEM.getKey(attack.weapon().getItem()).toString());
            fact.addProperty("offhand_attack", attack.offHand());
            fact.addProperty("combo", attack.combo());
            fact.addProperty("equipped_mainhand_range_attribute", player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getValue());
            fact.addProperty("targetfinder_contains_target", found);
            fact.addProperty("targetfinder_candidate_count", candidates.size());
            fact.addProperty("mite_can_melee", allowed);
            report.getAsJsonArray("cases").add(fact);
            diagnostics(mc, player, target, attack, fact);
            near(reach, expectedRange, name + ": the production guard uses the smaller configured source and BC range");
            if (name.equals("multiplier_expands_initial_candidate_search")) {
                JsonObject stages = fact.getAsJsonObject("native_filter_diagnostics");
                check(!stages.get("raw_initial_search_contains_target").getAsBoolean(),
                        name + ": the target must be outside Better Combat's unmodified initial search");
                check(stages.get("configured_initial_search_contains_target").getAsBoolean(),
                        name + ": the configured initial search must include the target");
            }
            check(found == expectedHit, name + ": real TargetFinder expected=" + expectedHit + ", actual=" + found);
            check(allowed == expectedHit, name + ": production MITE guard expected=" + expectedHit + ", actual=" + allowed);
            check(found == allowed, name + ": client candidates and the production MITE guard agree");
            fact.addProperty("passed", true);
        } finally {
            mc.level.removeEntity(target.getId(), Entity.RemovalReason.DISCARDED);
        }
    }

    private static void nativeCase(Minecraft mc, String name, double faceDistance, boolean expectedHit) {
        Mob target = target(mc, mc.player, faceDistance, 0D);
        try {
            double reach = SourcePlayerReach.meleeReach(mc.player, target);
            boolean allowed = SourcePlayerReach.canMelee(mc.player, target);
            JsonObject fact = fact(name, mc.player, target, faceDistance, 0D, reach, 2.25D, expectedHit);
            fact.addProperty("mite_can_melee", allowed);
            report.getAsJsonArray("cases").add(fact);
            near(reach, 2.25D, "MITE sword range without Better Combat");
            check(allowed == expectedHit, name + ": original MITE guard expected=" + expectedHit + ", actual=" + allowed);
            fact.addProperty("passed", true);
        } finally {
            mc.level.removeEntity(target.getId(), Entity.RemovalReason.DISCARDED);
        }
    }

    private static JsonObject fact(String name, Player player, Mob target, double faceDistance, double feetOffset,
            double reach, double expectedRange, boolean expectedHit) {
        JsonObject fact = new JsonObject();
        fact.addProperty("case", name);
        fact.addProperty("weapon", BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString());
        fact.addProperty("target_front_horizontal_distance", faceDistance);
        fact.addProperty("target_feet_height_difference", feetOffset);
        fact.addProperty("player_scale", player.getScale());
        fact.addProperty("player_yaw", player.getYRot());
        fact.addProperty("mite_multiplier", MiteConfig.weaponReachMultiplier());
        fact.addProperty("mite_reduction", MiteConfig.weaponReachReduction());
        fact.addProperty("source_height_adjustment_setting", MiteConfig.heightReachPenaltyEnabled());
        fact.addProperty("actual_guard_range", reach);
        fact.addProperty("expected_guard_range", expectedRange);
        fact.addProperty("expected_hit", expectedHit);
        fact.addProperty("eye_to_inflated_target_box", player.getEyePosition().distanceTo(SourcePlayerReach.closestPoint(
                target.getBoundingBox().inflate(target.getPickRadius()), player.getEyePosition())));
        return fact;
    }

    /** Exercise each actual BC filter independently so an invalid fixture cannot look like a range regression. */
    @SuppressWarnings("unchecked")
    private static void diagnostics(Minecraft mc, Player player, Mob target, Attack attack, JsonObject fact)
            throws ReflectiveOperationException {
        JsonObject stages = new JsonObject();
        fact.add("native_filter_diagnostics", stages);
        Vec3 origin = api().origin(player);
        stages.add("origin", vector(origin));
        stages.add("player_position", vector(player.position()));
        stages.add("player_eye", vector(player.getEyePosition()));
        stages.add("player_view", vector(player.getViewVector(1F)));
        stages.add("player_previous_position", vector(new Vec3(player.xo, player.yo, player.zo)));
        stages.addProperty("player_yaw", player.getYRot());
        stages.addProperty("player_pitch", player.getXRot());
        stages.addProperty("player_previous_yaw", player.yRotO);
        stages.addProperty("player_previous_pitch", player.xRotO);
        stages.addProperty("target_id", target.getId());
        stages.add("target_position", vector(target.position()));
        stages.add("target_box", box(target.getBoundingBox()));
        stages.addProperty("target_pick_radius", target.getPickRadius());
        stages.addProperty("target_pickable", target.isPickable());
        stages.addProperty("target_attackable", target.isAttackable());
        stages.addProperty("target_spectator", target.isSpectator());
        stages.addProperty("client_level_get_entity_returns_target", mc.level.getEntity(target.getId()) == target);
        stages.addProperty("player_chunk_loaded", mc.level.hasChunkAt(player.blockPosition()));
        stages.addProperty("target_chunk_loaded", mc.level.hasChunkAt(target.blockPosition()));
        boolean renderingContains = false;
        for (Entity entity : mc.level.entitiesForRendering()) if (entity == target) renderingContains = true;
        stages.addProperty("client_rendering_entities_contains_target", renderingContains);

        Class<?> finder = api().find.getDeclaringClass();
        Class<?> move = Class.forName("net.bettercombat.api.WeaponAttributes$Attack");
        Method initial = finder.getMethod("getInitialTargets", Player.class, Entity.class, double.class);
        List<?> rawInitial = (List<?>)initial.invoke(null, player, null, attack.rawRange());
        double searchRange = BetterCombatPlayerReach.searchRange(player, attack.rawRange());
        List<?> actualInitial = (List<?>)initial.invoke(null, player, null, searchRange);
        stages.addProperty("raw_initial_search_contains_target", rawInitial.contains(target));
        stages.addProperty("configured_initial_search_contains_target", actualInitial.contains(target));
        stages.addProperty("raw_initial_search_count", rawInitial.size());
        stages.addProperty("configured_initial_search_count", actualInitial.size());
        stages.addProperty("configured_broad_phase_range_argument", searchRange);
        Object config = Class.forName("net.bettercombat.BetterCombatMod").getMethod("getConfig").invoke(null);
        double searchMultiplier = config.getClass().getField("target_search_range_multiplier").getFloat(config);
        AABB searchBox = player.getBoundingBox().inflate(searchRange * searchMultiplier + 1D);
        stages.add("configured_broad_phase_box", box(searchBox));
        stages.addProperty("native_entity_query_contains_target", mc.level.getEntities(player, searchBox).contains(target));

        Class<?> extensions = Class.forName("net.bettercombat.api.client.AttackRangeExtensions");
        Object extensionContext = Class.forName("net.bettercombat.api.client.AttackRangeExtensions$Context")
                .getConstructor(Player.class, double.class).newInstance(player, attack.rawRange());
        List<?> sources = (List<?>)extensions.getMethod("sources").invoke(null);
        JsonArray modifiers = new JsonArray();
        double added = 0D, multiplied = 1D;
        for (Object source : sources) {
            Object modifier = ((Function<Object, Object>)source).apply(extensionContext);
            String operation = modifier.getClass().getMethod("operation").invoke(modifier).toString();
            double value = ((Number)modifier.getClass().getMethod("value").invoke(modifier)).doubleValue();
            JsonObject entry = new JsonObject();
            entry.addProperty("source", source.getClass().getName());
            entry.addProperty("operation", operation); entry.addProperty("value", value);
            modifiers.add(entry);
            if (operation.equals("ADD")) added += value;
            if (operation.equals("MULTIPLY")) multiplied *= value;
        }
        stages.add("bc_range_extensions", modifiers);
        stages.addProperty("post_bc_extension_range", (attack.rawRange() + added) * multiplied);
        Method applyRange = finder.getDeclaredMethod("applyAttackRangeModifiers", Player.class, double.class);
        applyRange.setAccessible(true);
        double finalRange = ((Number)applyRange.invoke(null, player, attack.rawRange())).doubleValue();
        stages.addProperty("native_configured_final_range", finalRange);

        Object result = finder.getMethod("findAttackTargetResult", Player.class, Entity.class, move, double.class)
                .invoke(null, player, null, attack.move(), attack.rawRange());
        Object obb = result.getClass().getField("obb").get(result);
        Class<?> obbType = obb.getClass();
        Vec3 axis = (Vec3)obbType.getField("axisZ").get(obb);
        stages.add("obb_center", vector((Vec3)obbType.getField("center").get(obb)));
        stages.add("obb_extent", vector((Vec3)obbType.getField("extent").get(obb)));
        stages.add("obb_axis_z", vector(axis));
        stages.addProperty("obb_intersects_inflated_target_box", (Boolean)obbType.getMethod("intersects", AABB.class)
                .invoke(obb, target.getBoundingBox().inflate(target.getPickRadius())));
        stages.addProperty("obb_contains_target_center", (Boolean)obbType.getMethod("contains", Vec3.class)
                .invoke(obb, target.position().add(0D, target.getBbHeight() / 2D, 0D)));
        Class<?> collisionType = Class.forName("net.bettercombat.client.collision.TargetFinder$CollisionFilter");
        Object collision = collisionType.getConstructor(obbType).newInstance(obb);
        List<?> afterCollision = (List<?>)collisionType.getMethod("filter", List.class).invoke(collision, List.of(target));
        stages.addProperty("collision_filter_accepts_target", afterCollision.contains(target));
        double angle = ((Number)move.getMethod("angle").invoke(attack.move())).doubleValue();
        Class<?> radialType = Class.forName("net.bettercombat.client.collision.TargetFinder$RadialFilter");
        Object radial = radialType.getConstructor(Vec3.class, Vec3.class, double.class, double.class)
                .newInstance(origin, axis, finalRange, angle);
        List<?> afterRadial = (List<?>)radialType.getMethod("filter", List.class).invoke(radial, List.of(target));
        stages.addProperty("attack_angle", angle);
        stages.addProperty("radial_filter_accepts_target", afterRadial.contains(target));
        Vec3 nearest = SourcePlayerReach.closestPoint(target.getBoundingBox(), origin);
        Vec3 center = target.position().add(0D, target.getBbHeight() / 2D, 0D);
        stages.addProperty("origin_to_target_box", origin.distanceTo(nearest));
        stages.add("nearest_target_point", vector(nearest));
        stages.add("nearest_point_ray", ray(mc, player, origin, nearest));
        stages.add("target_center_ray", ray(mc, player, origin, center));
        Class<?> helper = Class.forName("net.bettercombat.logic.TargetHelper");
        Object relation = helper.getMethod("getRelation", Player.class, Entity.class).invoke(null, player, target);
        stages.addProperty("target_relation", relation.toString());
        stages.addProperty("indirect_hit_allowed", (Boolean)helper.getMethod("isHitAllowed", boolean.class,
                Class.forName("net.bettercombat.logic.TargetHelper$Relation")).invoke(null, false, relation));
        if (fact.get("targetfinder_contains_target").getAsBoolean() != fact.get("expected_hit").getAsBoolean()
                || fact.get("mite_can_melee").getAsBoolean() != fact.get("expected_hit").getAsBoolean()) {
            stages.add("failed_case_native_sat", satDiagnostics(obb, target.getBoundingBox().inflate(target.getPickRadius())));
        }
    }

    /** The original private Separated method is invoked for every one of its fifteen actual axes. */
    private static JsonObject satDiagnostics(Object attackBox, AABB targetBounds) throws ReflectiveOperationException {
        Class<?> type = attackBox.getClass();
        Object targetBox = type.getConstructor(AABB.class).newInstance(targetBounds);
        type.getMethod("updateVertex").invoke(targetBox);
        Vec3[] attackVertices = (Vec3[])type.getField("vertices").get(attackBox);
        Vec3[] targetVertices = (Vec3[])type.getField("vertices").get(targetBox);
        String[] names = {"X", "Y", "Z"};
        Vec3[] attackAxes = new Vec3[3], targetAxes = new Vec3[3];
        Vec3[] attackScaledAxes = new Vec3[3], targetScaledAxes = new Vec3[3];
        JsonObject report = new JsonObject();
        for (int i = 0; i < names.length; i++) {
            attackAxes[i] = (Vec3)type.getField("axis" + names[i]).get(attackBox);
            targetAxes[i] = (Vec3)type.getField("axis" + names[i]).get(targetBox);
            attackScaledAxes[i] = (Vec3)type.getField("scaledAxis" + names[i]).get(attackBox);
            targetScaledAxes[i] = (Vec3)type.getField("scaledAxis" + names[i]).get(targetBox);
            report.add("attack_axis_" + names[i], vector(attackAxes[i]));
            report.add("attack_scaled_axis_" + names[i], vector(attackScaledAxes[i]));
            report.add("target_axis_" + names[i], vector(targetAxes[i]));
            report.add("target_scaled_axis_" + names[i], vector(targetScaledAxes[i]));
        }
        report.addProperty("attack_axis_x_dot_y", attackAxes[0].dot(attackAxes[1]));
        report.addProperty("attack_axis_y_dot_z", attackAxes[1].dot(attackAxes[2]));
        report.addProperty("attack_axis_z_dot_x", attackAxes[2].dot(attackAxes[0]));
        report.addProperty("attack_axis_x_cross_y_length_squared", attackAxes[0].cross(attackAxes[1]).lengthSqr());
        report.addProperty("attack_axis_y_cross_z_length_squared", attackAxes[1].cross(attackAxes[2]).lengthSqr());
        report.addProperty("attack_axis_z_cross_x_length_squared", attackAxes[2].cross(attackAxes[0]).lengthSqr());
        JsonArray aVertices = new JsonArray(), bVertices = new JsonArray();
        for (Vec3 vertex : attackVertices) aVertices.add(vector(vertex));
        for (Vec3 vertex : targetVertices) bVertices.add(vector(vertex));
        report.add("attack_vertices", aVertices); report.add("target_vertices", bVertices);
        Method separated = type.getDeclaredMethod("Separated", Vec3[].class, Vec3[].class, Vec3.class);
        separated.setAccessible(true);
        JsonArray axes = new JsonArray();
        for (int i = 0; i < 3; i++) axes.add(satAxis("attack_" + names[i], attackScaledAxes[i], attackVertices, targetVertices, separated));
        for (int i = 0; i < 3; i++) axes.add(satAxis("target_" + names[i], targetScaledAxes[i], attackVertices, targetVertices, separated));
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++)
            axes.add(satAxis("attack_" + names[i] + "_cross_target_" + names[j], attackScaledAxes[i].cross(targetScaledAxes[j]),
                    attackVertices, targetVertices, separated));
        report.add("sat_axes", axes);
        return report;
    }

    private static JsonObject satAxis(String label, Vec3 axis, Vec3[] attackVertices, Vec3[] targetVertices, Method separated)
            throws ReflectiveOperationException {
        JsonObject result = new JsonObject();
        result.addProperty("axis", label); result.add("vector", vector(axis));
        result.addProperty("length_squared", axis.lengthSqr());
        result.addProperty("equals_vec3_zero", axis.equals(Vec3.ZERO));
        result.addProperty("all_components_numerically_zero", axis.x == 0D && axis.y == 0D && axis.z == 0D);
        JsonArray bits = new JsonArray();
        bits.add(Long.toHexString(Double.doubleToRawLongBits(axis.x)));
        bits.add(Long.toHexString(Double.doubleToRawLongBits(axis.y)));
        bits.add(Long.toHexString(Double.doubleToRawLongBits(axis.z)));
        result.add("component_raw_bits", bits);
        double aMin = Double.POSITIVE_INFINITY, aMax = Double.NEGATIVE_INFINITY;
        double bMin = Double.POSITIVE_INFINITY, bMax = Double.NEGATIVE_INFINITY;
        for (Vec3 vertex : attackVertices) {
            double projection = vertex.dot(axis); aMin = Math.min(aMin, projection); aMax = Math.max(aMax, projection);
        }
        for (Vec3 vertex : targetVertices) {
            double projection = vertex.dot(axis); bMin = Math.min(bMin, projection); bMax = Math.max(bMax, projection);
        }
        JsonArray aInterval = new JsonArray(); aInterval.add(aMin); aInterval.add(aMax);
        JsonArray bInterval = new JsonArray(); bInterval.add(bMin); bInterval.add(bMax);
        result.add("attack_projected_interval", aInterval); result.add("target_projected_interval", bInterval);
        double longSpan = Math.max(aMax, bMax) - Math.min(aMin, bMin), sumSpan = aMax - aMin + bMax - bMin;
        result.addProperty("long_span", longSpan); result.addProperty("sum_span", sumSpan);
        result.addProperty("span_difference", longSpan - sumSpan);
        result.addProperty("native_separated", (Boolean)separated.invoke(null, attackVertices, targetVertices, axis));
        return result;
    }

    private static JsonObject ray(Minecraft mc, Player player, Vec3 origin, Vec3 destination) {
        var hit = mc.level.clip(new ClipContext(origin, destination, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        JsonObject result = new JsonObject();
        result.addProperty("type", hit.getType().name());
        result.add("location", vector(hit.getLocation()));
        result.addProperty("block_position", hit.getBlockPos().toShortString());
        if (hit.getType() == HitResult.Type.BLOCK) result.addProperty("block_state", mc.level.getBlockState(hit.getBlockPos()).toString());
        return result;
    }

    private static JsonArray vector(Vec3 value) {
        JsonArray result = new JsonArray(); result.add(value.x); result.add(value.y); result.add(value.z); return result;
    }

    private static JsonArray box(AABB value) {
        JsonArray result = new JsonArray();
        result.add(value.minX); result.add(value.minY); result.add(value.minZ);
        result.add(value.maxX); result.add(value.maxY); result.add(value.maxZ);
        return result;
    }

    private static Mob target(Minecraft mc, Player player, double faceDistance, double feetOffset) {
        Mob target = EntityType.ZOMBIE.create(mc.level);
        check(target != null, "The native client zombie constructor succeeds");
        target.setId(nextEntityId--);
        target.setNoAi(true); target.setNoGravity(true);
        Vec3 view = player.getViewVector(1F);
        Vec3 forward = new Vec3(view.x, 0D, view.z).normalize();
        // The four cardinal and 45-degree fixtures measure the nearest AABB face/corner,
        // including its larger diagonal support, rather than moving the target off the view axis.
        double halfWidth = target.getBbWidth() / 2D;
        double centerDistance = faceDistance + halfWidth * (Math.abs(forward.x) + Math.abs(forward.z));
        target.setPos(player.getX() + forward.x * centerDistance, player.getY() + feetOffset,
                player.getZ() + forward.z * centerDistance);
        target.setOldPosAndRot();
        mc.level.addEntity(target);
        return target;
    }

    private static void equip(Player player, ItemStack stack) {
        equip(player, stack, ItemStack.EMPTY);
    }

    private static void equip(Player player, ItemStack main, ItemStack off) {
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        var range = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        range.removeModifiers(); range.setBaseValue(3D);
        main.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ENTITY_INTERACTION_RANGE)) range.addTransientModifier(modifier);
        });
        off.forEachModifier(EquipmentSlot.OFFHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ENTITY_INTERACTION_RANGE)) range.addTransientModifier(modifier);
        });
    }

    private static void scale(Player player, double value) {
        player.getAttribute(Attributes.SCALE).setBaseValue(value);
        player.refreshDimensions();
    }

    private static void settings(MiteConfig.Snapshot base, double multiplier, double reduction, boolean heightAdjustment) {
        MiteConfig.receiveServerSnapshot(new MiteConfig.Snapshot(base.ores(), base.growth(), base.healthGrowth(),
                base.damageGrowth(), multiplier, reduction, heightAdjustment, base.underworldTrials(), base.overworldTrials(),
                base.caps(), base.disabled(), base.monsterSettings(), base.oreCandidates(), base.oreChances()));
    }

    private static Attack attack(Player player) throws ReflectiveOperationException {
        int combo = Math.max(0, ((Number)api().getCombo.invoke(player)).intValue());
        Object hand = api().hand.invoke(null, player, combo);
        check(hand != null, "The actual installed Better Combat registry resolves the equipped weapon's attack");
        Object move = hand.getClass().getMethod("attack").invoke(hand);
        ItemStack weapon = (ItemStack)hand.getClass().getMethod("itemStack").invoke(hand);
        boolean offHand = (Boolean)hand.getClass().getMethod("isOffHand").invoke(hand);
        double base = ((Number)api().range.invoke(null, player, weapon)).doubleValue();
        double multiplier = ((Number)move.getClass().getMethod("rangeMultiplier").invoke(move)).doubleValue();
        return new Attack(move, base * multiplier, weapon, offHand, combo);
    }

    private static Api api() throws ReflectiveOperationException {
        return ApiHolder.api();
    }

    /** Reflection keeps the subscriber and its optional-absence path free of Better Combat class links. */
    private static final class ApiHolder {
        private static Api cached;
        private static Api api() throws ReflectiveOperationException {
            if (cached == null) {
                Class<?> helper = Class.forName("net.bettercombat.logic.PlayerAttackHelper");
                Class<?> attack = Class.forName("net.bettercombat.api.WeaponAttributes$Attack");
                Class<?> finder = Class.forName("net.bettercombat.client.collision.TargetFinder");
                cached = new Api(helper.getMethod("getCurrentAttack", Player.class, int.class),
                        helper.getMethod("getRangeForItem", Player.class, ItemStack.class),
                        finder.getMethod("findAttackTargets", Player.class, Entity.class, attack, double.class),
                        finder.getMethod("getInitialTracingPoint", Player.class),
                        Class.forName("net.bettercombat.logic.PlayerAttackProperties").getMethod("setComboCount", int.class),
                        Class.forName("net.bettercombat.logic.PlayerAttackProperties").getMethod("getComboCount"));
            }
            return cached;
        }
    }

    private static void fail(Minecraft mc, Throwable failure) {
        if (failure instanceof InvocationTargetException invocation && invocation.getCause() != null) failure = invocation.getCause();
        if (report == null) {
            report = new JsonObject(); report.addProperty("author", "NingMeng123"); report.add("failures", new JsonArray());
        }
        report.getAsJsonArray("failures").add(failure.toString());
        report.addProperty("status", "failed");
        Mite.LOGGER.error("Better Combat player reach client audit failed", failure);
        finish(mc);
    }

    private static void finish(Minecraft mc) {
        done = true;
        report.addProperty("safe_client_shutdown_requested", !Boolean.getBoolean("mite.punchyPreview"));
        try {
            Path output = output(); Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
        } catch (Exception failure) { Mite.LOGGER.error("Cannot write the Better Combat player reach client audit", failure); }
        if (!Boolean.getBoolean("mite.punchyPreview")) mc.stop();
    }

    private static Path output() {
        return Path.of(System.getProperty("mite.betterCombatPlayerReachAuditOutput",
                "build/verification/bettercombat-player-reach-client-audit.json")).toAbsolutePath().normalize();
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < .00001D, message + "; expected=" + expected + ", actual=" + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Attack(Object move, double rawRange, ItemStack weapon, boolean offHand, int combo) {}
    private record Api(Method hand, Method range, Method find, Method tracingPoint, Method setCombo, Method getCombo) {
        private Vec3 origin(Player player) throws ReflectiveOperationException { return (Vec3)tracingPoint.invoke(null, player); }
    }
}

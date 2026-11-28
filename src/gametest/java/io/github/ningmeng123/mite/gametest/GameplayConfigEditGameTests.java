package io.github.ningmeng123.mite.gametest;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.config.GameplayConfigEdits;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.config.MonsterArmorSettings;
import io.github.ningmeng123.mite.network.GameplayConfigEditPayload;
import io.github.ningmeng123.mite.network.GameplayConfigEditResultPayload;
import io.github.ningmeng123.mite.network.GameplayConfigPayload;
import io.github.ningmeng123.mite.rules.MonsterArmorRules;
import io.github.ningmeng123.mite.rules.MonsterGrowthRules;
import io.github.ningmeng123.mite.rules.MonsterSpawningSettings.Option;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.ServerOpListEntry;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real server authorization, atomic validation, persistence and remote edit codecs. @author NingMeng123 */
@GameTestHolder("mite_config_edit")
@PrefixGameTestTemplate(false)
public final class GameplayConfigEditGameTests {
    private static final Gson GSON = new Gson();
    private GameplayConfigEditGameTests() {}

    @GameTest(template = "empty")
    public static void ordinaryPlayersAndRevokedOperatorsCannotWriteServerConfiguration(GameTestHelper helper) throws Exception {
        try (var settings = new Settings(); var operator = new Operator(helper)) {
            var before = MiteConfig.localSnapshot();
            byte[] disk = Files.readAllBytes(configPath());
            for (int permission : new int[]{0, 1, 2, 0}) {
                operator.permission(permission);
                helper.assertTrue(operator.player.hasPermissions(2) == (permission >= 2), "The fixture uses the server's real OP permission lookup");
                String result = GameplayConfigEdits.apply(operator.player, "spawn_rates", permission == 2 ? "null" : "this is invalid JSON");
                helper.assertTrue(result != null, "Unauthorized or invalid requests report failure");
                if (permission < 2) helper.assertValueEqual(result, "mite.config.remote.no_permission",
                        "Permission is rejected before an unauthorized payload is parsed");
                helper.assertTrue(MiteConfig.localSnapshot() == before, "A rejected request cannot publish a configuration revision");
                helper.assertTrue(Arrays.equals(Files.readAllBytes(configPath()), disk), "A rejected request cannot write the server config file");
            }
            operator.permission(2);
            var request = new GameplayConfigEdits.SpawnRates(Map.of("minecraft:zombie", .5D), Map.of("minecraft:zombie", 12.25D));
            helper.assertTrue(GameplayConfigEdits.apply(operator.player, "spawn_rates", GSON.toJson(request)) == null,
                    "A level-two operator can save a valid request");
            var saved = MiteConfig.localSnapshot();
            operator.permission(0);
            helper.assertTrue(GameplayConfigEdits.apply(operator.player, "spawn_rates", GSON.toJson(
                    new GameplayConfigEdits.SpawnRates(Map.of("minecraft:zombie", 0D), Map.of()))) != null,
                    "Revoking OP is enforced again on the next save");
            helper.assertTrue(MiteConfig.localSnapshot() == saved, "A client with a previously enabled editor cannot keep writing after deop");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void operatorChangesApplyImmediatelyPersistAndPublishOneAuthoritativeSnapshot(GameTestHelper helper) throws Exception {
        try (var settings = new Settings(); var operator = new Operator(helper)) {
            operator.permission(2);
            var draft = new HashMap<>(MiteConfig.localSnapshot().spawningSettings().values());
            draft.put(Option.REFERENCE_RULES.key(), 0D);
            draft.put(Option.BASE_CAP.key(), 37D);
            draft.put(Option.DAILY_CAP_INCREASE.key(), .25D);
            draft.put(Option.MAXIMUM_CAP.key(), 0D);
            helper.assertTrue(GameplayConfigEdits.apply(operator.player, "spawning_rules", GSON.toJson(draft)) == null,
                    "A complete valid server spawning draft is accepted");
            helper.assertValueEqual(MiteConfig.monsterSpawningSettings().cap(289, false, 1), 37,
                    "The runtime natural-spawn cap sees the saved value immediately");
            helper.assertValueEqual(MiteConfig.monsterSpawningSettings().cap(289, false, 5), 38,
                    "Fractional daily increases reach the live spawning rules");
            var disk = new TomlParser().parse(Files.readString(configPath()));
            helper.assertValueEqual(((Number)disk.get(List.of("monster_spawning", "base_cap"))).intValue(), 37,
                    "The OP save is written into the server's loaded common config");
            helper.assertValueEqual(((Number)disk.get(List.of("monster_spawning", "daily_cap_increase"))).doubleValue(), .25D,
                    "Decimal settings retain their value on disk");
            var published = MiteConfig.localSnapshot();
            var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                GameplayConfigPayload.CODEC.encode(bytes, new GameplayConfigPayload(published));
                helper.assertValueEqual(GameplayConfigPayload.CODEC.decode(bytes).snapshot(), published,
                        "The server's resulting revision survives the actual client synchronization codec");
                helper.assertValueEqual(bytes.readableBytes(), 0, "The synchronization packet is consumed completely");
            } finally { bytes.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void monsterArmorEditsValidatePersistAndSynchronizeTheWholeTable(GameTestHelper helper) throws Exception {
        try (var settings = new Settings(); var operator = new Operator(helper)) {
            var before = MonsterArmorSettings.localSettings();
            byte[] previousFile = Files.readAllBytes(configPath());
            var rows = new ArrayList<>(MonsterArmorRules.DEFAULTS.rows());
            rows.set(0, new MonsterArmorRules.Row(19, 30, 12.5D, 73.25D));
            rows.set(rows.size() - 1, new MonsterArmorRules.Row(9_007_199_254_740_993L, MonsterArmorRules.OPEN_END, 49, 98));
            var draft = new MonsterArmorRules.Settings(rows);
            helper.assertValueEqual(GameplayConfigEdits.apply(operator.player, "monster_armor", GSON.toJson(draft)),
                    "mite.config.remote.no_permission", "Only an operator can save the armor settings");
            operator.permission(2);
            for (var change : List.<Consumer<JsonObject>>of(
                    row -> row.addProperty("startDay", 19.5D),
                    row -> row.addProperty("startDay", "19"),
                    row -> row.addProperty("endDay", new BigInteger("9223372036854775808")),
                    row -> row.addProperty("endDay", 18),
                    row -> row.addProperty("endDay", 32),
                    row -> row.addProperty("ordinaryPercent", 100.01D),
                    row -> row.remove("bloodPercent"))) {
                var invalid = GSON.toJsonTree(draft).getAsJsonObject();
                change.accept(invalid.getAsJsonArray("rows").get(0).getAsJsonObject());
                helper.assertValueEqual(GameplayConfigEdits.apply(operator.player, "monster_armor", invalid.toString()),
                        "mite.config.remote.invalid", "Invalid armor data is rejected before saving");
            }
            helper.assertValueEqual(MonsterArmorSettings.localSettings(), before, "Rejected requests leave the active table untouched");
            helper.assertTrue(Arrays.equals(Files.readAllBytes(configPath()), previousFile), "Rejected requests leave the config file untouched");

            helper.assertTrue(GameplayConfigEdits.apply(operator.player, "monster_armor", GSON.toJson(draft)) == null,
                    "An operator can change dates and fractional probabilities together");
            helper.assertValueEqual(MonsterArmorSettings.localSettings(), draft, "A successful save immediately publishes the full table");
            var written = new TomlParser().parse(Files.readString(configPath()));
            var entries = written.<List<String>>get(List.of("monsters", "armor_progression"));
            helper.assertTrue(entries.contains("leather=19,30,12.5,73.25") && entries.contains("vibranium=9007199254740993,*,49.0,98.0"),
                    "The common config retains exact day limits and decimal chances");

            var snapshot = MiteConfig.localSnapshot();
            var packet = new GameplayConfigPayload(snapshot, Map.of(), Map.of(), null, MonsterArmorSettings.localSettings());
            var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                GameplayConfigPayload.CODEC.encode(bytes, packet);
                helper.assertValueEqual(GameplayConfigPayload.CODEC.decode(bytes).monsterArmor(), draft,
                        "The server synchronization packet retains the configured armor table exactly");
                bytes.writeUtf(GSON.toJson(snapshot), 262144);
                helper.assertTrue(GameplayConfigPayload.CODEC.decode(bytes).monsterArmor() == null,
                        "An older server packet without armor settings remains readable");
                helper.assertValueEqual(bytes.readableBytes(), 0, "The codec consumes both synchronization packets completely");
            } finally { bytes.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remoteGrowthEditsSaveTheMinimumReferenceAndLegacyEditsPreserveIt(GameTestHelper helper) throws Exception {
        try (var settings = new Settings(); var operator = new Operator(helper)) {
            operator.permission(2);
            var draft = GSON.toJsonTree(MiteConfig.localSnapshot().monsterSettings()).getAsJsonObject();
            draft.addProperty("minimumHealthReference", 55D);
            helper.assertTrue(GameplayConfigEdits.apply(operator.player, "growth", draft.toString()) == null,
                    "A valid operator draft can change the minimum health reference");
            helper.assertValueEqual(MiteConfig.monsterGrowthSettings().minimumHealthReference(), 55D,
                    "Server growth immediately uses the edited minimum");
            var disk = new TomlParser().parse(Files.readString(configPath()));
            helper.assertValueEqual(((Number)disk.get(List.of("monsters", "minimum_health_reference"))).doubleValue(), 55D,
                    "The edited minimum is saved to the common config");

            draft.remove("minimumHealthReference");
            draft.addProperty("healthBase", 25D);
            helper.assertTrue(GameplayConfigEdits.apply(operator.player, "growth", draft.toString()) == null,
                    "An older operator client can still edit the other growth fields");
            helper.assertValueEqual(MiteConfig.monsterGrowthSettings().minimumHealthReference(), 55D,
                    "The legacy edit preserves the configured minimum");
            draft.addProperty("minimumHealthReference", -1D);
            helper.assertTrue(GameplayConfigEdits.apply(operator.player, "growth", draft.toString()) != null,
                    "A negative minimum is rejected");
            helper.assertValueEqual(MiteConfig.monsterGrowthSettings().minimumHealthReference(), 55D,
                    "A rejected edit does not change the active minimum");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedAndPartlyInvalidOperatorDraftsLeaveEverySettingAndFileUntouched(GameTestHelper helper) throws Exception {
        try (var settings = new Settings(); var operator = new Operator(helper)) {
            operator.permission(2);
            var before = MiteConfig.localSnapshot();
            var values = currentValues();
            byte[] disk = Files.readAllBytes(configPath());
            var invalid = List.of(
                    Map.entry("missing_section", "{}"),
                    Map.entry("spawn_rates", "{\"rates\":{\"minecraft:zombie\":0},\"weights\":{\"minecraft:skeleton\":-1}}"),
                    Map.entry("spawning_rules", "{\"base_cap\":12.5}"),
                    Map.entry("gameplay", "{\"combat.weapon_reach_multiplier\":2,\"missing.option\":true}"),
                    Map.entry("gameplay", "{\"combat.weapon_reach_multiplier\":2,\"combat.height_reach_penalty\":0}"),
                    Map.entry("gameplay", "{\"combat.weapon_reach_multiplier\":1e400}"),
                    Map.entry("gameplay", "{\"combat.better_combat_damage_multiplier\":-0.1}"),
                    Map.entry("gameplay", "{\"combat.better_combat_attack_speed_multiplier\":0}"),
                    Map.entry("gameplay", "{\"combat.better_combat_attack_speed_multiplier\":10.1}"),
                    Map.entry("growth", "null"),
                    Map.entry("enchantments", "{\"caps\":{\"minecraft:sharpness\":0},\"disabled\":[],\"chances\":{}}"),
                    Map.entry("enchantments", "{\"caps\":{\"minecraft:sharpness\":2.5},\"disabled\":[],\"chances\":{}}"),
                    Map.entry("enchantments", "{\"caps\":{\"minecraft:sharpness\":4294967297},\"disabled\":[],\"chances\":{}}"),
                    Map.entry("spawning_rules", "not JSON"));
            for (var request : invalid) {
                helper.assertTrue(GameplayConfigEdits.apply(operator.player, request.getKey(), request.getValue()) != null,
                        "Invalid operator draft is rejected: " + request.getKey() + " / " + request.getValue());
                helper.assertTrue(MiteConfig.localSnapshot() == before, "Rejected drafts cannot replace the published runtime revision");
                helper.assertValueEqual(currentValues(), values, "Rejected drafts cannot mutate any editable config value");
                helper.assertTrue(Arrays.equals(Files.readAllBytes(configPath()), disk), "Rejected drafts cannot partially save to disk");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void generalGameplayEditsUseServerValuesAndPreserveUnrelatedSettings(GameTestHelper helper) throws Exception {
        try (var settings = new Settings(); var operator = new Operator(helper)) {
            operator.permission(2);
            var oldSpawning = MiteConfig.localSnapshot().spawningSettings();
            helper.assertValueEqual(MiteConfig.factorySnapshot().betterCombatDamageMultiplier(), .75D,
                    "The bundled Better Combat damage multiplier defaults to 75 percent");
            helper.assertValueEqual(MiteConfig.factorySnapshot().betterCombatAttackSpeedMultiplier(), .8D,
                    "The bundled Better Combat attack speed multiplier defaults to 80 percent");
            helper.assertTrue(GameplayConfigEdits.apply(operator.player, "gameplay", GSON.toJson(Map.of(
                    "combat.weapon_reach_multiplier", 2.25D, "combat.height_reach_penalty", false,
                    "combat.better_combat_damage_multiplier", .65D,
                    "combat.better_combat_attack_speed_multiplier", 1.2D,
                    "monsters.damage_growth_multiplier", 1.5D))) == null,
                    "A general gameplay patch is accepted through the server authority boundary");
            helper.assertValueEqual(MiteConfig.weaponReachMultiplier(), 2.25D, "The live reach rule receives the server edit");
            helper.assertValueEqual(MiteConfig.betterCombatDamageMultiplier(), .65D, "The live Better Combat damage uses the server edit");
            helper.assertValueEqual(GameplayConfigEdits.gameplayValues().get("combat.better_combat_damage_multiplier"), .65D,
                    "The remote editor receives the live damage multiplier");
            helper.assertValueEqual(MiteConfig.betterCombatAttackSpeedMultiplier(), 1.2D,
                    "The live Better Combat attack speed uses the server edit");
            helper.assertValueEqual(GameplayConfigEdits.gameplayValues().get("combat.better_combat_attack_speed_multiplier"), 1.2D,
                    "The remote editor receives the live attack speed multiplier");
            helper.assertValueEqual(MiteConfig.localSnapshot().damageGrowth(), 1.5D, "The server applies the edited attack growth multiplier");
            helper.assertFalse(MiteConfig.heightReachPenaltyEnabled(), "The live boolean rule receives the server edit");
            helper.assertValueEqual(MiteConfig.localSnapshot().spawningSettings(), oldSpawning, "A gameplay patch preserves unrelated dedicated settings");
            var disk = new TomlParser().parse(Files.readString(configPath()));
            helper.assertValueEqual(((Number)disk.get(List.of("combat", "weapon_reach_multiplier"))).doubleValue(), 2.25D,
                    "General gameplay saves target the loaded server config");
            helper.assertValueEqual(((Number)disk.get(List.of("combat", "better_combat_damage_multiplier"))).doubleValue(), .65D,
                    "The Better Combat damage multiplier is saved to the dedicated server config");
            helper.assertValueEqual(((Number)disk.get(List.of("combat", "better_combat_attack_speed_multiplier"))).doubleValue(), 1.2D,
                    "The Better Combat attack speed multiplier is saved to the dedicated server config");
            helper.assertValueEqual(disk.get(List.of("combat", "height_reach_penalty")), false, "Boolean values stay booleans on disk");
            var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                var snapshot = MiteConfig.localSnapshot();
                GameplayConfigPayload.CODEC.encode(bytes, new GameplayConfigPayload(snapshot));
                var synchronizedSnapshot = GameplayConfigPayload.CODEC.decode(bytes).snapshot();
                helper.assertValueEqual(synchronizedSnapshot.betterCombatDamageMultiplier(), .65D,
                        "Server synchronization includes the damage multiplier");
                helper.assertValueEqual(synchronizedSnapshot.betterCombatAttackSpeedMultiplier(), 1.2D,
                        "Server synchronization includes the attack speed multiplier");
                var legacy = GSON.toJsonTree(snapshot).getAsJsonObject();
                legacy.remove("betterCombatAttackSpeedMultiplier");
                bytes.writeUtf(legacy.toString(), 262144);
                var legacyAttackSpeed = GameplayConfigPayload.CODEC.decode(bytes).snapshot();
                helper.assertValueEqual(legacyAttackSpeed.betterCombatAttackSpeedMultiplier(), .8D,
                        "Older snapshots without the attack speed multiplier use the factory value");
                helper.assertValueEqual(legacyAttackSpeed.betterCombatDamageMultiplier(), .65D,
                        "The older snapshot preserves the configured damage multiplier");
                legacy.remove("betterCombatDamageMultiplier");
                bytes.writeUtf(legacy.toString(), 262144);
                helper.assertValueEqual(GameplayConfigPayload.CODEC.decode(bytes).snapshot().betterCombatDamageMultiplier(), .75D,
                        "Older snapshots without the multiplier use the factory value");
            } finally { bytes.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oldDefaultBetterCombatDamageUpgradesOnceAndCustomValuesSurvive(GameTestHelper helper) throws Exception {
        try (var ignored = new Settings()) {
            ModConfigSpec.BooleanValue migrated = MiteConfig.SPEC.getValues().get("combat.better_combat_damage_migrated");
            migrated.set(false);
            MiteConfig.BETTER_COMBAT_DAMAGE_MULTIPLIER.set(.8D);
            MiteConfig.SPEC.save();
            assertSavedBetterCombatDamage(helper, .75D);

            MiteConfig.BETTER_COMBAT_DAMAGE_MULTIPLIER.set(.8D);
            MiteConfig.SPEC.save();
            assertSavedBetterCombatDamage(helper, .8D);

            migrated.set(false);
            MiteConfig.BETTER_COMBAT_DAMAGE_MULTIPLIER.set(.65D);
            MiteConfig.SPEC.save();
            assertSavedBetterCombatDamage(helper, .65D);
        }
        helper.succeed();
    }

    private static void assertSavedBetterCombatDamage(GameTestHelper helper, double expected) throws Exception {
        var saved = new TomlParser().parse(Files.readString(configPath()));
        helper.assertValueEqual(saved.get("combat.better_combat_damage_migrated"), true,
                "The server records the completed Better Combat damage upgrade");
        helper.assertValueEqual(((Number)saved.get("combat.better_combat_damage_multiplier")).doubleValue(), expected,
                "Stored Better Combat damage multiplier");
        helper.assertValueEqual(MiteConfig.betterCombatDamageMultiplier(), expected, "Published Better Combat damage multiplier");
    }

    @GameTest(template = "empty")
    public static void oldDefaultAttackRateUpgradesOnceAndCustomRatesSurvive(GameTestHelper helper) throws Exception {
        try (var ignored = new Settings()) {
            ModConfigSpec.BooleanValue migrated = MiteConfig.SPEC.getValues().get("monsters.damage_rate_migrated");
            migrated.set(false);
            MiteConfig.MONSTER_DAMAGE_DAILY_PERCENT.set(.25D);
            MiteConfig.MONSTER_DAMAGE_BASE.set(8D);
            MiteConfig.MONSTER_DAMAGE_GROWTH.set(1D);
            MiteConfig.MONSTER_DAMAGE_FORMULA.set(MonsterGrowthRules.DEFAULT_FORMULA);
            MiteConfig.MONSTER_DAMAGE_BLOOD_MOON_FORMULA.set("");
            MiteConfig.SPEC.save();
            assertSavedAttackRate(helper, .5D);
            helper.assertValueEqual(MiteConfig.localSnapshot().monsterSettings().damageBase(), 8D,
                    "An explicitly configured innate attack base keeps its value and receives the new default growth rate");

            MiteConfig.MONSTER_DAMAGE_DAILY_PERCENT.set(.25D);
            MiteConfig.SPEC.save();
            assertSavedAttackRate(helper, .25D);

            migrated.set(false);
            MiteConfig.MONSTER_DAMAGE_FORMULA.set("base + reference * rate * days * 2");
            MiteConfig.SPEC.save();
            assertSavedAttackRate(helper, .25D);

            migrated.set(false);
            String health = "base + reference * rate * days * 4";
            String bloodMoonAttack = "base + reference * rate * days * 3";
            MiteConfig.MONSTER_GROWTH.set(1.5D);
            MiteConfig.MONSTER_HEALTH_GROWTH.set(2D);
            MiteConfig.MONSTER_DAMAGE_GROWTH.set(3D);
            MiteConfig.MONSTER_HEALTH_FORMULA.set(health);
            MiteConfig.MONSTER_DAMAGE_FORMULA.set("base / 0");
            MiteConfig.MONSTER_DAMAGE_BLOOD_MOON_FORMULA.set(bloodMoonAttack);
            MiteConfig.SPEC.save();
            var repaired = MiteConfig.localSnapshot();
            helper.assertValueEqual(repaired.monsterSettings().damageFormula(), MonsterGrowthRules.DEFAULT_FORMULA,
                    "The unusable legacy attack expression is repaired");
            helper.assertValueEqual(repaired.monsterSettings().damageBloodMoonFormula(), bloodMoonAttack,
                    "The valid attack settlement survives the repair");
            helper.assertValueEqual(repaired.monsterSettings().healthFormula(), health, "The newly loaded health formula survives the repair");
            helper.assertValueEqual(repaired.growth(), 1.5D, "The overall multiplier survives the repair");
            helper.assertValueEqual(repaired.healthGrowth(), 2D, "The health multiplier survives the repair");
            helper.assertValueEqual(repaired.damageGrowth(), 3D, "The attack multiplier survives the repair");
            assertSavedAttackRate(helper, .25D);
        }
        helper.succeed();
    }

    private static void assertSavedAttackRate(GameTestHelper helper, double expected) throws Exception {
        var saved = new TomlParser().parse(Files.readString(configPath()));
        helper.assertValueEqual(saved.get("monsters.damage_rate_migrated"), true, "The server records the completed upgrade");
        helper.assertValueEqual(((Number)saved.get("monsters.damage_daily_percent")).doubleValue(), expected, "Stored daily attack percent");
        helper.assertValueEqual(MiteConfig.localSnapshot().monsterSettings().damageRate(), expected / 100D, "Published attack growth rate");
    }

    @GameTest(template = "empty")
    public static void addonSpawningDraftsKeepPrimitiveTypesAndRejectIncompleteOrInvalidValues(GameTestHelper helper) throws Exception {
        try (var settings = new Settings(); var operator = new Operator(helper); var extension = new Extension()) {
            operator.permission(2);
            String valid = "{\"enabled\":false,\"day\":2,\"count\":7000000000,\"rate\":1.25}";
            helper.assertTrue(GameplayConfigEdits.apply(operator.player, "addon_spawning", valid) == null,
                    "The server invokes the installed addon's spawning save hook");
            helper.assertValueEqual(extension.saves, 1, "The addon's validated callback runs once");
            helper.assertValueEqual(extension.saved, Map.of("enabled", false, "day", 2, "count", 7000000000L, "rate", 1.25F),
                    "JSON numbers are restored to the addon's declared int, long and float types");
            for (String invalid : List.of(
                    "{\"enabled\":false,\"day\":2,\"rate\":1.25}",
                    "{\"enabled\":false,\"day\":2,\"count\":1,\"rate\":1.25,\"unknown\":0}",
                    "{\"enabled\":\"false\",\"day\":2,\"count\":1,\"rate\":1.25}",
                    "{\"enabled\":false,\"day\":2.5,\"count\":1,\"rate\":1.25}",
                    "{\"enabled\":false,\"day\":0,\"count\":1,\"rate\":1.25}",
                    "{\"enabled\":false,\"day\":2,\"count\":1,\"rate\":1e400}")) {
                helper.assertTrue(GameplayConfigEdits.apply(operator.player, "addon_spawning", invalid) != null,
                        "Malformed addon values are rejected before its persistence callback: " + invalid);
                helper.assertValueEqual(extension.saves, 1, "An invalid draft cannot reach the addon's save hook");
            }
            operator.permission(0);
            helper.assertTrue(GameplayConfigEdits.apply(operator.player, "addon_spawning", valid) != null,
                    "Addon saves use the same server OP permission check");
            helper.assertValueEqual(extension.saves, 1, "Unauthorized addon writes never invoke its callback");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void editRequestAndAcknowledgementCodecsPreserveIdsSectionsAndErrors(GameTestHelper helper) {
        var request = new GameplayConfigEditPayload(417, "addon_spawning", "{\"day\":2,\"enabled\":false}");
        var result = new GameplayConfigEditResultPayload(417, false, "需要 OP 权限");
        var authoritative = new GameplayConfigPayload(MiteConfig.localSnapshot(),
                Map.of("combat.weapon_reach_multiplier", 2.25D, "combat.height_reach_penalty", false),
                Map.of("enabled", true, "day", 2, "count", 7000000000L, "rate", 1.25F));
        var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            GameplayConfigEditPayload.CODEC.encode(bytes, request);
            GameplayConfigEditResultPayload.CODEC.encode(bytes, result);
            GameplayConfigPayload.CODEC.encode(bytes, authoritative);
            helper.assertValueEqual(GameplayConfigEditPayload.CODEC.decode(bytes), request, "The actual request codec preserves its correlation ID and edit body");
            helper.assertValueEqual(GameplayConfigEditResultPayload.CODEC.decode(bytes), result, "The actual acknowledgement codec preserves failure details and correlation ID");
            var decoded = GameplayConfigPayload.CODEC.decode(bytes);
            helper.assertValueEqual(decoded.snapshot(), authoritative.snapshot(), "The login synchronization includes the authoritative gameplay snapshot");
            helper.assertValueEqual(decoded.gameplayValues(), authoritative.gameplayValues(),
                    "The login synchronization preserves general gameplay booleans and decimal values");
            helper.assertValueEqual(decoded.addonSpawning().get("enabled"), true, "The addon spawning toggle arrives as a boolean");
            helper.assertValueEqual(((Number)decoded.addonSpawning().get("day")).intValue(), 2,
                    "The addon spawning editor receives the server's integer day instead of a local draft");
            helper.assertValueEqual(((Number)decoded.addonSpawning().get("count")).longValue(), 7000000000L,
                    "Large addon counts remain readable without narrowing to int");
            helper.assertValueEqual(((Number)decoded.addonSpawning().get("rate")).doubleValue(), 1.25D,
                    "The addon spawning editor receives the server's fractional rate");
            helper.assertValueEqual(bytes.readableBytes(), 0, "The codecs consume precisely their own packets");

            bytes.writeUtf(GSON.toJson(authoritative.snapshot()), 262144);
            var legacy = GameplayConfigPayload.CODEC.decode(bytes);
            helper.assertValueEqual(legacy.snapshot(), authoritative.snapshot(), "The new synchronization decoder accepts the older flattened snapshot JSON");
            helper.assertTrue(legacy.gameplayValues().isEmpty() && legacy.addonSpawning().isEmpty(),
                    "Missing optional editor maps in an older server packet do not create local write authority");
            GameplayConfigPayload.CODEC.encode(bytes, authoritative);
            helper.assertValueEqual(GSON.fromJson(bytes.readUtf(262144), MiteConfig.Snapshot.class), authoritative.snapshot(),
                    "An older read-only client can decode the new server packet through its original snapshot JSON parser");
            helper.assertValueEqual(bytes.readableBytes(), 0, "Both old/new synchronization compatibility paths consume exactly one packet");
        } finally { bytes.release(); }
        helper.succeed();
    }

    private static Path configPath() { return FMLPaths.CONFIGDIR.get().resolve("mite-common.toml"); }

    private static Map<ModConfigSpec.ConfigValue<?>, Object> currentValues() {
        var result = new LinkedHashMap<ModConfigSpec.ConfigValue<?>, Object>();
        collectValues(MiteConfig.SPEC.getValues(), result);
        return result;
    }

    private static void collectValues(UnmodifiableConfig config, Map<ModConfigSpec.ConfigValue<?>, Object> values) {
        for (var entry : config.entrySet()) {
            if (entry.getValue() instanceof UnmodifiableConfig child) collectValues(child, values);
            else if (entry.getValue() instanceof ModConfigSpec.ConfigValue<?> value)
                values.put(value, value.get() instanceof List<?> list ? List.copyOf(list) : value.get());
        }
    }

    @SuppressWarnings("unchecked")
    private static void set(ModConfigSpec.ConfigValue<?> value, Object contents) { ((ModConfigSpec.ConfigValue<Object>)value).set(contents); }

    /** Hold FML's reload lock so an earlier save's file-watcher callback cannot replace an in-progress test draft. */
    static Lock lockCommonConfig() throws ReflectiveOperationException {
        ModConfig config = ModConfigs.getModConfigs("mite").stream().filter(entry -> entry.getSpec() == MiteConfig.SPEC).findFirst().orElseThrow();
        var field = ModConfig.class.getDeclaredField("lock");
        field.setAccessible(true);
        Lock lock = (Lock)field.get(config);
        lock.lock();
        return lock;
    }

    private static final class Settings implements AutoCloseable {
        private final Lock configLock = lockCommonConfig();
        private final Map<ModConfigSpec.ConfigValue<?>, Object> original;
        private final MiteConfig.Snapshot remote;

        private Settings() throws ReflectiveOperationException {
            try {
                original = currentValues();
                remote = MiteConfig.effectiveSnapshot() == MiteConfig.localSnapshot() ? null : MiteConfig.effectiveSnapshot();
            } catch (RuntimeException | Error failure) {
                configLock.unlock();
                throw failure;
            }
        }

        @Override public void close() {
            try {
                original.forEach(GameplayConfigEditGameTests::set);
                MiteConfig.clearServerSnapshot();
                MiteConfig.refreshEnchantmentRules();
                MiteConfig.SPEC.save();
                if (remote != null) MiteConfig.receiveServerSnapshot(remote);
            } finally {
                configLock.unlock();
            }
        }
    }

    private static final class Operator implements AutoCloseable {
        private final ServerPlayer player;
        private Operator(GameTestHelper helper) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-config-edit"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        }
        private void permission(int level) {
            var operators = player.server.getPlayerList().getOps();
            if (level == 0) operators.remove(player.getGameProfile());
            else operators.add(new ServerOpListEntry(player.getGameProfile(), level, false));
        }
        @Override public void close() { player.server.getPlayerList().getOps().remove(player.getGameProfile()); player.discard(); }
    }

    private static final class Extension implements AutoCloseable {
        private final java.lang.reflect.Field field;
        private final Object original;
        private int saves;
        private Map<String, Object> saved = Map.of();
        private Extension() throws Exception {
            field = MiteExtensions.class.getDeclaredField("policy");
            field.setAccessible(true);
            original = field.get(null);
            field.set(null, new MiteExtensions.Policy() {
                @Override public List<MiteExtensions.SpawningControl> spawningControls() {
                    return List.of(new MiteExtensions.SpawningControl("enabled", "test.enabled", true, 0, 1),
                            new MiteExtensions.SpawningControl("day", "test.day", 3, 1, 1000000),
                            new MiteExtensions.SpawningControl("count", "test.count", 1L, 0, 10000000000D),
                            new MiteExtensions.SpawningControl("rate", "test.rate", 1F, 0, 100));
                }
                @Override public boolean saveSpawningControls(Map<String, Object> values) { saves++; saved = Map.copyOf(values); return true; }
            });
        }
        @Override public void close() throws Exception { field.set(null, original); }
    }
}

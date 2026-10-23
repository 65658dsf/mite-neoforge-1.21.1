package io.github.ningmeng123.mite.gametest;

import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.Gson;
import io.github.ningmeng123.mite.config.GameplayConfigExport;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.network.GameplayConfigPayload;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Defaults, authoritative network settings and dedicated-server export for mob combat. @author NingMeng123 */
@GameTestHolder("mite_bettercombat")
@PrefixGameTestTemplate(false)
public final class BetterCombatMobConfigGameTests {
    private BetterCombatMobConfigGameTests() {}

    @GameTest(template = "empty")
    public static void defaultAndLegacySnapshotsKeepMobCompatibilityDisabled(GameTestHelper helper) {
        helper.assertFalse(MiteConfig.BETTER_COMBAT_MOBS.getDefault(), "New installations default to original monster attacks");
        var legacy = new MiteConfig.Snapshot(Map.of(), 1D, 1D, 1D, 1D, 0D, true, true, true, Map.of(), Set.of());
        helper.assertFalse(legacy.betterCombatMobs(), "Existing Snapshot constructors retain the disabled default");
        try (var compatibility = new BetterCombatMobSettings(false)) {
            helper.assertFalse(MiteConfig.localSnapshot().betterCombatMobs(), "The local immutable publication includes the disabled flag");
            helper.assertFalse(MiteConfig.betterCombatMobsEnabled(), "Runtime consumers read the disabled local publication");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realNetworkCodecUsesServerFlagAndMissingLegacyFieldDefaultsToFalse(GameTestHelper helper) {
        try (var compatibility = new BetterCombatMobSettings(false)) {
            for (boolean enabled : new boolean[]{true, false}) {
                MiteConfig.BETTER_COMBAT_MOBS.set(enabled);
                MiteConfig.refreshEnchantmentRules();
                var server = MiteConfig.localSnapshot();
                var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
                try {
                    GameplayConfigPayload.CODEC.encode(bytes, new GameplayConfigPayload(server));
                    var decoded = GameplayConfigPayload.CODEC.decode(bytes).snapshot();
                    helper.assertValueEqual(decoded, server, "The real codec round trips the full server publication including the mob flag");
                    MiteConfig.BETTER_COMBAT_MOBS.set(!enabled);
                    MiteConfig.refreshEnchantmentRules();
                    MiteConfig.receiveServerSnapshot(decoded);
                    helper.assertTrue(MiteConfig.betterCombatMobsEnabled() == enabled,
                            "The received server flag overrides the client's opposite local setting");
                    helper.assertTrue(MiteConfig.BETTER_COMBAT_MOBS.get() == !enabled,
                            "Receiving the authoritative setting preserves the local editor preference");
                    MiteConfig.clearServerSnapshot();
                    helper.assertTrue(MiteConfig.betterCombatMobsEnabled() == !enabled,
                            "Disconnect restores the client's own local flag");
                } finally { bytes.release(); MiteConfig.clearServerSnapshot(); }
            }
            MiteConfig.BETTER_COMBAT_MOBS.set(true);
            MiteConfig.refreshEnchantmentRules();
            var legacyJson = new Gson().toJsonTree(MiteConfig.localSnapshot()).getAsJsonObject();
            legacyJson.remove("betterCombatMobs");
            var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                bytes.writeUtf(legacyJson.toString(), 262144);
                var decoded = GameplayConfigPayload.CODEC.decode(bytes).snapshot();
                helper.assertFalse(decoded.betterCombatMobs(), "An older payload without the flag decodes as disabled");
                MiteConfig.receiveServerSnapshot(decoded);
                helper.assertFalse(MiteConfig.betterCombatMobsEnabled(), "An older server does not inherit the client's enabled setting");
            } finally { bytes.release(); MiteConfig.clearServerSnapshot(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dedicatedServerExportRetainsTheLocalMobCompatibilityChoice(GameTestHelper helper) throws Exception {
        try (var compatibility = new BetterCombatMobSettings(false)) {
            for (boolean enabled : new boolean[]{false, true}) {
                MiteConfig.BETTER_COMBAT_MOBS.set(enabled);
                MiteConfig.refreshEnchantmentRules();
                var local = MiteConfig.localSnapshot();
                try (var opposite = new BetterCombatMobSettings(!enabled)) {
                    var remote = MiteConfig.localSnapshot();
                    MiteConfig.BETTER_COMBAT_MOBS.set(enabled);
                    MiteConfig.refreshEnchantmentRules();
                    MiteConfig.receiveServerSnapshot(remote);
                    var exported = GameplayConfigExport.export(Files.createTempDirectory("mite-mob-combat-export-"));
                    var parsed = new TomlParser().parse(Files.readString(exported, StandardCharsets.UTF_8));
                    helper.assertTrue(parsed.get("combat.better_combat_mobs").equals(enabled),
                            "The exported dedicated-server config keeps the local flag instead of a connected server's opposite flag");
                    helper.assertValueEqual(MiteConfig.localSnapshot(), local, "Export preserves the local immutable publication");
                    helper.assertTrue(MiteConfig.effectiveSnapshot() == remote, "Export preserves the connected server view");
                }
            }
        }
        helper.succeed();
    }
}

package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.UnderworldVaultLoot;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.trialspawner.PlayerDetector;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real vault configuration, save migration and vanilla key/display entry points. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class UnderworldVaultRoutingGameTests {
    private static final String VANILLA = "minecraft:chests/trial_chambers/";
    private static final String UNDERWORLD = "mite:chests/underworld_trial_chambers/";

    @GameTest(template = "empty")
    public static void ordinaryAndOminousVaultsRouteActualRewardWithoutMutatingVanillaDefault(GameTestHelper helper) {
        ServerLevel level = underworld(helper);
        BlockPos pos = position(helper);
        VaultBlockEntity untouched = vault(null, pos, false, VaultState.INACTIVE);
        VaultConfig originalDefault = untouched.getConfig();
        helper.assertValueEqual(originalDefault.lootTable(), table(VANILLA + "reward"), "Detached default is the vanilla table");
        VaultBlockEntity ordinary = vault(level, pos, false, VaultState.INACTIVE);
        VaultConfig normal = ordinary.getConfig();
        helper.assertValueEqual(normal.lootTable(), UnderworldVaultLoot.REWARD, "New ordinary vault routes immediately");
        helper.assertTrue(normal.overrideLootTableToDisplay().isEmpty(), "No override means preview uses the new actual table");
        for (boolean ominous : new boolean[] { false, true }) {
            VaultBlockEntity be = vault(level, pos, ominous, VaultState.ACTIVE);
            VaultConfig source = config(ominous, Optional.empty());
            be.setConfig(source);
            VaultConfig migrated = be.getConfig();
            helper.assertValueEqual(migrated.lootTable(), ominous ? UnderworldVaultLoot.REWARD_OMINOUS : UnderworldVaultLoot.REWARD,
                    "Actual entry point matches its configured mode");
            helper.assertTrue(be.getConfig() == migrated, "Repeated reads do not recreate the migrated config");
            helper.assertValueEqual(source.lootTable(), table(VANILLA + (ominous ? "reward_ominous" : "reward")),
                    "Source config was not mutated");
            helper.assertTrue(be.getBlockState().getValue(VaultBlock.OMINOUS) == ominous, "Routing does not change ominous block state");
        }
        helper.assertTrue(untouched.getConfig() == originalDefault, "Shared vanilla default remains unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allEightVanillaDisplayOverridesMapExactlyAndCustomNamesRemainUntouched(GameTestHelper helper) {
        ServerLevel level = underworld(helper);
        for (String suffix : List.of("reward", "reward_common", "reward_rare", "reward_unique",
                "reward_ominous", "reward_ominous_common", "reward_ominous_rare", "reward_ominous_unique")) {
            VaultBlockEntity be = vault(level, position(helper), false, VaultState.ACTIVE);
            be.setConfig(config(false, Optional.of(table(VANILLA + suffix))));
            helper.assertValueEqual(be.getConfig().overrideLootTableToDisplay().orElseThrow(), table(UNDERWORLD + suffix),
                    "Display override maps exact vanilla sub-table " + suffix);
            helper.assertValueEqual(be.getConfig().lootTable(), UnderworldVaultLoot.REWARD, "Display choice never changes actual reward mode");
        }
        for (String id : List.of("minecraft:chests/trial_chambers/reward_custom", "other:chests/trial_chambers/reward_unique",
                "minecraft:chests/simple_dungeon", "mite:chests/underworld_trial_chambers/reward_unique")) {
            VaultBlockEntity be = vault(level, position(helper), false, VaultState.ACTIVE);
            ResourceKey<LootTable> custom = table(id);
            be.setConfig(config(false, Optional.of(custom)));
            helper.assertTrue(be.getConfig().overrideLootTableToDisplay().orElseThrow() == custom, "Unrecognized override keeps its identity: " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void detachedOverworldNetherAndEndVaultsKeepVanillaConfigurations(GameTestHelper helper) {
        VaultConfig source = config(true, Optional.of(table(VANILLA + "reward_ominous_unique")));
        VaultBlockEntity detached = vault(null, position(helper), true, VaultState.ACTIVE);
        detached.setConfig(source);
        helper.assertTrue(detached.getConfig() == source, "A block entity without its level does not guess a dimension");
        for (ResourceKey<Level> dimension : List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
            ServerLevel level = helper.getLevel().getServer().getLevel(dimension);
            helper.assertTrue(level != null, "Vanilla dimension is loaded: " + dimension.location());
            VaultBlockEntity be = vault(level, position(helper), true, VaultState.ACTIVE);
            be.setConfig(source);
            helper.assertTrue(be.getConfig() == source, "Other server dimensions keep the original config object");
            helper.assertValueEqual(be.saveWithoutMetadata(level.registryAccess()).getCompound("config").getString("loot_table"),
                    VANILLA + "reward_ominous", "Save does not migrate another dimension");
        }
        helper.assertTrue(!UnderworldVaultLoot.applies(null), "Unknown level does not enable the server migration");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void authoredKeysRangesDetectorsSelectorsAndCustomDataPackTablesSurvive(GameTestHelper helper) {
        ServerLevel level = underworld(helper);
        ItemStack key = new ItemStack(Items.BREEZE_ROD, 3);
        key.set(DataComponents.CUSTOM_NAME, Component.literal("自定义宝库钥匙"));
        PlayerDetector detector = PlayerDetector.SHEEP;
        PlayerDetector.EntitySelector selector = PlayerDetector.EntitySelector.onlySelectPlayers(List.of());
        ResourceKey<LootTable> customDisplay = table("example:vault_preview");
        VaultConfig authored = new VaultConfig(table(VANILLA + "reward"), 7.25, 11.5, key,
                Optional.of(customDisplay), detector, selector);
        VaultBlockEntity be = vault(level, position(helper), false, VaultState.ACTIVE);
        be.setConfig(authored);
        VaultConfig actual = be.getConfig();
        helper.assertTrue(actual != authored, "Vanilla actual table requires a replacement config");
        helper.assertTrue(actual.keyItem() == key, "Custom key object, quantity and components are retained");
        helper.assertTrue(actual.activationRange() == 7.25 && actual.deactivationRange() == 11.5, "Authored ranges are unchanged");
        helper.assertTrue(actual.playerDetector() == detector && actual.entitySelector() == selector, "Detector and selector identities are retained");
        helper.assertTrue(actual.overrideLootTableToDisplay().orElseThrow() == customDisplay, "Custom preview table is not rewritten");
        for (String id : List.of("example:vault_rewards", "minecraft:chests/trial_chambers/reward_common",
                "minecraft:chests/trial_chambers/reward_custom", "other:chests/trial_chambers/reward")) {
            VaultConfig custom = new VaultConfig(table(id), 7.25, 11.5, key, Optional.of(customDisplay), detector, selector);
            be.setConfig(custom);
            helper.assertTrue(be.getConfig() == custom, "Custom actual table is left intact: " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacySaveMigratesBeforeAnyConfigReadAndRetainsPendingRewardsAndSharedData(GameTestHelper helper) throws Exception {
        ServerLevel level = underworld(helper);
        BlockPos pos = position(helper);
        Player player = player(level, pos);
        for (boolean ominous : new boolean[] { false, true }) {
            CompoundTag legacy = legacySave(helper.getLevel(), pos, ominous, player);
            // Compare against vanilla's own canonical reload, including its existing codec/set defaults.
            VaultBlockEntity reference = vault(helper.getLevel(), pos, ominous, VaultState.UNLOCKING);
            reference.loadWithComponents(legacy, helper.getLevel().registryAccess());
            CompoundTag expected = reference.saveWithoutMetadata(helper.getLevel().registryAccess());
            VaultBlockEntity be = vault(level, pos, ominous, VaultState.UNLOCKING);
            be.loadWithComponents(legacy, level.registryAccess());
            Object serverData = be.getServerData();
            Object sharedData = be.getSharedData();
            // Saving is deliberately the first operation capable of migrating this old config.
            CompoundTag migrated = be.saveWithoutMetadata(level.registryAccess());
            helper.assertValueEqual(migrated.getCompound("config").getString("loot_table"),
                    UNDERWORLD + (ominous ? "reward_ominous" : "reward"), "Save normalizes a pre-existing legacy vault before its first tick");
            helper.assertTrue(migrated.getCompound("server_data").equals(expected.getCompound("server_data")),
                    "Rewarded UUIDs, pending item components/counts, ejection state and delay survive migration");
            helper.assertTrue(migrated.getCompound("shared_data").equals(expected.getCompound("shared_data")),
                    "Connected UUIDs, particles range and already selected display survive migration");
            helper.assertTrue(be.getServerData() == serverData && be.getSharedData() == sharedData, "Existing data objects are not replaced");
            helper.assertValueEqual(be.getBlockState().getValue(VaultBlock.STATE), VaultState.UNLOCKING, "Current unlock state remains untouched");
            VaultBlockEntity restored = vault(level, pos, ominous, VaultState.UNLOCKING);
            restored.loadWithComponents(migrated, level.registryAccess());
            helper.assertTrue(migrated.equals(restored.saveWithoutMetadata(level.registryAccess())), "Migrated save is stable over another real BE reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void alreadyRewardedPlayerRemainsRejectedAfterLegacyMigrationAndAnotherReload(GameTestHelper helper) throws Exception {
        ServerLevel level = underworld(helper);
        BlockPos pos = position(helper);
        Player player = player(level, pos);
        CompoundTag saved = legacySave(helper.getLevel(), pos, false, player);
        for (int reload = 0; reload < 2; reload++) {
            VaultBlockEntity be = vault(level, pos, false, VaultState.ACTIVE);
            be.loadWithComponents(saved, level.registryAccess());
            VaultConfig actual = be.getConfig();
            CompoundTag before = be.saveWithoutMetadata(level.registryAccess());
            ItemStack key = new ItemStack(Items.TRIAL_KEY, 2);
            VaultBlockEntity.Server.tryInsertKey(level, pos, be.getBlockState(), actual, be.getServerData(), be.getSharedData(), player, key);
            CompoundTag after = be.saveWithoutMetadata(level.registryAccess());
            helper.assertValueEqual(key.getCount(), 2, "Already rewarded player does not spend another key after migration");
            helper.assertTrue(before.getCompound("server_data").equals(after.getCompound("server_data")), "Rejection does not replace pending rewards or the claimed-player ledger");
            helper.assertTrue(before.getCompound("shared_data").equals(after.getCompound("shared_data")), "Rejection does not overwrite current preview or connected players");
            helper.assertTrue(rewarded(be, player), "Original UUID remains in the real vanilla ledger");
            saved = after;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void actualVanillaDisplayBlockInteractionAndServerTickUseRoutedConfiguration(GameTestHelper helper) throws Exception {
        ServerLevel level = underworld(helper);
        BlockPos pos = position(helper);
        BlockState previous = level.getBlockState(pos);
        CompoundTag previousData = level.getBlockEntity(pos) == null ? null : level.getBlockEntity(pos).saveWithFullMetadata(level.registryAccess());
        try {
            for (boolean ominous : new boolean[] { false, true }) {
                level.setBlock(pos, state(ominous, VaultState.ACTIVE), 2);
                VaultBlockEntity be = (VaultBlockEntity)level.getBlockEntity(pos);
                helper.assertTrue(be != null, "Actual vault block has a real installed block entity");
                Player player = player(level, pos);
                VaultConfig source = config(ominous, Optional.empty());
                be.setConfig(new VaultConfig(source.lootTable(), 4, 4.5, source.keyItem(), Optional.empty(),
                        PlayerDetector.INCLUDING_CREATIVE_PLAYERS, PlayerDetector.EntitySelector.onlySelectPlayer(player)));
                VaultConfig actual = be.getConfig();
                helper.assertValueEqual(actual.lootTable(), table(UNDERWORLD + (ominous ? "reward_ominous" : "reward")),
                        "The same getConfig call used by the vanilla ticker and block interaction returns the underworld entry point");
                invoke(VaultBlockEntity.Server.class, "cycleDisplayItemFromLootTable",
                        new Class<?>[] { ServerLevel.class, VaultState.class, VaultConfig.class,
                                net.minecraft.world.level.block.entity.vault.VaultSharedData.class, BlockPos.class },
                        null, level, VaultState.ACTIVE, actual, be.getSharedData(), pos);
                helper.assertTrue(!be.getSharedData().getDisplayItem().isEmpty(), "Real vanilla preview resolves the routed table with no display override");
                ItemStack key = new ItemStack(ominous ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY, 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, key);
                ItemInteractionResult result = be.getBlockState().useItemOn(key, level, player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
                helper.assertValueEqual(result, ItemInteractionResult.SUCCESS, "Native VaultBlock interaction accepts the key");
                helper.assertValueEqual(key.getCount(), 1, "Survival player spends exactly one ordinary or ominous key");
                helper.assertValueEqual(level.getBlockState(pos).getValue(VaultBlock.STATE), VaultState.UNLOCKING, "Native unlock transition runs");
                helper.assertTrue(rewarded(be, player), "Native unlock adds the player to the real claimed-player ledger");
                CompoundTag unlocked = be.saveWithoutMetadata(level.registryAccess());
                helper.assertTrue(!unlocked.getCompound("server_data").getList("items_to_eject", 10).isEmpty(), "Native reward resolver creates actual queued item stacks");
                helper.assertTrue(unlocked.getCompound("server_data").getLong("state_updating_resumes_at") == level.getGameTime() + 14L,
                        "Native 14-tick opening delay is retained");
                VaultBlockEntity.Server.tick(level, pos, level.getBlockState(pos), be.getConfig(), be.getServerData(), be.getSharedData());
                helper.assertTrue(unlocked.getCompound("server_data").equals(be.saveWithoutMetadata(level.registryAccess()).getCompound("server_data")),
                        "Real server tick keeps queued rewards and opening delay before its deadline");
            }
        } finally {
            level.setBlock(pos, previous, 2);
            if (previousData != null && level.getBlockEntity(pos) != null) level.getBlockEntity(pos).loadWithComponents(previousData, level.registryAccess());
        }
        helper.succeed();
    }

    private static CompoundTag legacySave(ServerLevel sourceLevel, BlockPos pos, boolean ominous, Player rewardedPlayer) throws Exception {
        VaultBlockEntity source = vault(sourceLevel, pos, ominous, VaultState.UNLOCKING);
        source.setConfig(config(ominous, Optional.of(table(VANILLA + (ominous ? "reward_ominous_unique" : "reward_unique")))));
        source.getServerData().addToRewardedPlayers(rewardedPlayer);
        ItemStack pending = new ItemStack(Items.DIAMOND, 3);
        pending.set(DataComponents.CUSTOM_NAME, Component.literal("已开出的奖励，迁移不能重新抽取"));
        invoke(source.getServerData().getClass(), "setItemsToEject", new Class<?>[] { List.class }, source.getServerData(), List.of(pending, new ItemStack(Items.EMERALD, 5)));
        invoke(source.getServerData().getClass(), "pauseStateUpdatingUntil", new Class<?>[] { long.class }, source.getServerData(), 987654321L);
        source.getSharedData().setDisplayItem(new ItemStack(Items.APPLE));
        field(source.getSharedData(), "connectedPlayers").set(source.getSharedData(), Set.of(rewardedPlayer.getUUID()));
        field(source.getSharedData(), "connectedParticlesRange").setDouble(source.getSharedData(), 6.75);
        return source.saveWithoutMetadata(sourceLevel.registryAccess());
    }

    private static boolean rewarded(VaultBlockEntity be, Player player) throws Exception {
        return (Boolean)invoke(be.getServerData().getClass(), "hasRewardedPlayer", new Class<?>[] { Player.class }, be.getServerData(), player);
    }

    private static Field field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object invoke(Class<?> owner, String name, Class<?>[] parameterTypes, Object receiver, Object... arguments) throws Exception {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(receiver, arguments);
    }

    private static Player player(ServerLevel level, BlockPos pos) {
        Player player = new Player(level, pos, 0, new GameProfile(UUID.randomUUID(), "vault-routing-test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        player.setPos(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    private static VaultConfig config(boolean ominous, Optional<ResourceKey<LootTable>> display) {
        return new VaultConfig(table(VANILLA + (ominous ? "reward_ominous" : "reward")), 4, 4.5,
                new ItemStack(ominous ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY), display);
    }

    private static ResourceKey<LootTable> table(String id) {
        return ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(id));
    }

    private static VaultBlockEntity vault(ServerLevel level, BlockPos pos, boolean ominous, VaultState state) {
        VaultBlockEntity be = new VaultBlockEntity(pos, state(ominous, state));
        if (level != null) be.setLevel(level);
        return be;
    }

    private static BlockState state(boolean ominous, VaultState state) {
        return Blocks.VAULT.defaultBlockState().setValue(VaultBlock.OMINOUS, ominous).setValue(VaultBlock.STATE, state);
    }

    private static BlockPos position(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(new BlockPos(1, 1, 1));
        return new BlockPos(absolute.getX(), 200, absolute.getZ());
    }

    private static ServerLevel underworld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(level != null, "Actual underworld level is loaded");
        return level;
    }
}

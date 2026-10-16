package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.loot.ReducedTrialLoot;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.trialspawner.PlayerDetector;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native vault transactions must settle intentionally removed rewards without creating a free reroll. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ReducedTrialVaultRewardGameTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void removedNormalAndOminousRewardsSpendOneKeyAndFinishEmptyWithoutReroll(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        BlockPos pos = position(helper);
        BlockState previous = level.getBlockState(pos);
        CompoundTag previousData = previousData(level, pos);
        try {
            for (boolean ominous : new boolean[]{false, true}) {
                var tableKey = table(ominous ? "reward_ominous_rare" : "reward_common");
                VaultBlockEntity vault = install(level, pos, ominous);
                Player player = player(level, pos);
                VaultConfig config = config(tableKey, ominous, player);
                vault.setConfig(config);
                long rejectedSeed = rejectedSeed(helper, level, tableKey, pos, player);
                level.getRandomSequence(tableKey.location()).setSeed(rejectedSeed);
                ItemStack keys = new ItemStack(ominous ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY, 2);

                VaultBlockEntity.Server.tryInsertKey(level, pos, vault.getBlockState(), vault.getConfig(),
                        vault.getServerData(), vault.getSharedData(), player, keys);
                helper.assertValueEqual(keys.getCount(), 1, "A deliberately empty reward consumes exactly one key");
                helper.assertTrue(rewarded(vault, player), "The actual vanilla ledger records a removed reward");
                helper.assertValueEqual(level.getBlockState(pos).getValue(VaultBlock.STATE), VaultState.UNLOCKING,
                        "A deliberately empty reward starts the normal unlock animation");
                var saved = vault.saveWithoutMetadata(level.registryAccess());
                helper.assertTrue(saved.getCompound("server_data").getList("items_to_eject", 10).isEmpty(),
                        "The queue contains no placeholder items or replacement rewards");
                helper.assertTrue(!vault.getSharedData().hasDisplayItem(), "No empty item is shown as an ejectable reward");
                helper.assertTrue(saved.getCompound("server_data").getLong("state_updating_resumes_at") == level.getGameTime() + 14,
                        "The original 14 tick unlock delay remains in effect");

                expireAndTick(level, pos, vault);
                helper.assertValueEqual(level.getBlockState(pos).getValue(VaultBlock.STATE), VaultState.EJECTING,
                        "The empty reward safely enters the native ejection state");
                expireAndTick(level, pos, vault);
                VaultState finished = level.getBlockState(pos).getValue(VaultBlock.STATE);
                helper.assertTrue(finished == VaultState.ACTIVE || finished == VaultState.INACTIVE,
                        "The native empty ejection branch finishes normally");
                helper.assertTrue(vault.saveWithoutMetadata(level.registryAccess()).getCompound("server_data")
                        .getInt("total_ejections_needed") == 0, "Empty ejection bookkeeping is cleared");

                level.setBlock(pos, level.getBlockState(pos).setValue(VaultBlock.STATE, VaultState.ACTIVE), 2);
                VaultBlockEntity.Server.tryInsertKey(level, pos, vault.getBlockState(), vault.getConfig(),
                        vault.getServerData(), vault.getSharedData(), player, keys);
                helper.assertValueEqual(keys.getCount(), 1, "A rewarded player cannot spend another key or reroll the removed reward");
                helper.assertValueEqual(level.getBlockState(pos).getValue(VaultBlock.STATE), VaultState.ACTIVE,
                        "Rejected retry does not start another unlock");
                VaultBlockEntity restored = new VaultBlockEntity(pos, vault.getBlockState());
                restored.setLevel(level);
                restored.loadWithComponents(vault.saveWithoutMetadata(level.registryAccess()), level.registryAccess());
                helper.assertTrue(rewarded(restored, player), "The completed empty roll remains claimed after save and reload");
            }
        } finally {
            restore(level, pos, previous, previousData);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void genuinelyEmptyTablesStayFreeAndUnderworldRewardsRemainIntact(GameTestHelper helper) throws Exception {
        ServerLevel overworld = helper.getLevel();
        ServerLevel underworld = overworld.getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(underworld != null, "The real underworld dimension is loaded");
        BlockPos pos = position(helper);
        ResourceKey<LootTable> emptyTable = table("missing_empty_reward_fixture");
        helper.assertTrue(overworld.getServer().reloadableRegistries().getLootTable(emptyTable) == LootTable.EMPTY,
                "The genuine empty-table control has no original rewards to suppress");
        for (ServerLevel level : List.of(overworld, underworld)) {
            BlockState previous = level.getBlockState(pos);
            CompoundTag previousData = previousData(level, pos);
            try {
                VaultBlockEntity vault = install(level, pos, false);
                Player player = player(level, pos);
                vault.setConfig(config(emptyTable, false, player));
                ItemStack keys = new ItemStack(Items.TRIAL_KEY, 2);
                VaultBlockEntity.Server.tryInsertKey(level, pos, vault.getBlockState(), vault.getConfig(),
                        vault.getServerData(), vault.getSharedData(), player, keys);
                helper.assertValueEqual(keys.getCount(), 2, "An originally empty table never consumes a key");
                helper.assertTrue(!rewarded(vault, player), "An originally empty table never marks the player rewarded");
                helper.assertValueEqual(level.getBlockState(pos).getValue(VaultBlock.STATE), VaultState.ACTIVE,
                        "An originally empty table never starts an unlock");

                if (level == underworld) {
                    var common = table("reward_common");
                    long rejectedSeed = rejectedSeed(helper, overworld, common, pos, player(overworld, pos));
                    underworld.getRandomSequence(common.location()).setSeed(rejectedSeed);
                    vault.setConfig(config(common, false, player));
                    VaultBlockEntity.Server.tryInsertKey(level, pos, vault.getBlockState(), vault.getConfig(),
                            vault.getServerData(), vault.getSharedData(), player, keys);
                    helper.assertValueEqual(keys.getCount(), 1, "The underworld still completes its original reward transaction");
                    helper.assertTrue(rewarded(vault, player), "The ordinary underworld ledger remains intact");
                    helper.assertTrue(!vault.saveWithoutMetadata(level.registryAccess()).getCompound("server_data")
                            .getList("items_to_eject", 10).isEmpty(),
                            "A resource roll removed in the overworld still queues its actual reward underground");
                }
            } finally {
                restore(level, pos, previous, previousData);
            }
        }
        helper.succeed();
    }

    private static long rejectedSeed(GameTestHelper helper, ServerLevel level, ResourceKey<LootTable> key, BlockPos pos, Player player) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        RandomSource sequence = level.getRandomSequence(key.location());
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.THIS_ENTITY, player).withLuck(player.getLuck()).create(LootContextParamSets.VAULT);
        for (long seed = 1; seed <= 512; seed++) {
            sequence.setSeed(seed);
            var result = table.getRandomItems(params, sequence);
            if (result instanceof ReducedTrialLoot && result.isEmpty()) return seed;
        }
        helper.assertTrue(false, "A loaded native reward sub-table exercises a deliberately removed empty reward");
        throw new AssertionError("No rejected resource seed found");
    }

    private static void expireAndTick(ServerLevel level, BlockPos pos, VaultBlockEntity vault) throws Exception {
        invoke(vault.getServerData(), "pauseStateUpdatingUntil", new Class<?>[]{long.class}, level.getGameTime());
        VaultBlockEntity.Server.tick(level, pos, level.getBlockState(pos), vault.getConfig(), vault.getServerData(), vault.getSharedData());
    }

    private static boolean rewarded(VaultBlockEntity vault, Player player) throws Exception {
        return (Boolean)invoke(vault.getServerData(), "hasRewardedPlayer", new Class<?>[]{Player.class}, player);
    }

    private static Object invoke(Object receiver, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = receiver.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(receiver, arguments);
    }

    private static VaultBlockEntity install(ServerLevel level, BlockPos pos, boolean ominous) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(pos, Blocks.VAULT.defaultBlockState().setValue(VaultBlock.OMINOUS, ominous)
                .setValue(VaultBlock.STATE, VaultState.ACTIVE), 2);
        return (VaultBlockEntity)level.getBlockEntity(pos);
    }

    private static VaultConfig config(ResourceKey<LootTable> table, boolean ominous, Player player) {
        return new VaultConfig(table, 4, 4.5, new ItemStack(ominous ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY),
                Optional.empty(), PlayerDetector.INCLUDING_CREATIVE_PLAYERS, PlayerDetector.EntitySelector.onlySelectPlayer(player));
    }

    private static ResourceKey<LootTable> table(String name) {
        return ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("chests/trial_chambers/" + name));
    }

    private static Player player(ServerLevel level, BlockPos pos) {
        Player player = new Player(level, pos, 0, new GameProfile(UUID.randomUUID(), "trial-reward-test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        player.setPos(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    private static BlockPos position(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(new BlockPos(1, 1, 1));
        return new BlockPos(absolute.getX(), 210, absolute.getZ());
    }

    private static CompoundTag previousData(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) == null ? null : level.getBlockEntity(pos).saveWithFullMetadata(level.registryAccess());
    }

    private static void restore(ServerLevel level, BlockPos pos, BlockState previous, CompoundTag previousData) {
        level.setBlock(pos, previous, 2);
        if (previousData != null && level.getBlockEntity(pos) != null)
            level.getBlockEntity(pos).loadWithComponents(previousData, level.registryAccess());
    }
}

package io.github.ningmeng123.mite.worldgen;

import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.storage.loot.LootTable;

/** Routes only vanilla trial-vault tables in the underworld, preserving authored vault settings. @author NingMeng123 */
public final class UnderworldVaultLoot {
    public static final String VANILLA_PREFIX = "chests/trial_chambers/";
    public static final String UNDERWORLD_PREFIX = "chests/underworld_trial_chambers/";
    public static final ResourceKey<LootTable> REWARD = underworldTable("reward");
    public static final ResourceKey<LootTable> REWARD_OMINOUS = underworldTable("reward_ominous");
    private static final Set<String> DISPLAY_TABLES = Set.of(
            "reward", "reward_common", "reward_rare", "reward_unique",
            "reward_ominous", "reward_ominous_common", "reward_ominous_rare", "reward_ominous_unique");

    private UnderworldVaultLoot() {}

    public static boolean applies(@Nullable Level level) {
        return level != null && !level.isClientSide && level.dimension().equals(MiteWorldDimensions.UNDERWORLD);
    }

    /** Reuses the original immutable configuration when no server-side migration is needed. */
    public static VaultConfig remapConfig(@Nullable Level level, VaultConfig source) {
        return applies(level) ? remapConfig(source) : source;
    }

    /** This overload is useful for validation; callers must already have checked the dimension. */
    public static VaultConfig remapConfig(VaultConfig source) {
        ResourceKey<LootTable> lootTable = remapRewardTable(source.lootTable());
        var display = source.overrideLootTableToDisplay().map(UnderworldVaultLoot::remapDisplayTable);
        if (lootTable.equals(source.lootTable()) && display.equals(source.overrideLootTableToDisplay())) return source;
        // Keep custom keys, ranges, player selection and detector identity. Never mutate VaultConfig.DEFAULT.
        return new VaultConfig(lootTable, source.activationRange(), source.deactivationRange(), source.keyItem(),
                display, source.playerDetector(), source.entitySelector());
    }

    /** The actual reward is migrated only for the two vanilla vault entry points. */
    public static ResourceKey<LootTable> remapRewardTable(ResourceKey<LootTable> source) {
        ResourceLocation id = source.location();
        if (!id.getNamespace().equals("minecraft")) return source;
        return switch (id.getPath()) {
            case VANILLA_PREFIX + "reward" -> REWARD;
            case VANILLA_PREFIX + "reward_ominous" -> REWARD_OMINOUS;
            default -> source;
        };
    }

    /** Template display overrides may select common, rare or unique sub-tables. Custom overrides survive. */
    public static ResourceKey<LootTable> remapDisplayTable(ResourceKey<LootTable> source) {
        ResourceLocation id = source.location();
        if (!id.getNamespace().equals("minecraft") || !id.getPath().startsWith(VANILLA_PREFIX)) return source;
        String name = id.getPath().substring(VANILLA_PREFIX.length());
        return DISPLAY_TABLES.contains(name) ? underworldTable(name) : source;
    }

    private static ResourceKey<LootTable> underworldTable(String name) {
        return ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("mite", UNDERWORLD_PREFIX + name));
    }
}

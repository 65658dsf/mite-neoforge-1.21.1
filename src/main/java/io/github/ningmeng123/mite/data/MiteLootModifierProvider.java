package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.loot.RemoveWoodenToolsLootModifier;
import io.github.ningmeng123.mite.loot.NaturalFoodLootModifier;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;

/** Reproducible additive global-loot-modifier registration. @author NingMeng123 */
public final class MiteLootModifierProvider extends GlobalLootModifierProvider {
    public MiteLootModifierProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, Mite.MOD_ID);
    }

    @Override
    protected void start() {
        add("remove_wooden_tools", new RemoveWoodenToolsLootModifier(new LootItemCondition[0]));
        add("natural_food_sources", new NaturalFoodLootModifier(new LootItemCondition[0]));
        add("armor_structure_loot", new io.github.ningmeng123.mite.loot.ArmorStructureLootModifier(new LootItemCondition[0]));
        add("skeleton_arrows", new io.github.ningmeng123.mite.loot.SkeletonArrowLootModifier(new LootItemCondition[0]));
    }
}

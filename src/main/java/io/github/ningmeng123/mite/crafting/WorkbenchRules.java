package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.block.MaterialSmithingTableBlock;
import io.github.ningmeng123.mite.block.MaterialWorkbenchBlock;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteUpgradeTemplates;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** R196 compares material durability, not ore harvest level. @author NingMeng123 */
public final class WorkbenchRules {
    private WorkbenchRules() {}
    public static float materialCapacity(String material) {
        if (material == null) return 0;
        return switch (material) {
            case "wood" -> .5F;
            case "flint" -> 1F;
            case "obsidian" -> 2F;
            case "rusted_iron", "copper", "silver", "gold" -> 4F;
            case "iron" -> 8F;
            case "ancient_metal", "diamond" -> 16F;
            case "netherite" -> 32F;
            case "mithril" -> 64F;
            case "adamantium" -> 256F;
            case "vibranium" -> 512F;
            default -> 0F;
        };
    }
    public static float capacity(BlockState state) {
        return state.getBlock() instanceof MaterialWorkbenchBlock bench ? materialCapacity(bench.material()) : 0F;
    }
    @Nullable
    public static Block requiredWorkbench(ItemStack result) {
        if (result.getItem() instanceof BlockItem item && item.getBlock() instanceof MaterialSmithingTableBlock table) {
            return MiteWorkbenches.TABLES.get(table.material()).get();
        }
        return null;
    }
    public static float required(ItemStack result) {
        if (result.isEmpty()) return 0;
        if (result.is(Items.NETHERITE_INGOT)) return materialCapacity("mithril");
        if (result.getItem() instanceof io.github.ningmeng123.mite.phantom.PhantomBladeItem blade)
            return materialCapacity(blade.material());
        if (result.is(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get())) return materialCapacity("iron");
        float vibranium = VibraniumCrafting.required(result);
        if (vibranium >= 0F) return vibranium;
        var simply = io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat.definition(result);
        if (simply != null) return simply.profile().materialDurability();
        var shield = io.github.ningmeng123.mite.registry.MiteShields.definition(result);
        if (shield != null) return shield.materialDurability();
        if (result.getItem() instanceof io.github.ningmeng123.mite.item.R196ReinforcedBowItem bow)
            return materialCapacity(bow.material());
        if (result.getItem() instanceof io.github.ningmeng123.mite.item.R196CoinItem coin)
            return materialCapacity(coin.material());
        String decorative = io.github.ningmeng123.mite.registry.MiteDecorativeContent.material(result);
        if (decorative != null) return materialCapacity(decorative);
        if (result.getItem() instanceof BlockItem copper && (copper.getBlock() == Blocks.COPPER_TRAPDOOR
                || copper.getBlock() == Blocks.EXPOSED_COPPER_TRAPDOOR || copper.getBlock() == Blocks.WEATHERED_COPPER_TRAPDOOR
                || copper.getBlock() == Blocks.OXIDIZED_COPPER_TRAPDOOR || copper.getBlock() == Blocks.WAXED_COPPER_TRAPDOOR
                || copper.getBlock() == Blocks.WAXED_EXPOSED_COPPER_TRAPDOOR || copper.getBlock() == Blocks.WAXED_WEATHERED_COPPER_TRAPDOOR
                || copper.getBlock() == Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR)) return materialCapacity("copper");
        var arrow = io.github.ningmeng123.mite.registry.MiteRangedItems.arrowMaterial(result);
        var rod = io.github.ningmeng123.mite.registry.MiteRangedItems.rodMaterial(result);
        var carrotRod = io.github.ningmeng123.mite.registry.MiteRangedItems.carrotRodMaterial(result);
        var ranged = arrow != null ? arrow : rod != null ? rod : carrotRod;
        if (ranged != null) return ranged == io.github.ningmeng123.mite.rules.R196RangedRules.Material.FLINT
                || ranged == io.github.ningmeng123.mite.rules.R196RangedRules.Material.OBSIDIAN ? 0F : materialCapacity(ranged.id);
        if (result.is(Items.FLINT_AND_STEEL)) return materialCapacity("iron");
        if (result.getItem() instanceof io.github.ningmeng123.mite.item.RunestoneItem item)
            return item.getBlock() == io.github.ningmeng123.mite.registry.MiteWorldBlocks.MITHRIL_RUNESTONE.get()
                    ? materialCapacity("mithril") : materialCapacity("adamantium");
        if (result.getItem() instanceof BlockItem item && item.getBlock() instanceof MaterialWorkbenchBlock bench) {
            // RecipeHelper excludes obsidian when finding the preceding metal workbench stage.
            return switch (bench.material()) {
                case "copper", "silver", "gold" -> 1F;
                case "iron" -> 4F;
                case "ancient_metal" -> 8F;
                case "mithril" -> 16F;
                case "adamantium" -> 64F;
                case "vibranium" -> 256F;
                default -> 0F;
            };
        }
        var armor = EquipmentDurability.armorProfile(result);
        // Leather is not a metal; retained metal armor follows the same material equipment requirement.
        if (armor != null && armor.materialProtection() > 2) return armor.materialDurability();
        return materialCapacity(MiteTools.requiredBenchMaterial(result));
    }
    public static boolean allows(AbstractContainerMenu menu, ItemStack result) {
        Block bench = requiredWorkbench(result);
        if (bench != null) return menu instanceof CraftingMenu && menu instanceof WorkbenchAccess access
                && access.mite$isWorkbench(bench);
        float required = required(result);
        return required <= 0 || menu instanceof CraftingMenu && menu instanceof WorkbenchAccess access
                && access.mite$workbenchCapacity() >= required;
    }
    public static boolean allows(AbstractContainerMenu menu, ItemStack result, Level level) {
        if (allows(menu, result)) return true;
        if (requiredWorkbench(result) != null) return false;
        if (!(menu instanceof CraftingMenu || menu instanceof InventoryMenu)
                || !(menu.getSlot(1).container instanceof CraftingContainer grid)) return false;
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), level);
        return recipe.isPresent()
                && io.github.ningmeng123.mite.api.MiteExtensions.policy().isMaintenanceRecipe(recipe.get().value())
                && ItemStack.matches(result, recipe.get().value().assemble(grid.asCraftInput(), level.registryAccess()));
    }
    public static boolean allowsCrafter(Level level, BlockPos pos, ItemStack result) {
        return false;
    }
}

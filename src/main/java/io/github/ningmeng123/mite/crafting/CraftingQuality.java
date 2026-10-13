package io.github.ningmeng123.mite.crafting;

import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.EquipmentQualityRules;
import io.github.ningmeng123.mite.rules.EquipmentQualityRules.QualityRange;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ArmorDyeRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/** Source-based equipment quality, recipe overrides and exact manufacturing payments. @author NingMeng123 */
public final class CraftingQuality {
    private static final Map<ResourceLocation, Float> DIFFICULTIES = loadDifficulties();
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private record Selection(AbstractContainerMenu menu, ResourceLocation recipe, List<ItemStack> inputs, Quality quality) {}
    public record Result(TimedCraftingRecipes.Rule rule, ItemStack stack, Quality quality,
            QualityRange range, boolean fixed, boolean repair, boolean exempt, int cost, int consumption) {}
    public record Payment(int before, int cost) {}
    private CraftingQuality() {}

    public static float lowestDifficulty(ItemStack stack) {
        return DIFFICULTIES.getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()), -1F);
    }

    public static boolean canRepair(Player player, ItemStack stack) {
        if (!EquipmentDurability.isManaged(stack)) return true;
        float difficulty = lowestDifficulty(stack);
        return difficulty >= 0F && EquipmentQualityRules.canRepair(EquipmentDurability.quality(stack), difficulty,
                player.experienceLevel, player.totalExperience, EquipmentDurability.maximumQuality(stack), false, false, true);
    }

    public static Result resolve(Player player, AbstractContainerMenu menu, CraftingContainer grid,
            RecipeHolder<CraftingRecipe> recipe, ItemStack raw) {
        TimedCraftingRecipes.Rule rule = rule(recipe, raw, grid);
        if (rule == null || raw.isEmpty()) return null;
        if (!EquipmentDurability.isManaged(raw)) {
            return new Result(rule, RunestoneCrafting.apply(player, menu, grid, recipe.id(), raw), null, null, false, false, true, 0, 1);
        }
        boolean repair = recipe.value() instanceof RepairItemRecipe || recipe.value() instanceof LeatherMendingRecipe;
        boolean fixed = repair || recipe.value() instanceof ArmorDyeRecipe;
        QualityRange range = EquipmentQualityRules.selectionRange(player.experienceLevel,
                EquipmentDurability.maximumQuality(raw), false, false, true);
        Quality selected = fixed ? EquipmentDurability.quality(raw) : range.minimum();
        if (player instanceof ServerPlayer server) {
            Selection old = SELECTIONS.get(player.getUUID());
            if (!fixed && old != null && old.menu() == menu && old.recipe().equals(recipe.id())
                    && TimedCraftingController.matches(old.inputs(), grid)) {
                selected = range.clamp(old.quality());
                // Only changes to the minimum/material range reset selection. XP loss keeps the preview;
                // manufacturing eligibility is checked separately before starting and on every production tick.
                if (selected != old.quality()) TimedCraftingController.cancel(server);
            }
            SELECTIONS.put(player.getUUID(), new Selection(menu, recipe.id(), TimedCraftingController.snapshot(grid), selected));
        }
        ItemStack output = raw.copy();
        EquipmentDurability.setQuality(output, selected);
        if (fixed) range = new QualityRange(selected, selected);
        int cost = EquipmentQualityRules.selectedExperienceCost(rule.difficulty(), selected, fixed, false);
        int consumption = recipe.value() instanceof LeatherMendingRecipe
                ? LeatherMendingRecipe.materialConsumption(grid.asCraftInput()) : 1;
        return new Result(rule, output, selected, range, fixed, repair, fixed, cost, consumption);
    }

    /** Preview selection is independent of XP; actual manufacture retains the source ability requirement. */
    public static boolean canManufacture(Player player, Result result) {
        return result.quality() == null || result.fixed() || EquipmentQualityRules.range(result.rule().difficulty(),
                player.experienceLevel, player.totalExperience, EquipmentDurability.maximumQuality(result.stack()),
                false, false, true).contains(result.quality());
    }

    public static TimedCraftingRecipes.Rule rule(RecipeHolder<CraftingRecipe> recipe, ItemStack output, CraftingContainer grid) {
        return rule(recipe, output, grid.asCraftInput());
    }

    public static TimedCraftingRecipes.Rule rule(RecipeHolder<CraftingRecipe> recipe, ItemStack output,
            net.minecraft.world.item.crafting.CraftingInput input) {
        if (output.isEmpty()) return null;
        if (recipe.value() instanceof FoodManufacturingRecipe food) return food.manufacturing().rule();
        if (EquipmentDurability.isManaged(output)) {
            if (recipe.value() instanceof LeatherMendingRecipe) return new TimedCraftingRecipes.Rule(
                    LeatherMendingRecipe.difficulty(input), null, -1);
            if (recipe.value() instanceof RepairItemRecipe) return lowestDifficulty(output) < 0F ? null
                    : new TimedCraftingRecipes.Rule(lowestDifficulty(output) / 2F, benchMaterial(output), -1);
            if (recipe.value() instanceof ArmorDyeRecipe) return new TimedCraftingRecipes.Rule(100F, null, 0);
        }
        TimedCraftingRecipes.Rule known = TimedCraftingRecipes.get(recipe.id());
        if (known != null) return known;
        float difficulty = EquipmentDurability.isManaged(output) ? lowestDifficulty(output) : -1F;
        return difficulty < 0F ? null : new TimedCraftingRecipes.Rule(difficulty, benchMaterial(output), -1);
    }

    private static String benchMaterial(ItemStack output) {
        float capacity = WorkbenchRules.required(output);
        if (capacity <= 0F) return null;
        if (capacity >= 256F) return "adamantium";
        if (capacity >= 64F) return "mithril";
        if (capacity >= 32F) return "netherite";
        if (capacity >= 16F) return "ancient_metal";
        if (capacity >= 8F) return "iron";
        return "copper";
    }

    public static void select(ServerPlayer player, AbstractContainerMenu menu, Result result, int direction) {
        if (direction != -1 && direction != 1 || result.quality() == null || result.fixed()) return;
        Selection old = SELECTIONS.get(player.getUUID());
        if (old == null || old.menu() != menu) return;
        QualityRange range = result.range();
        Quality next = direction == 1 ? range.next(old.quality())
                : old.quality() == range.minimum() ? range.maximum() : Quality.fromOrdinal(old.quality().ordinal() - 1);
        SELECTIONS.put(player.getUUID(), new Selection(menu, old.recipe(), old.inputs(), next));
    }

    public static void clear(Player player) { SELECTIONS.remove(player.getUUID()); }
    public static void inputsChanged(AbstractContainerMenu menu) {
        SELECTIONS.entrySet().removeIf(entry -> entry.getValue().menu() == menu
                && menu.getSlot(1).container instanceof CraftingContainer grid
                && !TimedCraftingController.matches(entry.getValue().inputs(), grid));
    }

    /** Approval is side-effect free on our XP fields. Altered/cancelled costs must not grant free equipment. */
    public static Payment approvePayment(ServerPlayer player, int cost) {
        int before = player.totalExperience;
        if (cost < 0 || cost > 0 && before < cost) return null;
        if (cost == 0) return new Payment(before, 0);
        var pointEvent = new PlayerXpEvent.XpChange(player, -cost);
        if (NeoForge.EVENT_BUS.post(pointEvent).isCanceled() || pointEvent.getAmount() != -cost) return null;
        int newLevel = io.github.ningmeng123.mite.rules.ExperienceRules.levelForTotalExperience(before - cost);
        int delta = newLevel - player.experienceLevel;
        if (delta != 0) {
            var levelEvent = new PlayerXpEvent.LevelChange(player, delta);
            if (NeoForge.EVENT_BUS.post(levelEvent).isCanceled() || levelEvent.getLevels() != delta) return null;
        }
        return player.totalExperience == before ? new Payment(before, cost) : null;
    }

    public static boolean pay(ServerPlayer player, Payment payment) {
        if (payment == null || player.totalExperience != payment.before()) return false;
        if (payment.cost() > 0) {
            PlayerExperience.setTotal(player, payment.before() - payment.cost());
            player.increaseScore(-payment.cost());
        }
        return true;
    }

    private static Map<ResourceLocation, Float> loadDifficulties() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(CraftingQuality.class.getResourceAsStream(
                "/mite/quality-equipment-017.json")), StandardCharsets.UTF_8)) {
            Map<ResourceLocation, Float> values = new HashMap<>();
            JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("items").entrySet().forEach(entry ->
                    values.put(ResourceLocation.parse(entry.getKey()), entry.getValue().getAsJsonObject().get("lowest_difficulty").getAsFloat()));
            return Map.copyOf(values);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load quality manufacturing evidence", exception);
        }
    }
}

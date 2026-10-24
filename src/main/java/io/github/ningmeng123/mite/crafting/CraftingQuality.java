package io.github.ningmeng123.mite.crafting;

import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
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
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShieldDecorationRecipe;
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
        return io.github.ningmeng123.mite.api.MiteExtensions.policy().craftingDifficulty(stack, nativeLowestDifficulty(stack));
    }
    private static float nativeLowestDifficulty(ItemStack stack) {
        float vibranium = VibraniumCrafting.lowestDifficulty(stack);
        if (vibranium >= 0F) return vibranium;
        var simply = io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat.definition(stack);
        if (simply != null) return simply.craftingDifficulty();
        var shield = io.github.ningmeng123.mite.registry.MiteShields.definition(stack);
        if (shield != null) return shield.craftingDifficulty();
        float ranged = RangedRodRepair.lowestDifficulty(stack);
        if (ranged >= 0F) return ranged;
        return DIFFICULTIES.getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()), -1F);
    }

    public static boolean canRepair(Player player, ItemStack stack) {
        if (!EquipmentDurability.isManaged(stack)) return true;
        float difficulty = lowestDifficulty(stack);
        return difficulty >= 0F && EquipmentQualityRules.canRepair(EquipmentDurability.quality(stack), difficulty,
                player.experienceLevel, player.totalExperience, EquipmentDurability.maximumQuality(stack),
                PlayerCurses.has(player, R196Curse.CLUMSINESS, true), false, true);
    }

    public static Result resolve(Player player, AbstractContainerMenu menu, CraftingContainer grid,
            RecipeHolder<CraftingRecipe> recipe, ItemStack raw) {
        TimedCraftingRecipes.Rule rule = rule(recipe, raw, grid);
        if (rule == null || raw.isEmpty()) return null;
        if (io.github.ningmeng123.mite.api.MiteExtensions.policy().isMaintenanceRecipe(recipe.value())) {
            Quality quality=EquipmentDurability.isManaged(raw)?EquipmentDurability.quality(raw):null;
            return new Result(rule,raw.copy(),quality,quality==null?null:new QualityRange(quality,quality),true,false,true,0,1);
        }
        if (!EquipmentDurability.isManaged(raw)) {
            boolean repair = recipe.value() instanceof RepairItemRecipe && RangedRodRepair.applies(raw);
            return new Result(rule, RunestoneCrafting.apply(player, menu, grid, recipe.id(), raw), null, null, repair, repair, true, CoinCrafting.cost(raw), 1);
        }
        boolean repair = recipe.value() instanceof RepairItemRecipe || recipe.value() instanceof LeatherMendingRecipe;
        boolean fixed = repair || recipe.value() instanceof VibraniumUpgradeRecipe || recipe.value() instanceof ArmorDyeRecipe || shieldDecoration(recipe, raw);
        boolean clumsy = PlayerCurses.has(player, R196Curse.CLUMSINESS, true);
        QualityRange range = EquipmentQualityRules.selectionRange(player.experienceLevel,
                EquipmentDurability.maximumQuality(raw), clumsy, false, true);
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
        int cost = EquipmentQualityRules.selectedExperienceCost(rule.difficulty(), selected, fixed, clumsy);
        int consumption = recipe.value() instanceof LeatherMendingRecipe
                ? LeatherMendingRecipe.materialConsumption(grid.asCraftInput()) : 1;
        return new Result(rule, output, selected, range, fixed, repair, fixed, cost, consumption);
    }

    /** Preview selection is independent of XP; actual manufacture retains the source ability requirement. */
    public static boolean canManufacture(Player player, Result result) {
        return result.quality() == null || result.fixed() || EquipmentQualityRules.range(result.rule().difficulty(),
                player.experienceLevel, player.totalExperience, EquipmentDurability.maximumQuality(result.stack()),
                PlayerCurses.has(player, R196Curse.CLUMSINESS, true), false, true).contains(result.quality());
    }

    public static TimedCraftingRecipes.Rule rule(RecipeHolder<CraftingRecipe> recipe, ItemStack output, CraftingContainer grid) {
        return rule(recipe, output, grid.asCraftInput());
    }

    public static TimedCraftingRecipes.Rule rule(RecipeHolder<CraftingRecipe> recipe, ItemStack output,
            net.minecraft.world.item.crafting.CraftingInput input) {
        if (output.isEmpty()) return null;
        // Packing and unpacking torches are inventory operations, available through
        // every native click mode and the Crafter without a manufacturing session.
        if (instantTorchBundle(recipe.id())) return null;
        if (io.github.ningmeng123.mite.api.MiteExtensions.policy().isMaintenanceRecipe(recipe.value())) return new TimedCraftingRecipes.Rule(50F,null,-1);
        // Decorating an existing shield is a fixed-quality cosmetic operation, not manufacturing another metal shield.
        if (shieldDecoration(recipe, output)) return new TimedCraftingRecipes.Rule(100F, benchMaterial(output), -1);
        if (output.is(io.github.ningmeng123.mite.registry.MiteFoods.SALAD.get())) {
            // All salad recipes, including old empty-bowl and data-pack alternatives, share
            // a visible five-second base preparation period rather than the 25-tick minimum.
            TimedCraftingRecipes.Rule original = recipe.value() instanceof FoodManufacturingRecipe food
                    ? food.manufacturing().rule() : TimedCraftingRecipes.get(recipe.id());
            return new TimedCraftingRecipes.Rule(Math.max(100F, original == null ? 0F : original.difficulty()),
                    original == null ? null : original.requiredBench(), original == null ? 0 : original.sourceOrder());
        }
        if (recipe.value() instanceof FoodManufacturingRecipe food) return food.manufacturing().rule();
        if (recipe.value() instanceof RepairItemRecipe && RangedRodRepair.applies(output)) return RangedRodRepair.rule(output);
        if (EquipmentDurability.isManaged(output)) {
            if (recipe.value() instanceof LeatherMendingRecipe) return new TimedCraftingRecipes.Rule(
                    LeatherMendingRecipe.difficulty(input), null, -1);
            if (recipe.value() instanceof RepairItemRecipe) return lowestDifficulty(output) < 0F ? null
                    : new TimedCraftingRecipes.Rule(lowestDifficulty(output) / 2F, benchMaterial(output), -1);
            if (recipe.value() instanceof ArmorDyeRecipe) return new TimedCraftingRecipes.Rule(100F, null, 0);
        }
        TimedCraftingRecipes.Rule known = TimedCraftingRecipes.get(recipe.id());
        if (known != null) return known;
        // R196's dynamic recipes have no fixed output identity: use their operation, not the result item.
        var serializer = recipe.value().getSerializer();
        if (recipe.id().equals(ResourceLocation.withDefaultNamespace("firework_rocket_simple"))
                || serializer == RecipeSerializer.FIREWORK_ROCKET || serializer == RecipeSerializer.FIREWORK_STAR
                || serializer == RecipeSerializer.FIREWORK_STAR_FADE)
            return new TimedCraftingRecipes.Rule(100F, null, 1);
        if (serializer == RecipeSerializer.MAP_CLONING) return new TimedCraftingRecipes.Rule(100F, null, 155);
        if (serializer == RecipeSerializer.MAP_EXTENDING) return new TimedCraftingRecipes.Rule(300F, null, 156);
        var vibranium = VibraniumCrafting.rule(recipe.id(), output);
        if (vibranium != null) return vibranium;
        if (recipe.value() instanceof VibraniumUpgradeRecipe) return new TimedCraftingRecipes.Rule(
                MaterialUpgradeRequirements.difficulty(output), benchMaterial(output), -1);
        float difficulty = EquipmentDurability.isManaged(output) ? lowestDifficulty(output) : -1F;
        return difficulty < 0F ? null : new TimedCraftingRecipes.Rule(difficulty, benchMaterial(output), -1);
    }

    public static boolean instantTorchBundle(ResourceLocation recipe) {
        return recipe.getNamespace().equals("modern_ite") && switch (recipe.getPath()) {
            case "stack_torch_string_coal", "stack_torch_string_charcoal",
                    "stack_torch_sinew_coal", "stack_torch_sinew_charcoal", "unpack_torches_1" -> true;
            default -> false;
        };
    }

    public static boolean shieldDecoration(RecipeHolder<CraftingRecipe> recipe, ItemStack result) {
        return recipe.value() instanceof ShieldDecorationRecipe
                && io.github.ningmeng123.mite.registry.MiteShields.definition(result) != null;
    }

    private static String benchMaterial(ItemStack output) {
        float capacity = WorkbenchRules.required(output);
        if (capacity <= 0F) return null;
        if (capacity >= 512F) return "vibranium";
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
        return approveExperienceTransaction(player, cost, 0);
    }

    /** Minting costs and coin refunds are approved together before any item is consumed. */
    public static Payment approveExperienceTransaction(ServerPlayer player, int cost, int refund) {
        int before = player.totalExperience;
        if (cost < 0 || refund < 0 || cost > 0 && before < cost) return null;
        long target = (long)before - cost + refund;
        long change = (long)refund - cost;
        if (target > Integer.MAX_VALUE || target < -800 || change > Integer.MAX_VALUE || change < -Integer.MAX_VALUE) return null;
        if (change == 0) return new Payment(before, 0);
        var pointEvent = new PlayerXpEvent.XpChange(player, (int)change);
        if (NeoForge.EVENT_BUS.post(pointEvent).isCanceled() || pointEvent.getAmount() != change) return null;
        int newLevel = io.github.ningmeng123.mite.rules.ExperienceRules.levelForTotalExperience((int)target);
        int delta = newLevel - player.experienceLevel;
        if (delta != 0) {
            var levelEvent = new PlayerXpEvent.LevelChange(player, delta);
            if (NeoForge.EVENT_BUS.post(levelEvent).isCanceled() || levelEvent.getLevels() != delta) return null;
        }
        return player.totalExperience == before ? new Payment(before, (int)-change) : null;
    }

    public static boolean pay(ServerPlayer player, Payment payment) {
        return pay(player, payment, false);
    }

    public static boolean pay(ServerPlayer player, Payment payment, boolean coinExchange) {
        if (payment == null || player.totalExperience != payment.before()) return false;
        if (payment.cost() != 0) {
            PlayerExperience.setTotal(player, payment.before() - payment.cost());
            // Source coin minting subtracts XP without subtracting score; redemption adds both.
            if (!coinExchange || payment.cost() < 0) player.increaseScore(-payment.cost());
            if (coinExchange && payment.cost() < 0)
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, .1F, 1F);
        }
        return true;
    }

    private static Map<ResourceLocation, Float> loadDifficulties() {
        try {
            Map<ResourceLocation, Float> values = new HashMap<>();
            for (String resource : List.of("/mite/quality-equipment-017.json", "/mite/quality-equipment-027-armor.json", "/mite/quality-equipment-028-weapons.json")) {
                try (var reader = new InputStreamReader(Objects.requireNonNull(CraftingQuality.class.getResourceAsStream(resource)), StandardCharsets.UTF_8)) {
                    JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("items").entrySet().forEach(entry ->
                            values.put(ResourceLocation.parse(entry.getKey()), entry.getValue().getAsJsonObject().get("lowest_difficulty").getAsFloat()));
                }
            }
            return Map.copyOf(values);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load quality manufacturing evidence", exception);
        }
    }
}

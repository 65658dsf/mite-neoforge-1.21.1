package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.network.NutritionPayload;
import io.github.ningmeng123.mite.registry.MiteFoodCatalog;
import io.github.ningmeng123.mite.rules.AppleSkinNutritionRules;
import io.github.ningmeng123.mite.rules.ProgressionRules;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Adapt AppleSkin's display to server-owned S/N and native saturation. @author NingMeng123 */
public final class AppleSkinNutritionView {
    private AppleSkinNutritionView() {}
    private record Reserves(int nutrition, float saturation, int satiation, int capacity) {}

    /** Implemented on AppleSkin's queried result without linking any AppleSkin type. */
    public interface QueriedFood {
        ItemStack mite$queriedStack();
        FoodProperties mite$queriedProperties();
    }

    private static Reserves reserves(Player player) {
        if (player == null) return new Reserves(0, 0F, 0, 20);
        var snapshot = NutritionPayload.latest();
        if (snapshot != null && snapshot.entityId() == player.getId())
            return new Reserves(snapshot.nutrition(), snapshot.saturation(), snapshot.satiation(), snapshot.limit());
        var food = player.getFoodData();
        int nutrition = food instanceof DualFoodData dual ? dual.mite$getNutrition() : 0;
        return new Reserves(nutrition, food.getSaturationLevel(), food.getFoodLevel(), io.github.ningmeng123.mite.api.MiteExtensions.foodCapacity(player));
    }

    public static int capacity(Player player) { return reserves(player).capacity(); }
    public static float current(Player player) {
        var state = reserves(player);
        return AppleSkinNutritionRules.displayedCurrent(state.nutrition(), state.saturation(), state.capacity());
    }

    public static float previewGain(Player player, ItemStack stack) {
        if (player == null) return 0F;
        var profile = MiteFoodCatalog.profile(stack);
        if (profile == null) return 0F;
        var state = reserves(player);
        boolean seeds = stack.is(Items.WHEAT_SEEDS);
        var component = stack.get(DataComponents.FOOD);
        float saturationGain = seeds && component != null ? component.saturation() : profile.saturationGain();
        return AppleSkinNutritionRules.previewGain(state.nutrition(), state.saturation(), state.satiation(), state.capacity(),
                profile.nutrition(), seeds || saturationGain > 0F, saturationGain, FoodConsumption.canIngest(player, stack));
    }

    /** A temporary properties record for this HUD calculation, never an item-component mutation. */
    public static FoodProperties previewProperties(Player player, QueriedFood queried) {
        var original = queried.mite$queriedProperties();
        return new FoodProperties(original.nutrition(), previewGain(player, queried.mite$queriedStack()), original.canAlwaysEat(),
                original.eatSeconds(), original.usingConvertsTo(), original.effects());
    }
}

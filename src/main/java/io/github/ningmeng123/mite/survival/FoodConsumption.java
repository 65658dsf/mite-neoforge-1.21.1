package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.curse.CurseIngestion;
import io.github.ningmeng123.mite.item.FoodProfile;
import io.github.ningmeng123.mite.registry.MiteFoodCatalog;
import io.github.ningmeng123.mite.network.NutritionPayload;
import io.github.ningmeng123.mite.rules.NutritionRules;
import io.github.ningmeng123.mite.rules.ProgressionRules;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/** Shared ingestion permissions and food-energy checks. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class FoodConsumption {
    private FoodConsumption() {}
    public static boolean hasEnergy(Player p) {
        return p.getFoodData().getFoodLevel()>0 || ((DualFoodData)p.getFoodData()).mite$getNutrition()>0;
    }
    public static boolean canIngest(Player p, ItemStack stack) {
        return canIngest(p,MiteFoodCatalog.profile(stack)) && CurseIngestion.allowed(p, stack);
    }
    public static boolean canIngest(Player p, FoodProfile f) {
        if (f==null) return true;
        NutritionState state = NutritionState.get(p);
        var synced = NutritionPayload.latest();
        int protein=state.protein(), plants=state.plants(), resistanceLevel=state.resistanceLevel();
        if(p.level().isClientSide && synced!=null && synced.entityId()==p.getId()) {
            protein=synced.protein(); plants=synced.plants(); resistanceLevel=synced.resistanceLevel();
        }
        return NutritionRules.canIngest(p.getFoodData().getFoodLevel(),((DualFoodData)p.getFoodData()).mite$getNutrition(),
                ProgressionRules.foodLimitForLevel(p.experienceLevel),
                NutritionRules.adjustedSatiation(f.satiation(),f.sugar(),resistanceLevel),f.nutrition(),f.sugar(),
                f.alwaysEdible() || p.hasInfiniteMaterials(),f.protein(),f.plants(),protein,plants,
                p.hasEffect(MobEffects.CONFUSION),p.isEyeInFluid(net.minecraft.tags.FluidTags.WATER));
    }
    @SubscribeEvent public static void start(LivingEntityUseItemEvent.Start e) {
        if(e.getEntity() instanceof Player p && !EggThrowControl.isThrowing(p) && !canIngest(p,e.getItem())) e.setCanceled(true);
    }
    @SubscribeEvent public static void using(LivingEntityUseItemEvent.Tick e) {
        if(e.getEntity() instanceof Player p && !canIngest(p,e.getItem())) e.setCanceled(true);
    }
}

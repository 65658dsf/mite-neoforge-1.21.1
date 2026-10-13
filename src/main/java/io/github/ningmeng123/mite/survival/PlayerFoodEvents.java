package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import io.github.ningmeng123.mite.network.NutritionPayload;
import io.github.ningmeng123.mite.rules.ProgressionRules;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Applies limits before food is used or synchronized. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class PlayerFoodEvents {
    private static final Map<UUID,NutritionPayload> SENT = new HashMap<>();
    private PlayerFoodEvents() {
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        applyLimit(event.getEntity());
        sync(event.getEntity(),true);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        applyLimit(event.getEntity());
        sync(event.getEntity(),true);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        applyLimit(event.getEntity());
        sync(event.getEntity(),true);
    }

    @SubscribeEvent
    public static void beforePlayerTick(PlayerTickEvent.Pre event) {
        applyLimit(event.getEntity());
    }

    @SubscribeEvent public static void afterPlayerTick(PlayerTickEvent.Post event) {
        sync(event.getEntity(),false);
    }
    @SubscribeEvent public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }
    public static void sync(Player p,boolean force) {
        if(!(p instanceof ServerPlayer player)) return;
        NutritionState state=NutritionState.get(p);
        NutritionPayload payload=new NutritionPayload(p.getId(),p.getFoodData().getFoodLevel(),
                ((DualFoodData)p.getFoodData()).mite$getNutrition(),ProgressionRules.foodLimitForLevel(p.experienceLevel),
                state.protein(),state.fat(),state.plants(),state.resistance(),state.resistanceLevel(),
                p.getFoodData().getSaturationLevel());
        NutritionPayload old=SENT.get(p.getUUID());
        if(force || old==null || p.tickCount%20==0 || old.entityId()!=payload.entityId()
                || old.satiation()!=payload.satiation() || old.nutrition()!=payload.nutrition() || old.limit()!=payload.limit()
                || Float.compare(old.saturation(),payload.saturation())!=0
                || old.resistanceLevel()!=payload.resistanceLevel()
                || (old.protein()==0)!=(payload.protein()==0) || (old.plants()==0)!=(payload.plants()==0)) {
            NutritionPayload.send(player,payload);
            SENT.put(p.getUUID(),payload);
        }
    }

    private static void applyLimit(Player player) {
        if (player instanceof ServerPlayer) {
            ((LeveledFoodData) player.getFoodData()).mite$clampToLimit();
        }
    }
}

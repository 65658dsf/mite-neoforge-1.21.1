package io.github.ningmeng123.mite.combat;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsHandling;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** One player-only, main-hand movement cost; item attributes never slow weapon-bearing monsters. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class WeaponEncumbrance {
    public static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "held_weapon_weight");

    private WeaponEncumbrance() {}

    public static double penalty(ItemStack weapon) {
        if (weapon.isEmpty()) return 0D;
        double result;
        if (SimplySwordsCompat.definition(weapon) != null) result = SimplySwordsHandling.movementPenalty(weapon);
        else result = NativeWeaponAttributes.movementPenalty(weapon);
        return Double.isFinite(result) ? Math.clamp(result, 0D, 1D) : 0D;
    }

    public static void clear(Player player) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(MODIFIER_ID);
    }

    public static void refresh(Player player) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        double desired = player.isAlive() && !player.isSpectator() ? penalty(player.getMainHandItem()) : 0D;
        var current = speed.getModifier(MODIFIER_ID);
        if (desired <= 0D) {
            if (current != null) speed.removeModifier(MODIFIER_ID);
            return;
        }
        if (current != null && current.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                && Double.compare(current.amount(), -desired) == 0) return;
        if (current != null) speed.removeModifier(MODIFIER_ID);
        speed.addTransientModifier(new AttributeModifier(MODIFIER_ID, -desired, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    @SubscribeEvent public static void equipment(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player player && event.getSlot() == EquipmentSlot.MAINHAND) refresh(player);
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Pre event) { refresh(event.getEntity()); }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { clear(event.getEntity()); refresh(event.getEntity()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { clear(event.getEntity()); refresh(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); refresh(event.getEntity()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) { clear(event.getOriginal()); clear(event.getEntity()); }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) clear(player);
    }
}

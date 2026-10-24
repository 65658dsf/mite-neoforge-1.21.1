package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.mixin.SourceMobDropChanceAccess;
import io.github.ningmeng123.mite.rules.MobEnchantmentRules;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasion;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** One date roll per newly generated stack; saved and picked-up equipment never enters this path. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class MobEquipmentEnchantments {
    public static final String DATA_KEY = "mite:equipment_enchantment_spawn_decided";
    // Keep bookkeeping on the newborn owner, never in item components or persisted item data.
    private static final Map<Mob, Set<ItemStack>> GENERATED = Collections.synchronizedMap(new WeakHashMap<>());

    private MobEquipmentEnchantments() {}

    public static long highestDay(Mob mob) {
        return mob.level() instanceof ServerLevel level ? BloodMoonInvasion.highestDay(level) : 1L;
    }

    /** Source generators call this before applying their original quality and wear. */
    public static ItemStack generated(Mob mob, ItemStack stack, boolean sourceEnchantment) {
        long day = highestDay(mob);
        if (!stack.isEmpty() && !stack.isEnchanted()) {
            float tension = R196MobEquipment.tension(mob);
            float chance = MobEnchantmentRules.chance(day, tension, sourceEnchantment);
            // Preserve the old enchant=false first-day RNG sequence, including its wear draw.
            if ((sourceEnchantment || chance > 0F) && mob.getRandom().nextFloat() < chance)
                stack = EnchantmentHelper.enchantItem(mob.getRandom(), stack,
                        MobEnchantmentRules.power(day, tension, mob.getRandom().nextInt(18)),
                        mob.level().registryAccess(), Optional.empty());
        }
        if (!mob.getPersistentData().contains(DATA_KEY))
            GENERATED.computeIfAbsent(mob, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(stack);
        return stack;
    }

    /** Native enchantment decisions remain intact; only their still-unenchanted birth equipment gets the date supplement. */
    public static boolean populate(Mob mob) {
        if (!(mob instanceof Enemy) || !(mob.level() instanceof ServerLevel) || mob.getPersistentData().contains(DATA_KEY)) return false;
        mob.getPersistentData().putBoolean(DATA_KEY, true);
        Set<ItemStack> source = GENERATED.remove(mob);
        if (BloodMoonEquipment.spawnEquipmentLocked(mob) || !MobShields.spawnReason(mob.getSpawnType())) return false;
        long day = highestDay(mob);
        float chance = MobEnchantmentRules.dateChance(day);
        if (!(chance > 0F)) return false;
        boolean enchanted = false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty() || stack.isEnchanted() || !stack.isEnchantable()
                    || source != null && source.contains(stack)
                    || ((SourceMobDropChanceAccess)mob).mite$equipmentDropChance(slot) > 1F) continue;
            if (mob.getRandom().nextFloat() < chance) {
                ItemStack result = EnchantmentHelper.enchantItem(mob.getRandom(), stack,
                        MobEnchantmentRules.datePower(day), mob.level().registryAccess(), Optional.empty());
                mob.setItemSlot(slot, result);
                enchanted |= result.isEnchanted();
            }
        }
        return enchanted;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob)
                || !(mob instanceof Enemy)) return;
        if (event.loadedFromDisk()) {
            mob.getPersistentData().putBoolean(DATA_KEY, true);
            GENERATED.remove(mob);
            return;
        }
        populate(mob);
    }
}

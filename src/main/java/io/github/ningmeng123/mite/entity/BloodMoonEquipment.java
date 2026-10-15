package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.BloodMoonEquipmentRules;
import io.github.ningmeng123.mite.rules.LunarCalendar;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Shared spawn-only progression; all items retain source quality, wear and enchantment generation. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class BloodMoonEquipment {
    public static final String DATA_KEY = "mite:blood_moon_equipment";
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] SUFFIXES = {"helmet", "chestplate", "leggings", "boots"};
    private BloodMoonEquipment() {}

    public static boolean handles(Mob mob) {
        if (mob instanceof R196Mob source) return source instanceof R196Undead;
        return mob instanceof Zombie || mob instanceof AbstractSkeleton;
    }

    public static boolean bloodDate(Mob mob) {
        return mob.level().dimension().equals(Level.OVERWORLD) && LunarCalendar.state(mob.level().getDayTime(), true).bloodPeriod();
    }

    public static boolean progressionActive(Mob mob) {
        return handles(mob) && mob.level() instanceof ServerLevel level
                && BloodMoonEquipmentRules.bloodMoons(BloodMoonInvasion.highestDay(level)) >= 2L;
    }

    /** True means this armor roll was handled, including an intentionally empty result. */
    public static boolean populate(Mob mob) {
        if (!handles(mob) || !(mob.level() instanceof ServerLevel level)) return false;
        var data = mob.getPersistentData();
        if (data.contains(DATA_KEY)) return true;
        long count = BloodMoonEquipmentRules.bloodMoons(BloodMoonInvasion.highestDay(level));
        int stage = BloodMoonEquipmentRules.stage(count);
        if (stage < 0) return false;
        var result = new net.minecraft.nbt.CompoundTag();
        result.putInt("version", 1); result.putInt("stage", stage); result.putInt("tier", -1);
        data.put(DATA_KEY, result);
        boolean blood = bloodDate(mob);
        result.putBoolean("blood_date", blood);
        if (mob.getRandom().nextDouble() >= BloodMoonEquipmentRules.armorChance(stage, blood)) return true;
        int tier = BloodMoonEquipmentRules.selectTier(stage, blood, mob.getRandom().nextDouble());
        result.putInt("tier", tier);
        int filled = 0;
        for (int index = 0; index < SLOTS.length; index++) if (mob.getItemBySlot(SLOTS[index]).isEmpty()) {
            mob.setItemSlot(SLOTS[index], R196MobEquipment.generated(mob,
                    BloodMoonEquipmentRules.PREFIXES.get(tier) + SUFFIXES[index], true));
            filled |= 1 << index;
        }
        result.putInt("filled_slots", filled);
        return true;
    }

    public static double legacyChance(Mob mob, double sourceChance) {
        if (!(mob.level() instanceof ServerLevel level) || !bloodDate(mob)) return sourceChance;
        return BloodMoonEquipmentRules.bloodMoons(BloodMoonInvasion.highestDay(level)) == 1L ? .6D : sourceChance;
    }

    public static boolean generatedSlot(Mob mob, EquipmentSlot slot) {
        var data = mob.getPersistentData().getCompound(DATA_KEY);
        for (int index = 0; index < SLOTS.length; index++) if (SLOTS[index] == slot)
            return (data.getInt("filled_slots") & (1 << index)) != 0;
        return false;
    }

    /** Fill paths that never call the native armor hook, after source weapons/fixed sets are initialized. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob) || !handles(mob)) return;
        if (event.loadedFromDisk()) {
            // Mark untouched old entities as already decided, without changing any equipment.
            // The marker also prevents an upgrade if that old entity later crosses dimensions.
            markLegacyEntity(mob, "existing_entity");
            return;
        }
        if (!populate(mob)) markLegacyEntity(mob, "legacy_finalized");
    }

    private static void markLegacyEntity(Mob mob, String reason) {
        if (mob.getPersistentData().contains(DATA_KEY)) return;
        var existing = new net.minecraft.nbt.CompoundTag();
        existing.putInt("version", 1); existing.putInt("stage", -1); existing.putInt("tier", -1);
        existing.putBoolean(reason, true); mob.getPersistentData().put(DATA_KEY, existing);
    }
}

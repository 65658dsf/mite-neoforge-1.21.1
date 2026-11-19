package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import io.github.ningmeng123.mite.rules.BloodMoonEquipmentRules;
import io.github.ningmeng123.mite.rules.LunarCalendar;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Shared spawn-only progression; all items retain source quality, wear and enchantment generation. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class BloodMoonEquipment {
    public static final String DATA_KEY = "mite:blood_moon_equipment";
    /** Legacy addon constant; world age no longer suppresses birth equipment. */
    public static final long EARLY_EQUIPMENT_LAST_DAY = 16L;
    /** Legacy addon tag; retained for data-pack and binary compatibility. */
    public static final TagKey<EntityType<?>> EARLY_EQUIPMENT_EXEMPT = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "early_equipment_exempt"));
    private static final int VERSION = 3;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] SUFFIXES = {"helmet", "chestplate", "leggings", "boots"};
    private static final String[] TOOL_PREFIXES = {"mite:rusted_iron_", "mite:rusted_iron_", "minecraft:golden_",
            "mite:copper_", "mite:silver_", "minecraft:iron_", "mite:ancient_metal_", "mite:mithril_", "mite:adamantium_", "mite:vibranium_"};
    private static final String[] TOOLS = {"sword", "pickaxe", "shovel", "axe"};
    private BloodMoonEquipment() {}

    public static boolean handles(Mob mob) {
        if (mob instanceof R196Mob source) return source instanceof R196Undead;
        return mob instanceof Zombie || mob instanceof AbstractSkeleton || PiglinArmor.handles(mob);
    }

    public static boolean bloodDate(Mob mob) {
        return mob.level().dimension().equals(Level.OVERWORLD) && LunarCalendar.state(mob.level().getDayTime(), true).bloodPeriod();
    }

    public static boolean progressionActive(Mob mob) {
        if (!handles(mob) || !(mob.level() instanceof ServerLevel level)) return false;
        return BloodMoonEquipmentRules.stageForDay(BloodMoonInvasion.highestDay(level)) >= 0;
    }

    /** True means this armor roll was handled, including an intentionally empty result. */
    public static boolean populate(Mob mob) {
        if (!handles(mob) || !(mob.level() instanceof ServerLevel level)) return false;
        var data = mob.getPersistentData();
        if (data.contains(DATA_KEY)) return true;
        long day = BloodMoonInvasion.highestDay(level);
        int stage = BloodMoonEquipmentRules.stageForDay(day);
        if (stage < 0) return false;
        boolean blood = bloodDate(mob);
        var result = new CompoundTag();
        result.putInt("version", VERSION); result.putInt("stage", stage); result.putInt("tier", -1);
        result.putLong("highest_day", day); result.putDouble("progress", stage);
        data.put(DATA_KEY, result);
        result.putBoolean("blood_date", blood);
        int[] tiers = {-1, -1, -1, -1};
        result.putIntArray("armor_tiers", tiers);
        if (blood) {
            int filled = 0;
            for (int index = 0; index < SLOTS.length; index++) if (mob.getItemBySlot(SLOTS[index]).isEmpty()) {
                if (mob.getRandom().nextDouble() >= BloodMoonEquipmentRules.armorChance(stage, true)) continue;
                int tier = selectArmorTier(mob, stage, true);
                mob.setItemSlot(SLOTS[index], R196MobEquipment.generated(mob,
                        BloodMoonEquipmentRules.PREFIXES.get(tier) + SUFFIXES[index], true));
                tiers[index] = tier;
                filled |= 1 << index;
            }
            result.putIntArray("armor_tiers", tiers); result.putInt("filled_slots", filled);
            return true;
        }
        double armorChance = BloodMoonEquipmentRules.ordinaryArmorChance(stage);
        if (mob.getRandom().nextDouble() >= armorChance) return true;
        int tier = selectArmorTier(mob, stage, false);
        result.putInt("tier", tier);
        int filled = 0;
        for (int index = 0; index < SLOTS.length; index++) if (mob.getItemBySlot(SLOTS[index]).isEmpty()) {
            mob.setItemSlot(SLOTS[index], R196MobEquipment.generated(mob,
                    BloodMoonEquipmentRules.PREFIXES.get(tier) + SUFFIXES[index], true));
            tiers[index] = tier;
            filled |= 1 << index;
        }
        result.putIntArray("armor_tiers", tiers); result.putInt("filled_slots", filled);
        return true;
    }

    /** Called after spawn initialization so native weapons and explicitly supplied equipment keep their exact stacks. */
    private static void populateHeld(Mob mob) {
        CompoundTag equipment = mob.getPersistentData().getCompound(DATA_KEY);
        // Earlier versions keep their old completed/empty decisions; only fresh v3 rolls may fill a hand.
        if (equipment.getInt("version") != VERSION || equipment.contains("held")) return;
        CompoundTag held = heldResult(equipment, "decided");
        int stage = equipment.getInt("stage");
        if (stage < 0 || !(mob instanceof Zombie || mob instanceof R196Undead)) return;
        if (!mob.getMainHandItem().isEmpty()) { held.putBoolean("preserved", true); return; }
        boolean blood = equipment.getBoolean("blood_date");
        double progress = equipment.getDouble("progress");
        double heldChance = blood ? BloodMoonEquipmentRules.heldChance(stage, true)
                : BloodMoonEquipmentRules.ordinaryHeldChance(progress);
        if (mob.getRandom().nextDouble() >= heldChance) return;
        int tier = armorTier(mob);
        if (tier < 0) tier = selectTier(progress, blood, mob.getRandom().nextDouble());
        int tool = mob.getRandom().nextInt(TOOLS.length);
        mob.setItemSlot(EquipmentSlot.MAINHAND, R196MobEquipment.generated(mob, TOOL_PREFIXES[tier] + TOOLS[tool], true));
        held.putInt("tier", tier); held.putInt("tool", tool); held.putBoolean("generated", true);
    }

    private static int selectTier(double progress, boolean blood, double draw) {
        return blood ? BloodMoonEquipmentRules.selectTier((int)progress, true, draw)
                : BloodMoonEquipmentRules.selectOrdinaryTier(progress, draw);
    }

    private static int selectArmorTier(Mob mob, double progress, boolean blood) {
        int tier = selectTier(progress, blood, mob.getRandom().nextDouble());
        return PiglinArmor.handles(mob) ? Math.max(PiglinArmor.MINIMUM_TIER, tier) : tier;
    }

    /** Prefer armor created by this spawn roll, then recognized explicitly supplied armor. */
    private static int armorTier(Mob mob) {
        int generated = mob.getPersistentData().getCompound(DATA_KEY).getInt("filled_slots");
        for (int pass = 0; pass < 2; pass++) for (int slot = 0; slot < SLOTS.length; slot++) {
            if (pass == 0 && (generated & (1 << slot)) == 0) continue;
            String id = BuiltInRegistries.ITEM.getKey(mob.getItemBySlot(SLOTS[slot]).getItem()).toString();
            for (int tier = 0; tier < BloodMoonEquipmentRules.PREFIXES.size(); tier++)
                if (id.equals(BloodMoonEquipmentRules.PREFIXES.get(tier) + SUFFIXES[slot])) return tier;
            if (id.equals("mite:rusted_iron_chain_" + SUFFIXES[slot]) || id.equals("mite:rusted_iron_" + SUFFIXES[slot])) return 1;
            if (id.equals("mite:copper_chain_" + SUFFIXES[slot])) return 3;
        }
        return -1;
    }

    private static CompoundTag heldResult(CompoundTag equipment, String reason) {
        var held = new CompoundTag(); held.putInt("version", 1); held.putInt("tier", -1); held.putInt("tool", -1);
        held.putBoolean(reason, true); equipment.put("held", held); return held;
    }

    public static double legacyChance(Mob mob, double sourceChance) {
        if (!(mob.level() instanceof ServerLevel level) || !handles(mob)) return sourceChance;
        long day = BloodMoonInvasion.highestDay(level);
        if (!bloodDate(mob)) return !BloodMoonEquipmentRules.ordinaryActive(day)
                ? BloodMoonEquipmentRules.EARLY_ORDINARY_ARMOR_CHANCE : sourceChance;
        return BloodMoonEquipmentRules.bloodMoons(day) == 1L
                ? BloodMoonEquipmentRules.FIRST_BLOOD_ARMOR_CHANCE : sourceChance;
    }

    public static boolean generatedSlot(Mob mob, EquipmentSlot slot) {
        var data = mob.getPersistentData().getCompound(DATA_KEY);
        for (int index = 0; index < SLOTS.length; index++) if (SLOTS[index] == slot)
            return (data.getInt("filled_slots") & (1 << index)) != 0;
        return false;
    }

    /** Compatibility entry point for existing addons: birth equipment is never age-locked. */
    public static boolean spawnEquipmentLocked(Mob mob) {
        return false;
    }

    /** Legacy tag query retained for addons; it no longer controls equipment generation. */
    public static boolean earlyEquipmentExempt(Mob mob) {
        return mob.getType().is(EARLY_EQUIPMENT_EXEMPT);
    }

    public static boolean earlyEquipmentArcher(Mob mob) {
        if (!(mob instanceof AbstractSkeleton)) return false;
        boolean archer;
        if (mob instanceof R196Skeleton source) archer = source.isLongdead() && source.skeletonType() == 0;
        else if (mob.getType() == EntityType.SKELETON)
            archer = mob instanceof OrdinarySkeletonState state && state.mite$sourceSkeletonType() == 0;
        else archer = mob.getType() == EntityType.STRAY || mob.getType() == EntityType.BOGGED;
        return MiteExtensions.policy().earlyEquipmentArcher(mob, archer);
    }

    /** Retire old birth locks without re-rolling equipment or replacing anything the mob now owns. */
    private static void retireEarlyEquipmentLock(Mob mob) {
        CompoundTag equipment = mob.getPersistentData().getCompound(DATA_KEY);
        boolean locked = equipment.getBoolean("early_equipment_locked");
        equipment.remove("early_equipment_locked");
        if (!locked) return;
        equipment.putBoolean("existing_entity", true);
        if (!mob.getMainHandItem().isEmpty() || SourceEnchantmentCombat.disarmed(mob)) return;
        // These native identities require their ordinary birth weapon. Custom/source roles
        // retain their own equipment and hidden storage instead of receiving a guessed loadout.
        if (mob.getType() == EntityType.PILLAGER
                && !mob.isHolding(stack -> stack.getItem() instanceof CrossbowItem))
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        else if (mob.getType() == EntityType.VINDICATOR)
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        else if (mob instanceof AbstractSkeleton skeleton && earlyEquipmentArcher(mob)) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            skeleton.reassessWeaponGoal();
        }
    }

    /** Fill paths that never call the native armor hook, after source weapons/fixed sets are initialized. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob)) return;
        retireEarlyEquipmentLock(mob);
        if (event.loadedFromDisk() && mob.getType().getCategory() == MobCategory.MONSTER)
            markLegacyEntity(mob, "existing_entity");
        // Piglins finalize their armor in their own native spawn methods.
        if (PiglinArmor.handles(mob) || !handles(mob)) return;
        if (event.loadedFromDisk()) {
            // Mark untouched old entities as already decided, without changing any equipment.
            // The marker also prevents an upgrade if that old entity later crosses dimensions.
            markLegacyEntity(mob, "existing_entity");
            CompoundTag equipment = mob.getPersistentData().getCompound(DATA_KEY);
            if (equipment.getInt("version") == VERSION && !equipment.contains("held")) heldResult(equipment, "existing_entity");
            return;
        }
        if (!populate(mob)) markLegacyEntity(mob, "legacy_finalized");
        populateHeld(mob);
    }

    private static void markLegacyEntity(Mob mob, String reason) {
        if (mob.getPersistentData().contains(DATA_KEY)) return;
        var existing = new CompoundTag();
        existing.putInt("version", VERSION); existing.putInt("stage", -1); existing.putInt("tier", -1);
        existing.putDouble("progress", -1D);
        if (mob.level() instanceof ServerLevel level) existing.putLong("highest_day", BloodMoonInvasion.highestDay(level));
        existing.putBoolean(reason, true); mob.getPersistentData().put(DATA_KEY, existing);
    }
}

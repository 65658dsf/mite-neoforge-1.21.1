package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.config.MonsterArmorSettings;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.BloodMoonEquipmentRules;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/** Spawn-only shared highest-date materials above the fixed source equipment floor. @author NingMeng123 */
public final class SpecialMobEquipment {
    public static final String DATA_KEY = "mite:special_equipment_progression";
    private static final String[] MATERIALS = {"leather", "iron", "gold", "copper", "silver", "iron",
            "ancient_metal", "mithril", "adamantium", "vibranium"};
    private SpecialMobEquipment() {}

    public static boolean handles(Mob mob) {
        if (!(mob instanceof R196Mob source)) return false;
        return switch (source.miteKind()) {
            case BONE_LORD, LONGDEAD_GUARDIAN, ANCIENT_BONE_LORD, REVENANT, LONGDEAD -> true;
            default -> false;
        };
    }

    /** Select the final item first, then perform the source enchantment/quality/wear generation exactly once. */
    public static ItemStack generated(Mob mob, String sourceId, boolean enchant) {
        return R196MobEquipment.generated(mob, itemId(mob, sourceId), enchant);
    }

    public static String itemId(Mob mob, String sourceId) {
        if (!handles(mob) || !(mob.level() instanceof ServerLevel level)) return sourceId;
        ItemStack source = new ItemStack(R196MobEquipment.item(sourceId));
        var armor = MiteArmorContent.definition(source.getItem());
        var tool = MiteTools.definition(source);
        boolean bow = sourceId.equals("mite:ancient_metal_bow") || sourceId.equals("mite:mithril_bow");
        if (armor == null && tool == null && !bow) return sourceId;
        String floorMaterial = armor != null ? armor.material() : tool != null ? tool.material()
                : sourceId.equals("mite:mithril_bow") ? "mithril" : "ancient_metal";
        int floorTier = BloodMoonEquipmentRules.materialTier(floorMaterial);
        if (floorTier < 0) return sourceId;

        CompoundTag progress = progress(mob, level);
        double value = progress.getDouble("progress");
        if (value < 0D) return sourceId;
        int tier = progress.getBoolean("blood_date")
                ? BloodMoonEquipmentRules.selectTier((int)value, true, mob.getRandom().nextDouble())
                : BloodMoonEquipmentRules.selectOrdinaryTier(value, mob.getRandom().nextDouble());
        if (BloodMoonEquipmentRules.materialGrade(tier) <= BloodMoonEquipmentRules.materialGrade(floorTier)) return sourceId;

        // Adamantium bows and Vibranium chainmail do not exist; retain the highest registered family member.
        if (bow) return tier == 9 ? "mite:vibranium_bow" : "mite:mithril_bow";
        String material = MATERIALS[tier];
        if (armor != null && armor.chainmail() && material.equals("vibranium")) material = "adamantium";
        final String selectedMaterial = material;
        // Native iron plate armor has no R196 registration entry; its IDs are retained by the modern adapter.
        if (armor != null && !armor.chainmail() && material.equals("iron"))
            return "minecraft:iron_" + armor.type().getName();
        if (armor != null) return MiteArmorContent.definitions().stream()
                .filter(candidate -> !candidate.horse() && candidate.material().equals(selectedMaterial)
                        && candidate.chainmail() == armor.chainmail() && candidate.type() == armor.type())
                .map(MiteArmorContent.Definition::id).findFirst().orElse(sourceId);
        return MiteTools.definitions().stream()
                .filter(candidate -> candidate.material().equals(selectedMaterial) && candidate.family() == tool.family())
                .map(MiteTools.Definition::id).findFirst().orElse(sourceId);
    }

    /** Every piece of this newborn mob uses the same date snapshot; saved mobs never enter this generator. */
    private static CompoundTag progress(Mob mob, ServerLevel level) {
        CompoundTag data = mob.getPersistentData();
        if (data.contains(DATA_KEY)) return data.getCompound(DATA_KEY);
        long day = BloodMoonInvasion.highestDay(level);
        boolean blood = BloodMoonEquipment.bloodDate(mob);
        int stage = MonsterArmorSettings.localSettings().stageForDay(day);
        CompoundTag result = new CompoundTag();
        result.putInt("version", 1);
        result.putLong("highest_day", day);
        result.putBoolean("blood_date", blood);
        result.putDouble("progress", stage);
        data.put(DATA_KEY, result);
        return result;
    }
}

package io.github.ningmeng123.mite.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;

/** Scales natural monster equipment drops while preserving picked-up equipment. @author NingMeng123 */
public final class MobEquipmentDrops {
    public static final int NATURAL_DROP_DIVISOR = 3;

    private MobEquipmentDrops() {}

    /** Use the original slot chance: values above one mark guaranteed, including picked-up, gear. */
    public static boolean keep(Mob mob, float originalDropChance) {
        return mob.getType().getCategory() != MobCategory.MONSTER || originalDropChance > 1F
                || mob.getRandom().nextInt(NATURAL_DROP_DIVISOR) == 0;
    }
}

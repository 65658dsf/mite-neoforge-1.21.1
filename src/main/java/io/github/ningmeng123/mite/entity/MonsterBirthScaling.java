package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** Retain the combat bonus of a monster born in the Nether or Underworld. @author NingMeng123 */
public final class MonsterBirthScaling {
    private static final String BIRTH_BONUS = "mite:hostile_dimension_birth";
    private static final double MULTIPLIER = 1.5D;
    private static final ResourceLocation MODIFIER = ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "hostile_dimension_birth");
    private static final List<Holder<Attribute>> COMBAT_ATTRIBUTES = List.of(
            Attributes.MOVEMENT_SPEED, Attributes.FOLLOW_RANGE, Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS,
            Attributes.KNOCKBACK_RESISTANCE, Attributes.ATTACK_SPEED, Attributes.ATTACK_KNOCKBACK);

    private MonsterBirthScaling() {}

    static void join(Mob mob, ServerLevel level, boolean loadedFromDisk) {
        if (!MonsterDayScaling.handles(mob)) return;
        var data = mob.getPersistentData();
        if (!data.contains(BIRTH_BONUS)) {
            // Record neutral origins as well so later dimension joins do not change this decision.
            var dimension = level.dimension();
            data.putBoolean(BIRTH_BONUS, !loadedFromDisk && (dimension.equals(Level.NETHER)
                    || dimension.equals(MiteWorldDimensions.UNDERWORLD)));
        }
        if (!data.getBoolean(BIRTH_BONUS)) return;
        for (var key : COMBAT_ATTRIBUTES) {
            var attribute = mob.getAttribute(key);
            if (attribute != null && !attribute.hasModifier(MODIFIER))
                attribute.addPermanentModifier(new AttributeModifier(MODIFIER, MULTIPLIER - 1D,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    static double multiplier(Mob mob) {
        return mob.getPersistentData().getBoolean(BIRTH_BONUS) ? MULTIPLIER : 1D;
    }

    public static double damageMultiplier(Mob mob) {
        return MiteExtensions.policy().scalesMonsterDamage(mob) ? multiplier(mob) : 1D;
    }
}

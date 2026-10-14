package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Source equipment helper reads the existing forced/picked-up probabilities. @author NingMeng123 */
@Mixin(Mob.class)
public interface SourceMobDropChanceAccess {
    @Invoker("getEquipmentDropChance") float mite$equipmentDropChance(EquipmentSlot slot);
}

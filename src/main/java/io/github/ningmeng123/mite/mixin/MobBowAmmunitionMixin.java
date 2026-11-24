package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.MonsterDayScaling;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.R196RangedRules;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Upgrade a hostile bow shot's ammunition before the arrow takes its pickup stack. @author NingMeng123 */
@Mixin(ArrowItem.class)
public abstract class MobBowAmmunitionMixin {
    @ModifyVariable(method = "createArrow", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ItemStack mite$calendarAmmunition(ItemStack ammunition, Level level, ItemStack original,
            LivingEntity shooter, @Nullable ItemStack weapon) {
        if (!(level instanceof ServerLevel server) || !(shooter instanceof Mob mob) || !MonsterDayScaling.handles(mob)
                || weapon == null || !(weapon.getItem() instanceof BowItem)) return ammunition;
        var nativeMaterial = MiteRangedItems.arrowMaterial(ammunition);
        if (nativeMaterial == null) return ammunition;
        var upgraded = R196RangedRules.monsterArrow(MonsterDayScaling.currentDay(server), nativeMaterial);
        return upgraded == nativeMaterial ? ammunition : ammunition.transmuteCopy(MiteRangedItems.arrow(upgraded));
    }
}

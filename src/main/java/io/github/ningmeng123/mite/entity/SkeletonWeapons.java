package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import javax.annotation.Nullable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

/** Ranged skeletons may collect a sword without discarding their bow. @author NingMeng123 */
public final class SkeletonWeapons {
    private SkeletonWeapons() {}

    public static boolean melee(ItemStack stack) {
        if (stack.getItem() instanceof SwordItem) return true;
        var tool = MiteTools.definition(stack);
        return tool != null && (tool.family() == ToolFamily.SWORD || tool.family() == ToolFamily.DAGGER);
    }

    public static boolean eligible(AbstractSkeleton skeleton) {
        if (skeleton instanceof R196Skeleton old)
            return old.miteKind() == MobKind.LONGDEAD && old.skeletonType() == 0;
        if (skeleton.getType() == EntityType.SKELETON)
            return ((OrdinarySkeletonState)skeleton).mite$sourceSkeletonType() == 0;
        return false;
    }

    @Nullable public static EquipmentSlot pickupSlot(AbstractSkeleton skeleton, ItemStack incoming) {
        if (!eligible(skeleton)) return null;
        ItemStack main = skeleton.getMainHandItem(), spare = skeleton.getOffhandItem();
        if (main.getItem() instanceof BowItem && melee(incoming)
                && (spare.isEmpty() || melee(spare))) return EquipmentSlot.OFFHAND;
        if (melee(main) && spare.getItem() instanceof BowItem
                && incoming.getItem() instanceof BowItem) return EquipmentSlot.OFFHAND;
        if (spare.getItem() instanceof BowItem && melee(incoming) && (main.isEmpty() || melee(main))) return EquipmentSlot.MAINHAND;
        return null;
    }

    public static boolean shouldSwap(AbstractSkeleton skeleton) {
        if (!eligible(skeleton)) return false;
        ItemStack main = skeleton.getMainHandItem(), spare = skeleton.getOffhandItem();
        if (main.isEmpty()) return spare.getItem() instanceof BowItem || melee(spare);
        boolean bow = main.getItem() instanceof BowItem;
        if (bow ? !melee(spare) : !melee(main) || !(spare.getItem() instanceof BowItem)) return false;
        LivingEntity target = skeleton.getTarget();
        if (target == null || !target.isAlive()) return !bow;
        if (!skeleton.getSensing().hasLineOfSight(target)) return false;
        double distance = skeleton.distanceToSqr(target);
        return bow ? distance < 25D : distance > 36D;
    }
}

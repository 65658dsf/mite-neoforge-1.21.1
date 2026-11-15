package io.github.ningmeng123.mite.survival;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/** ang protects careful mobs unless standing on, crowded against, or overlapping solid blocks. @author NingMeng123 */
public final class SourceCactusContact {
    private SourceCactusContact() {}

    public static boolean canHurt(Entity entity) {
        // R196 isEntityLiving means og (modern Mob), while players extend of directly.
        if (!(entity instanceof Mob mob) || mob.getDeltaMovement().length() > (double) .2F) return true;
        if (mob.onGround() && !mob.isPassenger() && mob.getBlockStateOn().is(Blocks.CACTUS)) return true;
        if (!mob.level().getEntities(mob, mob.getBoundingBox().inflate(.2D, 0D, .2D),
                EntitySelector.pushableBy(mob)).isEmpty()) return true;
        return mob.level().getBlockCollisions(mob, mob.getBoundingBox()).iterator().hasNext();
    }
}

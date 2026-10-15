package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** og.getMaxTargettingRange uses the source recently-hit timer, not hurt animation. @author NingMeng123 */
@Mixin(LivingEntity.class)
public interface SourceMonsterLivingAccess {
    @Accessor("lastHurtByPlayerTime") int mite$recentlyHit();
}

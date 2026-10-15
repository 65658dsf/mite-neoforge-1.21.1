package io.github.ningmeng123.mite.entity;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;

/** Transient og target UUID and AI_retarget fields; serialized by the Mob bridge. @author NingMeng123 */
public interface SourceMonsterTargetMemory {
    @Nullable UUID mite$pendingTarget();
    void mite$pendingTarget(@Nullable UUID uuid);
    @Nullable LivingEntity mite$retarget();
    void mite$retarget(@Nullable LivingEntity target);
}

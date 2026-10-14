package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Preserve NeoForge spawn metadata when source finalization replaces a modern parent's spawn algorithm. @author NingMeng123 */
@Mixin(Mob.class)
public interface MobSpawnTypeAccessor {
    @Accessor("spawnType") void mite$setSpawnType(MobSpawnType reason);
}

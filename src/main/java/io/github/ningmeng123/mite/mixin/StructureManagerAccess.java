package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Resolve each generation request's own world without a global current-server reference. @author NingMeng123 */
@Mixin(StructureManager.class)
public interface StructureManagerAccess {
    @Accessor("level") LevelAccessor mite$structureLevel();
}

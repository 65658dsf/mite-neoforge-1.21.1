package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.MantleHeat;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Treat boundary contact as ongoing fire for the native negative ignition counter. @author NingMeng123 */
@Mixin(Entity.class)
public abstract class MantleFireContactMixin {
    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;noneMatch(Ljava/util/function/Predicate;)Z"))
    private boolean mite$keepIgnitionCounter(Stream<BlockState> blocks, Predicate<BlockState> outsideFire) {
        if (MantleHeat.touchesHotBoundary((Entity)(Object)this)) { blocks.close(); return false; }
        return blocks.noneMatch(outsideFire);
    }
}

package io.github.ningmeng123.mite.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Main-thread recovery must also exclude unpublished and not-yet-saved chunks. @author NingMeng123 */
@Mixin(ChunkMap.class)
public interface ChunkMapAccess {
    @Accessor("updatingChunkMap") Long2ObjectLinkedOpenHashMap<ChunkHolder> mite$updatingChunks();
    @Accessor("pendingUnloads") Long2ObjectLinkedOpenHashMap<ChunkHolder> mite$pendingUnloads();
}

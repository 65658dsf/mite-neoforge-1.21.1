package io.github.ningmeng123.mite.crafting;

import net.minecraft.world.level.block.Block;

/** Current world-backed bench capacity; re-read at extraction time. @author NingMeng123 */
public interface WorkbenchAccess {
    float mite$workbenchCapacity();
    boolean mite$isWorkbench(Block block);
}

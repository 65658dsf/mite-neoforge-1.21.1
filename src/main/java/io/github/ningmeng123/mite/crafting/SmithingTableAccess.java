package io.github.ningmeng123.mite.crafting;

import javax.annotation.Nullable;

/** Workstation that opened the native smithing menu, synced to its client. @author NingMeng123 */
public interface SmithingTableAccess {
    @Nullable
    SmithingTableTier mite$smithingTableTier();
}

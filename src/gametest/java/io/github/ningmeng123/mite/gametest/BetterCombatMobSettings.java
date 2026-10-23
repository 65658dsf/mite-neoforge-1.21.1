package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.config.MiteConfig;

/** Synchronous scope that restores both the local flag and any connected server view. @author NingMeng123 */
public final class BetterCombatMobSettings implements AutoCloseable {
    private final boolean enabled = MiteConfig.BETTER_COMBAT_MOBS.get();
    private final MiteConfig.Snapshot remote = MiteConfig.effectiveSnapshot() == MiteConfig.localSnapshot()
            ? null : MiteConfig.effectiveSnapshot();

    public BetterCombatMobSettings(boolean enabled) {
        MiteConfig.clearServerSnapshot();
        MiteConfig.BETTER_COMBAT_MOBS.set(enabled);
        MiteConfig.refreshEnchantmentRules();
    }

    @Override public void close() {
        MiteConfig.BETTER_COMBAT_MOBS.set(enabled);
        MiteConfig.refreshEnchantmentRules();
        MiteConfig.clearServerSnapshot();
        if (remote != null) MiteConfig.receiveServerSnapshot(remote);
    }
}

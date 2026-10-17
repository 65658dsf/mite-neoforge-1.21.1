package io.github.ningmeng123.mite.crafting;

/** Read-only current anvil durability for both the physical server menu and its remote view. @author NingMeng123 */
public interface AnvilDurabilityAccess {
    int mite$anvilRemainingDurability();
    int mite$anvilMaximumDurability();
}

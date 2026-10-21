package io.github.ningmeng123.mite.enchantment;

/** Server quotes are synchronized separately from vanilla offer levels. @author NingMeng123 */
public interface R196EnchantmentMenuAccess {
    boolean mite$usesSourceEnchanting();
    int mite$experienceCost(int slot);
}

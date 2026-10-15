package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.UnderworldVaultLoot;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Migrates existing and new underworld vaults without resetting rewards, pending items or delays. @author NingMeng123 */
@Mixin(VaultBlockEntity.class)
public abstract class UnderworldVaultLootMixin {
    @Shadow private VaultConfig config;

    @Unique private void mite$normalizeVaultLoot() {
        var vault = (VaultBlockEntity)(Object)this;
        VaultConfig replacement = UnderworldVaultLoot.remapConfig(vault.getLevel(), config);
        if (replacement != config) {
            config = replacement;
            vault.setChanged();
        }
    }

    // Both the server ticker and key insertion read getConfig before passing it to VaultBlockEntity.Server.
    // Display cycling therefore receives exactly the same migrated config as the eventual reward roll.
    @Inject(method = "getConfig", at = @At("HEAD"))
    private void mite$underworldRewardConfiguration(CallbackInfoReturnable<VaultConfig> cir) {
        mite$normalizeVaultLoot();
    }

    // A loaded vault can be saved before its first tick or key insertion. Persist the same migration there.
    @Inject(method = "saveAdditional", at = @At("HEAD"))
    private void mite$saveUnderworldRewardConfiguration(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        mite$normalizeVaultLoot();
    }
}

package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.crafting.AnvilDurability;
import io.github.ningmeng123.mite.crafting.AnvilDurabilityAccess;
import io.github.ningmeng123.mite.furnace.ThermalDataSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native menu packets carry signed shorts, so each read-only durability value uses two words. @author NingMeng123 */
@Mixin(AnvilMenu.class)
public abstract class AnvilDurabilityMenuMixin extends ItemCombinerMenu implements AnvilDurabilityAccess {
    @Unique private final int[] mite$durabilityWords = new int[4];

    protected AnvilDurabilityMenuMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) {
        super(type,id,inventory,access);
    }

    // FunctionalAnvilMixin installs sourceMode at the default injector order. Keep its established
    // cost at data index0 and sourceMode at index1; append our words after that callback.
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"), order = 1100)
    private void mite$addDurabilityData(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        for (int index=0;index<4;index++) addDataSlot(mite$durabilityWord(index));
    }

    @Unique private DataSlot mite$durabilityWord(int index) {
        return new ThermalDataSlot(
            () -> {
                int value = index < 2 ? mite$anvilRemainingDurability() : mite$anvilMaximumDurability();
                return (value >>> ((index & 1) * 16)) & 0xFFFF;
            },
            value -> {
                // setData is the native remote-menu receive path; it never changes physical anvil wear.
                mite$durabilityWords[index] = value & 0xFFFF;
            });
    }

    @Override public int mite$anvilRemainingDurability() {
        if (access == ContainerLevelAccess.NULL)
            return Math.clamp(mite$joinedDurability(0),0,mite$anvilMaximumDurability());
        return access.evaluate((level,pos) -> {
            MaterialAnvilBlockEntity anvil = AnvilDurability.entity(level,pos);
            if (anvil == null) return 0;
            return AnvilDurability.of(anvil).remaining();
        },0);
    }

    @Override public int mite$anvilMaximumDurability() {
        if (access == ContainerLevelAccess.NULL) return Math.max(mite$joinedDurability(2),0);
        // Re-resolve the current block entity: native damage stages can replace the iron block identity.
        return access.evaluate((level,pos) -> {
            MaterialAnvilBlockEntity anvil = AnvilDurability.entity(level,pos);
            return anvil == null ? 0 : AnvilDurability.of(anvil).maximum();
        },0);
    }

    @Unique private int mite$joinedDurability(int firstWord) {
        return mite$durabilityWords[firstWord] | (mite$durabilityWords[firstWord+1] << 16);
    }
}

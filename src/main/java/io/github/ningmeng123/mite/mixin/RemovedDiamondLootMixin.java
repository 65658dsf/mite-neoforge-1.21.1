package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.world.DiamondEquipmentRemoval;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Filter after every global modifier, including nested chest, vault and equipment tables. @author NingMeng123 */
@Mixin(LootTable.class)
public abstract class RemovedDiamondLootMixin {
    @Inject(method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At("RETURN"))
    private void mite$removeDiamondLoot(LootContext context, CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir) {
        var loot = cir.getReturnValue();
        loot.replaceAll(DiamondEquipmentRemoval::clean);
        loot.removeIf(ItemStack::isEmpty);
    }
}

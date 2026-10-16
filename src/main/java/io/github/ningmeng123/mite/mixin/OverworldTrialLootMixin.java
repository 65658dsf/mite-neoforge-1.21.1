package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.ningmeng123.mite.loot.OverworldTrialLootBalance;
import io.github.ningmeng123.mite.loot.ReducedTrialLoot;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Apply once after nested tables and global loot modifiers have finished the outer reward. @author NingMeng123 */
@Mixin(LootTable.class)
public abstract class OverworldTrialLootMixin {
    @ModifyReturnValue(method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At("RETURN"))
    private ObjectArrayList<ItemStack> mite$reduceOverworldTrialGems(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (OverworldTrialLootBalance.reduce(loot, context.getLevel(),
                ((LootTable)(Object)this).getLootTableId(), context.getRandom())) {
            return new ReducedTrialLoot(loot);
        }
        return loot;
    }
}

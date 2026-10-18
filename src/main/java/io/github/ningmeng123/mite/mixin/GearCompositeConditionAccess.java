package io.github.ningmeng123.mite.mixin;

import java.util.List;
import net.minecraft.world.level.storage.loot.predicates.CompositeLootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Inspect transparent native predicate trees before admitting an affix candidate. @author NingMeng123 */
@Mixin(CompositeLootItemCondition.class)
public interface GearCompositeConditionAccess {
    @Accessor("terms") List<LootItemCondition> mite$terms();
}

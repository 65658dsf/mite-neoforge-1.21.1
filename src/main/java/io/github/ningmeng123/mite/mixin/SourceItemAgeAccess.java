package io.github.ningmeng123.mite.mixin;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Preserves the source remaining food stack lifetime after an NPC takes one item. @author NingMeng123 */
@Mixin(ItemEntity.class)
public interface SourceItemAgeAccess { @Accessor("age") void mite$setAge(int age); }

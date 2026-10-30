package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.rules.StackingRules;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/** One final source limit for registry defaults and every server/client stack operation. @author NingMeng123 */
public final class MiteItemStacking {
    private static volatile Map<Item, Integer> limits = Map.of();
    private MiteItemStacking() {}

    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, MiteItemStacking::defaults);
    }

    private static void defaults(ModifyDefaultComponentsEvent event) {
        Map<Item, Integer> resolved = new IdentityHashMap<>();
        StackingRules.limits().forEach((id, maximum) -> {
            Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))
                    .orElseThrow(() -> new IllegalStateException("Stacking table references unregistered item " + id));
            if (item == Items.AIR) throw new IllegalStateException("Air is not a carried stacking identity");
            event.modify(item, patch -> patch.set(DataComponents.MAX_STACK_SIZE, maximum));
            resolved.put(item, maximum);
        });
        // Leaf families supplied by addons need the same default and transfer ceiling,
        // even when their identity was not present in the frozen source table.
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof LeavesBlock) {
                event.modify(item, patch -> patch.set(DataComponents.MAX_STACK_SIZE, 8));
                resolved.put(item, 8);
            }
        }
        limits = Collections.unmodifiableMap(resolved);
    }

    /** Zero means unmanaged; non-leaf foreign items keep their own component limits. */
    public static int limit(ItemStack stack) {
        int maximum = limits.getOrDefault(stack.getItem(), 0);
        if (maximum > 0 && stack.is(Items.SNOW)) {
            var state = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
            Integer layers = state.get(BlockStateProperties.LAYERS);
            maximum = StackingRules.snowLimit(layers == null ? 1 : layers);
        }
        return maximum;
    }
}

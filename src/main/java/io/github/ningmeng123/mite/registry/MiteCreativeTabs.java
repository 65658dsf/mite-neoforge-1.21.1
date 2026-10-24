package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.block.EmeraldEnchantingTableBlock;
import io.github.ningmeng123.mite.block.MaterialAnvilBlock;
import io.github.ningmeng123.mite.block.MaterialWorkbenchBlock;
import io.github.ningmeng123.mite.block.R196FurnaceBlock;
import io.github.ningmeng123.mite.block.RunestoneBlock;
import io.github.ningmeng123.mite.block.StrongboxBlock;
import io.github.ningmeng123.mite.rules.FunctionalBlockRules;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.item.GelatinousSphereItem;
import io.github.ningmeng123.mite.item.R196DisenchantingBottleItem;
import io.github.ningmeng123.mite.item.R196RecordItem;
import io.github.ningmeng123.mite.item.R196StoneBucketItem;
import io.github.ningmeng123.mite.item.RunestoneItem;
import java.util.Comparator;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FoodOnAStickItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One home for every added item, including component variants and the retained club ID. @author NingMeng123 */
public final class MiteCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Mite.MOD_ID);

    public enum Group {
        MATERIALS("materials", "silver_ingot"),
        TOOLS("tools", "adamantium_pickaxe"),
        COMBAT("combat", "mithril_sword"),
        FOODS("foods", "cheese"),
        FUNCTIONAL_BLOCKS("functional_blocks", "flint_crafting_table"),
        SPAWN_EGGS("spawn_eggs", "infernal_creeper_spawn_egg");

        private final String path;
        private final String icon;
        Group(String path, String icon) { this.path = path; this.icon = icon; }
        public ResourceKey<CreativeModeTab> key() {
            return ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, path));
        }
    }

    static {
        for (Group group : Group.values()) {
            TABS.register(group.path, () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mite." + group.path))
                    .icon(() -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, group.icon))))
                    .withTabsBefore(group == Group.MATERIALS ? CreativeModeTabs.SPAWN_EGGS : Group.values()[group.ordinal() - 1].key())
                    .displayItems((parameters, output) -> display(group, parameters, output)).build());
        }
    }

    private MiteCreativeTabs() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
        bus.addListener(EventPriority.LOWEST, MiteCreativeTabs::removeFromVanilla);
    }

    private static boolean owned(Item item) {
        return item == Items.WOODEN_SWORD || BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Mite.MOD_ID);
    }

    /** Vanilla items shared with modern Minecraft keep their own identity; only the renamed club is moved. */
    @Nullable public static Group group(Item item) {
        if (!owned(item)) return null;
        if (io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat.definition(new ItemStack(item)) != null)
            return Group.COMBAT;
        if (item instanceof SpawnEggItem) return Group.SPAWN_EGGS;
        if (item instanceof io.github.ningmeng123.mite.phantom.PhantomBladeItem) return Group.COMBAT;
        if (item instanceof R196StoneBucketItem || item == MiteFoodPlants.BLUEBERRY_BUSH_ITEM.get()
                || MiteFoods.entries().values().stream().anyMatch(entry -> entry.get() == item)) return Group.FOODS;
        var tool = MiteTools.definition(new ItemStack(item));
        if (tool != null) return switch (tool.family()) {
            case SWORD, DAGGER, BATTLE_AXE, WAR_HAMMER, CLUB, CUDGEL -> Group.COMBAT;
            default -> Group.TOOLS;
        };
        if (item instanceof ArmorItem || item instanceof AnimalArmorItem || item instanceof ArrowItem
                || item instanceof BowItem || item instanceof ShieldItem || item instanceof GelatinousSphereItem) return Group.COMBAT;
        if (item instanceof FishingRodItem || item instanceof FoodOnAStickItem<?>
                || item instanceof R196RecordItem || item instanceof R196DisenchantingBottleItem) return Group.TOOLS;
        if (item instanceof BlockItem blockItem) {
            var block = blockItem.getBlock();
            if (block instanceof MaterialWorkbenchBlock || block instanceof R196FurnaceBlock
                    || block instanceof MaterialAnvilBlock || block instanceof StrongboxBlock
                    || block instanceof EmeraldEnchantingTableBlock || block instanceof RunestoneBlock
                    || block instanceof DoorBlock || block instanceof IronBarsBlock) return Group.FUNCTIONAL_BLOCKS;
            return Group.MATERIALS;
        }
        return Group.MATERIALS;
    }

    private static void display(Group group, CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR && item.isEnabled(parameters.enabledFeatures()))
                .filter(item -> group(item) == group)
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).getPath()))
                .forEach(item -> variants(item, output));
    }

    private static void variants(Item item, CreativeModeTab.Output output) {
        if (item instanceof RunestoneItem) {
            for (int rune = 0; rune < 16; rune++) output.accept(RunestoneItem.withRune(new ItemStack(item), rune));
            return;
        }
        output.accept(item);
        if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof MaterialAnvilBlock anvil) {
            int durability = FunctionalBlockRules.anvilDurability(WorkbenchRules.materialCapacity(anvil.material()));
            for (int stage = 1; stage <= 2; stage++) {
                ItemStack stack = new ItemStack(item);
                stack.set(MiteFunctionalBlocks.ANVIL_DAMAGE.get(), FunctionalBlockRules.minimumDamage(stage, durability));
                output.accept(stack);
            }
        }
    }

    private static void removeFromVanilla(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().getNamespace().equals("minecraft") || event.getTab().getType() != CreativeModeTab.Type.CATEGORY) return;
        // Search is rebuilt from all category search entries; never strip its independent MITE results.
        Stream.concat(event.getParentEntries().stream(), event.getSearchEntries().stream()).filter(stack -> owned(stack.getItem()))
                .toList().forEach(stack -> event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
    }
}

package io.github.ningmeng123.mite.integration.jei;

import io.github.ningmeng123.mite.client.config.MiteConfigClient;
import io.github.ningmeng123.mite.config.GravelDropSettings;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import jeresources.api.drop.LootDrop;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;

/** The eight native JER world-generation output slots index potential drops across settings edits. @author NingMeng123 */
public final class GravelJerDrops {
    private static final int SLOTS_PER_PAGE = 8;
    private GravelJerDrops() {}

    public static List<Page> pages(boolean nether, boolean wholeFlint) {
        var possible = GravelDropSettings.defaults(wholeFlint);
        var products = (nether ? possible.nether() : possible.ordinary()).keySet().stream().map(id -> {
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
            stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("mite.jer.gravel_tool"))));
            return stack;
        }).toList();
        var pages = new ArrayList<Page>();
        for (int first = 0; first < products.size(); first += SLOTS_PER_PAGE) {
            pages.add(new Page(nether, products.subList(first, Math.min(first + SLOTS_PER_PAGE, products.size()))));
        }
        return List.copyOf(pages);
    }

    public record Page(boolean nether, List<ItemStack> products) {
        public Page {
            products = List.copyOf(products);
        }

        public List<ItemStack> getDrops() { return products; }

        public List<LootDrop> getLootDrops(ItemStack stack) {
            var rates = MiteConfigClient.gravelDropRates();
            if (rates == null || stack.isEmpty()) return List.of();
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!(nether ? rates.nether() : rates.ordinary()).containsKey(id)) return List.of();

            var player = Minecraft.getInstance().player;
            boolean silk = false;
            int normal = 0, affix = 0;
            if (player != null && !player.getMainHandItem().isEmpty()) {
                var tool = player.getMainHandItem();
                var enchantments = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
                silk = tool.getEnchantmentLevel(enchantments.getHolderOrThrow(Enchantments.SILK_TOUCH)) > 0;
                var fortune = EnchantmentSources.levels(tool, enchantments.getHolderOrThrow(Enchantments.FORTUNE));
                normal = fortune.normal();
                affix = fortune.affix();
            }
            double percent = rates.harvestPercent(nether, id, normal, affix, silk);
            return List.of(new GravelLootDrop(stack, percent, silk ? 0 : Math.max(normal, affix)));
        }
    }

    /** JER's default one-decimal percentage would show several attainable MITE minerals as 0.0%. */
    private static final class GravelLootDrop extends LootDrop {
        private static final MathContext PRECISION = new MathContext(8);
        private final double percent;

        GravelLootDrop(ItemStack stack, double percent, int fortune) {
            super(stack, 1, 1, percent < 100D ? Math.min(Math.nextDown(1F), (float) (percent / 100D)) : 1F, fortune);
            this.percent = percent;
        }

        @Override public String formatChance() {
            BigDecimal value = BigDecimal.valueOf(percent);
            BigDecimal rounded = value.round(PRECISION).stripTrailingZeros();
            if (percent < 100D && rounded.doubleValue() == 100D) return value.toPlainString();
            return rounded.scale() > 16 ? rounded.toString() : rounded.toPlainString();
        }
    }
}

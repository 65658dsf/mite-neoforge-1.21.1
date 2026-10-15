package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Source support stays exact while modern-only equipment retains its actual native metadata. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ModernEnchantmentCompatibilityGameTests {
    private ModernEnchantmentCompatibilityGameTests() {}

    @GameTest(template = "empty")
    public static void modernDevicesRetainNativeSharedEnchantmentsWithoutEnteringTheSourcePool(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var unbreaking = registry.getHolderOrThrow(Enchantments.UNBREAKING).value();
        for (Item item : new Item[]{Items.CROSSBOW, Items.TRIDENT, Items.MACE, Items.SHIELD, Items.ELYTRA}) {
            ItemStack stack = new ItemStack(item);
            helper.assertTrue(unbreaking.canEnchant(stack) && unbreaking.isPrimaryItem(stack), "Native durability support and table eligibility survive: " + item);
            helper.assertTrue(!R196EnchantmentCatalog.supported("unbreaking", stack), "Modern-only devices remain outside source equipment selection: " + item);
        }
        ItemStack mace = new ItemStack(Items.MACE);
        for (var key : java.util.List.of(Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS, Enchantments.FIRE_ASPECT)) {
            var enchantment = registry.getHolderOrThrow(key).value();
            helper.assertTrue(enchantment.canEnchant(mace), "Native mace secondary support survives: " + key.location());
            helper.assertTrue(!enchantment.isPrimaryItem(mace), "Native mace anvil-only status survives: " + key.location());
        }
        helper.assertTrue(!registry.getHolderOrThrow(Enchantments.SHARPNESS).value().canEnchant(mace), "The compatibility union does not invent native sharpness support for maces");
        helper.succeed();
    }
}

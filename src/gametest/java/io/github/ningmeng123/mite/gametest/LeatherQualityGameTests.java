package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.LeatherMendingRecipe;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Production result-slot consumption, preserved armor components, and R196 repair/dye distinctions. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class LeatherQualityGameTests {
    private LeatherQualityGameTests() {}

    @GameTest(template = "empty")
    public static void stringRepairConsumesSevenThenOneForTheFinalPartialStep(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 0)) {
            ItemStack chest = new ItemStack(Items.LEATHER_CHESTPLATE);
            chest.setDamageValue(15);
            helper.assertValueEqual(chest.getMaxDamage(), 16, "Ordinary leather chest armor has R196 maximum 16");
            helper.assertValueEqual(LeatherMendingRecipe.repairCost(chest), 8, "wh leather repair cost equals eight chest components");
            f.load(chest, new ItemStack(Items.STRING, 8));
            helper.assertValueEqual(LeatherMendingRecipe.materialConsumption(f.grid.asCraftInput()), 7,
                    "R196 leaves a partial final step for another batch rather than over-consuming the eighth thread");
            helper.assertValueEqual(LeatherMendingRecipe.difficulty(f.grid.asCraftInput()), 350F,
                    "Seven threads set the unmodified repair difficulty to 350");
            f.craft();
            helper.assertValueEqual(f.grid.getItem(1).getCount(), 1, "Actual ResultSlot consumes precisely seven strings");
            helper.assertTrue(f.grid.getItem(0).isEmpty(), "Only the one input armor item is consumed despite a seven-material operation");
            ItemStack once = f.takeOutput();
            helper.assertValueEqual(once.getDamageValue(), 1, "Seven ordinary threads repair fourteen damage");
            helper.assertValueEqual(EquipmentDurability.quality(once), Quality.AVERAGE, "Repair keeps ordinary quality");
            helper.assertValueEqual(f.player.totalExperience, 0, "Ordinary repair cannot introduce XP debt");

            ItemStack lastThread = f.grid.getItem(1).copy();
            f.load(once, lastThread);
            helper.assertValueEqual(LeatherMendingRecipe.materialConsumption(f.grid.asCraftInput()), 1,
                    "A single remaining damage permits one final thread");
            helper.assertValueEqual(LeatherMendingRecipe.difficulty(f.grid.asCraftInput()), 50F, "Final one-thread batch uses difficulty 50");
            f.craft();
            helper.assertTrue(f.grid.getItem(0).isEmpty() && f.grid.getItem(1).isEmpty(), "The final armor and string inputs are each consumed once");
            ItemStack repaired = f.takeOutput();
            helper.assertValueEqual(repaired.getDamageValue(), 0, "The second batch repairs the last durability point");
            f.load(repaired, new ItemStack(Items.STRING));
            helper.assertTrue(f.menu.getSlot(0).getItem().isEmpty(), "A fully repaired leather item cannot waste thread through another recipe");
            helper.assertFalse(TimedCraftingController.start(f.player, f.menu), "Pristine armor has no repair operation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sinewRepairPreservesFineQualityDyeAndEnchantmentsAndRequiresManufacturingAbility(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 319)) {
            ItemStack chest = decoratedFineChest(helper);
            chest.setDamageValue(7);
            f.load(chest, new ItemStack(MiteItems.SINEW.get(), 8));
            helper.assertValueEqual(chest.getMaxDamage(), 24, "Fine leather uses the real quality-adjusted maximum before computing repair step");
            helper.assertValueEqual(LeatherMendingRecipe.materialConsumption(f.grid.asCraftInput()), 2, "Fine leather step is 24/8=3; seven damage consumes two sinews first");
            helper.assertValueEqual(LeatherMendingRecipe.difficulty(f.grid.asCraftInput()), 100F, "Two-sinew repair starts with difficulty 100");
            helper.assertFalse(CraftingQuality.canRepair(f.player, chest), "Full manufacture D800 requires 320 XP for fine quality, despite free repair");
            helper.assertFalse(TimedCraftingController.start(f.player, f.menu), "319 XP cannot start a fine leather repair");
            f.tick(300);
            helper.assertValueEqual(f.grid.getItem(1).getCount(), 8, "Rejected repair leaves all sinews intact");
            helper.assertTrue(ItemStack.matches(f.grid.getItem(0), chest), "Rejected repair leaves all armor components intact");
            helper.assertValueEqual(f.player.totalExperience, 319, "Rejected repair charges nothing");
            helper.assertValueEqual(f.player.getInventory().countItem(Items.LEATHER_CHESTPLATE), 0, "Rejected repair cannot publish output");

            PlayerExperience.setTotal(f.player, 320);
            f.tick(1);
            helper.assertTrue(CraftingQuality.canRepair(f.player, chest), "Exactly 320 total XP meets the full-difficulty repair qualification");
            helper.assertValueEqual(f.player.experienceLevel, 6, "The eligibility fixture uses the verified R196 experience curve");
            helper.assertValueEqual(TimedCraftingController.period(f.player, f.menu), 106,
                    "D100 with fine quality is D200: base140/(1+6*.02+.2) truncates to106 ticks");
            f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
            helper.assertValueEqual(EquipmentDurability.quality(f.menu.getSlot(0).getItem()), Quality.FINE,
                    "Repair fixes the input quality; right click cannot upgrade or downgrade it");
            f.craft();
            helper.assertValueEqual(f.grid.getItem(1).getCount(), 6, "Real recipe completion removes exactly two sinews");
            ItemStack result = f.takeOutput();
            ItemStack expected = chest.copy();
            expected.setDamageValue(1);
            helper.assertTrue(ItemStack.matches(result, expected), "Repair changes only damage: quality, dye, enchantment, custom name and other components survive");
            helper.assertValueEqual(f.player.totalExperience, 320, "The XP balance proves repair's quality charge is exempt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dyeKeepsFineQualityAtZeroXpAndUsesQualityAdjustedTimeWithoutRepairingDamage(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 0)) {
            ItemStack chest = decoratedFineChest(helper);
            f.load(chest, new ItemStack(Items.RED_DYE));
            helper.assertTrue(f.menu.getSlot(0).getItem().is(Items.LEATHER_CHESTPLATE), "The genuine vanilla leather dye recipe still matches");
            helper.assertValueEqual(EquipmentDurability.quality(f.menu.getSlot(0).getItem()), Quality.FINE,
                    "Dyeing preserves input quality even when this player cannot manufacture fine armor");
            helper.assertValueEqual(TimedCraftingController.period(f.player, f.menu), 116,
                    "R196 dye D100 becomes D200 for fine quality, then base140/1.2 truncates to116 ticks");
            f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
            helper.assertValueEqual(EquipmentDurability.quality(f.menu.getSlot(0).getItem()), Quality.FINE, "A dye recipe has a fixed quality override");
            f.craft();
            helper.assertTrue(f.grid.getItem(0).isEmpty() && f.grid.getItem(1).isEmpty(), "Dye completion consumes one armor and one dye");
            ItemStack dyed = f.takeOutput();
            helper.assertTrue(dyed.get(DataComponents.DYED_COLOR) != null, "The result retains a dye component");
            helper.assertTrue(dyed.get(DataComponents.DYED_COLOR).rgb() != chest.get(DataComponents.DYED_COLOR).rgb(), "The selected dye genuinely changes the armor color");
            ItemStack expected = chest.copy();
            expected.set(DataComponents.DYED_COLOR, dyed.get(DataComponents.DYED_COLOR));
            helper.assertTrue(ItemStack.matches(dyed, expected), "Dyeing changes only the dye component, preserving quality, enchantments and name");
            helper.assertValueEqual(f.player.totalExperience, 0, "Fine-quality dyeing never charges manufacturing XP");
            helper.assertValueEqual(dyed.getMaxDamage(), 24, "The existing fine maximum is not multiplied a second time");

            dyed.setDamageValue(1);
            f.load(dyed, new ItemStack(Items.BLUE_DYE));
            helper.assertFalse(TimedCraftingController.start(f.player, f.menu), "R196 damaged-input exemption is for repair; dyeing cannot accept damaged armor");
            f.tick(300);
            helper.assertValueEqual(f.grid.getItem(0).getDamageValue(), 1, "Rejected dye cannot repair its input");
            helper.assertValueEqual(f.grid.getItem(1).getCount(), 1, "Rejected dye preserves its material");
        }
        helper.succeed();
    }

    private static ItemStack decoratedFineChest(GameTestHelper helper) {
        ItemStack chest = new ItemStack(Items.LEATHER_CHESTPLATE);
        EquipmentDurability.setQuality(chest, Quality.FINE);
        chest.set(DataComponents.DYED_COLOR, new DyedItemColor(0x336699, false));
        chest.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 品质修补测试"));
        chest.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.VANISHING_CURSE), 1);
        return chest;
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final BlockPos table;
        private final BlockState original;
        private final CraftingMenu menu;
        private final CraftingContainer grid;

        private Fixture(GameTestHelper helper, int experience) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-leather"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, experience);
            player.getFoodData().setFoodLevel(6);
            player.removeAllEffects();
            table = helper.absolutePos(new BlockPos(1, 1, 1));
            original = helper.getLevel().getBlockState(table);
            helper.getLevel().setBlockAndUpdate(table, MiteWorkbenches.TABLES.get("flint").get().defaultBlockState());
            menu = new CraftingMenu(51, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
            grid = (CraftingContainer)menu.getSlot(1).container;
        }

        private void load(ItemStack armor, ItemStack material) {
            TimedCraftingController.cancel(player);
            grid.clearContent();
            menu.getSlot(1).set(armor.copy());
            menu.getSlot(2).set(material.copy());
            tick(1);
            helper.assertTrue(menu.stillValid(player), "The real crafting menu is attached to a placed material workbench");
        }

        private void craft() { TimedCraftingTestSupport.craftOne(helper, player, menu, ClickType.PICKUP); }

        private void tick(int count) {
            for (int tick = 0; tick < count; tick++) TimedCraftingController.tick(player);
        }

        private ItemStack takeOutput() {
            helper.assertValueEqual(player.getInventory().countItem(Items.LEATHER_CHESTPLATE), 1, "Exactly one armor result reaches the inventory");
            helper.assertTrue(menu.getCarried().isEmpty(), "Manufacturing does not duplicate armor onto the cursor");
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                ItemStack stack = player.getInventory().items.get(slot);
                if (!stack.is(Items.LEATHER_CHESTPLATE)) continue;
                ItemStack result = stack.copy();
                player.getInventory().items.set(slot, ItemStack.EMPTY);
                return result;
            }
            helper.fail("Expected one leather chestplate output");
            return ItemStack.EMPTY;
        }

        @Override public void close() {
            try {
                TimedCraftingController.cancel(player);
                CraftingQuality.clear(player);
                grid.clearContent();
                player.getInventory().clearContent();
                player.containerMenu = player.inventoryMenu;
                player.server.getPlayerList().remove(player);
            } finally {
                helper.getLevel().setBlockAndUpdate(table, original);
                channel.finishAndReleaseAll();
            }
        }
    }
}

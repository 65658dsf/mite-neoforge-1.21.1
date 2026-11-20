package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.crafting.TimedCraftingRecipes;
import io.github.ningmeng123.mite.crafting.TimedCraftingRules;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Netherite ingot recipes use the adamantium batch cost and require a mithril workbench. @author NingMeng123 */
@GameTestHolder("mite_crafting_audit")
@PrefixGameTestTemplate(false)
public final class NetheriteCraftingGameTests {
    @GameTest(template = "empty")
    public static void alloyingNetheriteRequiresMithril(GameTestHelper helper) {
        checkRecipe(helper, "mite:adamantium_ingot_from_nuggets", 1, Items.NETHERITE_SCRAP, Items.NETHERITE_SCRAP,
                Items.NETHERITE_SCRAP, Items.NETHERITE_SCRAP, Items.GOLD_INGOT, Items.GOLD_INGOT,
                Items.GOLD_INGOT, Items.GOLD_INGOT);
    }

    @GameTest(template = "empty")
    public static void unpackingNetheriteRequiresMithril(GameTestHelper helper) {
        checkRecipe(helper, "mite:adamantium_ingots_from_block", 9, Items.NETHERITE_BLOCK);
    }

    private static void checkRecipe(GameTestHelper helper, String referenceRecipe, int outputCount, Item... ingredients) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var original = level.getBlockState(pos);
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "netherite-test"), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(pos.above().getCenter());
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            if (ingredients.length == 1) {
                checkBlocked(helper, player, player.inventoryMenu, ingredients);
                player.inventoryMenu.getCraftSlots().clearContent();
            }

            level.setBlockAndUpdate(pos, MiteWorkbenches.TABLES.get("ancient_metal").get().defaultBlockState());
            var menu = new CraftingMenu(42, player.getInventory(), ContainerLevelAccess.create(level, pos));
            player.containerMenu = menu;
            checkBlocked(helper, player, menu, ingredients);
            for (String material : List.of("adamantium", "vibranium", "mithril")) {
                level.setBlockAndUpdate(pos, MiteWorkbenches.TABLES.get(material).get().defaultBlockState());
                menu.slotsChanged(menu.getSlot(1).container);
                var preview = menu.getSlot(0).getItem();
                helper.assertTrue(preview.is(Items.NETHERITE_INGOT) && preview.getCount() == outputCount,
                        material + " workbench previews the full netherite batch");
                helper.assertTrue(TimedCraftingController.start(player, menu), material + " workbench can start crafting");
                TimedCraftingController.cancel(player);
            }

            float difficulty = TimedCraftingRecipes.get(ResourceLocation.parse(referenceRecipe)).difficulty();
            helper.assertValueEqual(TimedCraftingController.period(player, menu), TimedCraftingRules.period(difficulty, 0, .6F, false),
                    "Level-zero mithril workbench uses the corresponding adamantium batch difficulty");
            TimedCraftingTestSupport.craftOne(helper, player, menu, ClickType.QUICK_MOVE);
            helper.assertValueEqual(player.getInventory().countItem(Items.NETHERITE_INGOT), outputCount,
                    "The complete batch is delivered after the full period");
            for (int i = 0; i < ingredients.length; i++) {
                helper.assertTrue(menu.getSlot(i + 1).getItem().isEmpty(), "Crafting consumes the ingredient in slot " + i);
            }
        } finally {
            TimedCraftingController.cancel(player);
            level.getServer().getPlayerList().remove(player);
            channel.finishAndReleaseAll();
            level.setBlockAndUpdate(pos, original);
        }
        helper.succeed();
    }

    private static void checkBlocked(GameTestHelper helper, ServerPlayer player, AbstractContainerMenu menu, Item[] ingredients) {
        for (int i = 0; i < ingredients.length; i++) menu.getSlot(i + 1).set(new ItemStack(ingredients[i]));
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "A grid below mithril cannot preview netherite");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        helper.assertFalse(TimedCraftingController.isActive(player), "Result clicks cannot start crafting below mithril");
        helper.assertTrue(menu.getCarried().isEmpty() && player.getInventory().countItem(Items.NETHERITE_INGOT) == 0,
                "A blocked craft cannot deliver ingots");
        for (int i = 0; i < ingredients.length; i++) {
            helper.assertTrue(ItemStack.matches(menu.getSlot(i + 1).getItem(), new ItemStack(ingredients[i])),
                    "A blocked craft preserves the ingredient in slot " + i);
        }
    }
}

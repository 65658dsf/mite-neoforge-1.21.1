package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.RangedRodRepair;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real result clicks verify non-quality source rods receive timing and material gates. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class RangedRodRepairGameTests {
    private RangedRodRepairGameTests() {}
    @GameTest(template = "ranged_027")
    public static void AllEighteenSourceRodsCombineThroughRealTimedManufacturingWithoutBonusOrOldTags(GameTestHelper helper) {
        float[] lowest = {150F, 147.22223F, 169.44444F, 169.44444F, 169.44444F, 213.88889F, 302.77777F, 836.1111F, 2969.4443F};
        for (int index = 0; index < MiteRangedItems.rods().size(); index++) {
            Material material = MiteRangedItems.rods().get(index);
            String bench = material == Material.FLINT || material == Material.OBSIDIAN ? "flint" : material.id;
            for (boolean carrot : new boolean[]{false, true}) try (Fixture f = new Fixture(helper, bench)) {
                Item item = carrot ? MiteRangedItems.carrotRod(material) : MiteRangedItems.rod(material);
                ItemStack first = new ItemStack(item);
                int maximum = first.getMaxDamage();
                first.setDamageValue(maximum - 1);
                first.set(DataComponents.CUSTOM_NAME, Component.literal("source old rod"));
                CompoundTag custom = new CompoundTag(); custom.putInt("source_old_tag", 196);
                first.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
                f.load(first, first.copy());
                var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, f.grid.asCraftInput(), helper.getLevel()).orElseThrow();
                helper.assertTrue(recipe.value() instanceof RepairItemRecipe, "Native two-item repair selects its real registered recipe");
                ItemStack output = recipe.value().assemble(f.grid.asCraftInput(), helper.getLevel().registryAccess());
                helper.assertValueEqual(output.getDamageValue(), maximum - 2, "Source repair combines exactly two remaining uses without modern five-percent bonus");
                helper.assertTrue(!output.has(DataComponents.CUSTOM_NAME) && !output.has(DataComponents.CUSTOM_DATA)
                        && !output.has(EquipmentDurability.QUALITY.get()), "aaf fresh output does not propagate arbitrary NBT, names or a nonexistent rod quality");
                float expectedLowest = carrot ? 40F : lowest[index];
                helper.assertValueEqual(RangedRodRepair.lowestDifficulty(output), expectedLowest, "Excluded carrot-to-rod recipes do not lower original rod manufacture difficulty");
                var resolved = CraftingQuality.resolve(f.player, f.menu, f.grid, recipe, output);
                helper.assertTrue(resolved != null && resolved.quality() == null && resolved.repair() && resolved.fixed()
                        && resolved.exempt() && resolved.cost() == 0, "No-quality source repair still has the real repair flag, damaged-input eligibility and XP exemption");
                helper.assertValueEqual(resolved.rule().difficulty(), expectedLowest / 2F, "Source aaf uses half the lowest original manufacture difficulty");
                helper.assertTrue(!f.menu.getSlot(0).mayPickup(f.player), "Actual result slot cannot bypass the timed repair by instant pickup");
                TimedCraftingTestSupport.craftOne(helper, f.player, f.menu, ClickType.PICKUP);
                helper.assertTrue(f.grid.isEmpty(), "The completed native transaction consumes both damaged input rods");
                helper.assertValueEqual(f.player.getInventory().countItem(item), 1, "Exactly one repaired rod reaches the player's inventory");
                ItemStack made = f.player.getInventory().items.stream().filter(stack -> stack.is(item)).findFirst().orElseThrow();
                helper.assertValueEqual(made.getDamageValue(), maximum - 2, "Actual manufactured repair preserves the exact source no-bonus damage");
                helper.assertTrue(!made.has(DataComponents.CUSTOM_NAME) && !made.has(DataComponents.CUSTOM_DATA)
                        && !made.has(EquipmentDurability.QUALITY.get()), "Native consumption cannot reintroduce stripped names, tags or quality");
                helper.assertValueEqual(f.player.totalExperience, 0, "Source no-quality repair neither costs nor grants experience");
            }
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void SourceRodRepairRejectsPristineOrEnchantedInputAndCannotBypassAnInferiorBench(GameTestHelper helper) {
        for (boolean carrot : new boolean[]{false, true}) {
            Item item = carrot ? MiteRangedItems.carrotRod(Material.ADAMANTIUM) : MiteRangedItems.rod(Material.ADAMANTIUM);
            ItemStack damaged = new ItemStack(item); damaged.setDamageValue(damaged.getMaxDamage() - 1);
            ItemStack pristine = new ItemStack(item);
            helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                    CraftingInput.of(2, 1, List.of(damaged, pristine)), helper.getLevel()).isEmpty(), "Source combine requires both rods to be damaged");
            ItemStack enchanted = damaged.copy(); enchanted.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING), 1);
            helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                    CraftingInput.of(2, 1, List.of(damaged, enchanted)), helper.getLevel()).isEmpty(), "Any source enchantment forbids simple combine repair");
            try (Fixture f = new Fixture(helper, "mithril")) {
                f.load(damaged, damaged.copy());
                helper.assertTrue(!TimedCraftingController.start(f.player, f.menu), "An inferior metal bench cannot start adamantium source rod repair");
                helper.assertValueEqual(f.grid.getItem(0).getDamageValue(), damaged.getDamageValue(), "Rejected repair preserves the first rod");
                helper.assertValueEqual(f.grid.getItem(1).getDamageValue(), damaged.getDamageValue(), "Rejected repair preserves the second rod");
                helper.assertTrue(f.player.getInventory().isEmpty(), "Rejected repair cannot emit a repaired rod");
            }
        }
        helper.succeed();
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final BlockPos table;
        final BlockState original;
        final CraftingMenu menu;
        final CraftingContainer grid;
        Fixture(GameTestHelper helper, String material) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-rod-repair"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true); player.setOnGround(true); player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 0); player.getFoodData().setFoodLevel(6); player.removeAllEffects();
            table = helper.absolutePos(new BlockPos(1, 1, 1)); original = helper.getLevel().getBlockState(table);
            helper.getLevel().setBlockAndUpdate(table, MiteWorkbenches.TABLES.get(material).get().defaultBlockState());
            menu = new CraftingMenu(52, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu; grid = (CraftingContainer)menu.getSlot(1).container;
        }
        void load(ItemStack first, ItemStack second) {
            TimedCraftingController.cancel(player); grid.clearContent();
            menu.getSlot(1).set(first.copy()); menu.getSlot(2).set(second.copy());
            TimedCraftingController.tick(player);
            helper.assertTrue(menu.stillValid(player), "Rod repair fixture uses a real placed material bench");
        }
        @Override public void close() {
            try {
                TimedCraftingController.cancel(player); CraftingQuality.clear(player); grid.clearContent();
                player.getInventory().clearContent(); player.containerMenu = player.inventoryMenu;
                player.server.getPlayerList().remove(player);
            } finally { helper.getLevel().setBlockAndUpdate(table, original); channel.finishAndReleaseAll(); }
        }
    }
}

package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShieldDecorationRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native transactions verify manufacturing tiers and decoration never re-manufacture an existing shield. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ShieldCraftingIntegrationGameTests {
    private ShieldCraftingIntegrationGameTests() {}

    @GameTest(template = "ranged_027")
    public static void sevenCraftableShieldTiersUseRealTimedMetalManufacturing(GameTestHelper helper) {
        float[] expected = {880F, 880F, 880F, 1280F, 2080F, 6880F, 26080F};
        String[] materials = {"copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium"};
        for (int i = 0; i < materials.length; i++) try (Fixture f = new Fixture(helper, materials[i])) {
            Item shield = MiteShields.get(materials[i]);
            Item metal = BuiltInRegistries.ITEM.get(ResourceLocation.parse(switch (materials[i]) {
                case "copper", "iron", "gold" -> "minecraft:" + materials[i] + "_ingot";
                default -> "mite:" + materials[i] + "_ingot";
            }));
            f.grid.clearContent();
            for (int slot : new int[]{0, 2, 3, 4, 5, 7}) f.grid.setItem(slot, new ItemStack(Items.OAK_PLANKS));
            f.grid.setItem(1, new ItemStack(metal));
            f.menu.slotsChanged(f.grid);
            var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, f.grid.asCraftInput(), helper.getLevel()).orElseThrow();
            ItemStack raw = recipe.value().assemble(f.grid.asCraftInput(), helper.getLevel().registryAccess());
            helper.assertTrue(raw.is(shield), "Real six-plank/one-ingot recipe resolves to its metal shield");
            helper.assertValueEqual(CraftingQuality.lowestDifficulty(raw), expected[i], "Modern difficulty sums six source plank components plus the actual metal ingot");
            helper.assertValueEqual(WorkbenchRules.required(raw), MiteShields.definition(raw).materialDurability(), "Shield manufacturing requires the corresponding metal bench capacity");
            var resolved = CraftingQuality.resolve(f.player, f.menu, f.grid, recipe, raw);
            helper.assertTrue(resolved != null && !resolved.fixed() && !resolved.repair(), "New shields use source quality selection instead of cosmetic-operation rules");
            helper.assertValueEqual(resolved.rule().difficulty(), expected[i], "Timed manufacture uses the real shield difficulty");
            helper.assertFalse(f.menu.getSlot(0).mayPickup(f.player), "A native result click cannot bypass shield manufacturing time");
            TimedCraftingTestSupport.craftOne(helper, f.player, f.menu, ClickType.PICKUP);
            helper.assertTrue(f.grid.isEmpty(), "Completed shield crafting consumes all seven inputs");
            helper.assertValueEqual(f.player.getInventory().countItem(shield), 1, "One native transaction produces exactly one shield");
            ItemStack made = f.player.getInventory().items.stream().filter(stack -> stack.is(shield)).findFirst().orElseThrow();
            helper.assertValueEqual(EquipmentDurability.quality(made), Quality.AVERAGE, "Zero-XP manufacture produces the selected free Average quality");
            helper.assertValueEqual(f.player.totalExperience, 0, "Average manufacture neither grants nor charges experience");
        }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void inferiorBenchCannotManufactureAHigherTierShield(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "mithril")) {
            for (int slot : new int[]{0, 2, 3, 4, 5, 7}) f.grid.setItem(slot, new ItemStack(Items.OAK_PLANKS));
            f.grid.setItem(1, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mite:adamantium_ingot"))));
            f.menu.slotsChanged(f.grid);
            helper.assertFalse(TimedCraftingController.start(f.player, f.menu), "Mithril bench cannot start adamantium shield manufacture");
            helper.assertValueEqual(f.grid.getItem(1).getCount(), 1, "Rejected manufacture preserves its ingot");
            helper.assertValueEqual(f.grid.getItem(0).getCount(), 1, "Rejected manufacture preserves its planks");
            helper.assertTrue(f.player.getInventory().isEmpty(), "Rejected manufacture emits no shield");
        }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void everyShieldCanBeDecoratedWithoutChangingItsQualityWearOrChargingManufacturingXp(GameTestHelper helper) {
        var patterns = new BannerPatternLayers.Builder().add(helper.getLevel().registryAccess()
                .registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(BannerPatterns.CROSS), DyeColor.WHITE).build();
        for (var definition : MiteShields.definitions()) try (Fixture f = new Fixture(helper,
                definition.material().equals("rusted_iron") ? "copper" : definition.material())) {
            ItemStack shield = new ItemStack(MiteShields.get(definition.material()));
            EquipmentDurability.setQuality(shield, definition.maxQuality());
            int damage = Math.min(37, shield.getMaxDamage() - 1);
            shield.setDamageValue(damage);
            shield.set(DataComponents.CUSTOM_NAME, Component.literal("Existing metal shield"));
            ItemStack banner = new ItemStack(Items.BLUE_BANNER);
            banner.set(DataComponents.BANNER_PATTERNS, patterns);
            f.grid.setItem(0, shield.copy());
            f.grid.setItem(1, banner);
            f.menu.slotsChanged(f.grid);
            var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, f.grid.asCraftInput(), helper.getLevel()).orElseThrow();
            helper.assertTrue(recipe.value() instanceof ShieldDecorationRecipe, "Each metal shield is recognized by the real native banner-decoration recipe");
            ItemStack raw = recipe.value().assemble(f.grid.asCraftInput(), helper.getLevel().registryAccess());
            var resolved = CraftingQuality.resolve(f.player, f.menu, f.grid, recipe, raw);
            helper.assertTrue(resolved != null && resolved.fixed() && resolved.exempt() && !resolved.repair(), "Decoration locks existing quality and is not a new manufacture or repair");
            helper.assertValueEqual(resolved.quality(), definition.maxQuality(), "Zero-XP players do not downgrade an existing paid quality while decorating");
            helper.assertValueEqual(resolved.range().minimum(), definition.maxQuality(), "Decoration offers no quality cycling");
            helper.assertValueEqual(resolved.range().maximum(), definition.maxQuality(), "Decoration offers no quality cycling");
            helper.assertValueEqual(resolved.cost(), 0, "Existing high-quality shields do not incur another manufacture experience fee");
            TimedCraftingTestSupport.craftOne(helper, f.player, f.menu, ClickType.PICKUP);
            ItemStack decorated = f.player.getInventory().items.stream().filter(stack -> stack.is(shield.getItem())).findFirst().orElseThrow();
            helper.assertValueEqual(EquipmentDurability.quality(decorated), definition.maxQuality(), "Actual native consumption preserves the existing quality");
            helper.assertValueEqual(decorated.getMaxDamage(), shield.getMaxDamage(), "Decoration does not alter the quality-adjusted maximum");
            helper.assertValueEqual(decorated.getDamageValue(), damage, "Decorating a worn shield never repairs or resets its wear");
            helper.assertValueEqual(decorated.get(DataComponents.CUSTOM_NAME), shield.get(DataComponents.CUSTOM_NAME), "Decoration retains its custom name");
            helper.assertValueEqual(decorated.get(DataComponents.BASE_COLOR), DyeColor.BLUE, "Native banner base color is copied");
            helper.assertValueEqual(decorated.get(DataComponents.BANNER_PATTERNS), patterns, "Native banner pattern layers are copied");
            helper.assertValueEqual(f.player.totalExperience, 0, "Decorating high-quality equipment never spends manufacture experience");
            helper.assertTrue(f.grid.isEmpty(), "Native decoration consumes one old shield and one banner");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyManagedMetalShieldsEnterTheSourceUnbreakingPoolWithTheirOwnEnchantability(GameTestHelper helper) {
        for (var definition : MiteShields.definitions()) {
            ItemStack shield = new ItemStack(MiteShields.get(definition.material()));
            helper.assertTrue(R196EnchantmentTable.isSourceEquipment(shield), "All eight explicit shield identities use source equipment rules");
            helper.assertValueEqual(R196EnchantmentTable.enchantability(shield), definition.enchantability(), "Source enchanting uses metal enchantability, including native iron thirty");
            helper.assertTrue(R196EnchantmentCatalog.supported("unbreaking", shield), "Source durability enchantment explicitly supports metal shields");
            helper.assertFalse(R196EnchantmentCatalog.supported("protection", shield), "A shield does not acquire humanoid armor enchantments");
            var selected = R196EnchantmentTable.select(helper.getLevel().registryAccess(), shield, 100, RandomSource.create(196));
            if (definition.enchantability() == 0) {
                helper.assertTrue(selected.isEmpty() && !R196EnchantmentTable.enchantable(shield), "Unenchantable rusted iron retains its source material limitation");
            } else {
                helper.assertValueEqual(selected.size(), 1, "Shield source candidates contain only the durability enchantment");
                helper.assertTrue(selected.getFirst().enchantment.is(Enchantments.UNBREAKING), "Actual source weighted selection chooses Unbreaking");
                helper.assertValueEqual(selected.getFirst().level, 3, "Player table acquisition respects vanilla Unbreaking III while retaining source shield applicability");
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
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-shield"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 0);
            player.experienceLevel = 200;
            player.getFoodData().setFoodLevel(6);
            player.removeAllEffects();
            table = helper.absolutePos(new BlockPos(1, 1, 1));
            original = helper.getLevel().getBlockState(table);
            helper.getLevel().setBlockAndUpdate(table, MiteWorkbenches.TABLES.get(material).get().defaultBlockState());
            menu = new CraftingMenu(72, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
            grid = (CraftingContainer)menu.getSlot(1).container;
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

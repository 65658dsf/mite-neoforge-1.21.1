package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.crafting.TimedCraftingRules;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dynamic vanilla operations retain all result components while using their R196 timing. @author NingMeng123 */
@GameTestHolder("mite_crafting_audit")
@PrefixGameTestTemplate(false)
public final class DynamicCommonCraftingGameTests {
    @GameTest(template = "empty")
    public static void fireworkStarAndFadeUseTheirSourceRuleWithoutLosingExplosionData(GameTestHelper helper) {
        CraftingInput starInput = input(3, 3, new ItemStack(Items.GUNPOWDER), new ItemStack(Items.RED_DYE),
                new ItemStack(Items.BLUE_DYE), new ItemStack(Items.FIRE_CHARGE), new ItemStack(Items.DIAMOND),
                new ItemStack(Items.GLOWSTONE_DUST));
        ItemStack star = assemble(helper, starInput, RecipeSerializer.FIREWORK_STAR, 100F, 1);
        helper.assertTrue(star.is(Items.FIREWORK_STAR), "The actual star recipe assembles a firework star");
        FireworkExplosion expected = new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL,
                IntList.of(DyeColor.RED.getFireworkColor(), DyeColor.BLUE.getFireworkColor()), IntList.of(), true, true);
        helper.assertValueEqual(star.get(DataComponents.FIREWORK_EXPLOSION), expected,
                "Manufacturing policy preserves the genuine shape, colors, trail and twinkle");
        star.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 烟火测试"));
        ItemStack original = star.copy();
        CraftingInput fadeInput = input(2, 2, star, new ItemStack(Items.YELLOW_DYE), new ItemStack(Items.LIME_DYE));
        ItemStack faded = assemble(helper, fadeInput, RecipeSerializer.FIREWORK_STAR_FADE, 100F, 1);
        ItemStack expectedFaded = original.copy();
        expectedFaded.set(DataComponents.FIREWORK_EXPLOSION,
                expected.withFadeColors(IntList.of(DyeColor.YELLOW.getFireworkColor(), DyeColor.LIME.getFireworkColor())));
        helper.assertTrue(ItemStack.matches(faded, expectedFaded), "Adding fade colors retains the original explosion attributes and name");
        helper.assertTrue(ItemStack.matches(star, original), "Preview assembly does not mutate the input firework star");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mapCloningAndExtendingKeepTheMapIdentityAndCorrectPostProcessing(GameTestHelper helper) {
        ItemStack map = MapItem.create(helper.getLevel(), 0, 0, (byte) 1, true, false);
        map.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 地图测试"));
        ItemStack original = map.copy();
        helper.assertTrue(MapItem.getSavedData(map, helper.getLevel()) != null, "The test map has genuine level saved data");
        ItemStack clones = assemble(helper, input(3, 3, map, new ItemStack(Items.MAP), new ItemStack(Items.MAP)),
                RecipeSerializer.MAP_CLONING, 100F, 155);
        helper.assertTrue(ItemStack.matches(clones, original.copyWithCount(3)), "Map cloning retains the exact ID and components in all three copies");
        helper.assertValueEqual(clones.get(DataComponents.MAP_ID), map.get(DataComponents.MAP_ID), "Cloning shares the original saved map ID");
        List<ItemStack> extending = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) extending.add(slot == 4 ? map : new ItemStack(Items.PAPER));
        ItemStack extended = assemble(helper, CraftingInput.of(3, 3, extending), RecipeSerializer.MAP_EXTENDING, 300F, 156);
        ItemStack expected = original.copy();
        expected.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
        helper.assertTrue(ItemStack.matches(extended, expected), "Map extending preserves the ID and name and adds only the SCALE post-processing marker");
        helper.assertValueEqual(extended.get(DataComponents.MAP_POST_PROCESSING), MapPostProcessing.SCALE,
                "The genuine pending scale operation survives rule resolution");
        helper.assertTrue(ItemStack.matches(map, original), "Neither dynamic map preview mutates the original map");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realInventoryRocketClickWaitsOneFullPeriodAndKeepsAllExplosionComponents(GameTestHelper helper) {
        CraftingInput simpleInput = input(2, 1, new ItemStack(Items.PAPER), new ItemStack(Items.GUNPOWDER));
        var simpleRecipe = helper.getLevel().getRecipeManager().byKey(
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("firework_rocket_simple")).orElseThrow();
        helper.assertTrue(simpleRecipe.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe,
                "The actual static no-star rocket recipe is loaded as a crafting recipe");
        @SuppressWarnings("unchecked")
        var simple = (net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>) simpleRecipe;
        helper.assertTrue(simple.value().matches(simpleInput, helper.getLevel()), "The genuine static no-star recipe matches paper and gunpowder");
        ItemStack simpleOutput = simple.value().assemble(simpleInput, helper.getLevel().registryAccess());
        helper.assertTrue(simpleOutput.is(Items.FIREWORK_ROCKET) && simpleOutput.getCount() == 3,
                "The static no-star recipe preserves its complete three-rocket batch");
        var simpleRule = CraftingQuality.rule(simple, simpleOutput, simpleInput);
        helper.assertTrue(simpleRule != null, "The static no-star rocket ID also has a manufacturing rule");
        helper.assertValueEqual(simpleRule.difficulty(), 100F, "Static no-star rockets use the same original D100 manufacturing operation");
        helper.assertValueEqual(simpleRule.sourceOrder(), 1, "Static no-star rockets retain R196 firework source order one");
        helper.assertTrue(simpleRule.requiredBench() == null, "Static no-star fireworks need no material-specific bench");
        helper.assertTrue(ItemStack.matches(simpleOutput, new ItemStack(Items.FIREWORK_ROCKET, 3)),
                "Static no-star manufacturing keeps the native default rocket components");
        try (InventoryFixture fixture = new InventoryFixture(helper)) {
            ItemStack star = new ItemStack(Items.FIREWORK_STAR, 2);
            FireworkExplosion explosion = new FireworkExplosion(FireworkExplosion.Shape.CREEPER,
                    IntList.of(0x336699, 0xCC6633), IntList.of(0x33CC66), true, true);
            star.set(DataComponents.FIREWORK_EXPLOSION, explosion);
            fixture.menu.getSlot(1).set(new ItemStack(Items.PAPER, 2));
            fixture.menu.getSlot(2).set(new ItemStack(Items.GUNPOWDER, 2));
            fixture.menu.getSlot(3).set(star);
            ItemStack assembled = assemble(helper, fixture.grid.asCraftInput(), RecipeSerializer.FIREWORK_ROCKET, 100F, 1);
            helper.assertValueEqual(assembled.getCount(), 3, "The registered rocket recipe produces its complete three-rocket batch");
            Fireworks expected = new Fireworks(1, List.of(explosion));
            helper.assertValueEqual(assembled.get(DataComponents.FIREWORKS), expected, "The rocket includes its real flight duration and complete explosion component");
            helper.assertTrue(ItemStack.matches(fixture.menu.getSlot(0).getItem(), assembled), "The real inventory menu previews the assembled dynamic result");
            int period = TimedCraftingRules.period(100F, 0, 0F, false);
            helper.assertValueEqual(TimedCraftingController.period(fixture.player, fixture.menu), period, "Backpack rockets use the exact D100 period");
            List<ItemStack> before = new ArrayList<>();
            for (int slot = 0; slot < fixture.grid.getContainerSize(); slot++) before.add(fixture.grid.getItem(slot).copy());
            fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.FIREWORK_ROCKET), 0, "Clicking the rocket preview cannot manufacture it instantly");
            fixture.tick(period - 1);
            helper.assertValueEqual(TimedCraftingController.progress(fixture.player), period - 1, "N-1 ticks leave the dynamic recipe unfinished");
            for (int slot = 0; slot < before.size(); slot++) helper.assertTrue(ItemStack.matches(before.get(slot), fixture.grid.getItem(slot)),
                    "N-1 ticks cannot consume paper, gunpowder or the decorated firework star");
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.FIREWORK_ROCKET), 0, "N-1 ticks cannot deliver any rockets");
            helper.assertTrue(fixture.menu.getCarried().isEmpty(), "An unfinished rocket never reaches the cursor");
            fixture.tick(1);
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.FIREWORK_ROCKET), 3, "Tick N delivers exactly three rockets");
            for (int slot = 0; slot < before.size(); slot++) {
                ItemStack expectedInput = before.get(slot).copy();
                if (!expectedInput.isEmpty()) expectedInput.shrink(1);
                helper.assertTrue(ItemStack.matches(expectedInput, fixture.grid.getItem(slot)), "Tick N consumes exactly one ingredient set without changing remaining components");
            }
            for (int slot = 0; slot < fixture.player.getInventory().getContainerSize(); slot++) {
                ItemStack output = fixture.player.getInventory().getItem(slot);
                if (output.is(Items.FIREWORK_ROCKET)) helper.assertValueEqual(output.get(DataComponents.FIREWORKS), expected,
                        "The completed hand-crafting transaction retains all original explosion data");
            }
            fixture.tick(period * 2);
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.FIREWORK_ROCKET), 3, "An ordinary click does not automatically manufacture the second rocket batch");
            helper.assertFalse(TimedCraftingController.isActive(fixture.player), "One completed dynamic recipe leaves no queued batch");
        }
        helper.succeed();
    }

    private static CraftingInput input(int width, int height, ItemStack... supplied) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < width * height; slot++) items.add(slot < supplied.length ? supplied[slot] : ItemStack.EMPTY);
        return CraftingInput.of(width, height, items);
    }

    private static ItemStack assemble(GameTestHelper helper, CraftingInput input, RecipeSerializer<?> serializer, float difficulty, int order) {
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
        helper.assertTrue(recipe.value().getSerializer() == serializer, "The actual loaded recipe uses the expected dynamic serializer");
        helper.assertTrue(recipe.value().matches(input, helper.getLevel()), "The loaded dynamic recipe matches genuine crafting ingredients");
        ItemStack result = recipe.value().assemble(input, helper.getLevel().registryAccess());
        helper.assertFalse(result.isEmpty(), "The genuine dynamic recipe assembles a nonempty component-bearing result");
        ItemStack original = result.copy();
        var rule = CraftingQuality.rule(recipe, result, input);
        helper.assertTrue(rule != null, "The loaded dynamic operation has an R196 manufacturing rule");
        helper.assertValueEqual(rule.difficulty(), difficulty, "The dynamic operation uses its original manufacturing difficulty");
        helper.assertValueEqual(rule.sourceOrder(), order, "The rule retains the original R196 operation source order");
        helper.assertTrue(rule.requiredBench() == null, "Dynamic fireworks and map operations require no material-specific bench");
        helper.assertTrue(ItemStack.matches(result, original), "Resolving a manufacturing rule cannot strip dynamic result components");
        return result;
    }

    private static final class InventoryFixture implements AutoCloseable {
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final InventoryMenu menu;
        private final CraftingContainer grid;

        private InventoryFixture(GameTestHelper helper) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-dynamic"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            player.removeAllEffects();
            menu = player.inventoryMenu;
            player.containerMenu = menu;
            grid = (CraftingContainer) menu.getSlot(1).container;
        }

        private void tick(int ticks) {
            for (int tick = 0; tick < ticks; tick++) TimedCraftingController.tick(player);
        }

        @Override public void close() {
            try {
                TimedCraftingController.forget(player);
                grid.clearContent();
                player.getInventory().clearContent();
                player.server.getPlayerList().remove(player);
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }
}

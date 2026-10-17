package io.github.ningmeng123.mite.gametest;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.crafting.VibraniumUpgradeRecipe;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MaterialRecipeSerializers;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteWeaponContent;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real custom recipe assembly and JSON/network transport preserve upgrades without inheriting obsolete stats. @author NingMeng123 */
@GameTestHolder("mite_vibranium")
@PrefixGameTestTemplate(false)
public final class VibraniumUpgradeGameTests {
    private VibraniumUpgradeGameTests() {}

    @GameTest(template = "empty")
    public static void actualWorkbenchClickUpgradesDamagedNamedEnchantedEquipmentOnlyAtTheFullPeriodBoundary(GameTestHelper helper) {
        try (WorkbenchSession session = new WorkbenchSession(helper)) {
            var sharpness = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.SHARPNESS);
            ItemStack source = new ItemStack(MiteTools.get("adamantium_sword"));
            EquipmentDurability.setQuality(source, Quality.LEGENDARY);
            source.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 实际工作台升级"));
            source.enchant(sharpness, 3);
            source.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            source.set(DataComponents.MAX_DAMAGE, 400);
            source.setDamageValue(source.getMaxDamage() / 4);
            ItemStack sourceSnapshot = source.copy();
            // This is the loaded source-native I/I/A recipe in the physical 3x3 menu.
            session.menu.getSlot(1).set(new ItemStack(MiteMaterials.VIBRANIUM_INGOT.get(), 2));
            session.menu.getSlot(4).set(new ItemStack(MiteMaterials.VIBRANIUM_INGOT.get(), 2));
            session.menu.getSlot(7).set(source);
            helper.assertTrue(session.menu.stillValid(session.player), "A physical vibranium workbench backs the menu");
            helper.assertTrue(session.menu.getSlot(0).getItem().is(MiteTools.get("vibranium_sword")), "The native upgrade appears in the real result slot");
            int period = TimedCraftingController.period(session.player, session.menu);
            helper.assertTrue(period >= 25 && period < 2000, "The consistent high-XP player runs a bounded full production period");
            int initialExperience = session.player.totalExperience;
            session.menu.clicked(0, 0, ClickType.PICKUP, session.player);
            helper.assertTrue(TimedCraftingController.isActive(session.player), "A real UI click admits damaged named enchanted upgrade inputs");
            for (int tick = 0; tick < period - 1; tick++) TimedCraftingController.tick(session.player);
            helper.assertValueEqual(TimedCraftingController.progress(session.player), period - 1, "The upgrade cannot finish before its full quality-adjusted period");
            helper.assertTrue(ItemStack.matches(session.grid.getItem(6), sourceSnapshot), "Waiting consumes and changes none of the source equipment");
            helper.assertValueEqual(session.grid.getItem(0).getCount(), 2, "Waiting consumes none of the first ingot stack");
            helper.assertValueEqual(session.player.getInventory().countItem(MiteTools.get("vibranium_sword")), 0, "No upgraded sword is delivered early");
            TimedCraftingController.tick(session.player);
            helper.assertFalse(TimedCraftingController.isActive(session.player), "An ordinary result click completes exactly one batch");
            helper.assertTrue(session.grid.getItem(6).isEmpty(), "The completed upgrade consumes exactly its previous sword");
            helper.assertValueEqual(session.grid.getItem(0).getCount(), 1, "The completed upgrade consumes one first-slot ingot");
            helper.assertValueEqual(session.grid.getItem(3).getCount(), 1, "The completed upgrade consumes one second-slot ingot");
            helper.assertValueEqual(session.player.getInventory().countItem(MiteTools.get("vibranium_sword")), 1, "Exactly one upgraded sword reaches the actual player inventory");
            ItemStack upgraded = session.player.getInventory().items.stream().filter(stack -> stack.is(MiteTools.get("vibranium_sword"))).findFirst().orElseThrow();
            ItemStack pristine = new ItemStack(MiteTools.get("vibranium_sword"));
            helper.assertValueEqual(EquipmentDurability.quality(upgraded), Quality.LEGENDARY, "UI manufacturing retains the fixed inherited legendary quality");
            helper.assertValueEqual(upgraded.get(DataComponents.CUSTOM_NAME), sourceSnapshot.get(DataComponents.CUSTOM_NAME), "UI manufacturing retains the source name");
            helper.assertValueEqual(upgraded.getEnchantmentLevel(sharpness), 3, "UI manufacturing retains the source enchantment");
            helper.assertValueEqual(upgraded.getOrDefault(DataComponents.MAX_DAMAGE, 0), pristine.getOrDefault(DataComponents.MAX_DAMAGE, 0), "The delivered sword uses new-material base capacity");
            helper.assertTrue(Objects.equals(upgraded.get(DataComponents.ATTRIBUTE_MODIFIERS), pristine.get(DataComponents.ATTRIBUTE_MODIFIERS)), "Delivery keeps new-material attack attributes rather than the explicit old override");
            helper.assertTrue(upgraded.has(DataComponents.FIRE_RESISTANT), "Delivery keeps new-material lava resistance");
            helper.assertValueEqual(upgraded.getDamageValue(), upgraded.getMaxDamage() / 4, "Delivery preserves the old one-quarter wear against the new legendary capacity");
            helper.assertValueEqual(session.player.totalExperience, initialExperience, "An inherited fixed quality does not charge manufacture XP a second time");
            helper.assertTrue(session.menu.getCarried().isEmpty(), "The UI transaction leaves no second output on the cursor");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void swordArmorAndBowUpgradesPreservePersonalDataAndProportionalWear(GameTestHelper helper) {
        String[][] identities = {
                {"mite:adamantium_sword", "mite:vibranium_sword"},
                {"mite:adamantium_chestplate", "mite:vibranium_chestplate"},
                {"mite:ancient_metal_bow", "mite:vibranium_bow"}
        };
        for (String[] ids : identities) {
            var recipe = recipe(helper, ids[0], ids[1]);
            ItemStack source = new ItemStack(item(ids[0]));
            Quality quality = ids[0].endsWith("bow") ? Quality.MASTERWORK : Quality.LEGENDARY;
            EquipmentDurability.setQuality(source, quality);
            source.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 upgrade fixture"));
            source.set(DataComponents.REPAIR_COST, 7);
            // Explicit legacy overrides must not replace the upgraded material's defaults.
            source.set(DataComponents.MAX_DAMAGE, 400);
            source.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            source.remove(DataComponents.TOOL);
            var enchantment = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(ids[0].endsWith("bow") ? Enchantments.POWER
                            : ids[0].endsWith("chestplate") ? Enchantments.PROTECTION : Enchantments.SHARPNESS);
            source.enchant(enchantment, 3);
            source.setDamageValue(source.getMaxDamage() / 4);
            ItemStack original = source.copy();
            CraftingInput input = input(source);
            helper.assertTrue(recipe.matches(input, helper.getLevel()), "Native shape matches the source equipment ingredient");
            ItemStack output = recipe.assemble(input, helper.getLevel().registryAccess());
            ItemStack pristine = new ItemStack(item(ids[1]));
            helper.assertTrue(output.is(pristine.getItem()), "Upgrade returns the new material identity");
            helper.assertTrue(ItemStack.isSameItemSameComponents(source, original), "Preview assembly leaves the source untouched");
            helper.assertValueEqual(EquipmentDurability.quality(output), quality, "Upgrade preserves the previous quality");
            helper.assertValueEqual(output.get(DataComponents.CUSTOM_NAME), source.get(DataComponents.CUSTOM_NAME), "Upgrade preserves custom name");
            helper.assertValueEqual(output.getEnchantmentLevel(enchantment), 3, "Upgrade preserves real enchantment components");
            helper.assertValueEqual(output.getOrDefault(DataComponents.REPAIR_COST, 0), 7, "Upgrade preserves maintenance metadata");
            helper.assertValueEqual(output.getOrDefault(DataComponents.MAX_DAMAGE, 0), pristine.getOrDefault(DataComponents.MAX_DAMAGE, 0),
                    "Upgrade retains the new unscaled material capacity");
            helper.assertTrue(Objects.equals(output.get(DataComponents.ATTRIBUTE_MODIFIERS), pristine.get(DataComponents.ATTRIBUTE_MODIFIERS)),
                    "Upgrade retains target damage/armor/toughness/knockback attributes");
            helper.assertTrue(Objects.equals(output.get(DataComponents.TOOL), pristine.get(DataComponents.TOOL)), "Upgrade retains target mining rules");
            helper.assertTrue(output.has(DataComponents.FIRE_RESISTANT), "Upgrade retains the new fire and lava resistance");
            helper.assertValueEqual(output.getDamageValue(), output.getMaxDamage() / 4, "Upgrade preserves exactly one-quarter wear after quality scaling");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void customShapedRecipeSurvivesJsonAndNetworkAndStillPreservesQuality(GameTestHelper helper) {
        var original = recipe(helper, "mite:adamantium_sword", "mite:vibranium_sword");
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        var json = Recipe.CODEC.encodeStart(ops, original).getOrThrow();
        helper.assertValueEqual(json.getAsJsonObject().get("type").getAsString(), "mite:vibranium_upgrade", "JSON uses the registered upgrade serializer");
        var decoded = (VibraniumUpgradeRecipe)Recipe.CODEC.parse(ops, json).getOrThrow();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var serializer = MaterialRecipeSerializers.VIBRANIUM_UPGRADE.get();
            serializer.streamCodec().encode(buffer, decoded);
            var network = serializer.streamCodec().decode(buffer);
            ItemStack source = new ItemStack(MiteTools.get("adamantium_sword"));
            EquipmentDurability.setQuality(source, Quality.FINE);
            CraftingInput input = input(source);
            helper.assertTrue(network.matches(input, helper.getLevel()), "Network keeps native shaped matching");
            helper.assertValueEqual(EquipmentDurability.quality(network.assemble(input, helper.getLevel().registryAccess())), Quality.FINE,
                    "Decoded recipe executes the preservation behavior");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidSourceCountsCannotProduceAnUpgradeThroughDirectAssembly(GameTestHelper helper) {
        var recipe = recipe(helper, "mite:adamantium_sword", "mite:vibranium_sword");
        CraftingInput noEquipment = CraftingInput.of(1, 2, List.of(new ItemStack(MiteMaterials.VIBRANIUM_INGOT.get()),
                new ItemStack(MiteMaterials.VIBRANIUM_INGOT.get())));
        helper.assertFalse(recipe.matches(noEquipment, helper.getLevel()), "A source equipment item is required");
        helper.assertTrue(recipe.assemble(noEquipment, helper.getLevel().registryAccess()).isEmpty(), "Direct assembly also rejects missing equipment");
        CraftingInput multipleEquipment = CraftingInput.of(1, 2, List.of(new ItemStack(MiteTools.get("adamantium_sword")),
                new ItemStack(MiteTools.get("adamantium_sword"))));
        helper.assertTrue(VibraniumUpgradeRecipe.source(multipleEquipment).isEmpty(), "Two equipment inputs cannot choose an arbitrary source");
        helper.assertTrue(recipe.assemble(multipleEquipment, helper.getLevel().registryAccess()).isEmpty(), "Direct assembly rejects multiple source items");
        helper.succeed();
    }

    private static CraftingInput input(ItemStack source) {
        return CraftingInput.of(1, 2, List.of(new ItemStack(MiteMaterials.VIBRANIUM_INGOT.get()), source));
    }

    private static Item item(String id) {
        if (id.endsWith("chestplate")) return MiteArmorContent.get(id);
        if (id.equals("mite:ancient_metal_bow")) return MiteWeaponContent.ANCIENT_METAL_BOW.get();
        if (id.equals("mite:vibranium_bow")) return MiteWeaponContent.VIBRANIUM_BOW.get();
        return MiteTools.get(id);
    }

    private static VibraniumUpgradeRecipe recipe(GameTestHelper helper, String previous, String result) {
        String json = """
                {"type":"mite:vibranium_upgrade","category":"equipment","pattern":["V","A"],
                 "key":{"V":{"item":"mite:vibranium_ingot"},"A":{"item":"%s"}},
                 "result":{"id":"%s","count":1}}
                """.formatted(previous, result);
        return (VibraniumUpgradeRecipe)Recipe.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,
                helper.getLevel().registryAccess()), JsonParser.parseString(json)).getOrThrow();
    }

    private static final class WorkbenchSession implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final CraftingMenu menu;
        private final CraftingContainer grid;
        private final BlockPos table;
        private final BlockState previousTable;

        private WorkbenchSession(GameTestHelper helper) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-vib-ui"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            // Negotiate installed optional-mod payloads before their real player-login listeners run.
            NetworkRegistry.configureMockConnection(connection);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, Integer.MAX_VALUE);
            player.getFoodData().setFoodLevel(6);
            player.removeAllEffects();
            table = helper.absolutePos(new BlockPos(1, 1, 1));
            previousTable = helper.getLevel().getBlockState(table);
            helper.getLevel().setBlockAndUpdate(table, MiteWorkbenches.TABLES.get("vibranium").get().defaultBlockState());
            menu = new CraftingMenu(49, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
            grid = (CraftingContainer)menu.getSlot(1).container;
        }

        @Override public void close() {
            try {
                TimedCraftingController.forget(player);
                grid.clearContent();
                player.getInventory().clearContent();
                player.containerMenu = player.inventoryMenu;
                player.server.getPlayerList().remove(player);
            } finally {
                helper.getLevel().setBlockAndUpdate(table, previousTable);
                channel.finishAndReleaseAll();
            }
        }
    }
}

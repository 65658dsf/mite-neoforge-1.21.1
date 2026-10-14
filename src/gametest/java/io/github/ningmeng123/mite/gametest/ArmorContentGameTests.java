package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.crafting.TimedCraftingRecipes;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.SilverArmor;
import io.github.ningmeng123.mite.loot.ArmorStructureLootModifier;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real registered equipment, manufacture, entity effects, horse save and world loot. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ArmorContentGameTests {
    private ArmorContentGameTests() {}
    private static ItemStack stack(String id) { return new ItemStack(MiteArmorContent.get(id)); }

    @GameTest(template = "empty")
    public static void allNewArmorUsesCorrectSlotsSourceMaximumsAndMaterialQualityCaps(GameTestHelper helper) {
        String[] metals = {"copper", "silver", "golden", "rusted_iron", "ancient_metal", "mithril", "adamantium"};
        int[] chestMaximums = {64, 64, 64, 48, 256, 1024, 4096};
        Quality[] caps = {Quality.EXCELLENT, Quality.EXCELLENT, Quality.SUPERB, Quality.POOR, Quality.MASTERWORK, Quality.LEGENDARY, Quality.LEGENDARY};
        for (int index = 0; index < metals.length; index++) {
            String stem = metals[index];
            String plate = index == 2 ? "minecraft:golden_chestplate" : "mite:" + stem + "_chestplate";
            ItemStack chest = stack(plate);
            helper.assertTrue(chest.getItem() instanceof ArmorItem, "A wearable source chestplate is a real ArmorItem: " + plate);
            helper.assertValueEqual(((ArmorItem)chest.getItem()).getEquipmentSlot(), EquipmentSlot.CHEST, "Native equipping slot");
            helper.assertValueEqual(chest.getMaxDamage(), chestMaximums[index], "Source ordinary/poor durability: " + plate);
            helper.assertValueEqual(EquipmentDurability.maximumQuality(chest), caps[index], "Independent material quality cap");
            EquipmentDurability.setQuality(chest, Quality.LEGENDARY);
            helper.assertValueEqual(EquipmentDurability.quality(chest), caps[index], "Commands cannot exceed the material maximum");
        }
        helper.assertValueEqual(stack("mite:adamantium_helmet").getMaxDamage(), 2560, "Adamantium helmet has five source components");
        helper.assertValueEqual(stack("mite:adamantium_chain_helmet").getMaxDamage(), 1280, "Chainmail has half plate durability");
        helper.assertValueEqual(stack("mite:ancient_metal_chainmail_boots").getMaxDamage(), 64, "Catalog ancient-chainmail ID remains stable");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceRecipeShapesResolveAndAllTimedRulesAreInstalled(GameTestHelper helper) {
        int checked = 0;
        for (var element : MiteArmorContent.source().getAsJsonArray("recipes")) {
            var source = element.getAsJsonObject();
            ResourceLocation id = ResourceLocation.parse(source.get("id").getAsString());
            var holder = helper.getLevel().getRecipeManager().byKey(id).orElseThrow();
            helper.assertTrue(holder.value() instanceof CraftingRecipe, "Source armor recipe is a crafting recipe: " + id);
            CraftingRecipe recipe = (CraftingRecipe)holder.value();
            List<ItemStack> inputs = new ArrayList<>();
            source.getAsJsonArray("ingredients").forEach(input -> inputs.add(input.isJsonNull() ? ItemStack.EMPTY : stack(input.getAsString())));
            CraftingInput grid = CraftingInput.of(source.get("width").getAsInt(), source.get("height").getAsInt(), inputs);
            helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Actual shaped matcher accepts source pattern: " + id);
            ItemStack output = recipe.assemble(grid, helper.getLevel().registryAccess());
            helper.assertTrue(output.is(MiteArmorContent.get(source.get("output").getAsString())), "Source output identity");
            helper.assertValueEqual(output.getCount(), source.get("count").getAsInt(), "Source batch output quantity");
            var rule = TimedCraftingRecipes.get(id);
            helper.assertTrue(rule != null, "Shared server manufacturing rule installed for " + id);
            helper.assertValueEqual(rule.difficulty(), source.get("difficulty").getAsFloat(), "Exact runtime source difficulty");
            String bench = source.get("required_bench").isJsonNull() ? null : source.get("required_bench").getAsString();
            helper.assertTrue(java.util.Objects.equals(rule.requiredBench(), bench), "Source material workbench condition: " + id);
            checked++;
        }
        helper.assertValueEqual(checked, 70, "All source armor, chain ring and reverse recipes checked");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void copperChestActuallyCraftsOnceAndWeakBenchCannotExtractIt(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            ServerPlayer player = session.player();
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            var copper = MiteWorkbenches.TABLES.get("copper").get().defaultBlockState();
            helper.getLevel().setBlockAndUpdate(pos, copper);
            CraftingMenu menu = new CraftingMenu(91, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
            player.containerMenu = menu;
            CraftingContainer grid = (CraftingContainer)menu.getSlot(1).container;
            for (int slot : new int[]{0, 2, 3, 4, 5, 6, 7, 8}) grid.setItem(slot, new ItemStack(Items.COPPER_INGOT, 2));
            helper.assertTrue(menu.getSlot(0).getItem().is(MiteArmorContent.get("mite:copper_chestplate")), "Copper workbench resolves source chestplate");
            helper.getLevel().setBlockAndUpdate(pos, MiteWorkbenches.TABLES.get("flint").get().defaultBlockState());
            menu.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertFalse(TimedCraftingController.isActive(player), "A weaker physical workbench cannot start manufacture");
            helper.assertValueEqual(grid.getItem(0).getCount(), 2, "Rejected weak-bench operation spends nothing");
            helper.getLevel().setBlockAndUpdate(pos, copper);
            menu.slotsChanged(grid);
            int period = TimedCraftingController.period(player, menu);
            menu.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(TimedCraftingController.isActive(player), "A valid ordinary click begins source manufacture");
            for (int tick = 0; tick < period + 3; tick++) TimedCraftingController.tick(player);
            helper.assertValueEqual(grid.getItem(0).getCount(), 1, "Ordinary click consumes one recipe and leaves the second set");
            helper.assertFalse(TimedCraftingController.isActive(player), "Ordinary click does not silently repeat");
            ItemStack crafted = player.getInventory().items.stream().filter(s -> s.is(MiteArmorContent.get("mite:copper_chestplate"))).findFirst().orElse(menu.getCarried());
            helper.assertTrue(crafted.is(MiteArmorContent.get("mite:copper_chestplate")), "Actual ResultSlot delivers equipment");
            helper.assertValueEqual(crafted.getMaxDamage(), 64, "Crafted ordinary quality has source maximum");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void armorQualityPersistsAndCombineRepairUsesSourceDamageWithoutBonus(GameTestHelper helper) {
        ItemStack chest = stack("mite:mithril_chestplate");
        EquipmentDurability.setQuality(chest, Quality.LEGENDARY);
        chest.setDamageValue(3000);
        helper.assertValueEqual(chest.getMaxDamage(), 3584, "Legendary mithril chest maximum");
        ItemStack restored = ItemStack.parse(helper.getLevel().registryAccess(), chest.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertValueEqual(restored.getMaxDamage(), 3584, "Save reload scales base exactly once");
        helper.assertValueEqual(EquipmentDurability.quality(restored), Quality.LEGENDARY, "Save reload preserves source quality");
        RepairItemRecipe repair = new RepairItemRecipe(CraftingBookCategory.MISC);
        CraftingInput input = CraftingInput.of(2, 1, List.of(chest, restored));
        helper.assertTrue(repair.matches(input, helper.getLevel()), "Identical damageable source armor combines");
        ItemStack result = repair.assemble(input, helper.getLevel().registryAccess());
        helper.assertValueEqual(result.getDamageValue(), 2416, "Only 584+584 remaining points are combined");
        helper.assertValueEqual(result.getMaxDamage(), 3584, "Repair does not double-apply quality");
        EquipmentDurability.setQuality(restored, Quality.AVERAGE);
        helper.assertFalse(repair.matches(input, helper.getLevel()), "Mixed-quality armor cannot bypass equipment maintenance rules");
        helper.assertValueEqual(CraftingQuality.lowestDifficulty(stack("mite:rusted_iron_helmet")), 2000F, "Rusted plate repairs use the explicit source override");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realSilverPlateHalvesPoisonWhileChainmailQuartersItAndWearRemovesCoverage(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            ServerPlayer player = session.player();
            equipSilver(player, false);
            helper.assertTrue(Math.abs(SilverArmor.coverage(player) - 1F) < .0001F, "Full silver plate covers one source unit");
            MobEffectInstance original = new MobEffectInstance(MobEffects.POISON, 200, 1, true, false, false);
            player.addEffect(original);
            helper.assertValueEqual(player.getEffect(MobEffects.POISON).getDuration(), 100, "Actual effect application halves poison");
            helper.assertValueEqual(original.getDuration(), 200, "Source potion template is not mutated");
            helper.assertValueEqual(player.getEffect(MobEffects.POISON).getAmplifier(), 1, "Poison strength preserved");
            helper.assertFalse(player.getEffect(MobEffects.POISON).isVisible(), "Particles preserved");
            player.removeEffect(MobEffects.POISON);
            equipSilver(player, true);
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
            helper.assertValueEqual(player.getEffect(MobEffects.POISON).getDuration(), 150, "Silver chain coverage is half plate coverage");
            player.removeEffect(MobEffects.POISON);
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack armor = player.getItemBySlot(slot);
                armor.setDamageValue(armor.getMaxDamage() - 1);
            }
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
            helper.assertValueEqual(player.getEffect(MobEffects.POISON).getDuration(), 200, "The final durability point provides no silver poison resistance");
            player.removeEffect(MobEffects.POISON);
            player.addEffect(new MobEffectInstance(MobEffects.POISON, MobEffectInstance.INFINITE_DURATION));
            helper.assertTrue(player.getEffect(MobEffects.POISON).isInfiniteDuration(), "Modern infinite effect remains infinite");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allFiveHorseArmorsEquipWithRealProtectionTexturesAndSaveIdentity(GameTestHelper helper) {
        String[] metals = {"copper", "silver", "ancient_metal", "mithril", "adamantium"};
        int[] protection = {4, 4, 5, 6, 7};
        var horse = helper.spawn(EntityType.HORSE, new BlockPos(1, 1, 1));
        horse.setTamed(true);
        for (int index = 0; index < metals.length; index++) {
            ItemStack armor = stack("mite:" + metals[index] + "_horse_armor");
            helper.assertTrue(horse.isBodyArmorItem(armor), "Modern horse accepts source barding: " + metals[index]);
            helper.assertFalse(armor.isDamageableItem(), "Source horse armor is not player durability equipment");
            helper.assertFalse(EquipmentDurability.isManaged(armor), "No player quality is invented for source barding");
            horse.setItemSlot(EquipmentSlot.BODY, armor);
            horse.tick();
            helper.assertValueEqual((int)horse.getAttributeValue(Attributes.ARMOR), protection[index], "Real horse armor attribute matches source protection");
            helper.assertValueEqual(((AnimalArmorItem)armor.getItem()).getTexture().toString(),
                    "mite:textures/entity/horse/armor/horse_armor_" + metals[index] + ".png", "Renderer uses copied source entity texture");
            CompoundTag saved = new CompoundTag();
            horse.saveWithoutId(saved);
            var restored = EntityType.HORSE.create(helper.getLevel());
            helper.assertTrue(restored != null, "Horse save fixture created");
            restored.load(saved);
            helper.assertTrue(restored.getBodyArmorItem().is(armor.getItem()), "Entity save keeps exact barding identity");
            restored.discard();
        }
        horse.setItemSlot(EquipmentSlot.BODY, stack("mite:silver_horse_armor"));
        horse.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        helper.assertValueEqual(horse.getEffect(MobEffects.POISON).getDuration(), 100, "Silver barding covers the whole horse for poison resistance");
        horse.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allPlayerArmorCanUseModernEnchantmentTagsButHorseArmorCannot(GameTestHelper helper) {
        for (var definition : MiteArmorContent.definitions()) {
            if (!definition.isNew()) continue;
            ItemStack stack = stack(definition.id());
            if (definition.horse()) {
                helper.assertFalse(stack.is(ItemTags.ARMOR_ENCHANTABLE), "Horse armor has no invented enchantability");
                continue;
            }
            helper.assertTrue(stack.is(ItemTags.ARMOR_ENCHANTABLE), "Protection enchantments recognize source armor: " + definition.id());
            helper.assertTrue(stack.is(ItemTags.DURABILITY_ENCHANTABLE), "Unbreaking recognizes actual damageable source armor");
            helper.assertValueEqual(stack.getEnchantmentValue(), definition.enchantability(), "Source material enchanting value");
        }
        helper.assertValueEqual(new ItemStack(Items.CHAIN).getMaxStackSize(), 8, "Source chain remains eight-stack while modern placement is retained");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceMetalImmunitiesDistinguishFireFromLavaAndProtectAdamantium(GameTestHelper helper) {
        var fire = helper.getLevel().damageSources().inFire();
        var lava = helper.getLevel().damageSources().lava();
        for (String id : new String[]{"mite:copper_chestplate", "mite:silver_horse_armor", "mite:mithril_chain", "minecraft:chain"}) {
            ItemStack item = stack(id);
            helper.assertFalse(item.canBeHurtBy(fire), "Source metal ignores fire damage: " + id);
            helper.assertTrue(item.canBeHurtBy(lava), "Source non-adamantium metal is harmed by lava: " + id);
        }
        for (String id : new String[]{"mite:adamantium_helmet", "mite:adamantium_chain", "mite:adamantium_horse_armor"})
            helper.assertFalse(stack(id).canBeHurtBy(lava), "Source adamantium ignores lava: " + id);
        helper.assertTrue(new ItemStack(Items.LEATHER_CHESTPLATE).canBeHurtBy(fire), "Source metal scope does not grant leather fire immunity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realDungeonLootUsesSourceGearPoolAndHasNoInventedHighTierBarding(GameTestHelper helper) {
        var params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)))).create(LootContextParamSets.CHEST);
        var table = helper.getLevel().getServer().reloadableRegistries().getLootTable(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                        ResourceLocation.withDefaultNamespace("chests/simple_dungeon")));
        boolean copperFound = false;
        for (int seed = 1; seed <= 128; seed++) {
            var result = table.getRandomItems(params, seed);
            for (ItemStack item : result) {
                copperFound |= item.is(MiteArmorContent.get("mite:copper_horse_armor"));
                helper.assertFalse(item.is(MiteArmorContent.get("mite:mithril_horse_armor")) || item.is(MiteArmorContent.get("mite:adamantium_horse_armor")),
                        "Source-unsourced barding cannot bypass survival progress through modern chests");
            }
        }
        helper.assertTrue(copperFound, "Registered loot modifier makes source copper barding survive into real dungeon output");
        var surface = ArmorStructureLootModifier.pools().get("dungeon_surface");
        int copperOutcomes = 0, emptyOutcomes = 0;
        for (int draw = 0; draw < 156; draw++) {
            var entry = surface.select(draw);
            if (entry == null) emptyOutcomes++;
            else if (entry.item().equals("mite:copper_horse_armor")) copperOutcomes++;
        }
        helper.assertValueEqual(copperOutcomes, 5, "Exactly five of the source 156 weighted outcomes are copper horse armor");
        helper.assertValueEqual(emptyOutcomes, 148, "Non-equipment source probability remains empty in the gear overlay");
        helper.succeed();
    }

    private static void equipSilver(ServerPlayer player, boolean chain) {
        String stem = "mite:silver_" + (chain ? "chain_" : "");
        player.setItemSlot(EquipmentSlot.HEAD, stack(stem + "helmet"));
        player.setItemSlot(EquipmentSlot.CHEST, stack(stem + "chestplate"));
        player.setItemSlot(EquipmentSlot.LEGS, stack(stem + "leggings"));
        player.setItemSlot(EquipmentSlot.FEET, stack(stem + "boots"));
    }
    private static Session newPlayer(GameTestHelper helper) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-armor-027"), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 2))));
            player.setNoGravity(true);
            player.setOnGround(true);
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            player.setHealth(player.getMaxHealth());
            return new Session(player, channel);
        } catch (RuntimeException | Error failure) {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }
    private record Session(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            try {
                TimedCraftingController.cancel(player);
                if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player);
            } finally { channel.finishAndReleaseAll(); }
        }
    }
}

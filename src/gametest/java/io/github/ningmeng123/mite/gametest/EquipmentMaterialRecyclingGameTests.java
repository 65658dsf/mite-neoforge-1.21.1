package io.github.ningmeng123.mite.gametest;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.crafting.EquipmentRecyclingIngredient;
import io.github.ningmeng123.mite.furnace.EquipmentRecycling;
import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import io.github.ningmeng123.mite.integration.jei.FurnaceJeiRecipes;
import io.github.ningmeng123.mite.registry.MaterialRecipeSerializers;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteWeaponContent;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.loading.DatagenModLoader;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Runtime material matching across armor, bows, hooks and optional weapons. @author NingMeng123 */
@GameTestHolder("mite_equipment_recycling")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class EquipmentMaterialRecyclingGameTests {
    private static final String NAMESPACE = "mite_equipment_recycling";
    private EquipmentMaterialRecyclingGameTests() {}

    @SubscribeEvent
    public static void registerIndependentTierFixtures(RegisterEvent event) {
        // Test-only weapons must never become required entries in generated production tags.
        if (DatagenModLoader.isRunningDataGen()) return;
        event.register(Registries.ITEM, registry -> {
            Tier copper = new Tier() {
                @Override public int getUses() { return 500; }
                @Override public float getSpeed() { return 4F; }
                @Override public float getAttackDamageBonus() { return 2F; }
                @Override public TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_IRON_TOOL; }
                @Override public int getEnchantmentValue() { return 15; }
                @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.COPPER_INGOT); }
            };
            // Neither fixture participates in MITE's equipment definition registries.
            registry.register(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "unlisted_blade"),
                    new SwordItem(copper, new Item.Properties()));
            registry.register(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "copper_mithril_iron_blade"),
                    new SwordItem(Tiers.DIAMOND, new Item.Properties()));
        });
    }

    @GameTest(template = "empty")
    public static void armorChainmailHorseArmorShieldsBowsAndHooksUseOneMaterialRecipe(GameTestHelper helper) {
        List<Sample> samples = sourceEquipment();
        helper.assertTrue(samples.stream().anyMatch(sample -> sample.group().equals("armor")), "Armor equipment is exercised");
        helper.assertTrue(samples.stream().anyMatch(sample -> sample.group().equals("horse")), "Horse armor is exercised");
        helper.assertTrue(samples.stream().anyMatch(sample -> sample.group().equals("chainmail")), "Chainmail equipment is exercised");
        for (Sample sample : samples) for (boolean worn : new boolean[]{false, true})
            assertRecipes(helper, sample.stack(worn), nugget(sample.material()), sample.label());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nonWoodenEquipmentRejectsFuelAndWoodenBowAndRodBodiesKeepTheirFuelRules(GameTestHelper helper) {
        for (Sample sample : sourceEquipment()) for (RecipeType<?> type : new RecipeType<?>[]{null,
                RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING}) {
            ItemStack stack = sample.stack(true);
            if (woodenBody(sample)) {
                helper.assertFalse(EquipmentRecycling.isNonWoodEquipment(stack),
                        "Wooden bow and rod bodies keep their previous fuel classification: " + sample.label());
                continue;
            }
            helper.assertValueEqual(stack.getBurnTime(type), 0,
                    "Material equipment cannot burn through NeoForge fuel events: " + sample.label() + " / " + type);
            helper.assertValueEqual(FurnaceThermalController.fuel(stack).ticks(), 0,
                    "The source thermal path also rejects equipment fuel: " + sample.label());
        }
        for (Item wooden : new Item[]{Items.BOW, Items.CROSSBOW, Items.WOODEN_SWORD, Items.WOODEN_SHOVEL, Items.WOODEN_HOE}) {
            helper.assertTrue(new ItemStack(wooden).getBurnTime(RecipeType.SMELTING) > 0,
                    "Plain wooden weapons and tools retain fuel use: " + wooden);
            assertRecipes(helper, new ItemStack(wooden), null, "plain wood " + wooden);
        }
        helper.assertValueEqual(new ItemStack(Items.FISHING_ROD).getBurnTime(null), 200,
                "The native wooden fishing rod retains its source fuel duration despite recycling its iron hook");
        helper.assertValueEqual(new ItemStack(Items.CARROT_ON_A_STICK).getBurnTime(null), 200,
                "The native carrot rod retains its source wooden-body fuel duration");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualFurnaceAndBlastFurnaceRecycleEverySourceEquipmentCategory(GameTestHelper helper) {
        for (Block block : new Block[]{Blocks.FURNACE, Blocks.BLAST_FURNACE}) try (Fixture f = new Fixture(helper, block)) {
            int ticks = block == Blocks.FURNACE ? 200 : 100;
            for (Sample sample : sourceEquipment()) {
                Item nugget = nugget(sample.material());
                if (nugget == null) continue;
                ItemStack input = sample.stack(true);
                helper.assertValueEqual(FurnaceThermalController.requiredHeat(input), 2,
                        "All metal equipment recycles at the iron/gold temperature: " + sample.label());
                helper.assertTrue(f.furnace.canPlaceItem(0, input), "The real device accepts the actual equipment input: " + sample.label());
                f.furnace.setItem(0, input);
                f.furnace.setItem(1, new ItemStack(Items.COAL));
                f.tick(ticks - 1);
                helper.assertTrue(f.furnace.getItem(2).isEmpty() && f.furnace.getItem(0).getCount() == 1,
                        "Equipment is preserved until a full cooking cycle: " + sample.label());
                f.tick(1);
                ItemStack result = f.furnace.removeItem(2, 64);
                helper.assertTrue(result.is(nugget) && result.getCount() == 1 && f.furnace.getItem(0).isEmpty(),
                        "Native recycling consumes one equipment item and produces one matching nugget: " + sample.label());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loadedRecipesDiscoverUnknownTierByRepairMaterialAndIgnoreMisleadingNames(GameTestHelper helper) {
        ItemStack copper = new ItemStack(item(NAMESPACE + ":unlisted_blade"));
        copper.setDamageValue(copper.getMaxDamage() - 1);
        helper.assertTrue(io.github.ningmeng123.mite.registry.MiteTools.definition(copper) == null,
                "The synthetic copper tier has no MITE tool whitelist entry");
        helper.assertTrue(io.github.ningmeng123.mite.crafting.MaterialAnvilRules.material(copper) == null,
                "The synthetic copper tier has no source-equipment material entry");
        assertRecipes(helper, copper, item("mite:copper_nugget"), "unlisted copper tier");
        helper.assertValueEqual(copper.getBurnTime(null), 0, "Unknown metal tiers are not fuel");
        ItemStack misleading = new ItemStack(item(NAMESPACE + ":copper_mithril_iron_blade"));
        assertRecipes(helper, misleading, null, "a diamond tier whose name contains several metals");
        for (Block block : new Block[]{Blocks.FURNACE, Blocks.BLAST_FURNACE}) try (Fixture f = new Fixture(helper, block)) {
            f.furnace.setItem(0, copper.copy());
            f.furnace.setItem(1, new ItemStack(Items.COAL));
            f.tick(block == Blocks.FURNACE ? 200 : 100);
            helper.assertTrue(f.furnace.getItem(0).isEmpty() && f.furnace.getItem(2).is(item("mite:copper_nugget"))
                            && f.furnace.getItem(2).getCount() == 1,
                    "The same loaded recipe actually recycles an independently registered copper tier");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ingredientJsonRoundTripRetainsLiveMaterialPredicatesWithoutAnItemWhitelist(GameTestHelper helper) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        for (String metal : EquipmentRecycling.metals()) {
            Ingredient original = loadedMaterialIngredient(helper, metal);
            var json = Ingredient.CODEC_NONEMPTY.encodeStart(ops, original).getOrThrow();
            helper.assertTrue(json.isJsonObject(), "A material ingredient encodes as a typed JSON object");
            var object = json.getAsJsonObject();
            helper.assertValueEqual(object.get("type").getAsString(), "mite:equipment_recycling",
                    "JSON identifies the registered custom material ingredient");
            helper.assertValueEqual(object.get("material").getAsString(), metal, "JSON preserves the selected recoverable metal");
            helper.assertValueEqual(object.size(), 2, "JSON stores a type and material without enumerating equipment item identities");
            Ingredient decoded = Ingredient.CODEC_NONEMPTY.parse(ops, json).getOrThrow();
            assertMaterialPredicate(helper, decoded, metal, "JSON round trip");
        }
        for (String material : new String[]{"diamond", "runic", "not_a_metal"}) {
            JsonObject invalid = new JsonObject();
            invalid.addProperty("type", "mite:equipment_recycling");
            invalid.addProperty("material", material);
            helper.assertTrue(Ingredient.CODEC_NONEMPTY.parse(ops, invalid).error().isPresent(),
                    "The ingredient codec rejects unsupported recoverable materials: " + material);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ingredientNetworkRoundTripKeepsTheCustomRuntimePredicate(GameTestHelper helper) {
        for (String metal : EquipmentRecycling.metals()) {
            Ingredient original = loadedMaterialIngredient(helper, metal);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, original);
                Ingredient decoded = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
                helper.assertValueEqual(buffer.readableBytes(), 0, "The custom ingredient consumes its full network payload");
                assertMaterialPredicate(helper, decoded, metal, "recipe network round trip");
            } finally {
                buffer.release();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void optionalSimplySwordsWeaponsAutomaticallyUseTheirActualMetal(GameTestHelper helper) {
        if (!SimplySwordsCompat.enabled()) {
            helper.assertTrue(BuiltInRegistries.ITEM.keySet().stream().noneMatch(id -> id.getNamespace().equals("simplyswords")),
                    "The absent-mod run requires no Simply Swords classes or registered weapon identities");
            helper.succeed();
            return;
        }
        int metal = 0, excluded = 0;
        for (Item weapon : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(weapon);
            var definition = SimplySwordsCompat.definition(stack);
            if (definition == null) continue;
            if (stack.isDamageableItem()) stack.setDamageValue(stack.getMaxDamage() - 1);
            Item expected = nugget(definition.material());
            if (expected == null) {
                for (var holder : helper.getLevel().getRecipeManager().getRecipes()) {
                    if (!(holder.value() instanceof AbstractCookingRecipe cooking)
                            || !cooking.matches(new SingleRecipeInput(stack), helper.getLevel())) continue;
                    ItemStack result = cooking.assemble(new SingleRecipeInput(stack), helper.getLevel().registryAccess());
                    helper.assertTrue(EquipmentRecycling.metals().stream().noneMatch(metalName -> result.is(nugget(metalName))),
                            "Nonmetal Simply Swords weapons do not produce a base-metal nugget; their own unrelated recycling remains valid: "
                                    + BuiltInRegistries.ITEM.getKey(weapon));
                }
                excluded++;
                continue;
            }
            assertRecipes(helper, stack, expected, "Simply Swords " + BuiltInRegistries.ITEM.getKey(weapon));
            metal++;
            helper.assertValueEqual(stack.getBurnTime(null), 0, "All Simply Swords metal variants reject fuel use");
            helper.assertValueEqual(FurnaceThermalController.requiredHeat(stack), 2,
                    "Simply Swords metal variants use vanilla equipment recycling heat");
        }
        helper.assertTrue(metal >= 135, "All nine metal sets and fifteen weapon families are exercised");
        helper.assertTrue(excluded > 0, "Diamond, netherite, runic and unique weapon materials are not falsely converted to base-metal nuggets");
        for (String material : new String[]{"iron", "gold", "rusted_iron", "copper", "silver", "ancient_metal", "mithril", "adamantium", "vibranium"})
            for (Block block : new Block[]{Blocks.FURNACE, Blocks.BLAST_FURNACE}) try (Fixture f = new Fixture(helper, block)) {
                ItemStack weapon = new ItemStack(SimplySwordsCompat.weapon(material, "longsword"));
                helper.assertTrue(!weapon.isEmpty(), "The optional mod registers the expected actual metal variant");
                weapon.setDamageValue(weapon.getMaxDamage() - 1);
                f.furnace.setItem(0, weapon);
                f.furnace.setItem(1, new ItemStack(Items.COAL));
                f.tick(block == Blocks.FURNACE ? 200 : 100);
                helper.assertTrue(f.furnace.getItem(0).isEmpty() && f.furnace.getItem(2).is(nugget(material))
                                && f.furnace.getItem(2).getCount() == 1,
                        "Native processing recycles optional weapons by actual material: " + material);
            }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void runtimeRecipeViewerIncludesAllEquipmentAndIndependentTierInputs(GameTestHelper helper) {
        var shown = FurnaceJeiRecipes.build(helper.getLevel());
        List<Sample> samples = new ArrayList<>(sourceEquipment());
        samples.add(new Sample(item(NAMESPACE + ":unlisted_blade"), "copper", "independent tier"));
        if (SimplySwordsCompat.enabled()) for (Item weapon : BuiltInRegistries.ITEM) {
            var definition = SimplySwordsCompat.definition(new ItemStack(weapon));
            if (definition != null && nugget(definition.material()) != null)
                samples.add(new Sample(weapon, definition.material(), "Simply Swords"));
        }
        for (Sample sample : samples) {
            Item expected = nugget(sample.material());
            if (expected == null) continue;
            for (String furnace : new String[]{"minecraft:furnace", "minecraft:blast_furnace"})
                helper.assertTrue(shown.stream().anyMatch(recipe -> recipe.furnaceId().equals(furnace)
                                && recipe.inputs().stream().anyMatch(stack -> stack.is(sample.item()))
                                && recipe.output().is(expected) && recipe.output().getCount() == 1),
                        "Runtime ingredient enumeration exposes equipment in JEI: " + sample.label() + " / " + furnace);
            if (!woodenBody(sample))
                helper.assertTrue(shown.stream().noneMatch(recipe -> recipe.fuels().stream().anyMatch(stack -> stack.is(sample.item()))),
                        "A nonwooden equipment item is never offered as JEI fuel: " + sample.label());
        }
        helper.succeed();
    }

    private static List<Sample> sourceEquipment() {
        var samples = new LinkedHashMap<Item, Sample>();
        for (var definition : MiteArmorContent.definitions()) {
            Item item = item(definition.id());
            samples.put(item, new Sample(item, definition.material(), definition.horse() ? "horse" : definition.chainmail() ? "chainmail" : "armor"));
        }
        for (var definition : MiteShields.definitions()) {
            Item item = item(definition.id());
            samples.put(item, new Sample(item, definition.material(), "shield"));
        }
        for (var material : MiteRangedItems.rods()) {
            Item rod = MiteRangedItems.rod(material), carrot = MiteRangedItems.carrotRod(material);
            samples.put(rod, new Sample(rod, material.id, "fishing hook"));
            samples.put(carrot, new Sample(carrot, material.id, "carrot hook"));
        }
        samples.put(Items.IRON_HORSE_ARMOR, new Sample(Items.IRON_HORSE_ARMOR, "iron", "horse"));
        samples.put(Items.GOLDEN_HORSE_ARMOR, new Sample(Items.GOLDEN_HORSE_ARMOR, "gold", "horse"));
        samples.put(Items.DIAMOND_HORSE_ARMOR, new Sample(Items.DIAMOND_HORSE_ARMOR, "diamond", "horse"));
        samples.put(Items.LEATHER_HORSE_ARMOR, new Sample(Items.LEATHER_HORSE_ARMOR, "leather", "horse"));
        samples.put(Items.LEATHER_CHESTPLATE, new Sample(Items.LEATHER_CHESTPLATE, "leather", "armor"));
        samples.put(Items.NETHERITE_CHESTPLATE, new Sample(Items.NETHERITE_CHESTPLATE, "netherite", "armor"));
        samples.put(Items.DIAMOND_CHESTPLATE, new Sample(Items.DIAMOND_CHESTPLATE, "diamond", "armor"));
        Item ancientBow = MiteWeaponContent.ANCIENT_METAL_BOW.get(), mithrilBow = MiteWeaponContent.MITHRIL_BOW.get();
        samples.put(ancientBow, new Sample(ancientBow, "ancient_metal", "reinforced bow"));
        samples.put(mithrilBow, new Sample(mithrilBow, "mithril", "reinforced bow"));
        Item vibraniumBow = MiteWeaponContent.VIBRANIUM_BOW.get();
        samples.put(vibraniumBow, new Sample(vibraniumBow, "vibranium", "reinforced bow"));
        return List.copyOf(samples.values());
    }

    private static boolean woodenBody(Sample sample) {
        return sample.group().equals("fishing hook") || sample.group().equals("carrot hook") || sample.group().equals("reinforced bow");
    }

    private static Ingredient loadedMaterialIngredient(GameTestHelper helper, String metal) {
        return helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.SMELTING).stream()
                .map(holder -> holder.value()).filter(recipe -> recipe.getResultItem(helper.getLevel().registryAccess()).is(nugget(metal)))
                .flatMap(recipe -> recipe.getIngredients().stream())
                .filter(ingredient -> ingredient.getCustomIngredient() instanceof EquipmentRecyclingIngredient custom
                        && custom.material().equals(metal)).findFirst().orElseThrow();
    }

    private static void assertMaterialPredicate(GameTestHelper helper, Ingredient ingredient, String metal, String transport) {
        helper.assertFalse(ingredient.isSimple(), transport + " retains the predicate instead of flattening an item list");
        helper.assertTrue(ingredient.getCustomIngredient() instanceof EquipmentRecyclingIngredient,
                transport + " retains the actual custom ingredient implementation");
        var custom = (EquipmentRecyclingIngredient) ingredient.getCustomIngredient();
        helper.assertValueEqual(custom.material(), metal, transport + " retains its selected metal");
        helper.assertTrue(custom.getType() == MaterialRecipeSerializers.EQUIPMENT_RECYCLING.get(),
                transport + " resolves the registered ingredient type");
        ItemStack unknownCopper = new ItemStack(item(NAMESPACE + ":unlisted_blade"));
        unknownCopper.setDamageValue(unknownCopper.getMaxDamage() - 1);
        helper.assertTrue(ingredient.test(unknownCopper) == metal.equals("copper"),
                transport + " still evaluates an independent tier's repair material");
        helper.assertFalse(ingredient.test(new ItemStack(item(NAMESPACE + ":copper_mithril_iron_blade"))),
                transport + " does not infer metal from a misleading diamond weapon name");
        helper.assertFalse(ingredient.test(new ItemStack(Items.DIAMOND_CHESTPLATE)),
                transport + " does not recycle diamond armor as a base metal");
        for (Sample sample : sourceEquipment()) {
            Item expected = nugget(sample.material());
            helper.assertTrue(ingredient.test(sample.stack(true)) == (expected != null && expected == nugget(metal)),
                    transport + " preserves actual armor, shields, bows and hook material matching: " + sample.label());
        }
        if (SimplySwordsCompat.enabled()) for (Item weapon : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(weapon);
            var definition = SimplySwordsCompat.definition(stack);
            if (definition == null) continue;
            Item expected = nugget(definition.material());
            helper.assertTrue(ingredient.test(stack) == (expected != null && expected == nugget(metal)),
                    transport + " preserves optional weapon material matching: " + BuiltInRegistries.ITEM.getKey(weapon));
        }
    }

    private static void assertRecipes(GameTestHelper helper, ItemStack stack, Item expected, String label) {
        for (RecipeType<?> type : new RecipeType<?>[]{RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING}) {
            var recipes = helper.getLevel().getRecipeManager().getRecipes().stream().map(holder -> holder.value())
                    .filter(recipe -> recipe instanceof AbstractCookingRecipe cooking && cooking.getType() == type
                            && cooking.matches(new SingleRecipeInput(stack), helper.getLevel()))
                    .map(recipe -> (AbstractCookingRecipe) recipe).toList();
            boolean recyclable = expected != null && type != RecipeType.SMOKING;
            helper.assertValueEqual(recipes.size(), recyclable ? 1 : 0,
                    "Loaded cooking recipes select equipment by material without duplicate vanilla recipes: " + label + " / " + type);
            if (recyclable) {
                ItemStack result = recipes.getFirst().assemble(new SingleRecipeInput(stack), helper.getLevel().registryAccess());
                helper.assertTrue(result.is(expected) && result.getCount() == 1,
                        "Equipment recycles to exactly one nugget of its actual material: " + label);
                helper.assertValueEqual(recipes.getFirst().getCookingTime(), type == RecipeType.SMELTING ? 200 : 100,
                        "Material recipes retain standard equipment recycling time");
                helper.assertValueEqual(recipes.getFirst().getExperience(), 0.1F, "Material recipes retain vanilla recycling experience");
            }
        }
    }

    private static Item nugget(String material) {
        return switch (material) {
            case "copper", "silver", "ancient_metal", "mithril", "adamantium", "vibranium" -> item("mite:" + material + "_nugget");
            case "iron", "rusted_iron" -> Items.IRON_NUGGET;
            case "gold" -> Items.GOLD_NUGGET;
            default -> null;
        };
    }

    private static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item == Items.AIR) throw new IllegalArgumentException("Unregistered material recycling test item: " + id);
        return item;
    }

    private record Sample(Item item, String material, String group) {
        ItemStack stack(boolean worn) {
            ItemStack stack = new ItemStack(item);
            if (worn && stack.isDamageableItem()) stack.setDamageValue(stack.getMaxDamage() - 1);
            return stack;
        }
        String label() { return group + " " + BuiltInRegistries.ITEM.getKey(item); }
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos position, front;
        final BlockState old, oldFront;
        final AbstractFurnaceBlockEntity furnace;
        Fixture(GameTestHelper helper, Block block) {
            this.helper = helper;
            position = helper.absolutePos(new BlockPos(2, 2, 2));
            front = position.north();
            old = helper.getLevel().getBlockState(position);
            oldFront = helper.getLevel().getBlockState(front);
            helper.getLevel().setBlockAndUpdate(front, Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(position, block.defaultBlockState());
            furnace = (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(position);
        }
        void tick(int count) {
            for (int tick = 0; tick < count; tick++)
                AbstractFurnaceBlockEntity.serverTick(helper.getLevel(), position, helper.getLevel().getBlockState(position), furnace);
        }
        @Override public void close() {
            furnace.clearContent();
            helper.getLevel().setBlockAndUpdate(position, old);
            helper.getLevel().setBlockAndUpdate(front, oldFront);
        }
    }
}

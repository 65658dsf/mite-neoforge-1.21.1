package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import io.github.ningmeng123.mite.registry.MiteCreativeTabs;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Optional-mod registry, callbacks, recipes and source crafting/repair integration. @author NingMeng123 */
@GameTestHolder("mite_simplyswords")
@PrefixGameTestTemplate(false)
public final class SimplySwordsCompatibilityGameTests {
    private static final List<String> ADDED_MATERIALS = List.of("rusted_iron", "copper", "silver",
            "ancient_metal", "mithril", "adamantium", "vibranium");
    private static final List<String> ALL_METALS = List.of("rusted_iron", "copper", "silver", "gold",
            "ancient_metal", "mithril", "adamantium", "vibranium");
    private SimplySwordsCompatibilityGameTests() {}

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void everyCopperFamilySharesTheSixPointSwordBudgetAndStaysBelowSilverAndIron(GameTestHelper h) {
        if (SimplySwordsCompat.enabled()) for (String family : SimplySwordsCompat.families()) {
            var copper = new ItemStack(SimplySwordsCompat.weapon("copper", family));
            var silver = new ItemStack(SimplySwordsCompat.weapon("silver", family));
            var iron = new ItemStack(SimplySwordsCompat.weapon("iron", family));
            double copperDamage = baseDamage(copper), silverDamage = baseDamage(silver);
            h.assertTrue(Math.abs(copperDamage - silverDamage * .9375D) < .00001D,
                    "Real copper/silver " + family + " share the source eight-point basis with the reviewed six/six-point-four budgets");
            h.assertTrue(copperDamage < silverDamage && silverDamage < baseDamage(iron),
                    "The actual native iron family stays above added copper and silver: " + family);
        }
        h.succeed();
    }

    private static double baseDamage(ItemStack stack) {
        return 1D + stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().stream()
                .filter(entry -> entry.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE))
                .mapToDouble(entry -> entry.modifier().amount()).sum();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void optionalModCreatesExactlyOneHundredFiveIdentitiesAndReusesNativeGold(GameTestHelper h) {
        long count = BuiltInRegistries.ITEM.keySet().stream().filter(id -> id.getNamespace().equals("mite")
                && id.getPath().startsWith("simplyswords_")).count();
        h.assertValueEqual(count, SimplySwordsCompat.enabled() ? 105L : 0L,
                "Conditional registration creates seven material sets and no absent-mod placeholder items");
        for (String material : ALL_METALS) for (String family : SimplySwordsCompat.families()) {
            Item item = SimplySwordsCompat.weapon(material, family);
            if (!SimplySwordsCompat.enabled()) {
                h.assertTrue(item == Items.AIR, "Absent Simply Swords has no weapon: " + material + "/" + family);
                continue;
            }
            h.assertTrue(item instanceof SwordItem, "The real Simply Swords weapon class is registered");
            var definition = SimplySwordsCompat.definition(new ItemStack(item));
            h.assertTrue(definition != null, "Every available variant has a durability definition");
            h.assertValueEqual(definition.added(), !material.equals("gold"), "Gold retains its original identity");
            h.assertValueEqual(BuiltInRegistries.ITEM.getKey(item), material.equals("gold")
                    ? id("simplyswords", "gold_" + family)
                    : id("mite", "simplyswords_" + material + "_" + family), "Stable weapon registry identity");
            h.assertFalse(BuiltInRegistries.ITEM.containsKey(id("mite", "simplyswords_gold_" + family)),
                    "There is no duplicate MITE gold weapon");
        }
        h.assertTrue(SimplySwordsCompat.weapon("copper", "not_a_weapon") == Items.AIR,
                "Unknown weapon families cannot accidentally resolve to an unrelated item");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void everyMetalAndFamilyUsesActualQualityAdjustedDurability(GameTestHelper h) {
        if (absent(h)) return;
        for (String material : ALL_METALS) for (String family : SimplySwordsCompat.families()) {
            ItemStack stack = new ItemStack(SimplySwordsCompat.weapon(material, family));
            var profile = EquipmentDurability.toolProfile(stack);
            String label = material + "/" + family;
            h.assertTrue(profile != null, "Registered durability profile: " + label);
            h.assertValueEqual(profile.family(), family(family), "Weapon shape selects source wear: " + label);
            h.assertValueEqual(profile.materialDurability(), factor(material), "Source material durability: " + label);
            h.assertValueEqual(EquipmentDurability.maximumQuality(stack), cap(material), "Material quality cap: " + label);
            if (material.equals("vibranium")) {
                h.assertValueEqual(stack.getEnchantmentValue(), 120, "Vibranium preserves source enchantability: " + label);
                h.assertFalse(stack.canBeHurtBy(h.getLevel().damageSources().lava()), "Dropped vibranium resists lava: " + label);
                h.assertFalse(stack.canBeHurtBy(h.getLevel().damageSources().inFire()), "Dropped vibranium resists fire: " + label);
            }
            int base = (int)(400F * family(family).components() * factor(material));
            h.assertValueEqual(stack.getOrDefault(DataComponents.MAX_DAMAGE, 0), base,
                    "Default component stores the unscaled source maximum: " + label);
            for (Quality requested : Quality.values()) {
                EquipmentDurability.setQuality(stack, requested);
                Quality accepted = Quality.fromOrdinal(Math.min(requested.ordinal(), cap(material).ordinal()));
                h.assertValueEqual(EquipmentDurability.quality(stack), accepted, "Quality is capped: " + label);
                h.assertValueEqual(stack.getMaxDamage(), Math.round(base * accepted.multiplier()),
                        "ItemStack applies quality exactly once: " + label);
            }
        }
        for (String material : List.of("mithril", "vibranium")) {
            ItemStack original = new ItemStack(SimplySwordsCompat.weapon(material, "spear"));
            EquipmentDurability.setQuality(original, Quality.FINE);
            original.setDamageValue(713);
            ItemStack restored = ItemStack.parse(h.getLevel().registryAccess(), original.save(h.getLevel().registryAccess())).orElseThrow();
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                ItemStack.STREAM_CODEC.encode(buffer, restored);
                ItemStack decoded = ItemStack.STREAM_CODEC.decode(buffer);
                h.assertTrue(ItemStack.matches(original, decoded), "Weapon identity, quality and exact wear survive save and network: " + material);
                h.assertValueEqual(decoded.getMaxDamage(), material.equals("vibranium") ? 614400 : 76800,
                        "Saved/network Fine spear is not scaled twice: " + material);
            } finally { buffer.release(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void nativeWeaponsAreManagedWithoutTurningGemstonesIntoEquipment(GameTestHelper h) {
        if (absent(h)) return;
        long nativeWeapons = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("simplyswords")) continue;
            ItemStack stack = new ItemStack(item);
            if (item instanceof SwordItem) {
                nativeWeapons++;
                var definition = SimplySwordsCompat.definition(stack);
                h.assertTrue(definition != null && !definition.added(), "Every native weapon preserves its identity and receives a profile");
                h.assertValueEqual(stack.getOrDefault(DataComponents.MAX_DAMAGE, 0),
                        EquipmentDurabilityRules.baseToolDurability(definition.profile()), "Native weapon defaults use MITE durability");
            } else h.assertTrue(SimplySwordsCompat.definition(stack) == null,
                    "Nonweapon Simply Swords items have no equipment profile: " + BuiltInRegistries.ITEM.getKey(item));
        }
        h.assertTrue(nativeWeapons >= 75, "All five ordinary native material sets are loaded");
        assertNative(h, "iron_longsword", 8F, ToolFamily.SWORD);
        assertNative(h, "sword_on_a_stick", .5F, ToolFamily.SWORD);
        assertNative(h, "diamond_longsword", 16F, ToolFamily.SWORD);
        assertNative(h, "runic_longsword", 32F, ToolFamily.SWORD);
        assertNative(h, "soulrender", 64F, ToolFamily.SCYTHE);
        assertNative(h, "magiscythe", 64F, ToolFamily.SCYTHE);
        assertNative(h, "molten_edge", 64F, ToolFamily.BATTLE_AXE);
        assertNative(h, "hearthflame", 64F, ToolFamily.WAR_HAMMER);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void runicUpgradesUseMithrilWhileOrdinaryNetheriteWeaponsRemainRetired(GameTestHelper h) {
        if (absent(h)) return;
        ItemStack tablet = new ItemStack(BuiltInRegistries.ITEM.get(id("simplyswords", "runic_tablet")));
        h.assertFalse(tablet.isEmpty(), "The native runic tablet remains available");
        for (String family : SimplySwordsCompat.families()) {
            ItemStack netherite = new ItemStack(SimplySwordsCompat.weapon("netherite", family));
            ItemStack runic = new ItemStack(SimplySwordsCompat.weapon("runic", family));
            h.assertTrue(RemovedDiamondEquipment.isRemoved(netherite), "Every ordinary netherite weapon is retired: " + family);
            assertRetiredTags(h, netherite, family);
            h.assertFalse(RemovedDiamondEquipment.isRemoved(runic), "Runic weapons retain their own progression: " + family);
            h.assertTrue(ItemStack.parse(h.getLevel().registryAccess(), netherite.save(h.getLevel().registryAccess()))
                    .map(ItemStack::isEmpty).orElse(true), "Saved ordinary netherite weapons cannot return: " + family);
            h.assertTrue(ItemStack.matches(runic, ItemStack.parse(h.getLevel().registryAccess(), runic.save(h.getLevel().registryAccess()))
                    .orElse(ItemStack.EMPTY)), "Saved runic weapons remain available: " + family);
            var holder = h.getLevel().getRecipeManager().byKey(id("simplyswords", "runic_" + family)).orElseThrow();
            h.assertTrue(holder.value() instanceof SmithingTransformRecipe, "Native runic upgrade retains smithing: " + family);
            var recipe = (SmithingTransformRecipe)holder.value();
            ItemStack mithril = new ItemStack(SimplySwordsCompat.weapon("mithril", family));
            SmithingRecipeInput input = new SmithingRecipeInput(tablet, mithril, new ItemStack(Items.DIAMOND));
            h.assertTrue(recipe.matches(input, h.getLevel()), "Mithril weapon, tablet and diamond unlock runic progression: " + family);
            h.assertTrue(recipe.assemble(input, h.getLevel().registryAccess()).is(runic.getItem()),
                    "Each mithril family upgrades to the matching native runic weapon: " + family);
            h.assertFalse(recipe.matches(new SmithingRecipeInput(tablet, netherite, new ItemStack(Items.DIAMOND)), h.getLevel()),
                    "The runic route no longer requires retired netherite: " + family);
            h.assertFalse(recipe.matches(new SmithingRecipeInput(tablet,
                    new ItemStack(SimplySwordsCompat.weapon("iron", family)), new ItemStack(Items.DIAMOND)), h.getLevel()),
                    "Low-tier weapons cannot skip the mithril prerequisite: " + family);
        }
        for (String path : List.of("soulrender", "magiscythe", "hearthflame")) {
            ItemStack unique = new ItemStack(BuiltInRegistries.ITEM.get(id("simplyswords", path)));
            h.assertFalse(unique.isEmpty(), "Retired uniques keep their registry identities: " + path);
            h.assertTrue(RemovedDiamondEquipment.isRemoved(unique),
                    "Unique weapons are retired independently of their native material tier: " + path);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void actualAttackCallbacksKeepImpactSoundsAndSpendOneSourceWearBudget(GameTestHelper h) throws ReflectiveOperationException {
        if (absent(h)) return;
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var target = EntityType.PIG.create(h.getLevel());
        Object general = config("general");
        Field sounds = general.getClass().getField("enableWeaponImpactSounds");
        boolean previous = sounds.getBoolean(general);
        int[] observed = {0};
        Consumer<PlayLevelSoundEvent.AtEntity> listener = event -> { if (event.getEntity() == target) observed[0]++; };
        sounds.setBoolean(general, true);
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            for (String material : List.of("copper", "vibranium")) for (String family : SimplySwordsCompat.families()) {
                ItemStack stack = new ItemStack(SimplySwordsCompat.weapon(material, family));
                player.setItemSlot(EquipmentSlot.MAINHAND, stack);
                int before = observed[0];
                h.assertTrue(stack.hurtEnemy(target, player), "Real ItemStack attack callback succeeds: " + family);
                h.assertValueEqual(stack.getDamageValue(), 0, "hurtEnemy runs effects without premature base wear");
                h.assertValueEqual(observed[0], before + 1, "The original Simply Swords impact callback ran once");
                stack.postHurtEnemy(target, player);
                int wear = EquipmentDurabilityRules.attackWear(EquipmentDurability.toolProfile(stack), false, false);
                h.assertValueEqual(stack.getDamageValue(), wear, "postHurtEnemy applies one MITE wear budget: " + family);
                stack.mineBlock(h.getLevel(), Blocks.CRAFTING_TABLE.defaultBlockState(),
                        h.absolutePos(new BlockPos(1, 1, 1)), player);
                h.assertValueEqual(stack.getDamageValue(), wear, "A portable workbench adds no vanilla mining wear");
                if (family.equals("spear")) h.assertValueEqual(stack.getItem().getClass().getName(),
                        "net.sweenus.simplyswords.item.SimplySwordsThrowableItem", "The new spear retains Simply Swords throwing behavior");
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            sounds.setBoolean(general, previous);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void realRunicPostHitPowerSurvivesTheDurabilityCallback(GameTestHelper h) throws ReflectiveOperationException {
        if (absent(h)) return;
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var target = EntityType.PIG.create(h.getLevel());
        ItemStack stack = new ItemStack(SimplySwordsCompat.weapon("runic", "longsword"));
        Class<?> awakening = Class.forName("net.sweenus.simplyswords.api.AwakeningApi");
        stack = (ItemStack)awakening.getMethod("initializeFullyAwakened", ItemStack.class).invoke(null, stack);
        Class<?> componentClass = Class.forName("net.sweenus.simplyswords.power.GemPowerComponent");
        Object power = componentClass.getMethod("runic", ResourceLocation.class).invoke(null, id("simplyswords", "float"));
        Object supplier = Class.forName("net.sweenus.simplyswords.registry.ComponentTypeRegistry").getField("GEM_POWER").get(null);
        setExternalComponent(stack, (DataComponentType<?>)((Supplier<?>)supplier).get(), power);
        Object powers = config("gemPowers");
        Object floating = powers.getClass().getField("floating").get(powers);
        Field chance = floating.getClass().getField("chance");
        int previous = chance.getInt(floating);
        chance.setInt(floating, 100);
        try {
            player.setItemSlot(EquipmentSlot.MAINHAND, stack);
            h.assertFalse(target.hasEffect(MobEffects.LEVITATION), "The fixture starts without the runic effect");
            h.assertTrue(stack.hurtEnemy(target, player), "The real runic weapon callback succeeds");
            h.assertTrue(target.hasEffect(MobEffects.LEVITATION), "Runic GemPowerComponent.postHit still applies its actual passive");
            h.assertValueEqual(stack.getDamageValue(), 0, "Runic effects do not duplicate ordinary durability wear");
            stack.postHurtEnemy(target, player);
            h.assertValueEqual(stack.getDamageValue(), 50, "Runic attack uses precisely one source sword wear budget");
        } finally { chance.setInt(floating, previous); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void materialAnvilsAndPhysicalWorkbenchesGateTheAddedWeapons(GameTestHelper h) {
        if (absent(h)) return;
        BlockPos crafter = h.absolutePos(new BlockPos(1, 1, 1));
        BlockPos bench = crafter.east();
        for (String material : ALL_METALS) {
            ItemStack stack = new ItemStack(SimplySwordsCompat.weapon(material, "longsword"));
            EquipmentDurability.setQuality(stack, Quality.POOR);
            stack.setDamageValue(stack.getMaxDamage() / 2);
            var repairMaterial = MaterialAnvilRules.material(stack);
            h.assertTrue(repairMaterial != null && repairMaterial.name().equals(material), "Material anvil identifies " + material);
            String anvilMaterial = material.equals("rusted_iron") ? "copper" : material;
            var anvil = MiteFunctionalBlocks.ANVILS.get(anvilMaterial).get().defaultBlockState();
            ItemStack nuggets = new ItemStack(MaterialAnvilRules.repairItem(material), 64);
            var result = MaterialAnvilRules.previewRepair(anvil, stack, nuggets);
            h.assertTrue(result != null && result.allowed() && result.repair(), "Matching source nuggets repair " + material);
            h.assertTrue(result.output().getDamageValue() < stack.getDamageValue(), "Repair actually restores durability");
            h.assertValueEqual(EquipmentDurability.quality(result.output()), Quality.POOR, "Nugget repair preserves quality");
            h.assertValueEqual(result.output().getMaxDamage(), stack.getMaxDamage(), "Repair does not multiply quality twice");
            var ingot = MaterialAnvilRules.previewRepair(anvil, stack, new ItemStack(SimplySwordsCompat.ingot(material)));
            h.assertTrue(ingot != null && !ingot.allowed(), "The source repair system rejects ingots");
            h.assertValueEqual(WorkbenchRules.required(stack), factor(material), "Physical bench requires the metal capacity");
            h.assertTrue(CraftingQuality.lowestDifficulty(stack) >= factor(material) * 200F,
                    "Weapon crafting and quality repair have a positive metal-work difficulty");
            h.getLevel().setBlockAndUpdate(bench, MiteWorkbenches.TABLES.get("flint").get().defaultBlockState());
            h.assertFalse(WorkbenchRules.allowsCrafter(h.getLevel(), crafter, stack), "A flint bench cannot manufacture metal weapons");
            h.getLevel().setBlockAndUpdate(bench, MiteWorkbenches.TABLES.get(material.equals("vibranium")
                    ? "vibranium" : "adamantium").get().defaultBlockState());
            h.assertTrue(WorkbenchRules.allowsCrafter(h.getLevel(), crafter, stack), "A sufficient adjacent bench enables manufacturing");
        }
        ItemStack mithril = new ItemStack(SimplySwordsCompat.weapon("mithril", "greathammer"));
        mithril.setDamageValue(1000);
        var tooWeak = MaterialAnvilRules.previewRepair(Blocks.ANVIL.defaultBlockState(), mithril,
                new ItemStack(MaterialAnvilRules.repairItem("mithril"), 64));
        h.assertTrue(tooWeak != null && !tooWeak.allowed(), "A native iron anvil cannot repair mithril Simply Swords weapons");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void loadedRecipesCraftNinetyWeaponsAndLeaveRustedIronAsLoot(GameTestHelper h) {
        Map<Item, Integer> recipes = new HashMap<>();
        for (var holder : h.getLevel().getRecipeManager().getRecipes()) {
            ItemStack output = holder.value().getResultItem(h.getLevel().registryAccess());
            if (output.isEmpty()) continue;
            ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(output.getItem());
            h.assertFalse(outputId.getNamespace().equals("simplyswords") && outputId.getPath().startsWith("netherite_"),
                    "No loaded crafting or smithing recipe produces retired Simply Swords netherite: " + holder.id());
            if (holder.value().getType() != net.minecraft.world.item.crafting.RecipeType.CRAFTING) continue;
            var definition = SimplySwordsCompat.definition(output);
            if (definition == null || !definition.added()) continue;
            h.assertTrue(holder.value() instanceof ShapedRecipe, "Added weapons use real ordinary crafting recipes");
            var shaped = (ShapedRecipe)holder.value();
            List<ItemStack> inputs = shaped.getIngredients().stream().map(ingredient -> ingredient.isEmpty()
                    ? ItemStack.EMPTY : ingredient.getItems()[0].copy()).toList();
            CraftingInput input = CraftingInput.of(shaped.getWidth(), shaped.getHeight(), inputs);
            h.assertTrue(shaped.matches(input, h.getLevel()), "Loaded ingredient tags form a matching weapon recipe: " + holder.id());
            h.assertTrue(shaped.assemble(input, h.getLevel().registryAccess()).is(output.getItem()), "Recipe assembles its actual registered weapon");
            recipes.merge(output.getItem(), 1, Integer::sum);
        }
        h.assertValueEqual(recipes.values().stream().mapToInt(Integer::intValue).sum(), SimplySwordsCompat.enabled() ? 90 : 0,
                "Exactly six craftable material sets load; optional recipes vanish when the mod is absent");
        if (SimplySwordsCompat.enabled()) for (String material : ADDED_MATERIALS) for (String family : SimplySwordsCompat.families()) {
            Item weapon = SimplySwordsCompat.weapon(material, family);
            h.assertValueEqual(recipes.getOrDefault(weapon, 0), material.equals("rusted_iron") ? 0 : 1,
                    "Each craftable variant has one recipe and rusted iron remains loot equipment");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void addedWeaponsAppearInCombatSearchAndEnchantingTags(GameTestHelper h) {
        if (absent(h)) return;
        for (boolean permissions : new boolean[]{false, true}) {
            CreativeModeTabs.tryRebuildTabContents(h.getLevel().enabledFeatures(), permissions, h.getLevel().registryAccess());
            var combat = BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(MiteCreativeTabs.Group.COMBAT.key());
            var search = CreativeModeTabs.searchTab();
            for (String material : ADDED_MATERIALS) for (String family : SimplySwordsCompat.families()) {
                Item weapon = SimplySwordsCompat.weapon(material, family);
                ItemStack stack = new ItemStack(weapon);
                h.assertTrue(combat.getDisplayItems().stream().anyMatch(entry -> entry.is(weapon)), "New weapon appears in MITE Combat");
                h.assertTrue(search.getDisplayItems().stream().anyMatch(entry -> entry.is(weapon)), "New weapon appears in global creative search");
                h.assertTrue(stack.is(ItemTags.SWORDS), "Sword tag includes the actual added weapon");
                h.assertTrue(stack.is(ItemTags.SHARP_WEAPON_ENCHANTABLE) && stack.is(ItemTags.DURABILITY_ENCHANTABLE),
                        "Actual enchantment eligibility includes damage and durability enchantments");
            }
            for (var tab : CreativeModeTabs.allTabs()) for (String family : SimplySwordsCompat.families()) {
                Item retired = SimplySwordsCompat.weapon("netherite", family);
                h.assertTrue(tab.getDisplayItems().stream().noneMatch(entry -> entry.is(retired))
                                && tab.getSearchTabDisplayItems().stream().noneMatch(entry -> entry.is(retired)),
                        "Every creative category and search excludes retired Simply Swords netherite: " + family);
            }
        }
        h.succeed();
    }

    private static void assertRetiredTags(GameTestHelper h, ItemStack stack, String family) {
        for (var tag : List.of(ItemTags.SWORDS, ItemTags.AXES, ItemTags.PICKAXES, ItemTags.SHOVELS, ItemTags.HOES,
                ItemTags.HEAD_ARMOR, ItemTags.CHEST_ARMOR, ItemTags.LEG_ARMOR, ItemTags.FOOT_ARMOR,
                ItemTags.TRIMMABLE_ARMOR, ItemTags.HEAD_ARMOR_ENCHANTABLE, ItemTags.CHEST_ARMOR_ENCHANTABLE,
                ItemTags.LEG_ARMOR_ENCHANTABLE, ItemTags.FOOT_ARMOR_ENCHANTABLE, ItemTags.ARMOR_ENCHANTABLE,
                ItemTags.SWORD_ENCHANTABLE, ItemTags.FIRE_ASPECT_ENCHANTABLE, ItemTags.SHARP_WEAPON_ENCHANTABLE,
                ItemTags.WEAPON_ENCHANTABLE, ItemTags.MINING_ENCHANTABLE, ItemTags.MINING_LOOT_ENCHANTABLE,
                ItemTags.DURABILITY_ENCHANTABLE, ItemTags.EQUIPPABLE_ENCHANTABLE, ItemTags.VANISHING_ENCHANTABLE))
            h.assertFalse(stack.is(tag), "Retired Simply Swords netherite " + family + " is absent from " + tag.location());
        for (var definition : R196EnchantmentCatalog.definitions()) {
            var tag = TagKey.create(Registries.ITEM, id("mite", "enchantable/" + definition.id()));
            h.assertFalse(stack.is(tag), "Retired Simply Swords netherite " + family + " is absent from source support " + definition.id());
        }
    }

    private static boolean absent(GameTestHelper h) {
        if (SimplySwordsCompat.enabled()) return false;
        h.succeed();
        return true;
    }
    private static ResourceLocation id(String namespace, String path) { return ResourceLocation.fromNamespaceAndPath(namespace, path); }
    private static Object config(String name) throws ReflectiveOperationException {
        return Class.forName("net.sweenus.simplyswords.config.Config").getField(name).get(null);
    }
    @SuppressWarnings("unchecked")
    private static <T> void setExternalComponent(ItemStack stack, DataComponentType<T> type, Object value) { stack.set(type, (T)value); }
    private static void assertNative(GameTestHelper h, String path, float factor, ToolFamily family) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id("simplyswords", path)));
        var profile = EquipmentDurability.toolProfile(stack);
        h.assertTrue(!stack.isEmpty() && profile != null, "Native weapon is present and managed: " + path);
        h.assertValueEqual(profile.materialDurability(), factor, "Modern material adapter: " + path);
        h.assertValueEqual(profile.family(), family, "Unique weapon keeps its source durability family: " + path);
    }
    private static ToolFamily family(String family) {
        return switch (family) {
            case "sai" -> ToolFamily.DAGGER;
            case "greataxe", "halberd" -> ToolFamily.BATTLE_AXE;
            case "greathammer" -> ToolFamily.WAR_HAMMER;
            case "scythe" -> ToolFamily.SCYTHE;
            default -> ToolFamily.SWORD;
        };
    }
    private static float factor(String material) {
        return switch (material) {
            case "ancient_metal" -> 16F;
            case "mithril" -> 64F;
            case "adamantium" -> 256F;
            case "vibranium" -> 512F;
            default -> 4F;
        };
    }
    private static Quality cap(String material) {
        return switch (material) {
            case "rusted_iron" -> Quality.POOR;
            case "gold" -> Quality.SUPERB;
            case "copper", "silver" -> Quality.EXCELLENT;
            case "ancient_metal" -> Quality.MASTERWORK;
            default -> Quality.LEGENDARY;
        };
    }
}

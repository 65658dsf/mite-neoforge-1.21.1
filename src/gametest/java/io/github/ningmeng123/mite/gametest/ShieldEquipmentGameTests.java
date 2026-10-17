package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.EquipmentQuality;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real registered shields exercise quality, persistence, material maintenance and native wear. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ShieldEquipmentGameTests {
    private ShieldEquipmentGameTests() {}

    @GameTest(template = "empty")
    public static void allEightShieldMaterialsRetainNativeBlockingAndHaveCappedQuality(GameTestHelper helper) {
        int[] bases = {84, 84, 84, 84, 168, 336, 1344, 5376};
        int[] maxima = {63, 84, 84, 84, 168, 336, 1344, 5376};
        helper.assertValueEqual(MiteShields.definitions().size(), 8, "Seven new shields and the original iron shield are registered");
        helper.assertTrue(MiteShields.get("iron") == Items.SHIELD, "Original shield keeps its native registry identity");
        for (int i = 0; i < bases.length; i++) {
            var definition = MiteShields.definitions().get(i);
            ItemStack stack = new ItemStack(MiteShields.get(definition.material()));
            helper.assertTrue(stack.getItem() instanceof ShieldItem, "Metal shield remains a real ShieldItem");
            helper.assertTrue(EquipmentDurability.isManaged(stack), "Shield is source quality-managed");
            helper.assertTrue(EquipmentDurability.toolProfile(stack) == null && EquipmentDurability.armorProfile(stack) == null,
                    "Shield does not masquerade as a mining tool or humanoid armor");
            helper.assertValueEqual(stack.getOrDefault(DataComponents.MAX_DAMAGE, 0), bases[i], "Modern iron baseline scales by source material ratio");
            helper.assertValueEqual(stack.getMaxDamage(), maxima[i], "Rusted quality is capped at Poor; other defaults remain Average");
            helper.assertValueEqual(stack.getUseAnimation(), UseAnim.BLOCK, "Native blocking use animation is retained");
            helper.assertTrue(stack.canPerformAction(ItemAbilities.SHIELD_BLOCK), "Native shield blocking ability is retained");
            helper.assertValueEqual(stack.getItem().getEnchantmentValue(), definition.enchantability(), "Shield has source metal enchantability");
            stack.set(EquipmentDurability.QUALITY.get(), Quality.LEGENDARY.ordinal());
            helper.assertValueEqual(EquipmentDurability.quality(stack), definition.maxQuality(), "Command-injected quality respects its material ceiling");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shieldQualityWearAndDecorationSurviveSaveAndNetwork(GameTestHelper helper) {
        var patterns = new BannerPatternLayers.Builder().add(helper.getLevel().registryAccess()
                .registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(BannerPatterns.CROSS), DyeColor.WHITE).build();
        for (var definition : MiteShields.definitions()) {
            ItemStack shield = new ItemStack(MiteShields.get(definition.material()));
            EquipmentDurability.setQuality(shield, definition.maxQuality());
            shield.setDamageValue(shield.getMaxDamage() / 2);
            shield.set(DataComponents.BASE_COLOR, DyeColor.BLUE);
            shield.set(DataComponents.BANNER_PATTERNS, patterns);
            ItemStack saved = ItemStack.parse(helper.getLevel().registryAccess(), shield.save(helper.getLevel().registryAccess())).orElseThrow();
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                ItemStack.STREAM_CODEC.encode(buffer, saved);
                ItemStack decoded = ItemStack.STREAM_CODEC.decode(buffer);
                helper.assertTrue(ItemStack.isSameItemSameComponents(shield, decoded), "Metal identity, quality, exact wear and banner components survive both transports");
                helper.assertValueEqual(decoded.getMaxDamage(), shield.getMaxDamage(), "Persistence does not quality-scale maximum twice");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeShieldCombineRepairPreservesQualityAndMatchingBannerWithoutBonus(GameTestHelper helper) {
        RepairItemRecipe recipe = new RepairItemRecipe(CraftingBookCategory.MISC);
        var pattern = helper.getLevel().registryAccess().registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(BannerPatterns.CROSS);
        for (String material : List.of("iron", "copper", "mithril")) {
            ItemStack first = new ItemStack(MiteShields.get(material));
            EquipmentDurability.setQuality(first, Quality.FINE);
            first.setDamageValue(first.getMaxDamage() - 10);
            first.set(DataComponents.BASE_COLOR, DyeColor.RED);
            first.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder().add(pattern, DyeColor.WHITE).build());
            ItemStack second = first.copy();
            CraftingInput input = CraftingInput.of(2, 1, List.of(first, second));
            helper.assertTrue(recipe.matches(input, helper.getLevel()), "Matching decorated shields have a real native repair recipe");
            ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
            helper.assertValueEqual(EquipmentDurability.quality(result), Quality.FINE, "Repair preserves source quality");
            helper.assertValueEqual(result.getOrDefault(DataComponents.MAX_DAMAGE, 0), first.getOrDefault(DataComponents.MAX_DAMAGE, 0), "Repair restores the unscaled base component");
            helper.assertValueEqual(result.getMaxDamage(), first.getMaxDamage(), "Fine maximum is applied exactly once");
            helper.assertValueEqual(result.getDamageValue(), first.getMaxDamage() - 20, "Repair combines remaining uses without vanilla's five-percent bonus");
            helper.assertTrue(EquipmentQuality.sameColor(first, result), "Repair keeps banner base and pattern layers");
            second.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder().add(pattern, DyeColor.BLACK).build());
            helper.assertFalse(recipe.matches(input, helper.getLevel()), "Different pattern colors cannot silently merge");
            helper.assertTrue(recipe.assemble(input, helper.getLevel().registryAccess()).isEmpty(), "Direct repair assembly also rejects incompatible decorations");
            second.set(DataComponents.BANNER_PATTERNS, first.get(DataComponents.BANNER_PATTERNS));
            second.set(DataComponents.BASE_COLOR, DyeColor.BLUE);
            helper.assertFalse(recipe.matches(input, helper.getLevel()), "Different shield base colors cannot silently merge");
            second.set(DataComponents.BASE_COLOR, DyeColor.RED);
            EquipmentDurability.setQuality(second, Quality.AVERAGE);
            helper.assertFalse(recipe.matches(input, helper.getLevel()), "Different source qualities cannot merge");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void materialAnvilsRepairShieldsWithMatchingMetalNuggetsAndTierRequirements(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.experienceLevel = 200;
        player.totalExperience = 10_000_000;
        var cross = helper.getLevel().registryAccess().registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(BannerPatterns.CROSS);
        var matchingPatterns = new BannerPatternLayers.Builder().add(cross, DyeColor.WHITE).build();
        for (var definition : MiteShields.definitions()) {
            ItemStack shield = new ItemStack(MiteShields.get(definition.material()));
            shield.setDamageValue(shield.getMaxDamage() - 1);
            shield.set(DataComponents.BASE_COLOR, DyeColor.GREEN);
            shield.set(DataComponents.BANNER_PATTERNS, matchingPatterns);
            String anvilMaterial = definition.material().equals("rusted_iron") ? "copper" : definition.material();
            var anvil = anvilMaterial.equals("iron") ? Blocks.ANVIL.defaultBlockState()
                    : MiteFunctionalBlocks.ANVILS.get(anvilMaterial).get().defaultBlockState();
            var nugget = MaterialAnvilRules.repairItem(definition.material());
            helper.assertTrue(shield.getItem().isValidRepairItem(shield, new ItemStack(nugget)), "Shield native repair predicate recognizes the same metal nugget");
            helper.assertFalse(shield.getItem().isValidRepairItem(shield, new ItemStack(Items.OAK_PLANKS)), "Iron and metal shields do not bypass source metal repairs using planks");
            var repair = MaterialAnvilRules.plan(player, anvil, shield, new ItemStack(nugget));
            helper.assertTrue(repair != null && repair.allowed(), "Matching metal anvil and nugget produce a source repair plan");
            int restored = shield.getMaxDamage() / 2;
            helper.assertValueEqual(repair.output().getDamageValue(), shield.getDamageValue() - restored, "One metal component uses the source two-nugget complete repair budget");
            helper.assertValueEqual(repair.consumed(), 1, "One nugget is consumed");
            helper.assertValueEqual(repair.anvilWear(), restored, "Modern shield wear uses its own durability units rather than the mining-tool multiplier");
            helper.assertValueEqual(repair.output().get(DataComponents.BASE_COLOR), DyeColor.GREEN, "Anvil repairs retain shield decoration");
            helper.assertValueEqual(repair.output().get(DataComponents.BANNER_PATTERNS), matchingPatterns, "Nugget repair retains exact banner patterns");
            helper.assertValueEqual(shield.getDamageValue(), shield.getMaxDamage() - 1, "Planning does not mutate input equipment");
            ItemStack matching = shield.copy();
            var combination = MaterialAnvilRules.plan(player, anvil, shield, matching);
            helper.assertTrue(combination != null && combination.allowed(), "Matching shield quality, base color and patterns allow anvil combination");
            helper.assertValueEqual(combination.output().getDamageValue(), shield.getMaxDamage() - 2, "Matching decorated shield combination adds exactly its two remaining points");
            helper.assertValueEqual(combination.output().get(DataComponents.BANNER_PATTERNS), matchingPatterns, "Anvil combination keeps the matching banner patterns");
            matching.set(DataComponents.BASE_COLOR, DyeColor.BLUE);
            var differentBase = MaterialAnvilRules.plan(player, anvil, shield, matching);
            helper.assertTrue(differentBase != null && !differentBase.allowed(), "Anvil combination cannot consume a shield with a different banner base color");
            matching.set(DataComponents.BASE_COLOR, DyeColor.GREEN);
            matching.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder().add(cross, DyeColor.BLACK).build());
            var differentPattern = MaterialAnvilRules.plan(player, anvil, shield, matching);
            helper.assertTrue(differentPattern != null && !differentPattern.allowed(), "Anvil combination cannot consume a shield with a different banner pattern color");
            matching.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder().add(helper.getLevel().registryAccess()
                    .registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(BannerPatterns.STRIPE_TOP), DyeColor.WHITE).build());
            var differentShape = MaterialAnvilRules.plan(player, anvil, shield, matching);
            helper.assertTrue(differentShape != null && !differentShape.allowed(), "Anvil combination cannot consume a shield with a different banner pattern shape");
            var wrong = MaterialAnvilRules.plan(player, anvil, shield, new ItemStack(Items.OAK_PLANKS));
            helper.assertTrue(wrong != null && !wrong.allowed(), "Source material planning rejects wood repairs too");
            if (definition.materialDurability() > 8F) {
                var inferior = MaterialAnvilRules.plan(player, Blocks.ANVIL.defaultBlockState(), shield, new ItemStack(nugget));
                helper.assertTrue(inferior != null && !inferior.allowed(), "Native iron anvils cannot repair higher metal shield tiers");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shieldWearUsesNativeBreakCallbacksAndDoesNotReceiveArmorLastPointProtection(GameTestHelper helper) {
        Zombie owner = new Zombie(EntityType.ZOMBIE, helper.getLevel());
        try {
            for (var definition : MiteShields.definitions()) {
                ItemStack shield = new ItemStack(MiteShields.get(definition.material()));
                owner.setItemSlot(EquipmentSlot.OFFHAND, shield);
                shield.setDamageValue(shield.getMaxDamage() - 1);
                EquipmentDurability.hurt(shield, 1, owner, EquipmentSlot.OFFHAND);
                helper.assertTrue(shield.isEmpty(), "Managed shield consumes its final point and breaks rather than retaining humanoid armor protection");
            }
        } finally { owner.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allMetalShieldsSupportNativeUnbreakingThroughDurabilityTags(GameTestHelper helper) {
        var unbreaking = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING);
        for (var definition : MiteShields.definitions()) {
            ItemStack shield = new ItemStack(MiteShields.get(definition.material()));
            helper.assertTrue(unbreaking.value().canEnchant(shield), "New and original metal shields are legal native durability enchantment targets");
            shield.enchant(unbreaking, 5);
            helper.assertValueEqual(shield.getEnchantmentLevel(unbreaking), 5, "Managed shield stores the source five-level Unbreaking range");
        }
        helper.succeed();
    }
}

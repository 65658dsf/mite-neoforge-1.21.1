package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteWeaponContent;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native registration, exact per-slot toughness and quality persistence for the later expansion. @author NingMeng123 */
@GameTestHolder("mite_vibranium")
@PrefixGameTestTemplate(false)
public final class VibraniumEquipmentGameTests {
    private VibraniumEquipmentGameTests() {}

    @GameTest(template = "empty")
    public static void allThirteenToolFamiliesHaveSourceStatsAndNativeAbilities(GameTestHelper helper) {
        var definitions = MiteTools.definitions().stream().filter(d -> d.material().equals("vibranium")).toList();
        helper.assertValueEqual(definitions.size(), 13, "Twelve metal tool families and the later vibranium knife exist");
        for (var definition : definitions) {
            ItemStack stack = new ItemStack(MiteTools.get(definition.id()));
            helper.assertFalse(stack.is(Items.AIR), "Registered expansion identity exists: " + definition.id());
            helper.assertValueEqual(definition.harvestLevel(), 6, "Source material can harvest tier-six blocks");
            helper.assertValueEqual(stack.getMaxStackSize(), 1, "Equipment is nonstacking");
            helper.assertValueEqual(stack.getItem().getEnchantmentValue(), 120, "Source enchantability is 120");
            helper.assertTrue(stack.has(DataComponents.FIRE_RESISTANT), "Vibranium resists fire and lava");
            check(helper, amount(stack, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND),
                    definition.damage(),
                    "Native attack component retains the source type and material damage bonus");
        }
        ItemStack pickaxe = new ItemStack(MiteTools.get("vibranium_pickaxe"));
        helper.assertValueEqual(pickaxe.getMaxDamage(), 614400, "Pickaxe retains source hundredfold tool capacity");
        helper.assertValueEqual(pickaxe.getDestroySpeed(Blocks.STONE.defaultBlockState()), 14F, "Mining efficiency uses source 3.5 times pickaxe base four");
        helper.assertTrue(pickaxe.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "Vibranium harvests high-tier vanilla minerals");
        ItemStack shears = new ItemStack(MiteTools.get("vibranium_shears"));
        helper.assertTrue(shears.canPerformAction(ItemAbilities.SHEARS_DIG), "Extended shears retain native shearing actions");
        helper.assertTrue(MiteTools.definition(new ItemStack(MiteTools.get("vibranium_knife"))).family() == ToolFamily.KNIFE,
                "The knife uses its own source reach and wear family");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pickaxeDurabilityBarUsesQualityAdjustedCapacity(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(MiteTools.get("vibranium_pickaxe"));
        int baseCapacity = pickaxe.getMaxDamage();
        pickaxe.setDamageValue(baseCapacity / 2);
        int halfDurabilityColor = pickaxe.getBarColor();

        EquipmentDurability.setQuality(pickaxe, Quality.EXCELLENT);
        int capacity = pickaxe.getMaxDamage();
        helper.assertValueEqual(capacity, 1228800, "Excellent vibranium pickaxe capacity");

        pickaxe.setDamageValue(capacity - baseCapacity);
        helper.assertValueEqual(pickaxe.getBarWidth(), 7, "The base capacity remaining fills half of the bar");
        helper.assertValueEqual(pickaxe.getBarColor(), halfDurabilityColor, "Half durability has the same color at either quality");

        int remaining = 469991;
        pickaxe.setDamageValue(capacity - remaining);
        helper.assertTrue(pickaxe.isBarVisible(), "The worn pickaxe still shows its durability bar");
        helper.assertValueEqual(pickaxe.getBarWidth(), 5, "Durability below the base capacity still fills the bar proportionally");
        helper.assertValueEqual(pickaxe.getBarColor(), Mth.hsvToRgb((float) remaining / capacity / 3F, 1F, 1F),
                "The worn pickaxe color reflects its remaining fraction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeVibraniumBowLaunchesWithSeventyFivePercentVelocityBonus(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter());
        player.getInventory().clearContent();
        player.experienceLevel = 200;
        player.totalExperience = 1_000_000;
        var bowItem = MiteWeaponContent.VIBRANIUM_BOW.get();
        ItemStack bow = new ItemStack(bowItem), ammunition = new ItemStack(MiteRangedItems.arrow(Material.MITHRIL), 2);
        helper.assertValueEqual(bow.getMaxDamage(), 512, "Native vibranium bow has source capacity512");
        helper.assertValueEqual(bow.getEnchantmentValue(), 120, "Native vibranium bow has source enchantability120");
        helper.assertTrue(bow.is(ItemTags.BOW_ENCHANTABLE) && bow.is(ItemTags.DURABILITY_ENCHANTABLE), "Real bow enchantment tags include vibranium");
        helper.assertTrue(bow.has(DataComponents.FIRE_RESISTANT), "Bow retains source material lava immunity");
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        player.getInventory().setItem(1, ammunition);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        bowItem.releaseUsing(bow, helper.getLevel(), player, 71980);
        player.stopUsingItem();
        var arrows = helper.getLevel().getEntitiesOfClass(Arrow.class, player.getBoundingBox().inflate(5))
                .stream().filter(arrow -> arrow.getOwner() == player).toList();
        try {
            helper.assertValueEqual(arrows.size(), 1, "Actual native release creates exactly one projectile");
            helper.assertValueEqual(ammunition.getCount(), 1, "Actual release consumes one ordinary material arrow");
            helper.assertValueEqual(bow.getDamageValue(), 1, "Actual release consumes one bow durability point");
            helper.assertTrue(Math.abs(arrows.getFirst().getDeltaMovement().length() - 5.25D) < 0.1D,
                    "Source full pull velocity3 receives the documented75 percent bonus");
            helper.assertTrue(arrows.getFirst().isCritArrow(), "Full pull retains native critical projectile behavior");
        } finally { arrows.forEach(Arrow::discard); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void armorHasBalancedUniformToughnessAfterDefaultComponentModification(GameTestHelper helper) {
        String[] slots = {"helmet", "chestplate", "leggings", "boots"};
        int[] defense = {5, 7, 6, 4}, durability = {5120, 8192, 7168, 4096};
        double[] toughness = {4D, 4D, 4D, 4D};
        double totalKnockbackResistance = 0D;
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = new ItemStack(MiteArmorContent.get("mite:vibranium_" + slots[i]));
            var armor = (ArmorItem)stack.getItem();
            helper.assertValueEqual(stack.getMaxDamage(), durability[i], "Armor capacity retains this project's source scale");
            check(helper, amount(stack, Attributes.ARMOR, armor.getEquipmentSlot()), defense[i], "Armor uses the requested version's slot defense");
            check(helper, amount(stack, Attributes.ARMOR_TOUGHNESS, armor.getEquipmentSlot()), toughness[i], "Default components preserve balanced material toughness");
            totalKnockbackResistance += amount(stack, Attributes.KNOCKBACK_RESISTANCE, armor.getEquipmentSlot());
            EquipmentDurability.setQuality(stack, Quality.LEGENDARY);
            check(helper, amount(stack, Attributes.ARMOR_TOUGHNESS, armor.getEquipmentSlot()), toughness[i], "Quality increases capacity while intact toughness remains stable");
            helper.assertTrue(stack.has(DataComponents.FIRE_RESISTANT), "All four armor slots retain lava immunity");
        }
        check(helper, totalKnockbackResistance, .6D, "The four armor pieces provide the documented six displayed points of knockback resistance");
        ItemStack shield = new ItemStack(MiteShields.get("vibranium"));
        helper.assertTrue(shield.getItem() instanceof ShieldItem && shield.canPerformAction(ItemAbilities.SHIELD_BLOCK),
                "Modern shield extension uses the native block action");
        helper.assertValueEqual(shield.getMaxDamage(), 21504, "Modern shield extension keeps the established metal capacity ratio");
        helper.assertTrue(shield.has(DataComponents.FIRE_RESISTANT), "Shield also retains material lava immunity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legendaryQualityAndWearSurviveSaveAndNetworkForEveryNewCoreEquipmentItem(GameTestHelper helper) {
        List<ItemStack> equipment = new ArrayList<>();
        MiteTools.definitions().stream().filter(d -> d.material().equals("vibranium"))
                .forEach(d -> equipment.add(new ItemStack(MiteTools.get(d.id()))));
        MiteArmorContent.definitions().stream().filter(d -> d.material().equals("vibranium"))
                .forEach(d -> equipment.add(new ItemStack(MiteArmorContent.get(d.id()))));
        equipment.add(new ItemStack(MiteShields.get("vibranium")));
        equipment.add(new ItemStack(MiteWeaponContent.VIBRANIUM_BOW.get()));
        helper.assertValueEqual(equipment.size(), 19, "All thirteen tools, four armor pieces, shield and native bow are checked");
        for (ItemStack stack : equipment) {
            int averageCapacity = stack.getMaxDamage();
            EquipmentDurability.setQuality(stack, Quality.LEGENDARY);
            float legendaryMultiplier = stack.getItem() instanceof ShieldItem ? 2.625F : 3.5F;
            helper.assertValueEqual(stack.getMaxDamage(), Math.round(averageCapacity * legendaryMultiplier), "Legendary quality applies exactly once");
            stack.setDamageValue(stack.getMaxDamage() / 3);
            ItemStack saved = ItemStack.parse(helper.getLevel().registryAccess(), stack.save(helper.getLevel().registryAccess())).orElseThrow();
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                ItemStack.STREAM_CODEC.encode(buffer, saved);
                ItemStack decoded = ItemStack.STREAM_CODEC.decode(buffer);
                helper.assertTrue(ItemStack.isSameItemSameComponents(stack, decoded), "Material identity, legendary quality and wear survive both transports");
                helper.assertValueEqual(decoded.getMaxDamage(), stack.getMaxDamage(), "Save and network preserve scaled capacity");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    private static double amount(ItemStack stack, Holder<Attribute> attribute, EquipmentSlot slot) {
        double[] total = {0D};
        stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).forEach(slot, (candidate, modifier) -> {
            if (candidate.equals(attribute) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) total[0] += modifier.amount();
        });
        return total[0];
    }

    private static void check(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 0.00001D, message + ": expected " + expected + ", got " + actual);
    }
}

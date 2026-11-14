package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily.*;

/** Real component events retain source damage and normalize saved bases with independent bonuses. @author NingMeng123 */
@GameTestHolder("mite_gameplay")
@PrefixGameTestTemplate(false)
public final class NativeWeaponBalanceGameTests {
    private NativeWeaponBalanceGameTests() {}

    @GameTest(template = "empty")
    public static void swordsHeavyWeaponsAndModernWeaponsHaveTheirSelectedActualBudgets(GameTestHelper h) {
        for (var vector : List.of(
                new Vector("mite:rusted_iron_sword", 7D, 1.5D),
                new Vector("minecraft:golden_sword", 7D, 1.5D),
                new Vector("mite:copper_sword", 8D, 1.5D),
                new Vector("mite:silver_sword", 8D, 1.5D),
                new Vector("minecraft:iron_sword", 9D, 1.5D),
                new Vector("mite:ancient_metal_sword", 9D, 1.5D),
                new Vector("mite:mithril_sword", 10D, 1.5D),
                new Vector("mite:adamantium_sword", 11D, 1.5D),
                new Vector("mite:vibranium_sword", 14D, 1.5D),
                new Vector("mite:iron_battle_axe", 9D, .85D),
                new Vector("mite:iron_war_hammer", 7D, .75D),
                new Vector("mite:iron_dagger", 7D, 1.8D),
                new Vector("mite:vibranium_knife", 11D, 2D),
                new Vector("mite:iron_scythe", 6D, .8D),
                new Vector("minecraft:wooden_hoe", 1D, 1.1D),
                new Vector("minecraft:wooden_sword", 3D, 1.2D),
                new Vector("mite:wooden_cudgel", 2D, 1.6D),
                new Vector("mite:flint_knife", 3D, 2D),
                new Vector("mite:obsidian_knife", 4D, 2D),
                new Vector("mite:flint_hatchet", 4D, 1.25D),
                new Vector("minecraft:trident", 6D, .9D),
                new Vector("minecraft:mace", 10.5D, .6D))) {
            ItemStack stack = new ItemStack(MiteTools.get(vector.item()));
            close(h, 1D + amount(stack, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND), vector.damage(),
                    "Actual selected total damage " + vector.item());
            close(h, 4D + amount(stack, Attributes.ATTACK_SPEED, EquipmentSlot.MAINHAND), vector.speed(),
                    "Actual family cooldown " + vector.item());
            close(h, amount(stack, Attributes.ATTACK_DAMAGE, EquipmentSlot.OFFHAND), 0D,
                    "Offhand cannot add a second weapon base");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everySourceDefinitionRetainsItsMaterialAndTypeAttackBonus(GameTestHelper h) {
        Map<String, Double> materialDamage = Map.ofEntries(
                Map.entry("wood", 0D), Map.entry("flint", 1D), Map.entry("obsidian", 2D),
                Map.entry("rusted_iron", 2D), Map.entry("gold", 2D),
                Map.entry("copper", 3D), Map.entry("silver", 3D),
                Map.entry("iron", 4D), Map.entry("ancient_metal", 4D),
                Map.entry("mithril", 5D), Map.entry("adamantium", 6D), Map.entry("vibranium", 9D));
        Map<ToolFamily, Double> typeDamage = Map.ofEntries(
                Map.entry(SWORD, 4D), Map.entry(BATTLE_AXE, 4D), Map.entry(AXE, 3D),
                Map.entry(DAGGER, 2D), Map.entry(WAR_HAMMER, 2D), Map.entry(HATCHET, 2D),
                Map.entry(PICKAXE, 2D), Map.entry(CLUB, 2D),
                Map.entry(KNIFE, 1D), Map.entry(CUDGEL, 1D), Map.entry(SHOVEL, 1D),
                Map.entry(HOE, 1D), Map.entry(MATTOCK, 1D), Map.entry(SCYTHE, 1D), Map.entry(SHEARS, 0D));
        for (var definition : MiteTools.definitions()) {
            h.assertTrue(materialDamage.containsKey(definition.material()), "A source material fixture covers " + definition.id());
            double expected = materialDamage.get(definition.material()) + typeDamage.get(definition.family());
            close(h, definition.damage(), expected, "The registered source definition retains the independently extracted bonus");
            ItemStack stack = new ItemStack(MiteTools.get(definition.id()));
            close(h, amount(stack, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND), expected,
                    "The normal equipped component retains the exact source bonus " + definition.id());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyRegisteredCopperAndSilverFamilyHasItsSourceDamage(GameTestHelper h) {
        String[] families = {"sword", "dagger", "battle_axe", "war_hammer", "axe", "hatchet",
                "pickaxe", "shovel", "hoe", "mattock", "scythe", "shears"};
        double[] expected = {8D, 6D, 8D, 6D, 7D, 6D, 6D, 5D, 5D, 5D, 5D, 4D};
        for (int index = 0; index < families.length; index++) {
            var copper = new ItemStack(MiteTools.get("copper_" + families[index]));
            var silver = new ItemStack(MiteTools.get("silver_" + families[index]));
            close(h, 1D + amount(copper, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND), expected[index],
                    "Actual copper " + families[index] + " includes the source bonus and one player base point");
            close(h, 1D + amount(silver, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND), expected[index],
                    "Actual silver " + families[index] + " shares copper's ordinary source damage");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oldSavedHighBaseCannotBypassSelectedStatsAndOtherBonusesSurviveRepeatedReads(GameTestHelper h) {
        ItemStack stack = new ItemStack(MiteTools.get("vibranium_sword"));
        var extra = ResourceLocation.fromNamespaceAndPath("mite", "balance_fixture_extra");
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 13D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 999D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.4D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, 99D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY)
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(extra, 2.5D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build());
        var tag = stack.save(h.getLevel().registryAccess());
        ItemStack loaded = ItemStack.parse(h.getLevel().registryAccess(), tag).orElseThrow();
        for (int read = 0; read < 10; read++) {
            close(h, 1D + amount(loaded, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND), 16.5D,
                    "Saved base is 14 and the independent 2.5 bonus applies once");
            close(h, 4D + amount(loaded, Attributes.ATTACK_SPEED, EquipmentSlot.MAINHAND), 1.5D,
                    "Saved speed is normalized once");
            close(h, amount(loaded, Attributes.ATTACK_DAMAGE, EquipmentSlot.OFFHAND), 0D,
                    "Duplicate saved ANY bases cannot leak into the offhand");
            h.assertValueEqual(loaded.getAttributeModifiers().modifiers().stream().filter(e -> e.modifier().id().equals(extra)).count(), 1L,
                    "Repeated reads retain one independent attribute");
        }
        h.assertValueEqual(loaded.save(h.getLevel().registryAccess()), tag, "Normalization preserves the saved component payload");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void savedStarterAndModernWeaponsRebuildCanonicalStatsAndRetainIndependentBonuses(GameTestHelper h) {
        for (var vector : List.of(
                new Vector("minecraft:wooden_sword", 3D, 1.2D),
                new Vector("mite:wooden_cudgel", 2D, 1.6D),
                new Vector("mite:flint_knife", 3D, 2D),
                new Vector("mite:obsidian_knife", 4D, 2D),
                new Vector("mite:flint_hatchet", 4D, 1.25D),
                new Vector("minecraft:wooden_hoe", 1D, 1.1D),
                new Vector("minecraft:iron_sword", 9D, 1.5D),
                new Vector("minecraft:trident", 6D, .9D),
                new Vector("minecraft:mace", 10.5D, .6D))) {
            ItemStack stack = new ItemStack(MiteTools.get(vector.item()));
            var extraDamage = ResourceLocation.fromNamespaceAndPath("mite", "saved_family_damage");
            var extraSpeed = ResourceLocation.fromNamespaceAndPath("mite", "saved_family_speed");
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                    .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 99D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY)
                    .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, 99D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY)
                    .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(extraDamage, 2.5D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                    .add(Attributes.ATTACK_SPEED, new AttributeModifier(extraSpeed, .25D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build());
            var tag = stack.save(h.getLevel().registryAccess());
            ItemStack loaded = ItemStack.parse(h.getLevel().registryAccess(), tag).orElseThrow();
            for (int read = 0; read < 4; read++) {
                close(h, 1D + amount(loaded, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND), vector.damage() + 2.5D,
                        "Saved weapon retains its source damage and independent bonus " + vector.item());
                close(h, 4D + amount(loaded, Attributes.ATTACK_SPEED, EquipmentSlot.MAINHAND), vector.speed() + .25D,
                        "Saved weapon receives its selected family speed and independent bonus " + vector.item());
                close(h, amount(loaded, Attributes.ATTACK_DAMAGE, EquipmentSlot.OFFHAND), 0D, "Canonical ANY damage becomes main-hand only");
                close(h, amount(loaded, Attributes.ATTACK_SPEED, EquipmentSlot.OFFHAND), 0D, "Canonical ANY speed becomes main-hand only");
                h.assertValueEqual(loaded.getAttributeModifiers().modifiers().stream().filter(entry -> entry.modifier().id().equals(extraDamage)).count(), 1L,
                        "Repeated reads retain one independent damage modifier");
                h.assertValueEqual(loaded.getAttributeModifiers().modifiers().stream().filter(entry -> entry.modifier().id().equals(extraSpeed)).count(), 1L,
                        "Repeated reads retain one independent speed modifier");
            }
            h.assertValueEqual(loaded.save(h.getLevel().registryAccess()), tag, "Saved payload remains stable " + vector.item());
        }
        h.succeed();
    }

    private record Vector(String item, double damage, double speed) {}

    private static double amount(ItemStack stack, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            EquipmentSlot slot) {
        double[] value = {0D};
        stack.forEachModifier(slot, (candidate, modifier) -> {
            if (candidate.equals(attribute) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE)
                value[0] += modifier.amount();
        });
        return value[0];
    }

    private static void close(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < .00001D, message + " " + actual + " vs " + expected);
    }
}

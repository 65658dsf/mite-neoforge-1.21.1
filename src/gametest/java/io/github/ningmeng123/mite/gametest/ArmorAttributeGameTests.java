package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.rules.ArmorBalanceRules;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Default components, real equipment slots, mixed sets and native save/network migration. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ArmorAttributeGameTests {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private ArmorAttributeGameTests() {}

    @GameTest(template = "empty")
    public static void everyRegisteredHumanoidArmorMatchesMaterialAndDefaultComponents(GameTestHelper helper) {
        int checked = 0;
        for (var definition : MiteArmorContent.definitions()) {
            if (definition.horse()) continue;
            ItemStack stack = new ItemStack(MiteArmorContent.get(definition.id()));
            var armor = (ArmorItem)stack.getItem();
            var stats = ArmorBalanceRules.stats(definition.material(), definition.chainmail());
            double protection = stats.protection(definition.components());
            check(helper, amount(stack, Attributes.ARMOR, armor.getEquipmentSlot()), protection,
                    "Native item component uses modern defense: " + definition.id());
            check(helper, amount(stack, Attributes.ARMOR_TOUGHNESS, armor.getEquipmentSlot()), stats.toughness(definition.components()),
                    "Native item component exposes material toughness: " + definition.id());
            if (definition.isNew()) {
                helper.assertValueEqual(armor.getMaterial().value().getDefense(armor.getType()), (int)protection,
                        "Registered ArmorMaterial agrees with actual component defense");
                check(helper, armor.getMaterial().value().toughness(), stats.toughness(),
                        "Registered ArmorMaterial agrees with actual component toughness");
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot == armor.getEquipmentSlot()) continue;
                check(helper, amount(stack, Attributes.ARMOR, slot), 0D, "Wrong slot cannot supply armor: " + definition.id() + " in " + slot);
                check(helper, amount(stack, Attributes.ARMOR_TOUGHNESS, slot), 0D, "Wrong slot cannot supply toughness");
            }
            checked++;
        }
        helper.assertValueEqual(checked, 64, "All 65 source identities except the five horse armors checked");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void removedDiamondAndNetheriteArmorCannotLoadFromSavedStacks(GameTestHelper helper) {
        for (Item item : new Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
                Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS}) {
            ItemStack stack = new ItemStack(item);
            helper.assertTrue(RemovedDiamondEquipment.isRemoved(stack), "Retired armor belongs to the removed equipment set: " + item);
            helper.assertTrue(ItemStack.parse(helper.getLevel().registryAccess(), stack.save(helper.getLevel().registryAccess()))
                    .map(ItemStack::isEmpty).orElse(true), "Legacy retired armor cannot return through saved item loading: " + item);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void retainedLeatherIronGoldAndChainKeepTheirModernLowTierProtection(GameTestHelper helper) {
        Item[][] sets = {
                {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
                {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
                {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS},
                {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS}
        };
        int[][] expected = {{1, 3, 2, 1}, {2, 5, 5, 2}, {2, 4, 3, 1}, {2, 4, 4, 1}};
        for (int set = 0; set < sets.length; set++) {
            for (int piece = 0; piece < SLOTS.length; piece++) {
                var stack = new ItemStack(sets[set][piece]);
                check(helper, amount(stack, Attributes.ARMOR, SLOTS[piece]), expected[set][piece], "Retained low-tier armor uses independent modern slot values");
                check(helper, amount(stack, Attributes.ARMOR_TOUGHNESS, SLOTS[piece]), new double[]{.25D,1D,.5D,.75D}[set], "Every retained low-tier material supplies positive toughness");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void horseArmorHasOnePositiveBodyToughnessContributionWithoutHumanoidWear(GameTestHelper helper) {
        for (var definition : MiteArmorContent.definitions()) {
            if (!definition.horse()) continue;
            var stack = new ItemStack(MiteArmorContent.get(definition.id()));
            check(helper, amount(stack, Attributes.ARMOR, EquipmentSlot.BODY), definition.horseProtection(), "Horse keeps its source protection");
            check(helper, amount(stack, Attributes.ARMOR_TOUGHNESS, EquipmentSlot.BODY), ArmorBalanceRules.stats(definition.material(),false).toughness(), "Barding supplies its own material toughness in BODY");
            helper.assertFalse(EquipmentDurability.isManaged(stack), "Horse barding has no invented humanoid quality/wear treatment");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realZombieMixedEquipmentUsesIndependentSlotsAndDoesNotGrantArmorFromHands(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        try {
            zombie.tick();
            double baselineArmor = zombie.getAttributeValue(Attributes.ARMOR);
            double baselineToughness = zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            zombie.setItemSlot(EquipmentSlot.MAINHAND, stack("mite:adamantium_chestplate"));
            zombie.setItemSlot(EquipmentSlot.OFFHAND, stack("mite:mithril_helmet"));
            zombie.tick();
            check(helper, zombie.getAttributeValue(Attributes.ARMOR), baselineArmor, "Holding armor in either hand adds no protection");
            check(helper, zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS), baselineToughness, "Holding armor in either hand adds no toughness");
            zombie.setItemSlot(EquipmentSlot.HEAD, stack("mite:copper_helmet"));
            zombie.setItemSlot(EquipmentSlot.CHEST, stack("mite:mithril_chestplate"));
            zombie.setItemSlot(EquipmentSlot.LEGS, stack("mite:ancient_metal_leggings"));
            zombie.setItemSlot(EquipmentSlot.FEET, stack("mite:adamantium_chain_boots"));
            zombie.tick();
            check(helper, zombie.getAttributeValue(Attributes.ARMOR), baselineArmor + 17D,
                    "Real native equipment application sums copper two + mithril seven + ancient five + adamantium-chain three");
            check(helper, zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS), baselineToughness + 5.875D,
                    "Mixed material toughness sums .75 +2 +1.25 +1.875");
            zombie.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            zombie.tick();
            check(helper, zombie.getAttributeValue(Attributes.ARMOR), baselineArmor + 10D, "Unequipping chest removes exactly its modifier");
            check(helper, zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS), baselineToughness + 3.875D, "Unequipping chest removes exactly two toughness");
        } finally { zombie.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mobArmorAtLastDurabilityPointKeepsProtectionAndQualityDoesNotAddDefense(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        try {
            zombie.tick();
            double baseline = zombie.getAttributeValue(Attributes.ARMOR);
            ItemStack chest = stack("mite:mithril_chestplate");
            zombie.setItemSlot(EquipmentSlot.CHEST, chest);
            zombie.tick();
            check(helper, zombie.getAttributeValue(Attributes.ARMOR), baseline + 7D, "Fresh NPC chestpiece supplies seven protection");
            EquipmentDurability.setQuality(chest, Quality.LEGENDARY);
            chest.setDamageValue(chest.getMaxDamage() - 1);
            zombie.tick();
            check(helper, zombie.getAttributeValue(Attributes.ARMOR), baseline + 7D,
                    "NPC armor does not accidentally inherit the player last-point factor or a quality multiplier");
            check(helper, zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 2D,
                    "NPC toughness depends on material, not quality or wear");
        } finally { zombie.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oldStackWithoutExplicitAttributePatchInheritsModernDefaultsOnSaveReload(GameTestHelper helper) {
        ItemStack original = stack("mite:mithril_chestplate");
        EquipmentDurability.setQuality(original, Quality.LEGENDARY);
        original.setDamageValue(900);
        CompoundTag saved = (CompoundTag)original.save(helper.getLevel().registryAccess());
        helper.assertFalse(saved.getCompound("components").contains("minecraft:attribute_modifiers"),
                "Normal old-world equipment has no per-stack armor override; only non-default components are saved");
        ItemStack restored = ItemStack.parse(helper.getLevel().registryAccess(), saved).orElseThrow();
        check(helper, amount(restored, Attributes.ARMOR, EquipmentSlot.CHEST), 7D, "NBT stack resolves the new default defense");
        check(helper, amount(restored, Attributes.ARMOR_TOUGHNESS, EquipmentSlot.CHEST), 2D, "NBT stack resolves the new default toughness");
        helper.assertValueEqual(restored.getDamageValue(), 900, "The migration retains actual wear");
        helper.assertValueEqual(restored.getMaxDamage(), 3584, "The migration retains R196 quality-adjusted durability");
        helper.assertValueEqual(EquipmentDurability.quality(restored), Quality.LEGENDARY, "The migration retains quality");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explicitAttributeComponentsSurviveNativeNbtAndNetworkWithoutDefaultOverwrite(GameTestHelper helper) {
        ItemStack original = stack("mite:mithril_chestplate");
        var modifiers = ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, custom("command_armor", 11.25D), EquipmentSlotGroup.CHEST)
                .add(Attributes.ARMOR_TOUGHNESS, custom("command_toughness", 6.75D), EquipmentSlotGroup.CHEST)
                .add(Attributes.KNOCKBACK_RESISTANCE, custom("command_knockback", .125D), EquipmentSlotGroup.CHEST)
                .build().withTooltip(false);
        original.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
        EquipmentDurability.setQuality(original, Quality.LEGENDARY);
        original.setDamageValue(original.getMaxDamage() - 1);
        CompoundTag saved = (CompoundTag)original.save(helper.getLevel().registryAccess());
        helper.assertTrue(saved.getCompound("components").contains("minecraft:attribute_modifiers"),
                "The fixture really saves an explicit command/datapack attribute override");
        ItemStack restored = ItemStack.parse(helper.getLevel().registryAccess(), saved).orElseThrow();
        helper.assertValueEqual(restored.get(DataComponents.ATTRIBUTE_MODIFIERS), modifiers, "Explicit NBT component is not regenerated from the material table");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, restored);
            ItemStack decoded = ItemStack.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decoded.get(DataComponents.ATTRIBUTE_MODIFIERS), modifiers, "Native network component keeps IDs, slot, values and tooltip visibility");
            helper.assertValueEqual(decoded.getDamageValue(), 3583, "Network preserves last-point wear");
            helper.assertValueEqual(decoded.getMaxDamage(), 3584, "Network preserves quality maximum once");
            Zombie zombie = zombie(helper);
            try {
                zombie.tick();
                double baseline = zombie.getAttributeValue(Attributes.ARMOR);
                zombie.setItemSlot(EquipmentSlot.CHEST, decoded);
                zombie.tick();
                check(helper, zombie.getAttributeValue(Attributes.ARMOR), baseline + 11.25D, "Explicit armor works through the real NPC equipment refresh");
                check(helper, zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 6.75D, "Explicit toughness works through the real NPC equipment refresh");
                check(helper, zombie.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), .125D, "Explicit unrelated attribute survives refresh");
            } finally { zombie.discard(); }
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void oldExplicitBaseArmorNbtGetsBalancedStatsAndPositiveToughnessWithoutLosingCustomModifiers(GameTestHelper helper) {
        ItemStack old=new ItemStack(Items.IRON_CHESTPLATE);
        var savedModifiers=ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR,new AttributeModifier(ResourceLocation.withDefaultNamespace("armor.chestplate"),6D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.CHEST)
                .add(Attributes.KNOCKBACK_RESISTANCE,custom("legacy_knockback",.125D),EquipmentSlotGroup.CHEST).build();
        old.set(DataComponents.ATTRIBUTE_MODIFIERS,savedModifiers);
        var restored=ItemStack.parse(helper.getLevel().registryAccess(),old.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertValueEqual(restored.get(DataComponents.ATTRIBUTE_MODIFIERS),savedModifiers,"Saved legacy data and custom identities remain exact");
        check(helper,effectiveAmount(restored,Attributes.ARMOR,EquipmentSlot.CHEST),5D,"Recognized old iron base six is normalized to the new five");
        check(helper,effectiveAmount(restored,Attributes.ARMOR_TOUGHNESS,EquipmentSlot.CHEST),1D,"An explicit legacy patch lacking toughness gains the iron baseline");
        check(helper,effectiveAmount(restored,Attributes.KNOCKBACK_RESISTANCE,EquipmentSlot.CHEST),.125D,"Custom unrelated modifier survives normalization");
        check(helper,effectiveAmount(restored,Attributes.ARMOR_TOUGHNESS,EquipmentSlot.MAINHAND),0D,"A saved patch cannot grant baseline toughness from a hand");
        Zombie wearer=zombie(helper);
        try {
            wearer.tick(); double baseline=wearer.getAttributeValue(Attributes.ARMOR);
            wearer.setItemSlot(EquipmentSlot.CHEST,restored); wearer.tick();
            check(helper,wearer.getAttributeValue(Attributes.ARMOR),baseline+5D,"Loaded explicit base armor enters real equipped attributes");
            check(helper,wearer.getAttributeValue(Attributes.ARMOR_TOUGHNESS),1D,"Loaded explicit base toughness enters real equipped attributes");
        } finally {wearer.discard();}
        ItemStack oldMithril=stack("mite:mithril_chestplate");
        oldMithril.set(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR,new AttributeModifier(ResourceLocation.withDefaultNamespace("armor.chestplate"),9D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.CHEST)
                .add(Attributes.ARMOR_TOUGHNESS,new AttributeModifier(ResourceLocation.withDefaultNamespace("armor.chestplate"),3D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.CHEST)
                .add(Attributes.ARMOR,custom("legacy_bonus",1.25D),EquipmentSlotGroup.CHEST).build());
        check(helper,effectiveAmount(oldMithril,Attributes.ARMOR,EquipmentSlot.CHEST),8.25D,"New mithril base seven and custom armor bonus are both preserved");
        check(helper,effectiveAmount(oldMithril,Attributes.ARMOR_TOUGHNESS,EquipmentSlot.CHEST),2D,"Old explicit base toughness three becomes the new two");
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void repeatedLegacyArmorBaseIdsNormalizeOnceInTheArmorSlotWithoutChangingCustomTerms(GameTestHelper helper) {
        ItemStack old=stack("mite:mithril_chestplate");
        var canonical=ResourceLocation.withDefaultNamespace("armor.chestplate");
        var saved=ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR,new AttributeModifier(canonical,9D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ARMOR,new AttributeModifier(canonical,9D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.ANY)
                .add(Attributes.ARMOR,new AttributeModifier(canonical,9D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.CHEST)
                .add(Attributes.ARMOR_TOUGHNESS,new AttributeModifier(canonical,3D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ARMOR_TOUGHNESS,new AttributeModifier(canonical,3D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.ANY)
                .add(Attributes.ARMOR_TOUGHNESS,new AttributeModifier(canonical,3D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.CHEST)
                .add(Attributes.ARMOR,custom("duplicate_patch_bonus",1.25D),EquipmentSlotGroup.CHEST)
                .add(Attributes.KNOCKBACK_RESISTANCE,custom("duplicate_patch_knockback",.125D),EquipmentSlotGroup.CHEST).build();
        old.set(DataComponents.ATTRIBUTE_MODIFIERS,saved);
        ItemStack restored=ItemStack.parse(helper.getLevel().registryAccess(),old.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertValueEqual(restored.get(DataComponents.ATTRIBUTE_MODIFIERS),saved,"Normalization does not rewrite saved duplicate data or custom identities");
        check(helper,effectiveAmount(restored,Attributes.ARMOR,EquipmentSlot.CHEST),8.25D,"Repeated nine-point bases become one seven-point base plus the custom bonus");
        check(helper,effectiveAmount(restored,Attributes.ARMOR_TOUGHNESS,EquipmentSlot.CHEST),2D,"Repeated toughness bases become exactly one balanced baseline");
        for (EquipmentSlot wrong:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.HEAD,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.BODY}) {
            check(helper,effectiveAmount(restored,Attributes.ARMOR,wrong),0D,"Canonical saved armor cannot leak through " + wrong);
            check(helper,effectiveAmount(restored,Attributes.ARMOR_TOUGHNESS,wrong),0D,"Canonical saved toughness cannot leak through " + wrong);
        }
        check(helper,effectiveAmount(restored,Attributes.KNOCKBACK_RESISTANCE,EquipmentSlot.CHEST),.125D,"Custom unrelated effect survives duplicate cleanup");
        Zombie wearer=zombie(helper);
        try {
            wearer.tick(); double baseline=wearer.getAttributeValue(Attributes.ARMOR);
            wearer.setItemSlot(EquipmentSlot.MAINHAND,restored); wearer.tick();
            check(helper,wearer.getAttributeValue(Attributes.ARMOR),baseline,"Holding loaded armor grants no base armor");
            check(helper,wearer.getAttributeValue(Attributes.ARMOR_TOUGHNESS),0D,"Holding loaded armor grants no base toughness");
            wearer.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY); wearer.setItemSlot(EquipmentSlot.CHEST,restored); wearer.tick();
            check(helper,wearer.getAttributeValue(Attributes.ARMOR),baseline+8.25D,"Real equipped armor receives one normalized base and the custom armor bonus");
            check(helper,wearer.getAttributeValue(Attributes.ARMOR_TOUGHNESS),2D,"Real equipped toughness receives one normalized base");
            check(helper,wearer.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),.125D,"Real equipment keeps the custom unrelated effect");
        } finally {wearer.discard();}
        helper.succeed();
    }

    private static ItemStack stack(String id) { return new ItemStack(MiteArmorContent.get(id)); }

    private static AttributeModifier custom(String name, double amount) {
        return new AttributeModifier(ResourceLocation.fromNamespaceAndPath("mite", "test_" + name), amount, AttributeModifier.Operation.ADD_VALUE);
    }

    private static double amount(ItemStack stack, Holder<Attribute> attribute, EquipmentSlot slot) {
        double[] total = {0D};
        stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).forEach(slot, (candidate, modifier) -> {
            if (candidate.equals(attribute) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) total[0] += modifier.amount();
        });
        return total[0];
    }

    /** Read the NeoForge event result while amount() above deliberately verifies stored/default components. */
    private static double effectiveAmount(ItemStack stack, Holder<Attribute> attribute, EquipmentSlot slot) {
        double[] total = {0D};
        stack.getAttributeModifiers().forEach(slot, (candidate, modifier) -> {
            if (candidate.equals(attribute) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) total[0] += modifier.amount();
        });
        return total[0];
    }

    private static Zombie zombie(GameTestHelper helper) {
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "Native zombie fixture is available");
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setPersistenceRequired();
        zombie.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5D, 2D, 1.5D)));
        for (EquipmentSlot slot : EquipmentSlot.values()) zombie.setItemSlot(slot, ItemStack.EMPTY);
        helper.getLevel().addFreshEntity(zombie);
        return zombie;
    }

    private static void check(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .00001D, message + ": expected " + expected + ", got " + actual);
    }
}

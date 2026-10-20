package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsHandling;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real optional weapon attributes, equipment refresh and Better Combat cooldowns. @author NingMeng123 */
@GameTestHolder("mite_simplyswords")
@PrefixGameTestTemplate(false)
public final class SimplySwordsHandlingGameTests {
    private static final ResourceLocation ATTACK = id("mite:simplyswords_two_handed_attack_speed");
    private static final ResourceLocation MOVEMENT = id("mite:simplyswords_two_handed_movement_speed");
    private static final Set<String> TWO_HANDED_FAMILIES = Set.of("twinblade", "katana", "glaive", "claymore",
            "greataxe", "greathammer", "scythe", "halberd");

    private SimplySwordsHandlingGameTests() {}

    @GameTest(template = "empty", batch = "simplyswords_handling")
    public static void optionalHandlingLeavesEmptyAndUnrelatedWeaponsUntouched(GameTestHelper h) {
        for (ItemStack stack : List.of(ItemStack.EMPTY, new ItemStack(Items.IRON_SWORD),
                new ItemStack(Items.TRIDENT), new ItemStack(Items.STICK))) {
            h.assertFalse(SimplySwordsHandling.twoHanded(stack), "Only managed Simply Swords weapons use this handling rule");
            assertPenalties(h, stack, false);
        }
        if (!SimplySwordsCompat.enabled()) {
            h.assertTrue(SimplySwordsCompat.weapon("copper", "claymore") == Items.AIR,
                    "Absent Simply Swords registers no substitute two-handed weapon");
            h.assertTrue(BuiltInRegistries.ITEM.keySet().stream().noneMatch(item -> item.getNamespace().equals("mite")
                    && item.getPath().startsWith("simplyswords_")), "Absent optional mod creates no managed identities");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_handling")
    public static void nativeAddedRunicAndUniqueWeaponsUseTheirActualHandedness(GameTestHelper h) {
        if (absent(h)) return;
        for (String material : List.of("rusted_iron", "copper", "silver", "gold", "iron", "diamond",
                "runic", "ancient_metal", "mithril", "adamantium", "vibranium")) {
            for (String family : SimplySwordsCompat.families()) {
                ItemStack stack = new ItemStack(SimplySwordsCompat.weapon(material, family));
                String label = material + "/" + family;
                h.assertFalse(stack.isEmpty(), "Handedness fixture is a real weapon: " + label);
                boolean expected = TWO_HANDED_FAMILIES.contains(family);
                h.assertValueEqual(SimplySwordsHandling.twoHanded(stack), expected,
                        "Actual two-handed families include twinblade and katana but exclude throwable spears: " + label);
                h.assertValueEqual(stack.is(SimplySwordsHandling.TWO_HANDED), expected,
                        "Data tag supplies the same classification when Better Combat is unavailable: " + label);
                assertPenalties(h, stack, expected);
            }
        }
        for (String path : List.of("magiscythe", "hearthflame", "awakened_lichblade", "whisperwind", "sword_on_a_stick")) {
            ItemStack stack = nativeWeapon(h, path);
            h.assertTrue(SimplySwordsHandling.twoHanded(stack), "Unique weapon keeps its real two-handed profile: " + path);
            assertPenalties(h, stack, true);
        }
        for (String path : List.of("soulrender", "soulstalker", "mjolnir", "frostfall", "molten_edge", "livyatan")) {
            ItemStack stack = nativeWeapon(h, path);
            h.assertFalse(SimplySwordsHandling.twoHanded(stack), "Durability family does not turn a one-handed unique weapon into two-handed: " + path);
            assertPenalties(h, stack, false);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_handling")
    public static void repeatedAttributeQueriesRetainOriginalDamageAndNeverPersistOrDuplicatePenalties(GameTestHelper h) {
        if (absent(h)) return;
        for (ItemStack stack : List.of(new ItemStack(SimplySwordsCompat.weapon("vibranium", "claymore")),
                new ItemStack(SimplySwordsCompat.weapon("gold", "katana")), nativeWeapon(h, "magiscythe"))) {
            ItemStack before = stack.copy();
            List<Entry> originalDamage = defaults(stack, EquipmentSlot.MAINHAND).stream()
                    .filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE)).toList();
            h.assertFalse(originalDamage.isEmpty(), "Weapon fixture has original attack damage modifiers");
            for (int query = 0; query < 12; query++) {
                assertPenalties(h, stack, true);
                h.assertValueEqual(modifiers(stack, EquipmentSlot.MAINHAND).stream()
                                .filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE)).toList(), originalDamage,
                        "Handling preserves the original weapon damage IDs, amounts and operations");
            }
            h.assertTrue(ItemStack.matches(stack, before), "Attribute queries leave saved item components unchanged");
            ItemStack restored = ItemStack.parse(h.getLevel().registryAccess(), stack.save(h.getLevel().registryAccess())).orElseThrow();
            h.assertTrue(ItemStack.matches(restored, before), "Save/load does not bake a second handling penalty into components");
            assertPenalties(h, restored, true);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_handling")
    public static void realPlayerEquipmentSlowsBothSpeedsAndSwitchingHandsOrUnequippingRestoresThem(GameTestHelper h) {
        if (absent(h)) return;
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5D, 2D, 1.5D)));
        player.tick();
        double baseAttack = player.getAttributeValue(Attributes.ATTACK_SPEED);
        double baseMovement = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double baseDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        ItemStack claymore = new ItemStack(SimplySwordsCompat.weapon("copper", "claymore"));
        double originalSpeed = apply(defaults(claymore, EquipmentSlot.MAINHAND), Attributes.ATTACK_SPEED, baseAttack);
        double originalDamage = apply(defaults(claymore, EquipmentSlot.MAINHAND), Attributes.ATTACK_DAMAGE, baseDamage);
        player.setItemSlot(EquipmentSlot.MAINHAND, claymore);
        player.tick();
        check(h, player.getAttributeValue(Attributes.ATTACK_SPEED), originalSpeed * (1D - SimplySwordsHandling.ATTACK_SPEED_PENALTY),
                "Native equipment refresh slows the real player's full configured attack speed");
        check(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), baseMovement * (1D - SimplySwordsHandling.MOVEMENT_SPEED_PENALTY),
                "Holding the two-handed weapon slows the real movement attribute before attacking");
        check(h, player.getAttributeValue(Attributes.ATTACK_DAMAGE), originalDamage, "The attack damage of the real equipped weapon is preserved");
        h.assertTrue(player.getCurrentItemAttackStrengthDelay() > 20D / originalSpeed,
                "The native player attack recovery actually takes longer");
        for (int tick = 0; tick < 5; tick++) player.tick();
        check(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), baseMovement * (1D - SimplySwordsHandling.MOVEMENT_SPEED_PENALTY),
                "Repeated equipment ticks do not accumulate movement penalties");
        ItemStack katana = new ItemStack(SimplySwordsCompat.weapon("copper", "katana"));
        player.setItemSlot(EquipmentSlot.MAINHAND, katana);
        player.tick();
        check(h, player.getAttributeValue(Attributes.ATTACK_SPEED),
                apply(defaults(katana, EquipmentSlot.MAINHAND), Attributes.ATTACK_SPEED, baseAttack)
                        * (1D - SimplySwordsHandling.ATTACK_SPEED_PENALTY), "Switching between two-handed weapons applies exactly one penalty");
        ItemStack longsword = new ItemStack(SimplySwordsCompat.weapon("copper", "longsword"));
        player.setItemSlot(EquipmentSlot.MAINHAND, longsword);
        player.setItemSlot(EquipmentSlot.OFFHAND, claymore);
        player.tick();
        check(h, player.getAttributeValue(Attributes.ATTACK_SPEED),
                apply(defaults(longsword, EquipmentSlot.MAINHAND), Attributes.ATTACK_SPEED, baseAttack),
                "A two-handed weapon in the offhand does not slow the one-handed main weapon");
        check(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), baseMovement, "Switching to one-handed main equipment restores movement");
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        player.tick();
        check(h, player.getAttributeValue(Attributes.ATTACK_SPEED), baseAttack, "Empty main hand restores attack speed even with a two-handed offhand item");
        check(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), baseMovement, "An offhand-only weapon applies no movement penalty");
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        player.tick();
        check(h, player.getAttributeValue(Attributes.ATTACK_DAMAGE), baseDamage, "Unequipping restores original damage");
        check(h, player.getAttributeValue(Attributes.ATTACK_SPEED), baseAttack, "Unequipping removes the attack-speed modifier");
        check(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), baseMovement, "Unequipping removes the movement-speed modifier");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_handling")
    public static void installedBetterCombatRegistryAndPresetOverridesControlRealMobCooldown(GameTestHelper h) throws ReflectiveOperationException {
        if (absent(h)) return;
        if (BetterCombatBridge.enabled()) Loaded.checkRegistryAndCooldown(h);
        else {
            ItemStack stack = new ItemStack(SimplySwordsCompat.weapon("copper", "claymore"));
            h.assertTrue(SimplySwordsHandling.twoHanded(stack), "Simply Swords alone still uses the two-handed fallback tag");
            assertPenalties(h, stack, true);
        }
        h.succeed();
    }

    private record Entry(Holder<Attribute> attribute, AttributeModifier modifier) {}

    private static List<Entry> modifiers(ItemStack stack, EquipmentSlot slot) {
        List<Entry> entries = new ArrayList<>();
        stack.forEachModifier(slot, (attribute, modifier) -> entries.add(new Entry(attribute, modifier)));
        return entries;
    }

    private static List<Entry> defaults(ItemStack stack, EquipmentSlot slot) {
        List<Entry> entries = new ArrayList<>();
        stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .forEach(slot, (attribute, modifier) -> entries.add(new Entry(attribute, modifier)));
        return entries;
    }

    private static void assertPenalties(GameTestHelper h, ItemStack stack, boolean twoHanded) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            List<Entry> entries = modifiers(stack, slot);
            boolean active = twoHanded && slot == EquipmentSlot.MAINHAND;
            assertPenalty(h, entries, ATTACK, Attributes.ATTACK_SPEED, SimplySwordsHandling.ATTACK_SPEED_PENALTY, active);
            assertPenalty(h, entries, MOVEMENT, Attributes.MOVEMENT_SPEED, SimplySwordsHandling.MOVEMENT_SPEED_PENALTY, active);
        }
    }

    private static void assertPenalty(GameTestHelper h, List<Entry> entries, ResourceLocation id,
            Holder<Attribute> attribute, double penalty, boolean active) {
        List<Entry> matching = entries.stream().filter(entry -> entry.modifier().id().equals(id)).toList();
        h.assertValueEqual(matching.size(), active ? 1 : 0, "One stable handling modifier exists only in the intended hand: " + id);
        if (active) {
            h.assertValueEqual(matching.getFirst().attribute(), attribute, "Handling modifies the intended entity attribute");
            h.assertValueEqual(matching.getFirst().modifier().operation(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                    "Handling multiplies final speed rather than subtracting a fixed number of attacks or movement units");
            check(h, matching.getFirst().modifier().amount(), -penalty, "Configured speed reduction amount");
        }
    }

    private static double apply(List<Entry> entries, Holder<Attribute> attribute, double base) {
        List<AttributeModifier> modifiers = entries.stream().filter(entry -> entry.attribute().equals(attribute)).map(Entry::modifier).toList();
        double added = base;
        for (AttributeModifier modifier : modifiers) if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) added += modifier.amount();
        double result = added;
        for (AttributeModifier modifier : modifiers) if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) result += added * modifier.amount();
        for (AttributeModifier modifier : modifiers) if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) result *= 1D + modifier.amount();
        return attribute.value().sanitizeValue(result);
    }

    private static boolean absent(GameTestHelper h) {
        if (SimplySwordsCompat.enabled()) return false;
        h.succeed();
        return true;
    }

    private static ItemStack nativeWeapon(GameTestHelper h, String path) {
        ResourceLocation id = id("simplyswords:" + path);
        h.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "Native unique weapon exists: " + path);
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        h.assertTrue(SimplySwordsCompat.definition(stack) != null, "Native unique weapon has an existing compatibility definition: " + path);
        return stack;
    }

    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }

    private static void check(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < .00001D, message + ": expected " + expected + ", got " + actual);
    }

    /** Uses the installed API reflectively without adding optional libraries to the GameTest source set. */
    private static final class Loaded {
        private static void checkRegistryAndCooldown(GameTestHelper h) throws ReflectiveOperationException {
            int profiles = 0;
            for (var item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (SimplySwordsCompat.definition(stack) == null) continue;
                Boolean registered = registeredHandedness(stack);
                boolean expected = registered == null ? stack.is(SimplySwordsHandling.TWO_HANDED) : registered;
                h.assertValueEqual(SimplySwordsHandling.twoHanded(stack), expected,
                        "Handedness matches the actual loaded Better Combat registry: " + BuiltInRegistries.ITEM.getKey(item));
                if (registered != null) profiles++;
            }
            h.assertTrue(profiles >= 100, "The integration check actually reads the installed weapon registry");
            ItemStack claymore = new ItemStack(SimplySwordsCompat.weapon("copper", "claymore"));
            ItemStack oneHandedPreset = claymore.copy();
            preset(oneHandedPreset, "bettercombat:dagger");
            h.assertFalse(SimplySwordsHandling.twoHanded(oneHandedPreset), "An explicit one-handed Better Combat preset overrides the ordinary two-handed tag");
            assertPenalties(h, oneHandedPreset, false);
            ItemStack longsword = new ItemStack(SimplySwordsCompat.weapon("copper", "longsword"));
            preset(longsword, "bettercombat:claymore");
            h.assertTrue(SimplySwordsHandling.twoHanded(longsword), "An explicit two-handed preset also activates handling on an ordinarily one-handed weapon");
            assertPenalties(h, longsword, true);
            var mob = EntityType.ZOMBIE.create(h.getLevel());
            h.assertTrue(mob != null, "Native mob fixture is available");
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5D, 2D, 1.5D)));
            try {
                mob.setItemSlot(EquipmentSlot.MAINHAND, oneHandedPreset);
                mob.tick();
                var original = BetterCombatBridge.profile(mob, 0);
                mob.setItemSlot(EquipmentSlot.MAINHAND, claymore);
                mob.tick();
                var slowed = BetterCombatBridge.profile(mob, 0);
                h.assertTrue(original != null && slowed != null, "Both same-item presets resolve through the real mob combat bridge");
                h.assertTrue(slowed.cooldownTicks() > original.cooldownTicks(),
                        "The two-handed penalty lengthens the actual Better Combat mob cooldown while item speed components remain identical");
                h.assertValueEqual(claymore.get(DataComponents.ATTRIBUTE_MODIFIERS), oneHandedPreset.get(DataComponents.ATTRIBUTE_MODIFIERS),
                        "The cooldown comparison uses identical original weapon speed and damage components");
            } finally { mob.discard(); }
        }

        private static Boolean registeredHandedness(ItemStack stack) throws ReflectiveOperationException {
            Object attributes = Class.forName("net.bettercombat.logic.WeaponRegistry")
                    .getMethod("getAttributes", ItemStack.class).invoke(null, stack);
            return attributes == null ? null : (Boolean)attributes.getClass().getMethod("isTwoHanded").invoke(attributes);
        }

        @SuppressWarnings("unchecked")
        private static void preset(ItemStack stack, String value) throws ReflectiveOperationException {
            var component = (DataComponentType<ResourceLocation>)Class.forName("net.bettercombat.api.component.BetterCombatDataComponents")
                    .getField("WEAPON_PRESET_ID").get(null);
            stack.set(component, id(value));
        }
    }
}

package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the installed Simply Swords implicit registry and combat callback. @author NingMeng123 */
@GameTestHolder("mite_simplyswords")
@PrefixGameTestTemplate(false)
public final class SimplySwordsImplicitGameTests {
    private static final String REGISTRY = "net.sweenus.simplyswords.api.WeaponImplicitRegistry";
    private static final String COMPONENT_KEY = "simplyswords:weapon_implicit";
    private static final List<ImplicitRange> RANGES = List.of(
            new ImplicitRange("chakram", "chakram_haste", 5, 10, 25),
            new ImplicitRange("longsword", "longsword_deflect", 5, 8, 15),
            new ImplicitRange("twinblade", "twinblade_haste", 5, 15, 25),
            new ImplicitRange("rapier", "armor_pierce", 5, 12, 35),
            new ImplicitRange("spear", "spear_armor_pierce", 5, 15, 35),
            new ImplicitRange("glaive", "glaive_bleed", 10, 20, 60),
            new ImplicitRange("greathammer", "greathammer_sunder", 1, 1, 10),
            new ImplicitRange("greataxe", "greataxe_bleed", 10, 20, 60),
            new ImplicitRange("scythe", "scythe_execute", 3, 3, 15),
            new ImplicitRange("halberd", "halberd_bleed", 10, 20, 60));

    private record ImplicitRange(String family, String id, int minimum, int maximum, int legacyMaximum) {}

    private SimplySwordsImplicitGameTests() {}

    @GameTest(template = "empty", batch = "simplyswords_implicit")
    public static void nativeAndAddedWeaponsUseTheNewImplicitRanges(GameTestHelper helper) throws ReflectiveOperationException {
        if (!SimplySwordsCompat.enabled()) {
            helper.succeed();
            return;
        }

        Class<?> registry = Class.forName(REGISTRY);
        Method resolve = registry.getDeclaredMethod("resolveDefinition", ItemStack.class);
        resolve.setAccessible(true);
        Method getOrCreate = registry.getMethod("getOrCreateWeaponImplicit", ItemStack.class);
        Method tooltip = registry.getMethod("formatTooltip", ItemStack.class);
        for (String material : List.of("iron", "copper")) {
            for (ImplicitRange range : RANGES) {
                ItemStack stack = weapon(material, range.family());
                Object definition = resolve.invoke(null, stack);
                String label = material + " " + range.family();
                helper.assertTrue(definition != null, label + " resolves an implicit");
                Class<?> type = definition.getClass();
                helper.assertValueEqual(type.getMethod("id").invoke(definition), id(range.id()), label + " implicit ID");
                helper.assertValueEqual((int)type.getMethod("minValue").invoke(definition), range.minimum(), label + " minimum");
                helper.assertValueEqual((int)type.getMethod("maxValue").invoke(definition), range.maximum(), label + " maximum");
                if (range.minimum() != range.maximum()) {
                    assertTooltipValues(helper, tooltip, stack, range.minimum() + "-" + range.maximum());
                }
                int rolled = implicitValue(getOrCreate, stack);
                helper.assertTrue(rolled >= range.minimum() && rolled <= range.maximum(), label + " generates a value in range");
            }
        }
        assertTooltipValues(helper, tooltip, weapon("copper", "scythe"), "3");
        assertTooltipValues(helper, tooltip, weapon("copper", "greathammer"), "1", "10");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_implicit")
    public static void oldSavedImplicitsUseTheSameBoundsInTooltipsAndCombat(GameTestHelper helper) throws ReflectiveOperationException {
        if (!SimplySwordsCompat.enabled()) {
            helper.succeed();
            return;
        }

        Class<?> registry = Class.forName(REGISTRY);
        Method peek = registry.getMethod("peekWeaponImplicit", ItemStack.class);
        Method getOrCreate = registry.getMethod("getOrCreateWeaponImplicit", ItemStack.class);
        Method tooltip = registry.getMethod("formatTooltip", ItemStack.class);
        for (ImplicitRange range : RANGES) {
            ItemStack old = savedWeapon(helper, range.family(), range.id(), range.legacyMaximum());
            helper.assertValueEqual(implicitValue(peek, old), range.maximum(), range.family() + " tooltip reads the effective value");
            if (range.family().equals("greathammer")) assertTooltipValues(helper, tooltip, old, "1", "10");
            else assertTooltipValues(helper, tooltip, old, Integer.toString(range.maximum()));
            helper.assertValueEqual(implicitValue(getOrCreate, old), range.maximum(), range.family() + " combat reads the effective value");
        }
        ItemStack lowRapier = savedWeapon(helper, "rapier", "armor_pierce", 1);
        assertTooltipValues(helper, tooltip, lowRapier, "5");
        helper.assertValueEqual(implicitValue(getOrCreate, lowRapier), 5, "Combat respects the new minimum");
        ItemStack validSpear = savedWeapon(helper, "spear", "spear_armor_pierce", 15);
        helper.assertValueEqual(implicitValue(getOrCreate, validSpear), 15, "An existing in-range value is retained");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_implicit")
    public static void greathammerSundersArmorOnePercentPerHitUpToTen(GameTestHelper helper) throws ReflectiveOperationException {
        if (!SimplySwordsCompat.enabled()) {
            helper.succeed();
            return;
        }

        Method onHit = Class.forName(REGISTRY).getMethod("onHit", ItemStack.class, LivingEntity.class, LivingEntity.class, float.class);
        var sunderedArmor = BuiltInRegistries.MOB_EFFECT.getHolder(id("sundered_armor")).orElseThrow();
        ItemStack hammer = savedWeapon(helper, "greathammer", "greathammer_sunder", 10);
        var attacker = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        var target = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 1));
        target.getAttribute(Attributes.ARMOR).setBaseValue(20D);
        double baseArmor = target.getAttributeValue(Attributes.ARMOR);
        try {
            for (int hit = 1; hit <= 12; hit++) {
                onHit.invoke(null, hammer, target, attacker, 1F);
                var effect = target.getEffect(sunderedArmor);
                int percent = Math.min(hit, 10);
                helper.assertTrue(effect != null, "The native hit callback applies the armor debuff");
                helper.assertValueEqual(effect.getAmplifier() + 1, percent, "Armor debuff stacks after hit " + hit);
                double expectedArmor = baseArmor * (1D - percent / 100D);
                helper.assertTrue(Math.abs(target.getAttributeValue(Attributes.ARMOR) - expectedArmor) < .0001D,
                        "The active armor attribute reflects the armor debuff after hit " + hit);
            }

            target.removeEffect(sunderedArmor);
            // Frostfall resolves to the plain hammer type and invokes the same sunder handler.
            ItemStack plainHammer = new ItemStack(BuiltInRegistries.ITEM.get(id("frostfall")));
            onHit.invoke(null, plainHammer, target, attacker, 1F);
            onHit.invoke(null, plainHammer, target, attacker, 1F);
            var plainSunder = target.getEffect(sunderedArmor);
            helper.assertTrue(plainSunder != null, "The shared handler applies plain hammer sunder");
            helper.assertValueEqual(plainSunder.getAmplifier() + 1, 12, "Plain hammer sunder retains its original stack limit");
        } finally {
            attacker.discard();
            target.discard();
        }
        helper.succeed();
    }

    private static int implicitValue(Method method, ItemStack stack) throws ReflectiveOperationException {
        Object implicit = ((Optional<?>)method.invoke(null, stack)).orElseThrow();
        return (int)implicit.getClass().getMethod("value").invoke(implicit);
    }

    private static void assertTooltipValues(GameTestHelper helper, Method tooltip, ItemStack stack, String... expected)
            throws ReflectiveOperationException {
        Component line = (Component)tooltip.invoke(null, stack);
        helper.assertTrue(line != null && line.getContents() instanceof TranslatableContents translated
                && Arrays.stream(translated.getArgs()).map(String::valueOf).toList().equals(List.of(expected)),
                stack.getItem() + " implicit tooltip uses " + List.of(expected));
    }

    private static ItemStack savedWeapon(GameTestHelper helper, String family, String implicit, int value) {
        var registries = helper.getLevel().registryAccess();
        var saved = (CompoundTag)weapon("copper", family).save(registries);
        var components = saved.getCompound("components");
        var data = new CompoundTag();
        data.putString("implicit_id", id(implicit).toString());
        data.putString("weapon_type", id(family).toString());
        data.putInt("value", value);
        components.put(COMPONENT_KEY, data);
        saved.put("components", components);
        return ItemStack.parse(registries, saved).orElseThrow();
    }

    private static ItemStack weapon(String material, String family) {
        return new ItemStack(SimplySwordsCompat.weapon(material, family));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("simplyswords", path);
    }
}

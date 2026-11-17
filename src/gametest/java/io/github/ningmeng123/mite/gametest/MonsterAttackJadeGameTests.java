package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.entity.MonsterMeleeScaling;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Authoritative attack values through Jade callbacks and optional linkage. @author NingMeng123 */
@GameTestHolder("mite_jade_attack")
@PrefixGameTestTemplate(false)
public final class MonsterAttackJadeGameTests {
    private MonsterAttackJadeGameTests() {}

    @GameTest(template = "empty")
    public static void zombieAttackIncludesConfiguredBodyWeaponAndStrengthWithoutMutation(GameTestHelper helper) {
        if (jadeAvailable(helper)) RuntimeChecks.zombieAttack(helper);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryCubeAttackUsesActualContactDamageInsteadOfAttributeModifiers(GameTestHelper helper) {
        if (jadeAvailable(helper)) RuntimeChecks.cubeAttack(helper);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void staleMalformedAndMissingAttackPayloadsProduceNoTooltip(GameTestHelper helper) {
        if (jadeAvailable(helper)) RuntimeChecks.invalidPayloads(helper);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unrelatedAndDeadTargetsProduceNoAttackData(GameTestHelper helper) {
        if (jadeAvailable(helper)) RuntimeChecks.invalidTargets(helper);
        helper.succeed();
    }

    private static boolean jadeAvailable(GameTestHelper helper) {
        if (ModList.get().isLoaded("jade")) return true;
        try {
            Class.forName("snownee.jade.api.IWailaPlugin", false, MonsterAttackJadeGameTests.class.getClassLoader());
            helper.fail("Jade remains a compile-only optional dependency");
        } catch (ClassNotFoundException expected) { /* No optional API is resolved by the outer test class. */ }
        return false;
    }

    private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type) {
        T mob = type.create(helper.getLevel());
        helper.assertTrue(mob != null, "The fixture entity can be created");
        mob.moveTo(helper.absolutePos(new BlockPos(3, 10, 3)).getCenter());
        mob.setNoAi(true);
        mob.setNoGravity(true);
        return mob;
    }

    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .00001D, message + ": " + actual + " vs " + expected);
    }

    /** Loaded only by a runtime with Jade installed. */
    private static final class RuntimeChecks {
        private static final io.github.ningmeng123.mite.integration.jade.MonsterAttackJadeProvider PROVIDER =
                io.github.ningmeng123.mite.integration.jade.MonsterAttackJadeProvider.INSTANCE;
        private static final String DATA_KEY = io.github.ningmeng123.mite.integration.jade.MonsterAttackJadeProvider.DATA_KEY;

        private static void zombieAttack(GameTestHelper helper) {
            double originalBase = MiteConfig.MONSTER_DAMAGE_BASE.get();
            var zombie = mob(helper, EntityType.ZOMBIE);
            try {
                MiteConfig.MONSTER_DAMAGE_BASE.set(0D);
                MiteConfig.refreshEnchantmentRules();
                CompoundTag data = new CompoundTag();
                var accessor = accessor(zombie, data);
                helper.assertTrue(PROVIDER.shouldRequestData(accessor), "A living zombie requests authoritative attack data");
                PROVIDER.appendServerData(data, accessor);
                close(helper, snapshot(helper, accessor), zombie.getAttributeValue(Attributes.ATTACK_DAMAGE),
                        "The unarmed zombie uses its current innate attack");

                zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                zombie.tick();
                double armed = zombie.getAttributeValue(Attributes.ATTACK_DAMAGE);
                helper.assertTrue(armed > zombie.getAttributeBaseValue(Attributes.ATTACK_DAMAGE),
                        "The actual equipment tick installs the sword bonus");
                zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 1));
                MiteConfig.MONSTER_DAMAGE_BASE.set(9D);
                MiteConfig.refreshEnchantmentRules();
                double expected;
                try (MonsterMeleeScaling.Scope ignored = MonsterMeleeScaling.enter(zombie)) {
                    expected = MonsterMeleeScaling.attackValue(zombie, zombie.getAttributeValue(Attributes.ATTACK_DAMAGE));
                }
                helper.assertTrue(expected > zombie.getAttributeValue(Attributes.ATTACK_DAMAGE),
                        "The configured body changes actual scoped attack beyond the stored native attribute");
                CompoundTag before = zombie.saveWithoutId(new CompoundTag());
                for (int repeat = 0; repeat < 4; repeat++) PROVIDER.appendServerData(data, accessor);
                close(helper, snapshot(helper, accessor), expected, "Jade retains configured body, sword and Strength together");
                List<Component> lines = new ArrayList<>();
                PROVIDER.appendTooltip(tooltip(lines), accessor, null);
                helper.assertTrue(lines.size() == 1 && lines.getFirst().getContents() instanceof TranslatableContents translated
                                && translated.getKey().equals("jade.mite.monster_attack"),
                        "The real Jade callback emits one localized melee attack line");
                helper.assertTrue(before.equals(zombie.saveWithoutId(new CompoundTag())) && !MonsterMeleeScaling.active(zombie),
                        "Repeated inspection changes no entity state and restores the scoped melee context");
                helper.assertTrue(PROVIDER.line(3D).getContents() instanceof TranslatableContents integerLine
                                && List.of(integerLine.getArgs()).equals(List.of("3")),
                        "Integer attack values omit trailing decimal zeroes");
                helper.assertTrue(PROVIDER.line(3.125D).getContents() instanceof TranslatableContents decimalLine
                                && List.of(decimalLine.getArgs()).equals(List.of("3.13")),
                        "Fractional attack values round to two decimal places");
            } finally {
                zombie.discard();
                MiteConfig.MONSTER_DAMAGE_BASE.set(originalBase);
                MiteConfig.refreshEnchantmentRules();
            }
        }

        private static void cubeAttack(GameTestHelper helper) {
            for (var type : List.of(EntityType.SLIME, EntityType.MAGMA_CUBE)) {
                var cube = mob(helper, type);
                try {
                    cube.setSize(4, true);
                    cube.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(123D);
                    CompoundTag data = new CompoundTag();
                    var accessor = accessor(cube, data);
                    PROVIDER.appendServerData(data, accessor);
                    close(helper, snapshot(helper, accessor), type == EntityType.MAGMA_CUBE ? 8D : 4D,
                            "Ordinary cubes display size-based contact damage even with a different attack attribute");
                } finally { cube.discard(); }
            }
        }

        private static void invalidPayloads(GameTestHelper helper) {
            var zombie = mob(helper, EntityType.ZOMBIE);
            try {
                CompoundTag data = new CompoundTag();
                var accessor = accessor(zombie, data);
                assertHidden(helper, accessor, "Missing server data supplies no guessed client value");
                PROVIDER.appendServerData(data, accessor);
                CompoundTag valid = data.copy();
                data.getCompound(DATA_KEY).putUUID("entity", UUID.randomUUID());
                assertHidden(helper, accessor, "A previous target's packet is rejected");
                data.put(DATA_KEY, valid.getCompound(DATA_KEY).copy());
                data.getCompound(DATA_KEY).remove("entity");
                assertHidden(helper, accessor, "An absent identity is rejected");
                data.put(DATA_KEY, valid.getCompound(DATA_KEY).copy());
                data.getCompound(DATA_KEY).putString("attack_damage", "3");
                assertHidden(helper, accessor, "An incorrectly typed damage value is rejected");
                for (double value : new double[]{-1D, Double.NaN, Double.POSITIVE_INFINITY}) {
                    data.put(DATA_KEY, valid.getCompound(DATA_KEY).copy());
                    data.getCompound(DATA_KEY).putDouble("attack_damage", value);
                    assertHidden(helper, accessor, "Negative and nonfinite attack data is rejected");
                }
            } finally { zombie.discard(); }
        }

        private static void invalidTargets(GameTestHelper helper) {
            var cow = mob(helper, EntityType.COW);
            var zombie = mob(helper, EntityType.ZOMBIE);
            var ghast = mob(helper, EntityType.GHAST);
            try {
                CompoundTag data = new CompoundTag();
                var cowAccessor = accessor(cow, data);
                helper.assertFalse(PROVIDER.shouldRequestData(cowAccessor), "A passive cow does not request monster attack data");
                data.put(DATA_KEY, new CompoundTag());
                PROVIDER.appendServerData(data, cowAccessor);
                helper.assertFalse(data.contains(DATA_KEY), "Unrelated targets clear stale attack data");
                var ghastAccessor = accessor(ghast, data);
                PROVIDER.appendServerData(data, ghastAccessor);
                helper.assertFalse(data.contains(DATA_KEY), "A monster without a melee attack attribute supplies no attack value");
                var zombieAccessor = accessor(zombie, data);
                PROVIDER.appendServerData(data, zombieAccessor);
                zombie.setHealth(0F);
                helper.assertFalse(PROVIDER.shouldRequestData(zombieAccessor), "Dead monsters do not request fresh attack data");
                assertHidden(helper, zombieAccessor, "An old living-monster packet is hidden after death");
                PROVIDER.appendServerData(data, zombieAccessor);
                helper.assertFalse(data.contains(DATA_KEY), "Dead targets clear previous attack data");
            } finally {
                cow.discard();
                zombie.discard();
                ghast.discard();
            }
        }

        private static double snapshot(GameTestHelper helper, snownee.jade.api.EntityAccessor accessor) {
            Double value = PROVIDER.snapshot(accessor);
            helper.assertTrue(value != null, "The current target has authoritative attack data");
            return value;
        }

        private static void assertHidden(GameTestHelper helper, snownee.jade.api.EntityAccessor accessor, String message) {
            List<Component> lines = new ArrayList<>();
            PROVIDER.appendTooltip(tooltip(lines), accessor, null);
            helper.assertTrue(PROVIDER.snapshot(accessor) == null && lines.isEmpty(), message);
        }

        private static snownee.jade.api.EntityAccessor accessor(Entity entity, CompoundTag data) {
            return proxy(snownee.jade.api.EntityAccessor.class, (target, method, args) -> switch (method.getName()) {
                case "getEntity", "getRawEntity" -> entity;
                case "getLevel" -> entity.level();
                case "getServerData" -> data;
                default -> defaults(target, method, args);
            });
        }

        private static snownee.jade.api.ITooltip tooltip(List<Component> lines) {
            return proxy(snownee.jade.api.ITooltip.class, (target, method, args) -> {
                if (method.getName().equals("add") && args.length == 2 && args[0] instanceof Component line) {
                    if (!args[1].equals(PROVIDER.getUid())) throw new IllegalStateException("Attack lines use their provider tag");
                    lines.add(line);
                    return null;
                }
                return defaults(target, method, args);
            });
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> api, java.lang.reflect.InvocationHandler handler) {
            return (T) java.lang.reflect.Proxy.newProxyInstance(MonsterAttackJadeGameTests.class.getClassLoader(), new Class<?>[]{api}, handler);
        }

        private static Object defaults(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
            if (method.getName().equals("toString")) return "MonsterAttackJadeGameTests proxy";
            if (method.getName().equals("hashCode")) return System.identityHashCode(target);
            if (method.getName().equals("equals")) return target == args[0];
            if (method.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(target, method, args);
            if (method.getReturnType() == void.class) return null;
            throw new UnsupportedOperationException("Unexpected Jade API call " + method);
        }
    }
}

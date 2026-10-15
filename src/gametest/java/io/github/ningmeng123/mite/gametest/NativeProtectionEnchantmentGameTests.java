package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.enchantment.NativeProtectionEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.minecraft.world.level.Explosion;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual ignition and explosion event integration with independently stated source boundaries. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class NativeProtectionEnchantmentGameTests {
    private NativeProtectionEnchantmentGameTests() {}

    @GameTest(template = "empty")
    public static void ignitionUsesHighestWornLevelAndCeilingOnce(GameTestHelper helper) {
        Cow cow = cow(helper);
        equip(cow, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, "fire_protection", 1);
        cow.clearFire(); cow.igniteForTicks(101);
        helper.assertTrue(cow.getRemainingFireTicks() == 85, "Level I: 101 - ceil(101 * .15) = 85");
        equip(cow, EquipmentSlot.LEGS, Items.IRON_LEGGINGS, "fire_protection", 4);
        cow.clearFire(); cow.igniteForTicks(101);
        helper.assertTrue(cow.getRemainingFireTicks() == 40, "Levels I and IV use IV, not V: 101 - ceil(101 * .6) = 40");
        cow.setRemainingFireTicks(200); cow.igniteForTicks(101);
        helper.assertTrue(cow.getRemainingFireTicks() == 200, "An ignition request never reduces a longer existing fire");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeBurningTimeAttributeRemainsIndependent(GameTestHelper helper) {
        Cow cow = cow(helper);
        equip(cow, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, "fire_protection", 4);
        cow.getAttribute(Attributes.BURNING_TIME).setBaseValue(.5D);
        cow.clearFire(); cow.igniteForTicks(101);
        helper.assertTrue(cow.getRemainingFireTicks() == 20, "External .5 burning-time is retained: ceil(101*.5)=51, then source gives20");
        cow.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        cow.clearFire(); cow.igniteForTicks(101);
        helper.assertTrue(cow.getRemainingFireTicks() == 51, "Without source protection, external burning-time still works");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unenchantedAndNonLivingIgnitionRetainNativeDuration(GameTestHelper helper) {
        Cow cow = cow(helper); cow.clearFire(); cow.igniteForTicks(101);
        helper.assertTrue(cow.getRemainingFireTicks() == 101, "Unenchanted living entity keeps requested duration");
        ItemEntity item = new ItemEntity(helper.getLevel(), cow.getX(), cow.getY(), cow.getZ(), new ItemStack(Items.STONE));
        item.igniteForTicks(101);
        helper.assertTrue(item.getRemainingFireTicks() == 101, "Non-living entity keeps requested duration");
        cow.clearFire(); cow.igniteForTicks(0);
        helper.assertTrue(cow.getRemainingFireTicks() == 0, "Zero ignition does not create fire");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blastUsesHighestLevelAndFlooredImpulseThroughRegisteredEvent(GameTestHelper helper) {
        Cow cow = cow(helper);
        equip(cow, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, "blast_protection", 1);
        equip(cow, EquipmentSlot.LEGS, Items.IRON_LEGGINGS, "blast_protection", 4);
        Vec3 input = new Vec3(6D, 8D, 0D);
        Explosion explosion = new Explosion(helper.getLevel(), null, cow.getX(), cow.getY(), cow.getZ(), 4F, false, Explosion.BlockInteraction.KEEP);
        ExplosionKnockbackEvent event = new ExplosionKnockbackEvent(helper.getLevel(), explosion, cow, input);
        NeoForge.EVENT_BUS.post(event);
        helper.assertTrue(event.getKnockbackVelocity().distanceTo(new Vec3(2.4D, 3.2D, 0D)) < 1e-7,
                "Length10, maxlevelIV: 10-floor(10*.6)=4, preserving original direction; levels never stack");
        helper.assertTrue(NativeProtectionEnchantments.explosionImpulse(.9D, 4) == .9D,
                "Source floor deliberately leaves a sub-unit impulse unchanged");
        helper.assertTrue(NativeProtectionEnchantments.explosionImpulse(10D, 0) == 10D,
                "Without the enchantment, no resistance is added");
        helper.assertTrue(NativeProtectionEnchantments.explosionKnockback(cow, Vec3.ZERO).equals(Vec3.ZERO),
                "A zero vector remains finite and unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void realExplosionRetainsExternalResistanceWithoutStackingArmorAttributes(GameTestHelper helper) {
        Cow cow = cow(helper);
        cow.setInvulnerable(true);
        equip(cow, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, "blast_protection", 4);
        equip(cow, EquipmentSlot.LEGS, Items.IRON_LEGGINGS, "blast_protection", 4);
        cow.getAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE).setBaseValue(.5D);
        // Vanilla applies equipped attribute changes on the following living tick.
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(Math.abs(cow.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE) - .5D) < 1e-8,
                    "Two blast-protection pieces do not install or stack modern resistance attributes");
            cow.setDeltaMovement(Vec3.ZERO);
            helper.getLevel().explode(null, cow.getX() - 1D, cow.getY() + .5D, cow.getZ(), 2F, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            helper.assertTrue(cow.getDeltaMovement().length() > 0D && cow.getDeltaMovement().length() < .6D,
                    "A real explosion still respects external .5 resistance and source floor leaves the small remaining impulse intact");
            helper.succeed();
        });
    }

    private static Cow cow(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        cow.setNoAi(true);
        return cow;
    }

    private static void equip(LivingEntity entity, EquipmentSlot slot, net.minecraft.world.item.Item item, String id, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(entity.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(
                ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.withDefaultNamespace(id))), level);
        entity.setItemSlot(slot, stack);
    }
}

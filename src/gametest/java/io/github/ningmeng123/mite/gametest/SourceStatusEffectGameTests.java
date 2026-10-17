package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real effect ticks, native weapon/effect attributes, reloads and unchanged modern-species controls. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SourceStatusEffectGameTests {
    private SourceStatusEffectGameTests() {}
    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type) {
        T mob = type.create(h.getLevel());
        mob.moveTo(h.absolutePos(new BlockPos(4, 15, 4)).getCenter());
        mob.setNoAi(true); mob.setNoGravity(true);
        return mob;
    }
    private static Player player(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(h.absolutePos(new BlockPos(3, 15, 3)).getCenter());
        return player;
    }
    private static void close(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < .00001D, message + ": " + actual + " vs " + expected);
    }
    private static void pulse(MobEffectInstance effect, LivingEntity target) {
        target.invulnerableTime = 0;
        effect.tick(target, () -> {});
    }

    @GameTest(template = "empty")
    public static void poisonUsesHundredTickDurationBoundaryAndCanActuallyKillSourceTarget(GameTestHelper h) {
        var cow = mob(h, EntityType.COW);
        cow.setHealth(10F);
        MobEffectInstance effect = new MobEffectInstance(MobEffects.POISON, 101);
        pulse(effect, cow);
        close(h, cow.getHealth(), 10D, "Duration101 does not pulse");
        pulse(effect, cow);
        close(h, cow.getHealth(), 9D, "Duration100 pulses once");
        for (int tick = 0; tick < 99; tick++) pulse(effect, cow);
        close(h, cow.getHealth(), 9D, "No modern25/50/75 pulses occur");
        cow.setHealth(1F);
        pulse(new MobEffectInstance(MobEffects.POISON, 100), cow);
        h.assertTrue(cow.isDeadOrDying(), "Source poison uses the actual lethal hurt/death path at one health");
        cow.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void poisonKeepsModernOnlyEntityCadenceAndNonlethalBehavior(GameTestHelper h) {
        var husk = mob(h, EntityType.HUSK);
        husk.setHealth(10F);
        pulse(new MobEffectInstance(MobEffects.POISON, 25), husk);
        close(h, husk.getHealth(), 9D, "Modern-only species retains native25tick poison");
        husk.setHealth(1F);
        pulse(new MobEffectInstance(MobEffects.POISON, 100), husk);
        close(h, husk.getHealth(), 1D, "Modern-only species retains native nonlethal poison");
        husk.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void attackEffectsScaleTheEquippedWeaponTogetherAndSurviveReload(GameTestHelper h) {
        var zombie = mob(h, EntityType.ZOMBIE);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.tick();
        double basis = zombie.getAttributeValue(Attributes.ATTACK_DAMAGE);
        h.assertTrue(basis > zombie.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), "Native equipment tick applied the actual sword bonus");
        zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 1));
        close(h, zombie.getAttributeValue(Attributes.ATTACK_DAMAGE), basis * 1.8D, "StrengthII scales body and weapon by80percent");
        zombie.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400));
        close(h, zombie.getAttributeValue(Attributes.ATTACK_DAMAGE), basis * 1.4D, "WeaknessI subtracts40percent of the same base");
        zombie.removeEffect(MobEffects.WEAKNESS);
        close(h, zombie.getAttributeValue(Attributes.ATTACK_DAMAGE), basis * 1.8D, "Removing weakness leaves strength once");
        CompoundTag saved = zombie.saveWithoutId(new CompoundTag());
        var restored = mob(h, EntityType.ZOMBIE);
        restored.load(saved); restored.tick();
        close(h, restored.getAttributeValue(Attributes.ATTACK_DAMAGE), basis * 1.8D, "Source modifier is not doubled or reverted on reload");
        restored.removeAllEffects();
        close(h, restored.getAttributeValue(Attributes.ATTACK_DAMAGE), basis, "Milk-style removal restores the body and weapon baseline");
        zombie.discard(); restored.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void modernOnlySpeciesKeepFixedAttackBonusesAndFifteenPercentSlowness(GameTestHelper h) {
        var husk = mob(h, EntityType.HUSK);
        double attack = husk.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double speed = husk.getAttributeValue(Attributes.MOVEMENT_SPEED);
        husk.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200));
        close(h, husk.getAttributeValue(Attributes.ATTACK_DAMAGE), attack + 3D, "Modern StrengthI remains a fixed3");
        husk.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200));
        close(h, husk.getAttributeValue(Attributes.ATTACK_DAMAGE), Math.max(attack - 1D, 0D), "Modern WeaknessI remains a fixed4");
        husk.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200));
        SourceEnchantmentCombat.refreshMovement(husk);
        close(h, husk.getAttributeValue(Attributes.MOVEMENT_SPEED), speed * .85D, "Modern ordinary slowness remains15percent");
        husk.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void playersCombineSpeedAndSlowBeforeFreeActionAndRemoveEveryModifier(GameTestHelper h) {
        Player player = player(h);
        double base = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400));
        close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * .8D, "Unenchanted ordinary slowness is20percent");
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
        close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.2D, "SpeedII and SlowI add before multiplying movement");
        ItemStack leggings = new ItemStack(Items.IRON_LEGGINGS);
        leggings.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(R196Enchantments.key("free_action")), 4);
        player.setItemSlot(EquipmentSlot.LEGS, leggings);
        SourceEnchantmentCombat.refreshMovement(player);
        close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.2D, "FreeAction does not attenuate positive net speed");
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * .96D, "FreeActionIV attenuates negative SlowI once");
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        SourceEnchantmentCombat.refreshMovement(player);
        close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * .8D, "Unequipping restores source slowness");
        player.removeAllEffects();
        close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base, "Bulk effect removal leaves no source modifier");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nonPlayerSourceSpeedAndSlowKeepTheOldSeparateMultipliers(GameTestHelper h) {
        var cow = mob(h, EntityType.COW);
        double base = cow.getAttributeValue(Attributes.MOVEMENT_SPEED);
        cow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200));
        cow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1));
        close(h, cow.getAttributeValue(Attributes.MOVEMENT_SPEED), base * .8D * 1.4D, "NPC ni multiplies its two effect attributes separately");
        var restored = mob(h, EntityType.COW);
        restored.load(cow.saveWithoutId(new CompoundTag()));
        close(h, restored.getAttributeValue(Attributes.MOVEMENT_SPEED), base * .8D * 1.4D, "Saved speed and slow modifiers are rebuilt once on load");
        restored.removeAllEffects();
        close(h, restored.getAttributeValue(Attributes.MOVEMENT_SPEED), base, "Loaded NPC effect removal restores baseline");
        cow.removeAllEffects();
        close(h, cow.getAttributeValue(Attributes.MOVEMENT_SPEED), base, "NPC cleanup restores baseline");
        cow.discard(); restored.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceNonBiologicalIdentitiesRejectAllThreeBiologicalEffects(GameTestHelper h) {
        for (var type : List.of(EntityType.ENDERMAN, EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM, EntityType.MAGMA_CUBE,
                EntityType.ZOMBIE, EntityType.SKELETON, EntityType.WITHER_SKELETON, EntityType.ZOMBIFIED_PIGLIN,
                EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIE_HORSE, EntityType.SKELETON_HORSE, EntityType.WITHER)) {
            Mob entity = mob(h, type);
            rejected(h, entity, type.toString()); entity.discard();
        }
        for (MobKind kind : List.of(MobKind.WIGHT, MobKind.SHADOW, MobKind.NIGHTWING, MobKind.HELLHOUND,
                MobKind.FIRE_ELEMENTAL, MobKind.EARTH_ELEMENTAL, MobKind.CLAY_GOLEM, MobKind.REVENANT,
                MobKind.LONGDEAD, MobKind.LONGDEAD_GUARDIAN, MobKind.BONE_LORD, MobKind.ANCIENT_BONE_LORD)) {
            Mob entity = mob(h, MiteMobs.type(kind));
            rejected(h, entity, kind.name()); entity.discard();
        }
        for (var type : List.of(EntityType.COW, EntityType.SLIME, EntityType.GOAT)) {
            Mob entity = mob(h, type);
            h.assertTrue(entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 100)), "Biological/modern control still accepts wither " + type);
            entity.discard();
        }
        h.succeed();
    }

    private static void rejected(GameTestHelper h, LivingEntity target, String name) {
        for (var type : List.of(MobEffects.POISON, MobEffects.REGENERATION, MobEffects.WITHER))
            h.assertTrue(!target.addEffect(new MobEffectInstance(type, 100)) && !target.hasEffect(type), name + " rejects " + type);
    }

    @GameTest(template = "empty")
    public static void legacyNbtCannotBypassSourceBiologyWhenLoadingUndeadOrElementals(GameTestHelper h) {
        for (var type : List.of(EntityType.ZOMBIE, EntityType.SKELETON,
                MiteMobs.type(MobKind.EARTH_ELEMENTAL), MiteMobs.type(MobKind.FIRE_ELEMENTAL))) {
            Mob source = mob(h, type);
            source.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400));
            double legalSpeed = source.getAttributeValue(Attributes.MOVEMENT_SPEED);
            CompoundTag legacy = source.saveWithoutId(new CompoundTag());
            ListTag effects = legacy.getList("active_effects", 10);
            // Reproduce a pre-fix save: readAdditionalSaveData inserts these compounds without calling canBeAffected.
            for (var effect : List.of(MobEffects.POISON, MobEffects.REGENERATION, MobEffects.WITHER))
                effects.add(new MobEffectInstance(effect, 400).save());
            legacy.put("active_effects", effects);
            Mob restored = mob(h, type);
            restored.load(legacy);
            for (var effect : List.of(MobEffects.POISON, MobEffects.REGENERATION, MobEffects.WITHER))
                h.assertTrue(!restored.hasEffect(effect), "Legacy source NBT removes ineligible " + effect + " for " + type);
            h.assertTrue(restored.hasEffect(MobEffects.MOVEMENT_SPEED) && restored.getActiveEffects().size() == 1,
                    "Migration removes only the three rejected biological effects");
            close(h, restored.getAttributeValue(Attributes.MOVEMENT_SPEED), legalSpeed,
                    "Legal loaded effects retain their existing attributes");
            source.discard(); restored.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void modernGoatRetainsAllThreeEffectsWhenLoadingTheSameLegacyNbtShape(GameTestHelper h) {
        var source = mob(h, EntityType.GOAT);
        CompoundTag legacy = source.saveWithoutId(new CompoundTag());
        ListTag effects = new ListTag();
        for (var effect : List.of(MobEffects.POISON, MobEffects.REGENERATION, MobEffects.WITHER))
            effects.add(new MobEffectInstance(effect, 400).save());
        legacy.put("active_effects", effects);
        var restored = mob(h, EntityType.GOAT);
        restored.load(legacy);
        for (var effect : List.of(MobEffects.POISON, MobEffects.REGENERATION, MobEffects.WITHER))
            h.assertTrue(restored.hasEffect(effect) && restored.getEffect(effect).getDuration() == 400,
                    "Modern-only goat retains the original loaded effect and duration " + effect);
        h.assertValueEqual(restored.getActiveEffects().size(), 3, "Source migration does not filter modern-only loaded effect maps");
        source.discard(); restored.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryPlayerWebPenaltyRetainsPhysicalFrictionEvenWithoutFreeAction(GameTestHelper h) {
        Player player = player(h);
        double x = player.getX();
        player.makeStuckInBlock(Blocks.COBWEB.defaultBlockState(), new Vec3(.25D, .05D, .25D));
        player.move(MoverType.SELF, new Vec3(1D, 0D, 0D));
        close(h, player.getX() - x, .0625D, "Source separate75percent web ability penalty composes with physical25percent friction");
        h.succeed();
    }
}

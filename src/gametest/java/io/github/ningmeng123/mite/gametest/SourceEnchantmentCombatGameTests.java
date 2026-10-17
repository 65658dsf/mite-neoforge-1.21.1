package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Source effects through real native attack, attributes, equipment saving and movement. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SourceEnchantmentCombatGameTests {
    private static Player player(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 pos = Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 15, 2)));
        player.setPos(pos);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20D);
        player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024D);
        player.setHealth(100F);
        return player;
    }
    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type) {
        T mob = type.create(h.getLevel());
        mob.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(4, 15, 2))));
        mob.setNoAi(true); mob.setNoGravity(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D); mob.setHealth(200F);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }
    private static ItemStack enchanted(GameTestHelper h, net.minecraft.world.item.Item item, String id, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(R196Enchantments.key(id)), level);
        return stack;
    }
    private static void close(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < .00001D, message + ": " + actual + " vs " + expected);
    }

    @GameTest(template = "empty")
    public static void disarmingNativePlayerAttackDropsOnceAndCooldownSurvivesReload(GameTestHelper h) {
        Player player = player(h);
        var zombie = mob(h, EntityType.ZOMBIE);
        zombie.setCanPickUpLoot(true);
        ItemStack held = new ItemStack(Items.DIAMOND);
        String marker = "disarming-" + zombie.getUUID();
        held.set(DataComponents.CUSTOM_NAME, Component.literal(marker));
        zombie.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(h, Items.IRON_SWORD, "disarming", 5));
        try (var spawned = new SpawnedEntityCapture(h.getLevel(), entity -> entity instanceof ItemEntity item
                && item.getItem().getHoverName().getString().equals(marker))) {
            player.attack(zombie);
            h.assertTrue(zombie.getMainHandItem().isEmpty() && !zombie.canPickUpLoot(), "V disarms the actual mob and prevents immediate re-equipping");
            var drops = spawned.live(ItemEntity.class);
            h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().getCount() == 1, "Exactly one original held stack is dropped");
            CompoundTag saved = zombie.saveWithoutId(new CompoundTag());
            h.assertTrue(saved.getBoolean("CanPickUpLoot"), "NBT stores the original pickup preference, not the temporary denial");
            var restored = EntityType.ZOMBIE.create(h.getLevel());
            restored.load(saved);
            h.assertTrue(SourceEnchantmentCombat.disarmed(restored) && !restored.canPickUpLoot(), "Cooldown is restored with the entity");
            for (int tick = 0; tick < 39; tick++) SourceEnchantmentCombat.tick(new EntityTickEvent.Pre(restored));
            h.assertTrue(!restored.canPickUpLoot(), "Pickup remains blocked through tick39");
            SourceEnchantmentCombat.tick(new EntityTickEvent.Pre(restored));
            h.assertTrue(restored.canPickUpLoot(), "Pickup is restored exactly on tick40");
            zombie.discard(); restored.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void disarmRunsBeforeFailedDamageAndNeverStripsPlayers(GameTestHelper h) {
        Player attacker = player(h);
        var zombie = mob(h, EntityType.ZOMBIE);
        zombie.setInvulnerable(true);
        zombie.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        attacker.setItemInHand(InteractionHand.MAIN_HAND, enchanted(h, Items.IRON_SWORD, "disarming", 5));
        attacker.attack(zombie);
        h.assertTrue(zombie.getMainHandItem().isEmpty(), "Source disarm occurs before the hurt call even when damage is rejected");
        close(h, zombie.getHealth(), 200D, "Invulnerable target did not take damage");
        Player victim = player(h);
        victim.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        attacker.attack(victim);
        h.assertTrue(victim.getMainHandItem().is(Items.STICK), "Players are not legacy Mob disarming targets");
        h.getLevel().getEntitiesOfClass(ItemEntity.class, zombie.getBoundingBox().inflate(2), e -> e.getItem().is(Items.STICK)).forEach(ItemEntity::discard);
        zombie.discard(); h.succeed();
    }

    /** A native Cow hurt pipeline with deliberately one-point input, isolating raw damage from health actually lost. */
    private static final class OnePointCow extends Cow {
        private OnePointCow(GameTestHelper h) {
            super(EntityType.COW, h.getLevel());
            setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(4, 15, 2))));
            setNoAi(true); setNoGravity(true);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D); setHealth(200F);
        }
        @Override public boolean hurt(DamageSource source, float amount) { return super.hurt(source, Math.min(1F, amount)); }
    }

    @GameTest(template = "empty")
    public static void vampiricPlayerUsesRawDamageButMobUsesActualHealthLost(GameTestHelper h) {
        Player player = player(h);
        var cow = new OnePointCow(h);
        h.getLevel().addFreshEntity(cow);
        player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(h, Items.IRON_SWORD, "vampiric", 5));
        boolean largerThanOne = false;
        for (int trial = 0; trial < 64; trial++) {
            player.setHealth(1F); cow.setHealth(200F); cow.invulnerableTime = 0;
            player.attack(cow);
            if (player.getHealth() > 2F) largerThanOne = true;
        }
        h.assertTrue(largerThanOne, "Player can heal more than one from raw20 even though target lost just one point");
        var zombie = mob(h, EntityType.ZOMBIE);
        zombie.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20D);
        zombie.setItemInHand(InteractionHand.MAIN_HAND, enchanted(h, Items.IRON_SWORD, "vampiric", 5));
        boolean healed = false;
        for (int trial = 0; trial < 64; trial++) {
            zombie.setHealth(1F); cow.setHealth(200F); cow.invulnerableTime = 0;
            zombie.doHurtTarget(cow);
            if (zombie.getHealth() > 1F) healed = true;
            h.assertTrue(zombie.getHealth() <= 2F, "Mob Vampiric is limited by one actual point of health lost");
        }
        h.assertTrue(healed, "The native Mob melee hook also executes Vampiric");
        cow.discard(); zombie.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void vampiricBiologicalPredicateIncludesSlimesButExcludesConstructsAndUndead(GameTestHelper h) {
        var cow = EntityType.COW.create(h.getLevel());
        var slime = EntityType.SLIME.create(h.getLevel());
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        var golem = EntityType.IRON_GOLEM.create(h.getLevel());
        var enderman = EntityType.ENDERMAN.create(h.getLevel());
        var magma = EntityType.MAGMA_CUBE.create(h.getLevel());
        var hellhound = (net.minecraft.world.entity.LivingEntity)MiteMobs.type(MobKind.HELLHOUND).create(h.getLevel());
        h.assertTrue(SourceEnchantmentCombat.biological(cow) && SourceEnchantmentCombat.biological(slime), "Living animals and ordinary source slimes can transfer life");
        h.assertTrue(!SourceEnchantmentCombat.biological(zombie) && !SourceEnchantmentCombat.biological(golem)
                && !SourceEnchantmentCombat.biological(enderman) && !SourceEnchantmentCombat.biological(magma)
                && !SourceEnchantmentCombat.biological(hellhound), "The source predicate is broader than just the undead tag");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void stunUsesSourceDurationAndAmplifierAfterRealMobMelee(GameTestHelper h) {
        var zombie = mob(h, EntityType.ZOMBIE);
        var cow = mob(h, EntityType.COW);
        zombie.setItemInHand(InteractionHand.MAIN_HAND, enchanted(h, Items.WOODEN_SWORD, "stun", 5));
        for (int trial = 0; trial < 64 && !cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN); trial++) {
            cow.setHealth(200F); cow.invulnerableTime = 0;
            zombie.doHurtTarget(cow);
        }
        var effect = cow.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        h.assertTrue(effect != null && effect.getDuration() == 250 && effect.getAmplifier() == 25, "Stun V applies source250ticks, amplifier25 through real mob combat");
        zombie.discard(); cow.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void armorSpeedUsesMaximumAndFreeActionRefreshesWithoutDoubleReduction(GameTestHelper h) {
        Player player = player(h);
        player.setItemSlot(EquipmentSlot.FEET, enchanted(h, Items.IRON_BOOTS, "speed", 2));
        player.setItemSlot(EquipmentSlot.CHEST, enchanted(h, Items.IRON_CHESTPLATE, "speed", 5));
        player.setItemSlot(EquipmentSlot.LEGS, enchanted(h, Items.IRON_LEGGINGS, "free_action", 4));
        SourceEnchantmentCombat.refreshMovement(player);
        double base = player.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
        close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.25D, "Armor reads highest SpeedV, not totalVII");
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0));
        close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.25D * .96D, "FreeActionIV leaves4% source SlownessI");
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1));
        close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.25D * 1.2D, "Positive combined haste is not attenuated");
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        SourceEnchantmentCombat.refreshMovement(player);
        close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.25D * .8D, "Removing enchanted leggings restores the source ordinary slowness");
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.25D, "Removing potion leaves no resistance modifier behind");
        player.setItemSlot(EquipmentSlot.LEGS, enchanted(h, Items.IRON_LEGGINGS, "free_action", 4));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0));
        player.removeAllEffects();
        close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.25D, "Milk-style bulk effect removal also leaves no permanent slowdown");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void freeActionWebUsesSeparatePhysicalFrictionAndDoesNotAlterSlimeVectors(GameTestHelper h) {
        Player player = player(h);
        player.setItemSlot(EquipmentSlot.LEGS, enchanted(h, Items.IRON_LEGGINGS, "free_action", 4));
        SourceEnchantmentCombat.refreshMovement(player);
        double x = player.getX();
        player.makeStuckInBlock(Blocks.COBWEB.defaultBlockState(), new Vec3(.25D, .05D, .25D));
        player.move(MoverType.SELF, new Vec3(1D, 0D, 0D));
        close(h, player.getX() - x, .25D * .85D, "Source web ability penalty and native physical friction are both retained");
        x = player.getX();
        player.makeStuckInBlock(Blocks.SLIME_BLOCK.defaultBlockState(), new Vec3(.8D, 1D, .8D));
        player.move(MoverType.SELF, new Vec3(1D, 0D, 0D));
        close(h, player.getX() - x, .8D, "FreeAction's web hook does not affect a slime contact vector");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void thornsUsesHighestLevelAndFirstArmorAndWearsOnFailedProc(GameTestHelper h) {
        Player defender = player(h);
        var attacker = mob(h, EntityType.ZOMBIE);
        ItemStack boots = enchanted(h, Items.IRON_BOOTS, "minecraft:thorns", 1);
        ItemStack chest = enchanted(h, Items.IRON_CHESTPLATE, "minecraft:thorns", 3);
        defender.setItemSlot(EquipmentSlot.FEET, boots); defender.setItemSlot(EquipmentSlot.CHEST, chest);
        attacker.getRandom().setSeed(0L); // Legacy first float is above .45: a failed highest-level proc.
        SourceEnchantmentCombat.thorns(attacker, defender);
        h.assertTrue(boots.getDamageValue() == 1 && chest.getDamageValue() == 0,
                "Even a failed proc wears the first source-order armor stack, not a random or highest-level piece");
        h.assertTrue(attacker.getHealth() == 200F, "Failed proc deals no reflected damage");
        attacker.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void armorWearCountsAsNegativeEvenWhenNativeDamageReturnsFalse(GameTestHelper h) {
        Player defender = player(h);
        var attacker = mob(h, EntityType.ZOMBIE);
        ItemStack chest = enchanted(h, Items.IRON_CHESTPLATE, "minecraft:thorns", 3);
        defender.setItemSlot(EquipmentSlot.CHEST, chest);
        attacker.getRandom().setSeed(0L);
        float health = defender.getHealth();
        boolean nativeResult = SourceEnchantmentCombat.melee(attacker, defender, attacker.damageSources().mobAttack(attacker), 1F, () -> {
            chest.setDamageValue(chest.getDamageValue() + 1);
            return false;
        });
        h.assertTrue(!nativeResult && chest.getDamageValue() == 2 && defender.getHealth() == health,
                "A real armor-wear negative result attempts thorns even without lost health or a true native return");
        attacker.discard(); h.succeed();
    }
}

package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.progression.GearModifier;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteOreBlocks;
import io.github.ningmeng123.mite.survival.DualFoodData;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual mining, attributes, armor, primary hits, durability and event dispatch for all 17 ITE modifiers. @author NingMeng123 */
@GameTestHolder("mite_gear")
@PrefixGameTestTemplate(false)
public final class GearModifierEffectsGameTests {
    private GearModifierEffectsGameTests() {}

    private static final class TestPlayer extends Player {
        TestPlayer(ServerLevel level, BlockPos pos) {
            super(level, pos, 0F, new GameProfile(UUID.randomUUID(), "NingMeng123"));
            setPos(pos.getCenter()); setNoGravity(true); setOnGround(true);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10D);
            getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024D);
            setHealth(100F); getFoodData().setFoodLevel(6);
            ((DualFoodData)getFoodData()).mite$setNutrition(0);
        }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override protected void hurtArmor(DamageSource source, float amount) {}
        float armorDamage(DamageSource source, float amount) { return getDamageAfterArmorAbsorb(source, amount); }
    }
    private static TestPlayer player(GameTestHelper h) {
        return new TestPlayer(h.getLevel(), h.absolutePos(new BlockPos(2, 15, 2)));
    }
    private static ItemStack modifier(ItemStack stack, GearModifier modifier, int rank) {
        stack.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 1, 0, Map.of(modifier.id(), rank), 1));
        return stack;
    }
    private static void close(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < .0002D, message + ": " + actual + " vs " + expected);
    }
    private static void equip(TestPlayer player, EquipmentSlot slot, ItemStack stack) {
        player.setItemSlot(slot, stack); player.tick(); player.setOnGround(true);
    }
    private static ItemAttributeModifiers zeroArmorAttributes(double toughness) {
        // An EMPTY component invokes NeoForge's fallback to the item's default armor attributes.
        return ItemAttributeModifiers.builder().add(Attributes.ARMOR,
                new AttributeModifier(ResourceLocation.fromNamespaceAndPath("mite", "gametest_zero_armor"),
                        0D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
                // Explicitly cancel the normalized material baseline while testing unrelated fractional armor effects.
                .add(Attributes.ARMOR_TOUGHNESS,new AttributeModifier(ResourceLocation.fromNamespaceAndPath("mite","gametest_zero_toughness"),
                        -toughness,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.CHEST).build();
    }

    @GameTest(template = "empty")
    public static void efficiencyAndUnnaturalReachActualDigSpeedAndBlockProgress(GameTestHelper h) {
        TestPlayer player = player(h);
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        equip(player, EquipmentSlot.MAINHAND, pickaxe);
        BlockState stone = Blocks.STONE.defaultBlockState();
        float baseline = player.getDigSpeed(stone, player.blockPosition());
        float progress = stone.getDestroyProgress(player, h.getLevel(), player.blockPosition());
        modifier(pickaxe, GearModifier.TOOL_EFFICIENCY, 1);
        close(h, player.getDigSpeed(stone, player.blockPosition()), baseline * 1.125F,
                "Efficiency adds .25 to iron's material efficiency 2 exactly once");
        close(h, stone.getDestroyProgress(player, h.getLevel(), player.blockPosition()), progress * 1.125F,
                "The R196 block progress replacement retains the modified native speed");
        modifier(pickaxe, GearModifier.TOOL_UNNATURAL, 1);
        close(h, player.getDigSpeed(stone, player.blockPosition()), baseline * 1.05F,
                "Iron raw material level 3 exceeds stone minimum 2 by one");
        ItemStack adamantium = new ItemStack(io.github.ningmeng123.mite.registry.MiteTools.get("adamantium_pickaxe"));
        equip(player, EquipmentSlot.MAINHAND, adamantium);
        BlockState diamondOre = Blocks.DIAMOND_ORE.defaultBlockState();
        float oreBaseline = player.getDigSpeed(diamondOre, player.blockPosition());
        modifier(adamantium, GearModifier.TOOL_UNNATURAL, 1);
        close(h, player.getDigSpeed(diamondOre, player.blockPosition()), oreBaseline * 1.1F,
                "Unnatural compares material five to diamond ore's stone material two, not its harvest threshold four");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aquadynamicAddsWetSpeedAndRemovesTheNativeSubmergedPenalty(GameTestHelper h) {
        TestPlayer player = player(h);
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        equip(player, EquipmentSlot.MAINHAND, pickaxe);
        BlockState stone = Blocks.STONE.defaultBlockState();
        float dry = player.getDigSpeed(stone, player.blockPosition());
        modifier(pickaxe, GearModifier.TOOL_AQUADYNAMIC, 1);
        close(h, player.getDigSpeed(stone, player.blockPosition()), dry, "Aquadynamic gives no dry-weather speed");
        BlockPos water = player.blockPosition();
        BlockPos eyes = BlockPos.containing(player.getEyePosition());
        for (int y = water.getY(); y <= eyes.getY(); y++)
            h.getLevel().setBlock(new BlockPos(water.getX(), y, water.getZ()), Blocks.WATER.defaultBlockState(), 3);
        player.baseTick(); player.baseTick(); player.setOnGround(true);
        h.assertTrue(player.isInWaterOrRain() && player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), "Water fixture submerges body and eyes");
        close(h, player.getDigSpeed(stone, water), dry * 1.625F,
                "Wet iron speed becomes base*(2+1.25)/2 and is not divided by five underwater");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bothDurabilityModifiersReachNativeItemWearBeforeUnbreaking(GameTestHelper h) {
        TestPlayer player = player(h);
        ItemStack sword = modifier(new ItemStack(Items.IRON_SWORD), GearModifier.TOOL_DURABILITY, 5);
        ItemStack chest = modifier(new ItemStack(Items.IRON_CHESTPLATE), GearModifier.ARMOR_DURABILITY, 5);
        sword.hurtAndBreak(10, h.getLevel(), player, ignored -> {});
        chest.hurtAndBreak(10, h.getLevel(), player, ignored -> {});
        h.assertTrue(sword.getDamageValue() == 5 && chest.getDamageValue() == 5, "Native wear consumes half the original budget");
        ItemStack tiny = modifier(new ItemStack(Items.IRON_SWORD), GearModifier.TOOL_DURABILITY, 1);
        tiny.hurtAndBreak(1, h.getLevel(), player, ignored -> {});
        h.assertTrue(tiny.getDamageValue() == 0, "The original tool wear truncation can prevent a one-point action");
        chest.hurtAndBreak(1, h.getLevel(), player, ignored -> {});
        h.assertTrue(chest.getDamageValue() == 6, "Armor retains the original minimum one-point wear");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void damageAndProtectionUseRealEquipmentAttributes(GameTestHelper h) {
        TestPlayer player = player(h);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        equip(player, EquipmentSlot.MAINHAND, sword);
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        modifier(sword, GearModifier.TOOL_DAMAGE, 3); player.tick();
        close(h, player.getAttributeValue(Attributes.ATTACK_DAMAGE), damage + 3D, "Sharpness contributes three flat attack points");
        ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
        equip(player, EquipmentSlot.CHEST, chest);
        double armor = player.getAttributeValue(Attributes.ARMOR);
        modifier(chest, GearModifier.ARMOR_PROTECTION, 4); player.tick();
        close(h, player.getAttributeValue(Attributes.ARMOR), armor + 1D, "Four general protection ranks add one armor point");
        h.assertTrue(!sword.isEnchanted() && !chest.isEnchanted(), "The custom attributes never create ordinary enchantments");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void protectionOneSurvivesNativeArmorFlooringWithoutChangingOrdinaryFractionalArmor(GameTestHelper h) {
        TestPlayer player = player(h);
        ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
        // Iron armor's current material toughness is 1.25; cancel that entire baseline for this zero-toughness control.
        chest.set(DataComponents.ATTRIBUTE_MODIFIERS, zeroArmorAttributes(1.25D));
        equip(player, EquipmentSlot.CHEST, chest);
        close(h, player.getAttributeValue(Attributes.ARMOR), 0D, "The explicit nonempty override removes default chestplate armor");
        close(h, player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 0D, "The fractional armor fixture has no toughness");
        player.getAttribute(Attributes.ARMOR).setBaseValue(.75D);
        DamageSource source = new DamageSource(h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.MOB_ATTACK));
        h.assertFalse(source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR), "The fractional protection fixture must enter the native armor pipeline");
        close(h, player.armorDamage(source, 8F), 8F, "Direct native armor calculation keeps the old .75-point rounding before a modifier");
        player.setHealth(100F); player.invulnerableTime = 0;
        player.hurt(source, 8F);
        close(h, 100F - player.getHealth(), 8F, "Ordinary fractional armor still follows native floor(.75)=0");
        modifier(chest, GearModifier.ARMOR_PROTECTION, 1); player.tick();
        player.setHealth(100F); player.invulnerableTime = 0;
        close(h, player.getAttributeValue(Attributes.ARMOR), 1D, "The fixture crosses a native integer boundary with protection I");
        float expected = CombatRules.getDamageAfterAbsorb(player, 8F, source, .25F, 0F);
        close(h, player.armorDamage(source, 8F), expected, "The native armor method receives protection I's restored .25 points");
        player.hurt(source, 8F);
        close(h, 100F - player.getHealth(), expected, "A real hurt preserves protection I's .25 armor without reviving ordinary .75 armor");
        h.assertTrue(100F - player.getHealth() < 8F, "Protection I already reduces real health loss");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void specializedProtectionAndBlessedEnterTheNativeArmorComputation(GameTestHelper h) {
        TestPlayer player = player(h);
        ItemStack chest = new ItemStack(Items.LEATHER_CHESTPLATE);
        chest.set(DataComponents.ATTRIBUTE_MODIFIERS, zeroArmorAttributes(.25D));
        equip(player, EquipmentSlot.CHEST, chest);
        close(h, player.getAttributeValue(Attributes.ARMOR), 0D, "Specialized armor fixture has no ordinary armor contribution");
        close(h, player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 0D, "Specialized armor fixture has no ordinary toughness contribution");
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        var arrow = new DamageSource(registry.getHolderOrThrow(DamageTypes.ARROW));
        var fireball = new DamageSource(registry.getHolderOrThrow(DamageTypes.FIREBALL));
        var explosion = new DamageSource(registry.getHolderOrThrow(DamageTypes.EXPLOSION));
        DamageSource[] sources = {arrow, fireball, explosion};
        GearModifier[] protections = {GearModifier.ARMOR_PROJECTILE_PROTECTION,
                GearModifier.ARMOR_FIRE_PROTECTION, GearModifier.ARMOR_EXPLOSION_PROTECTION};
        for (int i = 0; i < protections.length; i++) {
            modifier(chest, protections[i], 5);
            close(h, player.armorDamage(sources[i], 10F), CombatRules.getDamageAfterAbsorb(player, 10F, sources[i], 2.5F, 0F),
                    "Specialized protection contributes 2.5 armor points for " + protections[i]);
            close(h, player.armorDamage(new DamageSource(registry.getHolderOrThrow(DamageTypes.MOB_ATTACK)), 10F), 10F,
                    "Specialized protection leaves unrelated damage unchanged");
        }
        modifier(chest, GearModifier.ARMOR_BLESSED, 5);
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        var undeadHit = new DamageSource(registry.getHolderOrThrow(DamageTypes.MOB_ATTACK), zombie);
        close(h, player.armorDamage(undeadHit, 10F), CombatRules.getDamageAfterAbsorb(player, 10F, undeadHit, 5F, 0F),
                "Blessed adds five armor points against a responsible undead attacker");
        close(h, player.armorDamage(arrow, 10F), 10F, "Blessed does not protect from an unrelated projectile");
        h.succeed();
    }

    private static final class RecordingCow extends Cow {
        float received;
        RecordingCow(Level level, Player player) {
            super(EntityType.COW, level); setNoAi(true); setNoGravity(true); setPos(player.position().add(1D, 0D, 0D));
        }
        @Override public boolean hurt(DamageSource source, float amount) { received = amount; return false; }
    }

    @GameTest(template = "empty")
    public static void indomitableAndDemonMultiplyTheActualPrimaryMeleeHit(GameTestHelper h) {
        TestPlayer player = player(h);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        equip(player, EquipmentSlot.MAINHAND, sword);
        ItemStack chest = modifier(new ItemStack(Items.IRON_CHESTPLATE), GearModifier.ARMOR_INDOMITABLE, 1);
        equip(player, EquipmentSlot.CHEST, chest);
        RecordingCow target = new RecordingCow(h.getLevel(), player);
        player.setHealth(50F); player.attack(target);
        close(h, target.received, 20.25F, "Half health applies 1.25x to the ten-point fixture plus normalized 6.2 sword modifier");
        modifier(chest, GearModifier.ARMOR_INDOMITABLE, 4); player.tick(); player.setOnGround(true);
        player.setHealth(10F); player.attack(target);
        close(h, target.received, 32.4F, "Ten percent health enables 2x at any Indomitable rank");
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "The test server loads the Nether dimension");
        TestPlayer demon = new TestPlayer(nether, new BlockPos(0, 80, 0));
        equip(demon, EquipmentSlot.MAINHAND, modifier(sword.copy(), GearModifier.TOOL_DEMON_POWER, 1));
        RecordingCow netherTarget = new RecordingCow(nether, demon);
        demon.attack(netherTarget);
        close(h, netherTarget.received, 20.25F, "Demon Power multiplies real normalized melee damage by 1.25 outside the Overworld");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void slowingRequiresAnActualHealthLosingMeleeHit(GameTestHelper h) {
        TestPlayer player = player(h);
        equip(player, EquipmentSlot.MAINHAND, modifier(new ItemStack(Items.IRON_SWORD), GearModifier.TOOL_SLOWDOWN, 5));
        Cow target = EntityType.COW.create(h.getLevel());
        target.setNoAi(true); target.setNoGravity(true); target.setPos(player.position().add(1D, 0D, 0D));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D); target.setHealth(200F);
        player.attack(target);
        h.assertTrue(target.getHealth() < 200F, "The primary melee hit actually removed health");
        var slow = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        h.assertTrue(slow != null && slow.getDuration() == 100 && slow.getAmplifier() == 2,
                "Five weaving ranks apply exactly 100 ticks of Slowness III after health loss");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void steadyAndImmunityReachNativeKnockbackAndEffectApplication(GameTestHelper h) {
        TestPlayer player = player(h);
        player.knockback(.4D, 1D, 0D);
        double ordinary = player.getDeltaMovement().x;
        player.setDeltaMovement(Vec3.ZERO); player.setOnGround(true);
        equip(player, EquipmentSlot.CHEST, modifier(new ItemStack(Items.IRON_CHESTPLATE), GearModifier.ARMOR_STEADY, 5));
        player.setDeltaMovement(Vec3.ZERO); player.setOnGround(true); player.knockback(.4D, 1D, 0D);
        close(h, player.getDeltaMovement().x, ordinary * .25D, "Steady V multiplies the native knockback amount by .25");
        equip(player, EquipmentSlot.HEAD, modifier(new ItemStack(Items.IRON_HELMET), GearModifier.ARMOR_IMMUNITY, 4));
        MobEffectInstance poison = new MobEffectInstance(MobEffects.POISON, 100);
        player.addEffect(poison);
        h.assertTrue(player.getEffect(MobEffects.POISON).getDuration() == (int)(100F * (1F - .6F)),
                "Native addEffect receives the original truncated 60-percent reduction");
        h.assertTrue(poison.getDuration() == 100, "The original effect template is not mutated");
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100));
        h.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() == 100, "Immunity leaves beneficial effects unchanged");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void geologyUsesRealSmeltingRecipesAndNativeDropEvents(GameTestHelper h) {
        TestPlayer player = player(h);
        ItemStack pickaxe = modifier(new ItemStack(Items.IRON_PICKAXE), GearModifier.TOOL_GEOLOGY, 1);
        BlockDropsEvent ore = new BlockDropsEvent(h.getLevel(), player.blockPosition(), MiteOreBlocks.MITHRIL_ORE.get().defaultBlockState(),
                null, new ArrayList<>(), player, pickaxe);
        int ordinary = ore.getDroppedExperience();
        NeoForge.EVENT_BUS.post(ore);
        h.assertTrue(ore.getDroppedExperience() == ordinary + 20, "Mithril's registered ore-to-ingot recipe rewards half its source 40 XP");
        BlockDropsEvent gem = new BlockDropsEvent(h.getLevel(), player.blockPosition(), Blocks.DIAMOND_ORE.defaultBlockState(),
                null, new ArrayList<>(), player, pickaxe);
        int gemXp = gem.getDroppedExperience(); NeoForge.EVENT_BUS.post(gem);
        h.assertTrue(gem.getDroppedExperience() == gemXp, "Existing gem mining XP is not awarded twice");
        BlockDropsEvent placed = new BlockDropsEvent(h.getLevel(), player.blockPosition(), MiteOreBlocks.MITHRIL_ORE.get().defaultBlockState()
                .setValue(io.github.ningmeng123.mite.rules.OreBlockRules.PLACED, true), null, new ArrayList<>(), player, pickaxe);
        int placedXp = placed.getDroppedExperience(); NeoForge.EVENT_BUS.post(placed);
        h.assertTrue(placed.getDroppedExperience() == placedXp, "Placed source ores preserve the reference geology exclusion");
        h.succeed();
    }
}

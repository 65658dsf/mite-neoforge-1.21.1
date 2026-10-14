package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.item.SourceArmorDefense;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual NeoForge health transactions prove source mitigation is used exactly once. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SourceArmorDefenseGameTests {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final String[] PARTS = {"boots", "leggings", "chestplate", "helmet"};
    private SourceArmorDefenseGameTests() {}

    @GameTest(template = "empty")
    public static void armorStagesReduceRealHealthDamageUsingComparableFlatProtection(GameTestHelper helper) {
        String[] materials = {"copper", "iron", "mithril", "adamantium"};
        float[] expectedDefense = {7F, 8F, 9F, 10F};
        float[] expectedDamage = {4F, 3F, 2F, 1F};
        for (int index = 0; index < materials.length; index++) {
            try (Session session = newPlayer(helper)) {
                var player = session.player();
                equip(player, materials[index]);
                player.doTick();
                helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ARMOR) - expectedDefense[index]) < .0001,
                        "Displayed source stage agrees with actual defense: " + materials[index]);
                float before = player.getHealth();
                player.hurt(player.damageSources().cactus(), 11F);
                close(helper, before - player.getHealth(), expectedDamage[index], "True health loss for source " + materials[index]);
                int wear = 0;
                for (var slot : SLOTS) wear += player.getItemBySlot(slot).getDamageValue();
                helper.assertValueEqual(wear, (int)expectedDefense[index], "One total source wear budget is distributed across the set");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void protectionAndProjectileProtectionUseSourceBonusesWithoutNativeDoubleReduction(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player();
            equip(player, "iron");
            var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            for (var slot : SLOTS) {
                player.getItemBySlot(slot).enchant(registry.getHolderOrThrow(Enchantments.PROTECTION), 4);
                player.getItemBySlot(slot).enchant(registry.getHolderOrThrow(Enchantments.PROJECTILE_PROTECTION), 4);
            }
            float before = player.getHealth();
            var projectile = new DamageSource(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.ARROW));
            player.hurt(projectile, 25F);
            close(helper, before - player.getHealth(), 1F, "Eight base + four ordinary bonus + twelve projectile bonus yields exactly one damage from 25");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void resistanceProvidesFiveFlatDefenseInsteadOfAnAdditionalModernPercentage(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player();
            equip(player, "iron");
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
            float before = player.getHealth();
            player.hurt(player.damageSources().cactus(), 14F);
            close(helper, before - player.getHealth(), 1F, "Eight armor plus five resistance is applied once to incoming fourteen");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modernBreachKeepsItsNativeArmorEffectivenessDataFunction(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player();
            equip(player, "iron");
            var attacker = EntityType.ZOMBIE.create(helper.getLevel());
            helper.assertTrue(attacker != null, "Native living attacker fixture created");
            ItemStack mace = new ItemStack(Items.MACE);
            mace.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.BREACH), 2);
            attacker.setItemSlot(EquipmentSlot.MAINHAND, mace);
            var source = new DamageSource(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.PLAYER_ATTACK), attacker);
            helper.assertTrue(source.getWeaponItem() != null && source.getWeaponItem().is(Items.MACE), "Actual damage source supplies the enchanted weapon");
            float before = player.getHealth();
            player.hurt(source, 8F);
            close(helper, before - player.getHealth(), 2.4F, "Native Breach II subtracts 0.3 from the per-hit source armor effectiveness ratio");
            int wear = 0;
            for (var slot : SLOTS) wear += player.getItemBySlot(slot).getDamageValue();
            helper.assertValueEqual(wear, 8, "Modern armor effectiveness does not change source pre-piercing wear budget");
            attacker.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fireResistanceIsSourceDefenseForManagedArmorAndNativeImmunityForBarePlayers(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player();
            equip(player, "iron");
            var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            for (var slot : SLOTS) player.getItemBySlot(slot).enchant(registry.getHolderOrThrow(Enchantments.FIRE_PROTECTION), 4);
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
            float before = player.getHealth();
            player.hurt(player.damageSources().inFire(), 14F);
            close(helper, before - player.getHealth(), 1F, "Eight fire-enchantment protection and five potion protection are not full modern immunity");
        }
        try (Session session = newPlayer(helper)) {
            var player = session.player();
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
            float before = player.getHealth();
            helper.assertFalse(player.hurt(player.damageSources().inFire(), 14F), "Bare player keeps native fire immunity");
            close(helper, before - player.getHealth(), 0F, "No managed armor means the source rewrite is inactive");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void featherFallingIsFifteenFlatProtectionAndBypassedArmorStillMakesOneWearAttempt(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player();
            // ud consumes a portion for an empty slot too; fill all four slots so the one-point wear assertion is deterministic.
            equip(player, "iron");
            ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
            boots.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FEATHER_FALLING), 4);
            float before = player.getHealth();
            player.hurt(player.damageSources().fall(), 16F);
            close(helper, before - player.getHealth(), 1F, "Source feather falling four subtracts fifteen from sixteen");
            int wear = 0;
            for (var slot : SLOTS) wear += player.getItemBySlot(slot).getDamageValue();
            helper.assertValueEqual(wear, 1, "Source ud makes a one-point armor wear attempt even when mundane defense is bypassed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lavaDoesNotAcquireFireFlagThatSourceNeverSet(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player();
            equip(player, "iron");
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
            var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            for (var slot : SLOTS) player.getItemBySlot(slot).enchant(registry.getHolderOrThrow(Enchantments.FIRE_PROTECTION), 4);
            float before = player.getHealth();
            player.hurt(player.damageSources().lava(), 3F);
            close(helper, before - player.getHealth(), 3F, "R196 lava bypasses mundane armor and does not use the normal fire flag");
            int wear = 0;
            for (var slot : SLOTS) wear += player.getItemBySlot(slot).getDamageValue();
            helper.assertValueEqual(wear, 10, "The source minimum wear attempt is multiplied by ten for lava");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledWearKeepsSnapshotDefenseAndRemovingArmorMidHitCannotApplyMagicTwice(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player();
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
            Consumer<ArmorHurtEvent> listener = event -> {
                if (event.getEntity() == player) {
                    event.setCanceled(true);
                    player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                }
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                float before = player.getHealth();
                player.hurt(player.damageSources().cactus(), 10F);
                close(helper, before - player.getHealth(), 7F / 3F, "Defense was snapshotted before armor event, and resistance is not reapplied after armor removal");
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
            helper.assertFalse(SourceArmorDefense.canHandle(player), "Player is now bare");
            player.invulnerableTime = 0;
            float before = player.getHealth();
            player.hurt(player.damageSources().cactus(), 2F);
            close(helper, before - player.getHealth(), 1.6F, "Following bare-player hit resumes native resistance with no leaked marker");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unprofiledArmorMakesMixedEquipmentUseNativeMitigation(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player();
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.TURTLE_HELMET));
            helper.assertFalse(SourceArmorDefense.canHandle(player), "Unknown armor profile prevents guessed source defense for mixed mod equipment");
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
            helper.assertFalse(player.hurt(player.damageSources().inFire(), 100F), "Mixed unsupported set retains native fire immunity");
        }
        helper.succeed();
    }

    private static void equip(ServerPlayer player, String material) {
        for (int slot = 0; slot < 4; slot++) {
            String id = material.equals("iron") ? "minecraft:iron_" + PARTS[slot] : "mite:" + material + "_" + PARTS[slot];
            player.setItemSlot(SLOTS[slot], new ItemStack(MiteArmorContent.get(id)));
        }
    }
    private static void close(GameTestHelper helper, float actual, float expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .0001F, message + ": expected " + expected + ", got " + actual);
    }
    private static Session newPlayer(GameTestHelper helper) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-defense-027"), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 2))));
            player.setNoGravity(true);
            player.setOnGround(true);
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            player.setHealth(player.getMaxHealth());
            for (int tick = 0; tick < 60; tick++) player.tick();
            return new Session(player, channel);
        } catch (RuntimeException | Error failure) {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }
    private record Session(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            try { if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player); }
            finally { channel.finishAndReleaseAll(); }
        }
    }
}

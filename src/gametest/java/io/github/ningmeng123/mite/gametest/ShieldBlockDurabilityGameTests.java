package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Shield wear is exercised only through real hurt and the cancellable NeoForge shield event. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ShieldBlockDurabilityGameTests {
    private static LivingEntity defender(GameTestHelper h, boolean player, InteractionHand hand, String material) {
        LivingEntity defender;
        if (player) defender = h.makeMockPlayer(GameType.SURVIVAL);
        else {
            var zombie = EntityType.ZOMBIE.create(h.getLevel()); zombie.setNoAi(true); defender = zombie;
        }
        defender.setNoGravity(true); defender.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 10, 1))));
        defender.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D); defender.setHealth(200F);
        defender.setYRot(0F); defender.setYHeadRot(0F); defender.yBodyRot = 0F;
        defender.setItemInHand(hand, new ItemStack(MiteShields.get(material)));
        defender.startUsingItem(hand);
        for (int tick = 0; tick < 6; ++tick) defender.tick();
        h.assertTrue(defender.isBlocking(), "The native blocking warm-up completes for the real equipped shield");
        return defender;
    }
    private static Player attacker(GameTestHelper h, LivingEntity defender) {
        Player attacker = h.makeMockPlayer(GameType.SURVIVAL);
        attacker.setPos(defender.position().add(0D, 0D, 2D)); attacker.setNoGravity(true);
        attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        return attacker;
    }

    @GameTest(template = "empty")
    public static void acceptedFrontBlocksWearTheRealPlayerAndMobShieldButRearHitsDoNot(GameTestHelper h) {
        for (boolean isPlayer : List.of(false, true)) for (InteractionHand hand : InteractionHand.values()) {
            LivingEntity defender = defender(h, isPlayer, hand, "copper"); Player attacker = attacker(h, defender);
            ItemStack shield = defender.getItemInHand(hand); float health = defender.getHealth();
            var damage = defender.damageSources().playerAttack(attacker);
            h.assertTrue(defender.isDamageSourceBlocked(damage), "The source is inside the native front blocking arc");
            defender.hurt(damage, 4F);
            h.assertValueEqual(shield.getDamageValue(), 5, "A real four-damage block costs exactly 1 + floor(4) native durability units");
            h.assertTrue(defender.getHealth() == health, "The accepted block preserves the defender's health");
            attacker.setPos(defender.position().add(0D, 0D, -2D)); defender.invulnerableTime = 0;
            defender.hurt(damage, 4F);
            h.assertValueEqual(shield.getDamageValue(), 5, "A real rear hit causes no additional shield wear");
            h.assertTrue(defender.getHealth() < health, "The rear hit bypasses the raised shield");
            defender.discard(); attacker.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledAndZeroWearShieldEventsControlTheActualDamagePath(GameTestHelper h) {
        for (boolean isPlayer : List.of(false, true)) for (boolean cancel : List.of(false, true)) {
            LivingEntity defender = defender(h, isPlayer, InteractionHand.OFF_HAND, "iron");
            Player attacker = attacker(h, defender); ItemStack shield = defender.getOffhandItem(); shield.setDamageValue(17);
            float health = defender.getHealth(); boolean[] seen = {false};
            Consumer<LivingShieldBlockEvent> listener = event -> {
                if (event.getEntity() != defender) return;
                seen[0] = true;
                if (cancel) event.setCanceled(true); else event.setShieldDamage(0F);
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                defender.hurt(defender.damageSources().playerAttack(attacker), 4F);
                String fixture = " (player=" + isPlayer + ", canceled=" + cancel + ")";
                h.assertTrue(seen[0], "Actual damage posts the native shield event before any wear" + fixture);
                h.assertValueEqual(shield.getDamageValue(), 17, "Canceled and zero-budget shield events prevent wear" + fixture);
                h.assertTrue(cancel ? defender.getHealth() < health : defender.getHealth() == health,
                        "Canceling the block permits health damage; canceling only wear keeps the block" + fixture);
            } finally { NeoForge.EVENT_BUS.unregister(listener); defender.discard(); attacker.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeSmallHitThresholdAndEventReplacementBudgetRemainEffective(GameTestHelper h) {
        for (boolean isPlayer : List.of(false, true)) {
            LivingEntity defender = defender(h, isPlayer, InteractionHand.OFF_HAND, "silver"); Player attacker = attacker(h, defender);
            ItemStack shield = defender.getOffhandItem(); var damage = defender.damageSources().playerAttack(attacker);
            defender.hurt(damage, 2F);
            h.assertValueEqual(shield.getDamageValue(), 0, "Native sub-three-damage blocks cost no durability");
            Consumer<LivingShieldBlockEvent> listener = event -> {
                if (event.getEntity() == defender) event.setShieldDamage(7F);
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                defender.invulnerableTime = 0; defender.hurt(damage, 4F);
                h.assertValueEqual(shield.getDamageValue(), 8, "Event-authorized seven-damage budget costs 1 + floor(7), not the raw hit");
            } finally { NeoForge.EVENT_BUS.unregister(listener); defender.discard(); attacker.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void almostBrokenShieldsActuallyBreakAndEndTheNativeUseState(GameTestHelper h) {
        for (boolean isPlayer : List.of(false, true)) for (InteractionHand hand : InteractionHand.values()) {
            LivingEntity defender = defender(h, isPlayer, hand, "rusted_iron"); Player attacker = attacker(h, defender);
            ItemStack shield = defender.getItemInHand(hand); shield.setDamageValue(shield.getMaxDamage() - 1);
            defender.hurt(defender.damageSources().playerAttack(attacker), 4F);
            h.assertTrue(shield.isEmpty() && defender.getItemInHand(hand).isEmpty(), "A real block can consume the shield's last durability point");
            h.assertFalse(defender.isUsingItem(), "Native use state ends immediately when a held shield breaks");
            defender.discard(); attacker.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void miteAxesAlsoDisablePlayerShieldsThroughTheNativePlayerHook(GameTestHelper h) {
        for (String id : List.of("mite:copper_axe", "mite:ancient_metal_battle_axe", "mite:rusted_iron_hatchet")) {
            LivingEntity defender = defender(h, true, InteractionHand.OFF_HAND, "iron"); Player attacker = attacker(h, defender);
            attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MiteTools.get(id)));
            h.assertTrue(attacker.canDisableShield(), "The shared native disable predicate recognizes " + id);
            defender.hurt(defender.damageSources().playerAttack(attacker), 4F);
            h.assertFalse(defender.isUsingItem(), "The native Player.blockUsingShield hook lowers the shield after a MITE axe block");
            h.assertTrue(((Player)defender).getCooldowns().isOnCooldown(MiteShields.get("iron")), "The real player shield cooldown is applied");
            defender.discard(); attacker.discard();
        }
        h.succeed();
    }
}

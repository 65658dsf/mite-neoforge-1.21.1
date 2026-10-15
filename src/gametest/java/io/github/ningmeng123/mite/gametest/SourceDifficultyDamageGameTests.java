package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.R196ExplosionCalculator;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.IScalingFunction;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent source uf/abr expectations through actual Player.hurt, not a direct rules calculation. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SourceDifficultyDamageGameTests {
    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 15, 2))));
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
        player.setHealth(100F);
        return player;
    }

    private static void hit(GameTestHelper helper, Player player, DamageSource source, float input, float expected) {
        player.setHealth(100F);
        player.invulnerableTime = 0;
        player.setAbsorptionAmount(0F);
        helper.assertTrue(player.hurt(source, input), "The actual survival player accepts the damage transaction");
        helper.assertTrue(Math.abs((100F - player.getHealth()) - expected) < .0001F,
                "Source incoming damage is " + expected + ", actual " + (100F - player.getHealth()) + " for " + source);
    }

    @GameTest(template = "empty")
    public static void ordinaryMobHitsDoNotReceiveTheModernHardMultiplier(GameTestHelper helper) {
        Player player = player(helper);
        for (var type : List.of(EntityType.ZOMBIE, EntityType.SPIDER, EntityType.SKELETON)) {
            var attacker = type.create(helper.getLevel());
            hit(helper, player, attacker.damageSources().mobAttack(attacker), 4F, 4F);
            attacker.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mobArrowDamageAndUnownedArrowsRetainTheirSourceAmount(GameTestHelper helper) {
        Player player = player(helper);
        var skeleton = EntityType.SKELETON.create(helper.getLevel());
        Arrow arrow = EntityType.ARROW.create(helper.getLevel());
        hit(helper, player, player.damageSources().arrow(arrow, skeleton), 4F, 4F);
        hit(helper, player, player.damageSources().arrow(arrow, null), 4F, 4F);
        arrow.discard(); skeleton.discard(); helper.succeed();
    }

    @GameTest(template = "empty")
    public static void playerAndEnvironmentalDamageDoNotGainAWorldDifficultyFactor(GameTestHelper helper) {
        Player player = player(helper);
        Player attacker = player(helper);
        hit(helper, player, player.damageSources().playerAttack(attacker), 4F, 4F);
        hit(helper, player, player.damageSources().generic(), 4F, 4F);
        hit(helper, player, player.damageSources().fall(), 4F, 4F);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mobPlayerAndUnownedExplosionsApplyExactlyOneSourcePlayerMultiplier(GameTestHelper helper) {
        Player player = player(helper);
        var creeper = EntityType.CREEPER.create(helper.getLevel());
        var tnt = EntityType.TNT.create(helper.getLevel());
        hit(helper, player, player.damageSources().explosion(creeper, creeper), 4F, 6F);
        hit(helper, player, player.damageSources().explosion(tnt, player(helper)), 4F, 6F);
        hit(helper, player, player.damageSources().explosion(null, null), 4F, 6F);
        hit(helper, player, player.damageSources().badRespawnPointExplosion(player.position()), 4F, 6F);
        creeper.discard(); tnt.discard(); helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceCreeperCalculatorDoesNotAlreadyIncludeThePlayerMultiplier(GameTestHelper helper) {
        Player player = player(helper);
        var creeper = EntityType.CREEPER.create(helper.getLevel());
        Vec3 center = player.position();
        var blast = new Explosion(helper.getLevel(), creeper, center.x, center.y, center.z, 3F, false, Explosion.BlockInteraction.KEEP);
        float raw = new R196ExplosionCalculator(creeper, 1.1F).getEntityDamageAmount(blast, player);
        // abr: power1.1 * radius4 * damage4 * 2/3; uf alone adds the final player1.5.
        helper.assertTrue(Math.abs(raw - 11.733334F) < .0001F, "The existing source calculator returns abr damage before uf's player adjustment");
        hit(helper, player, player.damageSources().explosion(creeper, creeper), raw, 17.6F);
        creeper.discard(); helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceDifficultyAdjustmentRetainsImmunityAndCancellableIncomingEvents(GameTestHelper helper) {
        Player player = player(helper);
        DamageSource source = player.damageSources().explosion(null, null);
        Consumer<LivingIncomingDamageEvent> cancellation = event -> {
            if (event.getEntity() == player) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, cancellation);
        try {
            player.hurt(source, 4F);
            helper.assertValueEqual(player.getHealth(), 100F, "Incoming cancellation still prevents adjusted damage");
        } finally { NeoForge.EVENT_BUS.unregister(cancellation); }
        player.getAbilities().invulnerable = true;
        helper.assertTrue(!player.hurt(source, 4F), "Creative-style invulnerability is checked before difficulty adjustment");
        helper.assertValueEqual(player.getHealth(), 100F, "Immunity retains health");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void customNeoForgeScalingRetainsItsOwnDamageAndRunsOnce(GameTestHelper helper) throws ReflectiveOperationException {
        // Exercise the exact public extension hook without adding a production enum constant.
        // The field is restored synchronously before another test can observe the temporary function.
        Field field = DamageScaling.class.getDeclaredField("scaling");
        field.setAccessible(true);
        Player player = player(helper);
        // Damage types select their enum through data. Generic is not guaranteed to use NEVER;
        // use its actual registered policy so the extension travels through the real hurt lookup.
        for (DamageSource source : List.of(player.damageSources().generic(), player.damageSources().explosion(null, null))) {
            DamageScaling scaling = source.type().scaling();
            IScalingFunction previous = scaling.getScalingFunction();
            int[] calls = {0};
            IScalingFunction custom = (damageSource, victim, input, difficulty) -> { calls[0]++; return input * .25F; };
            try {
                field.set(scaling, custom);
                helper.assertTrue(source.type().scaling().getScalingFunction() == custom,
                        "The real registered damage type reaches the temporarily supplied custom function");
                hit(helper, player, source, 4F, 1F);
                helper.assertValueEqual(calls[0], 1,
                        "The custom scaling function, including custom explosion scaling, remains the single damage adjustment");
            } finally { field.set(scaling, previous); }
            helper.assertTrue(scaling.getScalingFunction() == previous, "The global damage extension state was restored");
        }
        helper.succeed();
    }
}

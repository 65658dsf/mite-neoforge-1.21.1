package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MonsterHunting;
import io.github.ningmeng123.mite.entity.PiglinRetaliation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises real piglin anger broadcasts and the NeoForge brain target event. @author NingMeng123 */
@GameTestHolder("mite_hunting")
@PrefixGameTestTemplate(false)
public final class PiglinHuntingGameTests {
    private static final long ORDINARY_NIGHT = 18_000L;

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final BloodMoonHuntingTestClock clock;
        final List<Mob> entities = new ArrayList<>();

        Fixture(GameTestHelper h) {
            this.h = h;
            clock = new BloodMoonHuntingTestClock(h.getLevel());
            h.getLevel().setDayTime(ORDINARY_NIGHT);
        }

        <T extends Mob> T mob(EntityType<T> type, double x) {
            T mob = type.create(h.getLevel());
            var origin = h.absolutePos(new BlockPos(1, 5, 1));
            mob.moveTo(origin.getX() + .5D + x, origin.getY(), origin.getZ() + .5D, 0, 0);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            if (mob instanceof AbstractPiglin piglin) piglin.setImmuneToZombification(true);
            h.getLevel().addFreshEntity(mob);
            entities.add(mob);
            return mob;
        }

        boolean startAttack(Piglin piglin, LivingEntity target) {
            return StartAttacking.<Piglin>create(actor -> Optional.of(target))
                    .tryStart(h.getLevel(), piglin, h.getLevel().getGameTime());
        }

        @Override public void close() {
            entities.forEach(Mob::discard);
            clock.close();
        }
    }

    private static void invokeAi(String name, AbstractPiglin piglin, LivingEntity target)
            throws ReflectiveOperationException {
        Method method = PiglinAi.class.getDeclaredMethod(name,
                name.equals("wasHurtBy") ? Piglin.class : AbstractPiglin.class, LivingEntity.class);
        method.setAccessible(true);
        method.invoke(null, piglin, target);
    }

    @GameTest(template = "empty")
    public static void hurtBroadcastLetsUnhurtPiglinAlliesRetaliateOnOrdinaryNights(GameTestHelper h)
            throws ReflectiveOperationException {
        try (var f = new Fixture(h)) {
            var injured = f.mob(EntityType.PIGLIN, 0);
            var ally = f.mob(EntityType.PIGLIN, 1);
            var attacker = f.mob(EntityType.VILLAGER, 2);
            injured.setLastHurtByMob(attacker);
            injured.getBrain().setMemory(MemoryModuleType.NEARBY_ADULT_PIGLINS, List.of(ally));
            invokeAi("wasHurtBy", injured, attacker);
            h.assertTrue(ally.getLastHurtByMob() == null, "The responding ally has never been hurt");
            h.assertTrue(ally.getBrain().getMemory(MemoryModuleType.ANGRY_AT).filter(attacker.getUUID()::equals).isPresent(),
                    "Vanilla's nearby-adult broadcast records the attacker's UUID on the unhurt ally");
            h.assertTrue(PiglinRetaliation.isRetaliating(ally, attacker), "The actual hurt broadcast retains retaliation provenance");
            h.assertTrue(f.startAttack(ally, attacker), "StartAttacking accepts the ally's real ordinary-night retaliation");
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(ally));
            h.assertTrue(ally.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == attacker,
                    "Pre-AI expiry does not remove the responding ally's attack target");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void proactiveHoglinAngerAndBroadcastDoNotBypassTheBloodNightGate(GameTestHelper h)
            throws ReflectiveOperationException {
        try (var f = new Fixture(h)) {
            var hunter = f.mob(EntityType.PIGLIN, 0);
            var ally = f.mob(EntityType.PIGLIN, 1);
            var hoglin = f.mob(EntityType.HOGLIN, 2);
            hunter.getBrain().setMemory(MemoryModuleType.NEARBY_ADULT_PIGLINS, List.of(ally));
            // These are the same anger setter and synchronous broadcast used by StartHuntingHoglin.
            invokeAi("setAngerTarget", hunter, hoglin);
            invokeAi("broadcastAngerTarget", hunter, hoglin);
            for (Piglin piglin : List.of(hunter, ally)) {
                h.assertTrue(piglin.getBrain().getMemory(MemoryModuleType.ANGRY_AT).filter(hoglin.getUUID()::equals).isPresent(),
                        "The normal hoglin hunt has real angry memory");
                h.assertFalse(PiglinRetaliation.isRetaliating(piglin, hoglin), "Proactive anger and broadcasts do not become retaliation");
                h.assertFalse(MonsterHunting.mayAttackPrey(piglin, hoglin), "Angry memory alone does not exempt passive prey");
                h.assertFalse(f.startAttack(piglin, hoglin), "The real StartAttacking path rejects ordinary-night hoglin hunting");
                h.assertTrue(piglin.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty(), "No attack memory leaks past the rejected event");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void proactiveReassignmentOfTheSameAngerTargetClearsOldRetaliation(GameTestHelper h)
            throws ReflectiveOperationException {
        try (var f = new Fixture(h)) {
            var injured = f.mob(EntityType.PIGLIN, 0);
            var ally = f.mob(EntityType.PIGLIN, 1);
            var target = f.mob(EntityType.VILLAGER, 2);
            injured.setLastHurtByMob(target);
            injured.getBrain().setMemory(MemoryModuleType.NEARBY_ADULT_PIGLINS, List.of(ally));
            invokeAi("wasHurtBy", injured, target);
            h.assertTrue(PiglinRetaliation.isRetaliating(ally, target), "Fixture starts with genuine ally retaliation");
            invokeAi("setAngerTarget", ally, target);
            h.assertTrue(ally.getBrain().getMemory(MemoryModuleType.ANGRY_AT).filter(target.getUUID()::equals).isPresent(),
                    "The later proactive assignment retains the very same angry UUID");
            h.assertFalse(PiglinRetaliation.isRetaliating(ally, target), "Proactive rewriting clears the old retaliation source even for the same UUID");
            h.assertFalse(f.startAttack(ally, target), "The stale retaliation source cannot permit ordinary-night hunting");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bloodNightBrainHuntStopsAndCannotRestartFromLingeringAnger(GameTestHelper h)
            throws ReflectiveOperationException {
        try (var f = new Fixture(h)) {
            var hunter = f.mob(EntityType.PIGLIN, 0);
            var hoglin = f.mob(EntityType.HOGLIN, 2);
            h.getLevel().setDayTime(BloodMoonHuntingTestClock.BLOOD_NIGHT);
            invokeAi("setAngerTarget", hunter, hoglin);
            h.assertTrue(f.startAttack(hunter, hoglin), "Blood-night brain hunting acquires a real attack memory");
            h.getLevel().setDayTime(ORDINARY_NIGHT);
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(hunter));
            h.assertTrue(hunter.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty(),
                    "The pre-AI path removes active brain hunting as soon as the blood night ends");
            h.assertTrue(hunter.getBrain().getMemory(MemoryModuleType.ANGRY_AT).filter(hoglin.getUUID()::equals).isPresent(),
                    "Fixture retains vanilla's longer-lived angry memory");
            h.assertFalse(f.startAttack(hunter, hoglin), "Lingering angry memory cannot reacquire the ordinary-night prey");
        }
        h.succeed();
    }
}

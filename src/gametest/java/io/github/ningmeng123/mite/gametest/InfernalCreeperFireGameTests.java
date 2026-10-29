package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual creeper detonations leave world fire only on approved, solidly supported ground. @author NingMeng123 */
@GameTestHolder("mite_infernal_fire")
@PrefixGameTestTemplate(false)
public final class InfernalCreeperFireGameTests {
    private InfernalCreeperFireGameTests() {}

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void infernalCreeperLeavesPhysicalGroundFire(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 0)) {
            Creeper creeper = fixture.creeper(true, false);
            Explosion explosion = fixture.detonate(creeper, false, ignored -> {});
            Set<BlockPos> fire = fixture.fire();
            helper.assertValueEqual(explosion.radius(), 6F, "Infernal fire retains the existing modern block strength");
            helper.assertTrue(fire.size() >= 10, "A real infernal detonation leaves many world fire blocks, not only flame particles");
            fixture.assertSupportedFire(explosion, fire);
            helper.assertTrue(creeper.isRemoved(), "Ground fire retains the native creeper removal lifecycle");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void chargedInfernalCreeperRetainsItsLargerBurningArea(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 1)) {
            Explosion explosion = fixture.detonate(fixture.creeper(true, true), false, ignored -> {});
            Set<BlockPos> fire = fixture.fire();
            helper.assertValueEqual(explosion.radius(), 12F, "Charging and infernal multiplication both remain effective");
            helper.assertTrue(fire.size() >= 30, "A charged infernal explosion produces physical ground fire");
            helper.assertTrue(fire.stream().anyMatch(pos -> pos.distToCenterSqr(explosion.center()) > 9D * 9D),
                    "Charged infernal fire reaches ground beyond the uncharged blast's ordinary reach");
            fixture.assertSupportedFire(explosion, fire);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void ordinaryAndChargedOrdinaryCreepersDoNotLeaveFire(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 2)) {
            for (boolean charged : new boolean[]{false, true}) {
                fixture.detonate(fixture.creeper(false, charged), false, ignored -> {});
                helper.assertTrue(fixture.fire().isEmpty(), "Ordinary creepers remain non-incendiary, charged=" + charged);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void mobGriefingAndCancelledDetonationsPreventGroundFire(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 3)) {
            fixture.rule(GameRules.RULE_MOBGRIEFING, false);
            fixture.detonate(fixture.creeper(true, true), false, ignored -> {});
            helper.assertTrue(fixture.fire().isEmpty(), "mobGriefing=false prevents infernal fire as well as block destruction");
            fixture.rule(GameRules.RULE_MOBGRIEFING, true);
            fixture.detonate(fixture.creeper(true, true), true, ignored -> {});
            helper.assertTrue(fixture.fire().isEmpty(), "A cancelled native explosion cannot leave ground fire");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void protectedExplosionPositionsCannotIgnite(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 4)) {
            Explosion explosion = fixture.detonate(fixture.creeper(true, false), false,
                    event -> event.getAffectedBlocks().removeIf(pos -> pos.getX() >= fixture.anchor.getX()));
            Set<BlockPos> fire = fixture.fire();
            helper.assertTrue(fire.size() >= 5, "The unprotected half of the actual blast still burns");
            helper.assertTrue(fire.stream().noneMatch(pos -> pos.getX() >= fixture.anchor.getX()),
                    "Positions removed by a public protection event are never ignited");
            fixture.assertSupportedFire(explosion, fire);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void fireDoesNotFloatOverAirOrWater(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 5)) {
            for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
                if (x >= -3) fixture.put(fixture.anchor.offset(x, -1, z),
                        (x > 3 ? Blocks.WATER : Blocks.AIR).defaultBlockState());
            }
            Explosion explosion = fixture.detonate(fixture.creeper(true, true), false, ignored -> {});
            Set<BlockPos> fire = fixture.fire();
            helper.assertTrue(fire.size() >= 10, "The solid ground section still receives real fire");
            helper.assertTrue(fire.stream().noneMatch(pos -> pos.getX() >= fixture.anchor.getX() - 3),
                    "Residual fire neither floats over air nor sits on a water surface");
            fixture.assertSupportedFire(explosion, fire);
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final BlockPos anchor;
        final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        final boolean oldGriefing;
        final boolean oldFireTick;

        Fixture(GameTestHelper helper, int slot) {
            this.helper = helper;
            level = helper.getLevel();
            anchor = new BlockPos((3650 + slot * 8) * 16 + 8, 240, 3650 * 16 + 8);
            oldGriefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
            oldFireTick = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
            rule(GameRules.RULE_MOBGRIEFING, true);
            // Preserve the initial placement for inspection; fire ticking is not a production ignition condition.
            rule(GameRules.RULE_DOFIRETICK, false);
            for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
                put(anchor.offset(x, -1, z), Blocks.OBSIDIAN.defaultBlockState());
                put(anchor.offset(x, 0, z), Blocks.AIR.defaultBlockState());
            }
        }

        void put(BlockPos pos, BlockState state) {
            previous.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, state, 2);
        }

        Creeper creeper(boolean infernal, boolean charged) {
            Creeper creeper = infernal ? (Creeper)MiteMobs.type(MobKind.INFERNAL_CREEPER).create(level) : EntityType.CREEPER.create(level);
            CompoundTag tag = new CompoundTag();
            creeper.addAdditionalSaveData(tag);
            tag.putBoolean("powered", charged);
            creeper.readAdditionalSaveData(tag);
            creeper.moveTo(anchor.getX() + .5D, anchor.getY(), anchor.getZ() + .5D, 0F, 0F);
            creeper.setNoAi(true);
            creeper.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(creeper), "The tested creeper enters the actual entity lifecycle");
            return creeper;
        }

        Explosion detonate(Creeper creeper, boolean cancel, Consumer<ExplosionEvent.Detonate> protection) {
            AtomicReference<Explosion> observed = new AtomicReference<>();
            Consumer<ExplosionEvent.Start> start = event -> {
                if (event.getLevel() != level || event.getExplosion().getDirectSourceEntity() != creeper) return;
                observed.set(event.getExplosion());
                try {
                    // Explosion owns an independent RNG; seeding only level.random would leave the fire test random.
                    Field random = Explosion.class.getDeclaredField("random");
                    random.setAccessible(true);
                    ((RandomSource)random.get(event.getExplosion())).setSeed(817263L);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Cannot seed the real explosion's fire RNG", failure);
                }
                if (cancel) event.setCanceled(true);
            };
            Consumer<ExplosionEvent.Detonate> detonate = event -> {
                if (event.getLevel() == level && event.getExplosion().getDirectSourceEntity() == creeper) protection.accept(event);
            };
            NeoForge.EVENT_BUS.addListener(ExplosionEvent.Start.class, start);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ExplosionEvent.Detonate.class, detonate);
            try {
                level.random.setSeed(817263L);
                Method explode = Creeper.class.getDeclaredMethod("explodeCreeper");
                explode.setAccessible(true);
                explode.invoke(creeper);
                helper.assertTrue(observed.get() != null, "The actual private creeper detonation enters the public explosion pipeline");
                return observed.get();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot invoke actual creeper detonation", failure);
            } finally {
                NeoForge.EVENT_BUS.unregister(start);
                NeoForge.EVENT_BUS.unregister(detonate);
                creeper.discard();
            }
        }

        Set<BlockPos> fire() {
            Set<BlockPos> result = new HashSet<>();
            previous.keySet().forEach(pos -> { if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) result.add(pos); });
            return result;
        }

        void assertSupportedFire(Explosion explosion, Set<BlockPos> fire) {
            for (BlockPos pos : fire) {
                helper.assertTrue(explosion.getToBlow().contains(pos), "Fire remains inside the public event's approved affected positions");
                helper.assertTrue(pos.getY() == anchor.getY(), "Ground fire is placed in an air cell immediately above the floor");
                helper.assertTrue(level.getBlockState(pos.below()).isSolidRender(level, pos.below()), "Every fire block has a solid opaque support");
            }
        }

        void rule(GameRules.Key<GameRules.BooleanValue> key, boolean value) {
            level.getGameRules().getRule(key).set(value, level.getServer());
        }

        @Override public void close() {
            previous.forEach((pos, state) -> level.setBlock(pos, state, 2));
            rule(GameRules.RULE_MOBGRIEFING, oldGriefing);
            rule(GameRules.RULE_DOFIRETICK, oldFireTick);
        }
    }
}

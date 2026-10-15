package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Compare actual Creeper.explodeCreeper block rays with the native modern engine. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CreeperExplosionGameTests {
    @GameTest(template = "empty")
    public static void ordinaryCreeperBlockRaysMatchModernRadiusThree(GameTestHelper helper) {
        compare(helper, 0, false, false, 3, 3F);
    }

    @GameTest(template = "empty")
    public static void chargedCreeperBlockRaysMatchModernRadiusSix(GameTestHelper helper) {
        compare(helper, 1, false, true, 3, 6F);
    }

    @GameTest(template = "empty")
    public static void infernalCreeperDoublesModernBlockPower(GameTestHelper helper) {
        compare(helper, 2, true, false, 3, 6F);
    }

    @GameTest(template = "empty")
    public static void chargedInfernalCreeperPreservesBothMultipliers(GameTestHelper helper) {
        compare(helper, 3, true, true, 3, 12F);
    }

    @GameTest(template = "empty")
    public static void explicitExplosionRadiusNbtIsNotDiscarded(GameTestHelper helper) {
        compare(helper, 4, false, false, 5, 5F);
    }

    private static void compare(GameTestHelper helper, int slot, boolean infernal, boolean charged, int nbtRadius, float expectedRadius) {
        try (Fixture fixture = new Fixture(helper, slot)) {
            for (long seed : new long[]{0L, 817263L}) {
                // Source infernal bodies are 1.8 blocks tall, whereas the native type is 1.7.
                // Keep the independent control's origin identical without adding this target
                // to the baseline explosion's living-entity query before it is detonated.
                Creeper creeper = fixture.creeper(infernal, charged, nbtRadius);
                Vec3 center = creeper.position().add(0D, creeper.getBbHeight() * .25F, 0D);
                fixture.restoreArena();
                fixture.level.random.setSeed(seed);
                Explosion nativeBlast = fixture.level.explode(null, center.x, center.y, center.z, expectedRadius, Level.ExplosionInteraction.MOB);
                Set<BlockPos> expectedRays = new HashSet<>(nativeBlast.getToBlow());
                Set<BlockPos> expectedDestruction = fixture.destroyed();
                helper.assertTrue(expectedDestruction.size() > 5,
                        "The independent native control really excavates the mixed dirt/stone/wood arena");
                fixture.clearDrops();
                fixture.restoreArena();
                fixture.add(creeper);
                fixture.level.random.setSeed(seed);
                Explosion actual = fixture.detonate(creeper, false);
                helper.assertValueEqual(actual.radius(), expectedRadius,
                        "The real creeper keeps the native radius, with exactly the charged/infernal/NBT multipliers");
                helper.assertTrue(new HashSet<>(actual.getToBlow()).equals(expectedRays),
                        "Every modern block ray matches the independent native engine under the same world and RNG seed");
                helper.assertTrue(fixture.destroyed().equals(expectedDestruction),
                        "Actual block removal matches native results, rather than only a radius field or a direct Level.explode stub");
                helper.assertTrue(creeper.isRemoved(), "The real native detonation retains its death/removal lifecycle");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realCreeperStillHonorsMobGriefingAndExplosionCancellation(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 5)) {
            fixture.restoreArena();
            fixture.rule(GameRules.RULE_MOBGRIEFING, false);
            fixture.detonate(fixture.add(fixture.creeper(false, false, 3)), false);
            helper.assertTrue(fixture.destroyed().isEmpty(), "mobGriefing=false still prevents all real creeper block destruction");
            fixture.rule(GameRules.RULE_MOBGRIEFING, true);
            fixture.detonate(fixture.add(fixture.creeper(true, true, 3)), true);
            helper.assertTrue(fixture.destroyed().isEmpty(), "A cancelled NeoForge explosion still preserves the entire arena");
            helper.assertTrue(fixture.drops.live(ItemEntity.class).isEmpty(), "Protected/cancelled explosions cannot create block drops");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final BlockPos anchor;
        final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        final Map<BlockPos, BlockState> arena = new LinkedHashMap<>();
        final SpawnedEntityCapture drops;
        final boolean oldGriefing;
        final boolean oldDrops;

        Fixture(GameTestHelper helper, int slot) {
            this.helper = helper;
            level = helper.getLevel();
            // Fixed isolated sites stay clear of the randomized GameTest grid and other live blast fixtures.
            anchor = new BlockPos((3500 + slot * 8) * 16 + 8, 240, 3500 * 16 + 8);
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) for (int y = -3; y <= -1; y++) {
                BlockPos pos = anchor.offset(x, y, z);
                BlockState state = (y == -3 && (x + z) % 3 == 0 ? Blocks.STONE : Blocks.DIRT).defaultBlockState();
                put(pos, state);
            }
            for (int x : new int[]{-3, 3}) for (int z = -2; z <= 2; z++) {
                put(anchor.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
            }
            oldGriefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
            oldDrops = level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
            rule(GameRules.RULE_MOBGRIEFING, true);
            rule(GameRules.RULE_DOBLOCKDROPS, true);
            AABB bounds = new AABB(anchor).inflate(40D);
            drops = new SpawnedEntityCapture(level, entity -> entity instanceof ItemEntity && entity.getBoundingBox().intersects(bounds));
        }

        void put(BlockPos pos, BlockState state) {
            previous.put(pos, level.getBlockState(pos));
            arena.put(pos, state);
        }

        void restoreArena() { arena.forEach((pos, state) -> level.setBlock(pos, state, 2)); }

        Set<BlockPos> destroyed() {
            Set<BlockPos> result = new HashSet<>();
            arena.forEach((pos, state) -> { if (level.getBlockState(pos).isAir()) result.add(pos); });
            return result;
        }

        Creeper creeper(boolean infernal, boolean charged, int radius) {
            Creeper creeper = infernal ? (Creeper)MiteMobs.type(MobKind.INFERNAL_CREEPER).create(level) : EntityType.CREEPER.create(level);
            CompoundTag tag = new CompoundTag();
            creeper.addAdditionalSaveData(tag);
            tag.putBoolean("powered", charged);
            tag.putByte("ExplosionRadius", (byte)radius);
            creeper.readAdditionalSaveData(tag);
            creeper.moveTo(anchor.getX() + .5D, anchor.getY(), anchor.getZ() + .5D, 0F, 0F);
            creeper.setNoAi(true);
            creeper.setNoGravity(true);
            return creeper;
        }

        Creeper add(Creeper creeper) {
            helper.assertTrue(level.addFreshEntity(creeper), "The tested creeper enters the actual entity lifecycle");
            return creeper;
        }

        Explosion detonate(Creeper creeper, boolean cancel) {
            AtomicReference<Explosion> observed = new AtomicReference<>();
            Consumer<ExplosionEvent.Start> listener = event -> {
                if (event.getLevel() == level && event.getExplosion().getDirectSourceEntity() == creeper) {
                    observed.set(event.getExplosion());
                    if (cancel) event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(ExplosionEvent.Start.class, listener);
            try {
                Method explode = Creeper.class.getDeclaredMethod("explodeCreeper");
                explode.setAccessible(true);
                explode.invoke(creeper);
                helper.assertTrue(observed.get() != null, "The real private creeper detonation enters the NeoForge explosion pipeline");
                return observed.get();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot invoke actual creeper detonation", failure);
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
                creeper.discard();
            }
        }

        void rule(GameRules.Key<GameRules.BooleanValue> key, boolean value) { level.getGameRules().getRule(key).set(value, level.getServer()); }
        void clearDrops() { drops.live(ItemEntity.class).forEach(Entity::discard); }

        @Override public void close() {
            clearDrops();
            drops.close();
            previous.forEach((pos, state) -> level.setBlock(pos, state, 2));
            rule(GameRules.RULE_MOBGRIEFING, oldGriefing);
            rule(GameRules.RULE_DOBLOCKDROPS, oldDrops);
        }
    }
}

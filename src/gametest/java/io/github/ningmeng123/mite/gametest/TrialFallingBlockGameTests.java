package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
import io.github.ningmeng123.mite.worldgen.NaturalTrialFallingBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real falling-block physics retain only natural trial sand's no-drop identity. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class TrialFallingBlockGameTests {
    private TrialFallingBlockGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void overworldNaturalSandStaysProtectedAfterNativeFallAndPlayerSandDropsNormally(GameTestHelper helper) {
        landing(helper, helper.getLevel().getServer().overworld(), 0);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void underworldNaturalSandStaysProtectedAfterNativeFallAndPlayerSandDropsNormally(GameTestHelper helper) {
        landing(helper, underworld(helper), 1);
        helper.succeed();
    }

    private static void landing(GameTestHelper helper, ServerLevel level, int slot) {
        try (Fixture fixture = new Fixture(helper, level, slot)) {
            for (boolean natural : new boolean[]{true, false}) {
                FallingBlockEntity falling = fixture.fall(Blocks.SAND.defaultBlockState(), natural);
                helper.assertValueEqual(((NaturalTrialFallingBlock)falling).mite$isNaturalTrialBlock(), natural,
                        "The real fall method transfers the original block's provenance before removing it");
                helper.assertValueEqual(falling.dropItem, !natural, "Only natural falling sand disables vanilla item conversion");
                helper.assertFalse(NaturalTrialBlocks.isNatural(level, fixture.source), "Source provenance is consumed when sand becomes an entity");
                fixture.finish(falling);
                helper.assertTrue(level.getBlockState(fixture.landed).is(Blocks.SAND), "Real entity movement and native landing place sand on the solid floor");
                helper.assertValueEqual(NaturalTrialBlocks.isNatural(level, fixture.landed), natural,
                        "The landed block inherits precisely the falling entity's source identity");
                helper.assertTrue(fixture.items().isEmpty(), "A successful native landing cannot duplicate sand as an item");
                helper.assertTrue(level.destroyBlock(fixture.landed, true), "The landed block remains removable through native destruction");
                helper.assertValueEqual(fixture.count(Items.SAND), natural ? 0 : 1,
                        "Natural sand remains unharvestable after moving; the same position's player sand keeps its ordinary drop");
                fixture.clearItems();
            }
        }
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void nativeFallingSandNbtRestoresNaturalFlagDropItemAndLandingProvenance(GameTestHelper helper) {
        for (ServerLevel level : levels(helper)) {
            try (Fixture fixture = new Fixture(helper, level, 2)) {
                FallingBlockEntity original = fixture.fall(Blocks.SAND.defaultBlockState(), true);
                original.tick();
                CompoundTag saved = original.saveWithoutId(new CompoundTag());
                helper.assertTrue(saved.getBoolean("mite:natural_trial_block"), "Entity NBT saves independent natural trial provenance");
                helper.assertFalse(saved.getBoolean("DropItem"), "Native entity NBT also saves the no-item-conversion flag");
                original.discard();
                FallingBlockEntity loaded = fixture.load(saved);
                helper.assertTrue(((NaturalTrialFallingBlock)loaded).mite$isNaturalTrialBlock() && !loaded.dropItem,
                        "Native Entity.load restores both midair source identity and failed-landing suppression");
                fixture.finish(loaded);
                helper.assertTrue(level.getBlockState(fixture.landed).is(Blocks.SAND)
                                && NaturalTrialBlocks.isNatural(level, fixture.landed),
                        "A saved and reloaded natural falling entity restores provenance on actual landing");
                helper.assertTrue(level.destroyBlock(fixture.landed, true), "Reloaded natural sand can be removed");
                helper.assertTrue(fixture.items().isEmpty(), "Saving in midair cannot enable an item drop after landing");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void blockedNaturalSandLandingNeverBecomesAnItemAndPlayerSandStillDoes(GameTestHelper helper) {
        for (ServerLevel level : levels(helper)) {
            try (Fixture fixture = new Fixture(helper, level, 3)) {
                for (boolean natural : new boolean[]{true, false}) {
                    level.setBlock(fixture.landed, Blocks.TORCH.defaultBlockState(), 2);
                    FallingBlockEntity falling = fixture.fall(Blocks.SAND.defaultBlockState(), natural);
                    fixture.finish(falling);
                    helper.assertTrue(level.getBlockState(fixture.landed).is(Blocks.TORCH), "The native torch obstruction prevents sand from placing a block");
                    helper.assertFalse(NaturalTrialBlocks.isNatural(level, fixture.landed), "A failed landing cannot mark the obstruction as a natural trial block");
                    helper.assertValueEqual(fixture.count(Items.SAND), natural ? 0 : 1,
                            "Native failed-landing conversion is suppressed only for natural sand");
                    fixture.clearItems();
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void naturalFallingSandTimeoutCannotDropItemsWhilePlayerTimeoutRemainsNative(GameTestHelper helper) {
        for (ServerLevel level : levels(helper)) {
            try (Fixture fixture = new Fixture(helper, level, 4)) {
                for (boolean natural : new boolean[]{true, false}) {
                    FallingBlockEntity falling = fixture.fall(Blocks.SAND.defaultBlockState(), natural);
                    falling.setNoGravity(true);
                    falling.setDeltaMovement(Vec3.ZERO);
                    falling.time = 600;
                    falling.tick();
                    helper.assertTrue(falling.isRemoved(), "The real native time > 600 branch expires the falling entity");
                    helper.assertValueEqual(fixture.count(Items.SAND), natural ? 0 : 1,
                            "Airborne timeout respects provenance without disabling ordinary falling-block drops");
                    helper.assertTrue(level.getBlockState(fixture.landed).isAir(), "A timed-out entity cannot create a phantom landed block");
                    fixture.clearItems();
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void ordinaryNoDropFallingEntityDoesNotBecomeNaturalThroughNativeNbt(GameTestHelper helper) {
        for (ServerLevel level : levels(helper)) {
            try (Fixture fixture = new Fixture(helper, level, 5)) {
                FallingBlockEntity original = fixture.fall(Blocks.SAND.defaultBlockState(), false);
                original.dropItem = false;
                CompoundTag saved = original.saveWithoutId(new CompoundTag());
                helper.assertFalse(saved.getBoolean("DropItem") || saved.getBoolean("mite:natural_trial_block"),
                        "Native DropItem false alone is not evidence that an ordinary entity came from a trial chamber");
                original.discard();
                FallingBlockEntity loaded = fixture.load(saved);
                helper.assertFalse(((NaturalTrialFallingBlock)loaded).mite$isNaturalTrialBlock(), "Reload does not promote an arbitrary no-drop falling entity to natural provenance");
                fixture.finish(loaded);
                helper.assertTrue(level.getBlockState(fixture.landed).is(Blocks.SAND)
                                && !NaturalTrialBlocks.isNatural(level, fixture.landed),
                        "A custom ordinary DropItem setting cannot mark its landed block as natural");
                helper.assertTrue(level.destroyBlock(fixture.landed, true), "The ordinary landed sand can be mined");
                helper.assertValueEqual(fixture.count(Items.SAND), 1, "Only the entity's failed-landing behavior was customized; its ordinary landed block keeps normal drops");
            }
        }
        helper.succeed();
    }

    private static List<ServerLevel> levels(GameTestHelper helper) {
        return List.of(helper.getLevel().getServer().overworld(), underworld(helper));
    }

    private static ServerLevel underworld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(level != null, "The real underworld dimension must load");
        return level;
    }

    private record Previous(BlockState state, CompoundTag blockEntity) {}

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final BlockPos floor;
        final BlockPos source;
        final BlockPos landed;
        final Map<BlockPos, Previous> previous = new LinkedHashMap<>();
        final List<FallingBlockEntity> loadedEntities = new ArrayList<>();
        final SpawnedEntityCapture spawned;
        final boolean oldBlockDrops;
        final boolean oldEntityDrops;

        Fixture(GameTestHelper helper, ServerLevel level, int slot) {
            this.helper = helper;
            this.level = level;
            floor = new BlockPos((1400 + slot * 4) * 16 + 8, 200, 1400 * 16 + 8);
            source = floor.above(6);
            landed = floor.above();
            for (BlockPos mutable : BlockPos.betweenClosed(floor.offset(-2, 0, -2), floor.offset(2, 8, 2))) {
                BlockPos pos = mutable.immutable();
                BlockEntity entity = level.getBlockEntity(pos);
                previous.put(pos, new Previous(level.getBlockState(pos), entity == null ? null : entity.saveWithFullMetadata(level.registryAccess())));
                level.setBlock(pos, pos.getY() == floor.getY() ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            oldBlockDrops = level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
            oldEntityDrops = level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS);
            rule(GameRules.RULE_DOBLOCKDROPS, true);
            rule(GameRules.RULE_DOENTITYDROPS, true);
            AABB area = new AABB(Vec3.atLowerCornerOf(floor.offset(-2, 0, -2)), Vec3.atLowerCornerOf(floor.offset(3, 9, 3)));
            spawned = new SpawnedEntityCapture(level, entity -> (entity instanceof ItemEntity || entity instanceof FallingBlockEntity)
                    && entity.getBoundingBox().intersects(area));
        }

        FallingBlockEntity fall(BlockState state, boolean natural) {
            level.setBlock(source, state, 2);
            if (natural) NaturalTrialBlocks.record(level.getChunkAt(source), source);
            FallingBlockEntity entity = FallingBlockEntity.fall(level, source, state);
            helper.assertTrue(level.getBlockState(source).isAir(), "The native falling-block factory actually removes the original source");
            return entity;
        }

        FallingBlockEntity load(CompoundTag tag) {
            FallingBlockEntity entity = new FallingBlockEntity(EntityType.FALLING_BLOCK, level);
            entity.load(tag);
            loadedEntities.add(entity);
            return entity;
        }

        void finish(FallingBlockEntity entity) {
            int ticks = 0;
            // Native physics is deliberately stepped synchronously, as in ShardAndGemGameTests.
            // Assertions use synchronous entity-spawn capture rather than delayed world indexes.
            while (!entity.isRemoved() && ticks++ < 80) entity.tick();
            helper.assertTrue(entity.isRemoved(), "The real falling-block landing path must complete within eighty native ticks");
        }

        List<ItemEntity> items() { return spawned.live(ItemEntity.class); }
        int count(Item item) { return items().stream().filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum(); }
        void clearItems() { items().forEach(Entity::discard); }
        void rule(GameRules.Key<GameRules.BooleanValue> key, boolean value) { level.getGameRules().getRule(key).set(value, level.getServer()); }

        @Override public void close() {
            spawned.close();
            loadedEntities.forEach(Entity::discard);
            for (var entry : previous.entrySet()) level.setBlock(entry.getKey(), entry.getValue().state, 2);
            for (var entry : previous.entrySet()) {
                CompoundTag tag = entry.getValue().blockEntity;
                if (tag != null) {
                    BlockEntity entity = BlockEntity.loadStatic(entry.getKey(), entry.getValue().state, tag, level.registryAccess());
                    if (entity != null) level.setBlockEntity(entity);
                }
            }
            rule(GameRules.RULE_DOBLOCKDROPS, oldBlockDrops);
            rule(GameRules.RULE_DOENTITYDROPS, oldEntityDrops);
        }
    }
}

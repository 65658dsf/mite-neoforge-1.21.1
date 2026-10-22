package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.world.CollapsePhysics;
import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
import io.github.ningmeng123.mite.worldgen.NaturalTrialFallingBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual falling ticks verify source collision behavior without replacing sand physics. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CollapseFallingEntityGameTests {
    private CollapseFallingEntityGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void sourceDirtAndSoulSandFallThroughWaterBelowZeroWithoutChangingState(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            for (BlockState state : List.of(Blocks.DIRT.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState())) {
                f.set(f.landed.above(2), Blocks.WATER.defaultBlockState());
                FallingBlockEntity falling = f.fall(state, false);
                h.assertTrue(falling.getY() < 0, "The actual falling factory starts below modern sea-level zero");
                f.finish(falling);
                h.assertValueEqual(f.level.getBlockState(f.landed), state, "The complete native landing keeps the original dirt or soul-sand state");
                h.assertTrue(f.items().isEmpty(), "A successful water traversal and landing cannot duplicate its block as an item");
                f.set(f.landed, Blocks.AIR.defaultBlockState());
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void collapseCrushesTorchAgainstSolidFloorWithoutDroppingTorchOrDirt(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.set(f.landed, Blocks.TORCH.defaultBlockState());
            f.finish(f.fall(Blocks.DIRT.defaultBlockState(), false));
            h.assertTrue(f.level.getBlockState(f.landed).is(Blocks.DIRT), "The real native tick places dirt after crushing the torch cell");
            h.assertTrue(f.items().isEmpty(), "Source crushed destruction runs no ordinary torch loot and creates no dirt item");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void collapseDislodgesWallButtonAboveOpenSpaceWithItsOrdinaryDrop(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            BlockPos button = f.landed.above(3);
            f.set(button.west(), Blocks.STONE.defaultBlockState());
            f.set(button, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL)
                    .setValue(ButtonBlock.FACING, net.minecraft.core.Direction.EAST));
            h.assertTrue(f.level.getBlockState(button).canSurvive(f.level, button), "A wall button is valid before the actual collision");
            f.finish(f.fall(Blocks.DIRT.defaultBlockState(), false));
            h.assertTrue(f.level.getBlockState(button).isAir(), "The falling entity physically crosses and clears the button");
            h.assertTrue(f.level.getBlockState(f.landed).is(Blocks.DIRT), "The falling entity continues through open space to the floor");
            h.assertValueEqual(f.count(Items.STONE_BUTTON), 1, "A dislodged object above air uses its ordinary drop exactly once");
            h.assertValueEqual(f.count(Items.DIRT), 0, "Crossing an object cannot turn successful dirt landing into a duplicated item");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void sourceCollapseBreaksGlassSupportAndContinuesItsActualNativeFall(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            for (BlockState glass : List.of(Blocks.GLASS.defaultBlockState(), Blocks.RED_STAINED_GLASS.defaultBlockState())) {
                BlockPos shelf = f.landed.above(3);
                f.set(shelf, glass);
                f.finish(f.fall(Blocks.DIRT.defaultBlockState(), false));
                h.assertTrue(f.level.getBlockState(shelf).isAir(), "A full glass shelf is destroyed by the native landing collision");
                h.assertTrue(f.level.getBlockState(f.landed).is(Blocks.DIRT), "The actual entity continues below the former glass shelf");
                h.assertValueEqual(f.count(Items.DIRT), 0, "Breaking glass does not drop an extra dirt item");
                f.set(f.landed, Blocks.AIR.defaultBlockState()); f.clearItems();
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void ordinarySandStillUsesItsNativeTorchObstructionInsteadOfSourceDirtCrushing(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.set(f.landed, Blocks.TORCH.defaultBlockState());
            f.finish(f.fall(Blocks.SAND.defaultBlockState(), false));
            h.assertTrue(f.level.getBlockState(f.landed).is(Blocks.TORCH), "The collapse mixin does not change ordinary sand's established torch interaction");
            h.assertValueEqual(f.count(Items.SAND), 1, "The ordinary native obstruction branch still converts sand into its item");
            h.assertValueEqual(f.count(Items.TORCH), 0, "An unmanaged falling entity cannot invoke source crushing");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void dislodgedBlockDropsKeepTheNativeNeoForgeCancellationTransaction(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            BlockPos button = f.landed.above(3);
            f.set(button.west(), Blocks.STONE.defaultBlockState());
            f.set(button, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL)
                    .setValue(ButtonBlock.FACING, net.minecraft.core.Direction.EAST));
            int[] observed = {0};
            Consumer<BlockDropsEvent> cancel = event -> {
                if (event.getLevel() == f.level && event.getPos().equals(button)) {
                    observed[0]++; event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BlockDropsEvent.class, cancel);
            try {
                f.finish(f.fall(Blocks.SOUL_SAND.defaultBlockState(), false));
            } finally { NeoForge.EVENT_BUS.unregister(cancel); }
            h.assertValueEqual(observed[0], 1, "Dislodging uses exactly one real cancellable BlockDropsEvent");
            h.assertTrue(f.level.getBlockState(button).isAir() && f.level.getBlockState(f.landed).is(Blocks.SOUL_SAND),
                    "Canceling native loot preserves the physical removal and landing transaction");
            h.assertTrue(f.items().isEmpty(), "Canceled button loot cannot leak through a separate item-spawn path");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void savedNaturalDirtKeepsTrialNoDropsAndNativeImpactDamage(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            FallingBlockEntity original = f.fall(Blocks.DIRT.defaultBlockState(), true);
            h.assertTrue(((NaturalTrialFallingBlock)original).mite$isNaturalTrialBlock() && !original.dropItem,
                    "Using the original native factory transfers natural trial provenance to dirt too");
            CompoundTag tag = original.saveWithoutId(new CompoundTag()); original.discard();
            h.assertTrue(tag.getBoolean("HurtEntities"), "Requested soil impact damage persists through the native entity save");
            h.assertTrue(tag.getFloat("FallHurtAmount") == 1F && tag.getInt("FallHurtMax") == 20,
                    "Soil uses one damage point per falling block with a twenty-point cap");
            FallingBlockEntity loaded = new FallingBlockEntity(EntityType.FALLING_BLOCK, f.level);
            loaded.load(tag); f.entities.add(loaded);
            h.assertTrue(((NaturalTrialFallingBlock)loaded).mite$isNaturalTrialBlock() && !loaded.dropItem,
                    "The existing native serialization restores provenance and no-item conversion");
            f.finish(loaded);
            h.assertTrue(f.level.getBlockState(f.landed).is(Blocks.DIRT) && NaturalTrialBlocks.isNatural(f.level, f.landed),
                    "The actual below-zero landing preserves natural provenance across a midair save");
            f.level.destroyBlock(f.landed, true);
            h.assertTrue(f.items().isEmpty(), "Natural trial dirt stays unharvestable after collapse and reload");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void actualFallingSoilHurtsCreaturesMoreFromGreaterHeightAndDoesNotDuplicateItsBlock(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            Cow target = f.target();
            float shortDamage;
            try {
                FallingBlockEntity shortFall = f.fall(Blocks.DIRT.defaultBlockState(), false);
                shortFall.setPos(shortFall.getX(), f.landed.getY() + 3, shortFall.getZ());
                float initial = target.getHealth();
                f.finish(shortFall);
                shortDamage = initial - target.getHealth();
                h.assertTrue(shortDamage > 0, "Native movement and landing physically damage an entity below falling dirt");
                f.set(f.landed, Blocks.AIR.defaultBlockState());
                target.setHealth(initial); target.invulnerableTime = 0;
                FallingBlockEntity highFall = f.fall(Blocks.ROOTED_DIRT.defaultBlockState(), false);
                f.finish(highFall);
                h.assertTrue(initial - target.getHealth() > shortDamage, "A longer real fall does more damage than the three-block drop");
                h.assertTrue(f.level.getBlockState(f.landed).is(Blocks.ROOTED_DIRT), "Damaging rooted dirt still settles as its original soil block");
                h.assertTrue(f.items().isEmpty(), "Impact damage cannot create an extra dropped soil item");
            } finally { target.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void fallingSoilAndSandBothRespectNativeDamageCancellation(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            Cow target = f.target();
            int[] hits = {0};
            Consumer<LivingIncomingDamageEvent> cancel = event -> {
                if (event.getEntity() == target && event.getSource().getDirectEntity() instanceof FallingBlockEntity) {
                    hits[0]++; event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, cancel);
            float initial = target.getHealth();
            try {
                f.finish(f.fall(Blocks.SOUL_SAND.defaultBlockState(), false));
                h.assertTrue(hits[0] > 0, "Native falling damage reaches the cancellable living damage transaction");
                h.assertValueEqual(target.getHealth(), initial, "Canceling real damage protects the target while soil still lands");
                f.set(f.landed, Blocks.AIR.defaultBlockState());
                FallingBlockEntity sand = f.fall(Blocks.SAND.defaultBlockState(), false);
                h.assertTrue(sand.saveWithoutId(new CompoundTag()).getBoolean("HurtEntities"), "Ordinary falling sand also carries the requested impact damage");
                f.finish(sand);
                h.assertValueEqual(hits[0], 2, "Both falling materials reach the same real cancellable damage transaction exactly once");
                h.assertValueEqual(target.getHealth(), initial, "Cancellation protects the target from sand while its block still lands");
            } finally { NeoForge.EVENT_BUS.unregister(cancel); target.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void everyManagedSoilAndRepresentativeNativeGravityBlockHurtsOnRealLanding(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            Cow target = f.target();
            try {
                for (BlockState state : impactStates()) {
                    f.set(f.landed, Blocks.AIR.defaultBlockState()); f.clearItems();
                    target.setHealth(100F); target.invulnerableTime = 0;
                    FallingBlockEntity falling = f.fall(state, false);
                    CompoundTag saved = falling.saveWithoutId(new CompoundTag());
                    h.assertTrue(saved.getBoolean("HurtEntities") && saved.getFloat("FallHurtAmount") == 1F
                                    && saved.getInt("FallHurtMax") == 20,
                            "The actual falling factory enables the shared impact profile for " + state);
                    int[] hits = {0};
                    Consumer<LivingIncomingDamageEvent> capture = event -> {
                        if (event.getEntity() == target && event.getSource().getDirectEntity() == falling) {
                            h.assertTrue(event.getSource().is(DamageTypes.FALLING_BLOCK), "Native impact retains its falling-block damage source");
                            hits[0]++;
                        }
                    };
                    NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, capture);
                    try { f.finish(falling); }
                    finally { NeoForge.EVENT_BUS.unregister(capture); }
                    h.assertTrue(target.getHealth() < 100F && hits[0] == 1,
                            "Real movement and landing damage the target once for " + state);
                    h.assertTrue(f.level.getBlockState(f.landed).is(state.getBlock()),
                            "Impact still lands the original carried block while retaining native neighbor-shape updates");
                    h.assertTrue(f.items().isEmpty(), "Impact damage cannot duplicate a successfully landed block as an item");
                }
            } finally { target.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void oldMidairNativeGravitySavesAcquireImpactAndRetainTheNativeHeightFormula(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            Cow target = f.target();
            try {
                for (BlockState state : List.of(Blocks.SAND.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
                        Blocks.WHITE_CONCRETE_POWDER.defaultBlockState())) {
                    f.set(f.landed, Blocks.AIR.defaultBlockState()); f.clearItems();
                    FallingBlockEntity original = f.fall(state, false);
                    CompoundTag saved = original.saveWithoutId(new CompoundTag()); original.discard();
                    saved.putBoolean("HurtEntities", false); saved.putFloat("FallHurtAmount", 0F); saved.putInt("FallHurtMax", 0);
                    FallingBlockEntity loaded = new FallingBlockEntity(EntityType.FALLING_BLOCK, f.level);
                    loaded.load(saved); f.entities.add(loaded);
                    CompoundTag upgraded = loaded.saveWithoutId(new CompoundTag());
                    h.assertTrue(upgraded.getBoolean("HurtEntities") && upgraded.getFloat("FallHurtAmount") == 1F
                                    && upgraded.getInt("FallHurtMax") == 20,
                            "Loading an old non-damaging gravity block upgrades its saved impact profile: " + state);
                    target.setHealth(100F); target.invulnerableTime = 0;
                    loaded.setPos(target.position());
                    int[] hits = {0}; float[] firstAmount = {-1F};
                    Consumer<LivingIncomingDamageEvent> capture = event -> {
                        if (event.getEntity() == target && event.getSource().getDirectEntity() == loaded) {
                            if (hits[0]++ == 0) firstAmount[0] = event.getOriginalAmount();
                            h.assertTrue(event.getSource().is(DamageTypes.FALLING_BLOCK), "A restored entity retains the native falling-block damage type");
                        }
                    };
                    NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, capture);
                    try {
                        loaded.causeFallDamage(3.5F, 1F, loaded.damageSources().fall());
                        h.assertTrue(hits[0] == 1 && firstAmount[0] == 3F && target.getHealth() == 97F,
                                "Actual native fall damage uses ceil(3.5 - 1) times one, including its fractional-height rounding");
                        target.setHealth(100F); target.invulnerableTime = 0;
                        loaded.setPos(f.source.getX() + .5D, f.source.getY(), f.source.getZ() + .5D);
                        loaded.setDeltaMovement(Vec3.ZERO);
                        f.finish(loaded);
                    } finally { NeoForge.EVENT_BUS.unregister(capture); }
                    h.assertTrue(hits[0] == 2 && target.getHealth() < 100F,
                            "The reloaded block also damages through a real subsequent movement and landing");
                    h.assertValueEqual(f.level.getBlockState(f.landed), state, "Reloading preserves the actual carried gravity block");
                    h.assertTrue(f.items().isEmpty(), "A saved damaging fall still has no duplicate item output");
                }
            } finally { target.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void nativeAnvilAndDripstoneTicksKeepTheirStrongerOwnImpactProfilesAfterReload(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            BlockState tip = Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN)
                    .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP);
            for (BlockState state : List.of(Blocks.ANVIL.defaultBlockState(), tip)) {
                f.set(f.source, state);
                state.tick(f.level, f.source, RandomSource.create(196L));
                var falls = f.spawned.live(FallingBlockEntity.class).stream().filter(e -> e.getStartPos().equals(f.source)).toList();
                h.assertValueEqual(falls.size(), 1, "The actual unsupported block tick creates one native falling entity: " + state);
                FallingBlockEntity original = falls.getFirst();
                CompoundTag saved = original.saveWithoutId(new CompoundTag()); original.discard();
                float amount = state.is(Blocks.ANVIL) ? 2F : 6F;
                h.assertTrue(saved.getBoolean("HurtEntities") && saved.getFloat("FallHurtAmount") == amount && saved.getInt("FallHurtMax") == 40,
                        "The native anvil/stalactite spawn callback supplies its own stronger impact profile");
                FallingBlockEntity loaded = new FallingBlockEntity(EntityType.FALLING_BLOCK, f.level);
                loaded.load(saved); f.entities.add(loaded);
                CompoundTag restored = loaded.saveWithoutId(new CompoundTag());
                h.assertTrue(restored.getFloat("FallHurtAmount") == amount && restored.getInt("FallHurtMax") == 40,
                        "The shared impact loader cannot weaken an existing native damage configuration");
                Cow target = f.target();
                int[] hits = {0};
                Consumer<LivingIncomingDamageEvent> capture = event -> {
                    if (event.getEntity() == target && event.getSource().getDirectEntity() == loaded) {
                        hits[0]++;
                        h.assertTrue(event.getSource().is(state.is(Blocks.ANVIL) ? DamageTypes.FALLING_ANVIL : DamageTypes.FALLING_STALACTITE)
                                        && event.getOriginalAmount() == 3F * amount,
                                "The real native impact keeps its own damage source and fractional-height formula");
                    }
                };
                NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, capture);
                try {
                    loaded.setPos(target.position()); loaded.causeFallDamage(3.5F, 1F, loaded.damageSources().fall());
                    h.assertTrue(hits[0] == 1 && target.getHealth() == 100F - 3F * amount,
                            "The restored original anvil or dripstone damages through its native living transaction");
                } finally { NeoForge.EVENT_BUS.unregister(capture); target.discard(); loaded.discard(); }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void directlyConstructedFallingEntityGetsDefaultImpactAndPreservesACustomSavedProfile(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            FallingBlockEntity direct = new FallingBlockEntity(EntityType.FALLING_BLOCK, f.level); f.entities.add(direct);
            Cow target = f.target();
            try {
                direct.setPos(target.position()); direct.causeFallDamage(3.5F, 1F, direct.damageSources().fall());
                h.assertValueEqual(target.getHealth(), 97F, "A directly constructed command-style entity acquires impact at the real damage entrypoint");
                direct.setHurtsEntities(3F, 60);
                CompoundTag saved = direct.saveWithoutId(new CompoundTag()); direct.discard();
                FallingBlockEntity loaded = new FallingBlockEntity(EntityType.FALLING_BLOCK, f.level); loaded.load(saved); f.entities.add(loaded);
                CompoundTag restored = loaded.saveWithoutId(new CompoundTag());
                h.assertTrue(restored.getBoolean("HurtEntities") && restored.getFloat("FallHurtAmount") == 3F && restored.getInt("FallHurtMax") == 60,
                        "An effective custom three-per-block sixty-cap profile survives actual native save/load unchanged");
                target.setHealth(100F); target.invulnerableTime = 0;
                int[] hits = {0};
                Consumer<LivingIncomingDamageEvent> capture = event -> {
                    if (event.getEntity() == target && event.getSource().getDirectEntity() == loaded) {
                        hits[0]++;
                        h.assertTrue(event.getSource().is(DamageTypes.FALLING_BLOCK) && event.getOriginalAmount() == 9F,
                                "The damage entrypoint preserves the configured three-per-block profile instead of resetting it");
                    }
                };
                NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, capture);
                try {
                    loaded.setPos(target.position()); loaded.causeFallDamage(3.5F, 1F, loaded.damageSources().fall());
                    h.assertTrue(hits[0] == 1 && target.getHealth() == 91F, "The restored custom profile reaches the actual native living damage event");
                } finally { NeoForge.EVENT_BUS.unregister(capture); }
            } finally { target.discard(); }
        }
        h.succeed();
    }

    private static List<BlockState> impactStates() {
        return List.of(Blocks.DIRT.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.COARSE_DIRT.defaultBlockState(), Blocks.PODZOL.defaultBlockState(), Blocks.ROOTED_DIRT.defaultBlockState(),
                Blocks.MYCELIUM.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState(), Blocks.SAND.defaultBlockState(),
                Blocks.RED_SAND.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), Blocks.WHITE_CONCRETE_POWDER.defaultBlockState(),
                Blocks.SUSPICIOUS_SAND.defaultBlockState(), Blocks.SUSPICIOUS_GRAVEL.defaultBlockState(),
                Blocks.SCAFFOLDING.defaultBlockState(), Blocks.DRAGON_EGG.defaultBlockState());
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void savedMidairGrassKeepsNativeLandingWithoutDuplicationOrRestartingCollapse(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            for (boolean natural : new boolean[]{false, true}) {
                FallingBlockEntity original = fixture.fall(Blocks.GRASS_BLOCK.defaultBlockState(), natural);
                CompoundTag saved = original.saveWithoutId(new CompoundTag());
                original.discard();
                FallingBlockEntity loaded = new FallingBlockEntity(EntityType.FALLING_BLOCK, fixture.level);
                loaded.load(saved);
                fixture.entities.add(loaded);
                helper.assertFalse(CollapsePhysics.managed(loaded.getBlockState()), "A legacy midair grass block is no longer managed collapse soil");
                fixture.finish(loaded);
                helper.assertTrue(fixture.level.getBlockState(fixture.source).isAir()
                                && fixture.level.getBlockState(fixture.landed).is(Blocks.GRASS_BLOCK),
                        "The pre-existing native entity completes one ordinary landing without recreating its old source");
                helper.assertValueEqual(NaturalTrialBlocks.isNatural(fixture.level, fixture.landed), natural,
                        "Legacy grass landing preserves its saved natural-trial provenance");
                helper.assertTrue(fixture.items().isEmpty(), "Loading and settling legacy falling grass never produces a duplicate item");
                fixture.set(fixture.floor, Blocks.AIR.defaultBlockState());
                helper.assertFalse(CollapsePhysics.tryFall(fixture.level, fixture.landed), "Even after its new support is removed, settled legacy grass stays exempt");
                helper.assertTrue(fixture.level.getBlockState(fixture.landed).is(Blocks.GRASS_BLOCK), "Compatibility never restarts the erroneous grass collapse lifecycle");
                fixture.set(fixture.floor, Blocks.BEDROCK.defaultBlockState());
                fixture.set(fixture.landed, Blocks.AIR.defaultBlockState());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void oldMidairSoilSaveIsUpgradedAndImpactDamageIsCapped(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            FallingBlockEntity original = f.fall(Blocks.DIRT.defaultBlockState(), true);
            CompoundTag tag = original.saveWithoutId(new CompoundTag()); original.discard();
            tag.putBoolean("HurtEntities", false); tag.putFloat("FallHurtAmount", 0F);
            FallingBlockEntity loaded = new FallingBlockEntity(EntityType.FALLING_BLOCK, f.level);
            loaded.load(tag); f.entities.add(loaded);
            CompoundTag upgraded = loaded.saveWithoutId(new CompoundTag());
            h.assertTrue(upgraded.getBoolean("HurtEntities") && upgraded.getFloat("FallHurtAmount") == 1F,
                    "A pre-fix midair soil save acquires the new impact rule on load");
            h.assertTrue(((NaturalTrialFallingBlock)loaded).mite$isNaturalTrialBlock() && !loaded.dropItem,
                    "Upgrading damage cannot reset trial provenance or native drop suppression");
            Cow target = f.target();
            try {
                loaded.setPos(target.position());
                float initial = target.getHealth();
                loaded.causeFallDamage(1F, 1F, loaded.damageSources().fall());
                h.assertValueEqual(target.getHealth(), initial, "The first block of soil movement remains a harmless settling allowance");
                target.invulnerableTime = 0;
                loaded.causeFallDamage(1000F, 1F, loaded.damageSources().fall());
                h.assertValueEqual(initial - target.getHealth(), 20F, "Native soil impact is capped at twenty damage even for a very long saved fall");
            } finally { target.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void managedEntityTimeoutUsesNativeSingleItemConversionAndTrialSuppression(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            for (boolean natural : new boolean[]{false, true}) {
                FallingBlockEntity falling = f.fall(Blocks.SOUL_SAND.defaultBlockState(), natural);
                falling.setNoGravity(true); falling.setDeltaMovement(Vec3.ZERO); falling.time = 600;
                falling.tick();
                h.assertTrue(falling.isRemoved(), "The native time greater than 600 branch remains intact");
                h.assertValueEqual(f.count(Items.SOUL_SAND), natural ? 0 : 1, "Native timeout drops exactly once and respects natural trial no-drop identity");
                h.assertTrue(f.level.getBlockState(f.landed).isAir(), "Expiration never also creates a landed block");
                f.clearItems();
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void canceledNativeFallingFactoryRestoresSourceAndProvenanceWithoutStartingItsUpperChain(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            BlockPos upper = f.source.above();
            f.set(upper, Blocks.DIRT.defaultBlockState());
            for (BlockState state : List.of(Blocks.DIRT.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState(),
                    Blocks.ROOTED_DIRT.defaultBlockState())) {
                for (boolean natural : new boolean[]{false, true}) {
                    f.set(f.source, state);
                    if (natural) NaturalTrialBlocks.record(f.level.getChunkAt(f.source), f.source);
                    List<FallingBlockEntity> rejected = new ArrayList<>();
                    Consumer<EntityJoinLevelEvent> cancel = event -> {
                        if (event.getLevel() == f.level && event.getEntity() instanceof FallingBlockEntity falling
                                && falling.getStartPos().equals(f.source)) {
                            rejected.add(falling); event.setCanceled(true);
                        }
                    };
                    NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, EntityJoinLevelEvent.class, cancel);
                    boolean started;
                    try {
                        started = CollapsePhysics.tryFall(f.level, f.source);
                    } finally { NeoForge.EVENT_BUS.unregister(cancel); }
                    f.entities.addAll(rejected);
                    h.assertFalse(started, "Canceling the actual native entity join must report that no collapse started");
                    h.assertValueEqual(rejected.size(), 1, "The test cancels the one real factory transaction at precisely this source position");
                    h.assertFalse(rejected.getFirst().isAddedToLevel(), "A rejected falling entity cannot remain tracked in the server level");
                    h.assertValueEqual(f.level.getBlockState(f.source), state, "Factory cancellation restores exactly the removed dirt or soul-sand state");
                    h.assertValueEqual(NaturalTrialBlocks.isNatural(f.level, f.source), natural,
                            "Restoring a rejected factory neither loses natural provenance nor promotes ordinary dirt");
                    h.assertTrue(f.items().isEmpty() && f.spawned.live(FallingBlockEntity.class).isEmpty(),
                            "Cancellation creates neither a block item nor an accepted falling entity");
                    var pending = f.level.getChunkAt(upper).getExistingDataOrNull(CollapsePhysics.PENDING.get());
                    h.assertTrue(pending == null || pending.get(upper) == null,
                            "An unsuccessful start must not mark the upper dirt for a delayed chain collapse");
                    h.assertFalse(f.level.getBlockTicks().hasScheduledTick(upper, Blocks.DIRT),
                            "An unsuccessful start must not schedule a native dirt tick above its restored source");
                    f.set(f.source, Blocks.AIR.defaultBlockState());
                }
            }
        }
        h.succeed();
    }

    private record Saved(BlockState state, CompoundTag data) {}
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final BlockPos floor, landed, source;
        final Map<BlockPos, Saved> previous = new LinkedHashMap<>();
        final List<FallingBlockEntity> entities = new ArrayList<>();
        final SpawnedEntityCapture spawned;
        final boolean blockDrops, entityDrops;
        Fixture(GameTestHelper h) {
            helper = h; level = h.getLevel();
            BlockPos reference = h.absolutePos(new BlockPos(2, 2, 2));
            floor = new BlockPos((reference.getX() & ~15) + 8, -40, (reference.getZ() & ~15) + 8);
            landed = floor.above(); source = floor.above(8);
            blockDrops = level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
            entityDrops = level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS);
            rule(GameRules.RULE_DOBLOCKDROPS, true); rule(GameRules.RULE_DOENTITYDROPS, true);
            for (BlockPos mutable : BlockPos.betweenClosed(floor.offset(-2, 0, -2), floor.offset(2, 10, 2))) {
                BlockPos pos = mutable.immutable();
                BlockEntity blockEntity = level.getBlockEntity(pos);
                previous.put(pos, new Saved(level.getBlockState(pos), blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess())));
                set(pos, pos.getY() == floor.getY() ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            AABB area = new AABB(Vec3.atLowerCornerOf(floor.offset(-2, 0, -2)), Vec3.atLowerCornerOf(floor.offset(3, 11, 3)));
            spawned = new SpawnedEntityCapture(level, entity -> (entity instanceof ItemEntity || entity instanceof FallingBlockEntity)
                    && entity.getBoundingBox().intersects(area));
        }
        void set(BlockPos pos, BlockState state) { level.setBlock(pos, state, 2); }
        FallingBlockEntity fall(BlockState state, boolean natural) {
            set(source, state);
            if (natural) NaturalTrialBlocks.record(level.getChunkAt(source), source);
            FallingBlockEntity falling = FallingBlockEntity.fall(level, source, state); entities.add(falling);
            helper.assertTrue(level.getBlockState(source).isAir(), "The real factory removes the source before its entity starts moving");
            return falling;
        }
        Cow target() {
            Cow cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "A real living target is available");
            cow.setNoAi(true); cow.setNoGravity(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D); cow.setHealth(100F);
            cow.moveTo(Vec3.atBottomCenterOf(landed));
            helper.assertTrue(level.addFreshEntity(cow), "The real target joins the falling-block level");
            return cow;
        }
        void finish(FallingBlockEntity falling) {
            for (int ticks = 0; ticks < 100 && !falling.isRemoved(); ticks++) falling.tick();
            helper.assertTrue(falling.isRemoved(), "The actual native entity must settle within one hundred synchronous ticks");
        }
        List<ItemEntity> items() { return spawned.live(ItemEntity.class); }
        int count(Item item) { return items().stream().filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum(); }
        void clearItems() { items().forEach(Entity::discard); }
        void rule(GameRules.Key<GameRules.BooleanValue> rule, boolean value) { level.getGameRules().getRule(rule).set(value, level.getServer()); }
        @Override public void close() {
            spawned.close(); entities.forEach(Entity::discard);
            previous.forEach((pos, saved) -> level.setBlock(pos, saved.state(), 2));
            previous.forEach((pos, saved) -> {
                if (saved.data() != null) {
                    BlockEntity entity = BlockEntity.loadStatic(pos, saved.state(), saved.data(), level.registryAccess());
                    if (entity != null) level.setBlockEntity(entity);
                }
            });
            rule(GameRules.RULE_DOBLOCKDROPS, blockDrops); rule(GameRules.RULE_DOENTITYDROPS, entityDrops);
        }
    }
}

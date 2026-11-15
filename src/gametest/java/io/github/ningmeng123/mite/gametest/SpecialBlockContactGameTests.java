package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.ai.ConspicuousCactus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.loading.DatagenModLoader;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Actual cactus damage callbacks and weighted-plate redstone transitions. @author NingMeng123 */
@GameTestHolder("mite_special_blocks")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class SpecialBlockContactGameTests {
    private static final ResourceLocation CUSTOM_PLATE = ResourceLocation.fromNamespaceAndPath(
            "mite_special_blocks", "custom_weighted_pressure_plate");
    private static final AtomicInteger PLATE_FIXTURE_IDS = new AtomicInteger();
    private static final TicketType<Integer> PLATE_TICKET = TicketType.create("mite_weighted_plate_test", Integer::compareTo);

    private SpecialBlockContactGameTests() {}

    @SubscribeEvent
    public static void registerCustomPressurePlate(RegisterEvent event) {
        if (DatagenModLoader.isRunningDataGen()) return;
        event.register(Registries.BLOCK, registry -> registry.register(CUSTOM_PLATE,
                new WeightedPressurePlateBlock(7, BlockSetType.GOLD, BlockBehaviour.Properties.of())));
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void carefulMobSideContactIsSafeThroughTheExactSpeedBoundary(GameTestHelper helper) {
        BlockPos cactus = cactus(helper);
        Mob pig = pig(helper, side(cactus));
        try {
            for (double speed : new double[]{0D, .1D, (double) .2F}) {
                pig.setDeltaMovement(speed, 0D, 0D);
                helper.assertValueEqual(contact(helper, cactus, pig), 0F,
                        "A careful mob beside the inset cactus shape avoids damage through speed 0.2F");
            }
        } finally { pig.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void recklessMobMotionUsesEveryAxisAndPlayersKeepContactDamage(GameTestHelper helper) {
        BlockPos cactus = cactus(helper);
        Mob pig = pig(helper, side(cactus));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        try {
            for (Vec3 motion : List.of(new Vec3(.21D, 0D, 0D), new Vec3(0D, .21D, 0D), new Vec3(0D, 0D, .21D))) {
                pig.setDeltaMovement(motion);
                helper.assertValueEqual(contact(helper, cactus, pig), 1F,
                        "Reckless three-axis motion keeps native cactus damage");
            }
            player.moveTo(side(cactus));
            player.setDeltaMovement(Vec3.ZERO);
            helper.assertValueEqual(contact(helper, cactus, player), 1F,
                    "The source careful-mob exception excludes stationary players");
        } finally { pig.discard(); player.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void standingOnAndActuallyPenetratingCactusStillHurtsStillMobs(GameTestHelper helper) {
        BlockPos cactus = cactus(helper);
        Mob pig = pig(helper, new Vec3(cactus.getX() + .5D, cactus.getY() + .9375D, cactus.getZ() + .5D));
        try {
            pig.setOnGround(true);
            helper.assertTrue(pig.getBlockStateOn().is(Blocks.CACTUS), "The native supporting block is cactus");
            helper.assertValueEqual(contact(helper, cactus, pig), 1F, "Standing on the cactus top keeps damage");
            pig.moveTo(cactus.getCenter());
            pig.setOnGround(false);
            helper.assertValueEqual(contact(helper, cactus, pig), 1F,
                    "An actual intersecting collision shape keeps damage without a speed requirement");
        } finally { pig.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void nearbyPushableEntityRemovesTheCarefulMobException(GameTestHelper helper) {
        BlockPos cactus = cactus(helper);
        Mob pig = pig(helper, side(cactus));
        Mob other = pig(helper, side(cactus).add(-.6D, 0D, 0D));
        helper.getLevel().addFreshEntity(pig);
        helper.getLevel().addFreshEntity(other);
        helper.runAfterDelay(2, () -> {
            try {
                pig.moveTo(side(cactus));
                other.moveTo(side(cactus).add(-.6D, 0D, 0D));
                pig.setDeltaMovement(Vec3.ZERO);
                helper.assertTrue(!helper.getLevel().getBlockCollisions(pig, pig.getBoundingBox()).iterator().hasNext(),
                        "The crowding fixture avoids solid-shape penetration");
                helper.assertValueEqual(contact(helper, cactus, pig), 1F,
                        "A nearby pushable entity supplies the source crowding condition");
            } finally { pig.discard(); other.discard(); }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void canceledCactusDamageKeepsTrapCountsAndAcceptedLethalDamageRecordsThem(GameTestHelper helper) {
        BlockPos cactus = cactus(helper);
        Mob pig = pig(helper, cactus.getCenter());
        Consumer<LivingIncomingDamageEvent> cancel = event -> {
            if (event.getEntity() == pig) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, cancel);
        try {
            pig.setHealth(.5F);
            helper.getLevel().getBlockState(cactus).entityInside(helper.getLevel(), cactus, pig);
            helper.assertValueEqual(pig.getHealth(), .5F, "The native cancellable damage event remains effective");
            helper.assertValueEqual(ConspicuousCactus.killCount(helper.getLevel(), cactus), 0,
                    "Canceled damage cannot establish a lethal cactus trap");
        } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        try {
            helper.getLevel().getBlockState(cactus).entityInside(helper.getLevel(), cactus, pig);
            helper.assertValueEqual(ConspicuousCactus.killCount(helper.getLevel(), cactus), 1,
                    "Accepted native lethal damage still updates the existing trap state");
        } finally { pig.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void weightedPlatesReadStackContentsAndTheirDistinctCapacities(GameTestHelper helper) {
        withPlate(helper, fixture -> {
            ItemEntity item = fixture.item(64, false);
            return () -> {
                helper.assertValueEqual(power(helper, fixture.pos, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, item), 15,
                        "One 64-item entity reaches gold capacity");
                helper.assertValueEqual(power(helper, fixture.pos, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, item), 2,
                        "The same 64 items produce ceil(64 / 640 * 15) on iron");
                item.getItem().setCount(4);
                helper.assertValueEqual(power(helper, fixture.pos, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, item), 1,
                        "Four gold items remain at the first signal step");
                item.getItem().setCount(5);
                helper.assertValueEqual(power(helper, fixture.pos, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, item), 2,
                        "The fifth gold item reaches the second signal step");
            };
        });
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void ironCapacityAccumulatesManyStacksAndClampsAtFullSignal(GameTestHelper helper) {
        withPlate(helper, fixture -> {
            List<ItemEntity> items = new ArrayList<>();
            for (int stack = 0; stack < 11; stack++) items.add(fixture.item(64, false));
            return () -> {
                helper.assertValueEqual(power(helper, fixture.pos, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, items.getFirst()), 15,
                        "More than 640 dropped items clamp the native output to fifteen");
                items.getLast().discard();
                helper.assertValueEqual(power(helper, fixture.pos, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, items.getFirst()), 15,
                        "Exactly ten 64-item stacks reach iron capacity");
                for (int stack = 1; stack < 10; stack++) items.get(stack).discard();
                helper.assertValueEqual(power(helper, fixture.pos, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, items.getFirst()), 2,
                        "Removing stacks recomputes their actual contents");
            };
        });
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void weightedPlatesIgnoreMobsPlayersAndEntitiesIgnoringTriggers(GameTestHelper helper) {
        withPlate(helper, fixture -> {
            Mob pig = fixture.track(pig(helper, Vec3.atBottomCenterOf(fixture.pos)));
            helper.assertTrue(helper.getLevel().addFreshEntity(pig), "The actual plate-test mob joins the prepared chunk");
            var player = fixture.track(helper.makeMockPlayer(GameType.SURVIVAL));
            player.moveTo(Vec3.atBottomCenterOf(fixture.pos));
            ItemEntity ignored = fixture.item(64, true);
            return () -> {
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(Mob.class, pig.getBoundingBox()).contains(pig),
                        "The native entity query can see the mob used for the item-only control");
                for (Block plate : List.of(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE)) {
                    helper.assertValueEqual(power(helper, fixture.pos, plate, pig), 0, "Mobs do not supply item weight");
                    helper.assertValueEqual(power(helper, fixture.pos, plate, player), 0, "Players do not supply item weight");
                    helper.assertValueEqual(power(helper, fixture.pos, plate, ignored), 0,
                            "The native ignoring-block-triggers contract stays effective for custom dropped items");
                }
            };
        });
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void customWeightedPlateKeepsItsNativeEntityCountAndCapacity(GameTestHelper helper) {
        withPlate(helper, fixture -> {
            ItemEntity item = fixture.item(64, false);
            Block custom = BuiltInRegistries.BLOCK.get(CUSTOM_PLATE);
            return () -> {
                helper.assertTrue(custom instanceof WeightedPressurePlateBlock,
                        "The custom pressure plate was registered during the normal block-registration event");
                helper.assertValueEqual(power(helper, fixture.pos, custom, item), 3,
                        "An extension's custom weighted plate keeps its entity-count semantics and capacity seven");
            };
        });
    }

    private static BlockPos cactus(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
        helper.getLevel().setBlock(pos.below(), Blocks.SAND.defaultBlockState(), 2);
        helper.getLevel().setBlock(pos, Blocks.CACTUS.defaultBlockState(), 2);
        return pos;
    }

    private static Vec3 side(BlockPos cactus) {
        return new Vec3(cactus.getX() - .4D, cactus.getY(), cactus.getZ() + .5D);
    }

    private static Mob pig(GameTestHelper helper, Vec3 position) {
        Mob pig = EntityType.PIG.create(helper.getLevel());
        pig.moveTo(position);
        pig.setNoAi(true);
        pig.setNoGravity(true);
        pig.setDeltaMovement(Vec3.ZERO);
        return pig;
    }

    private static float contact(GameTestHelper helper, BlockPos cactus, LivingEntity entity) {
        entity.invulnerableTime = 0;
        entity.setHealth(entity.getMaxHealth());
        float before = entity.getHealth();
        helper.getLevel().getBlockState(cactus).entityInside(helper.getLevel(), cactus, entity);
        return before - entity.getHealth();
    }

    private static ItemEntity item(GameTestHelper helper, BlockPos pos, int count, boolean ignoresTriggers) {
        ItemStack stack = new ItemStack(Items.COBBLESTONE, count);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        ItemEntity item = ignoresTriggers
                ? new ItemEntity(helper.getLevel(), pos.getX() + .5D, pos.getY(), pos.getZ() + .5D, stack) {
                    @Override public boolean isIgnoringBlockTriggers() { return true; }
                }
                : new ItemEntity(helper.getLevel(), pos.getX() + .5D, pos.getY(), pos.getZ() + .5D, stack);
        item.setNoGravity(true);
        item.noPhysics = true;
        item.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(helper.getLevel().addFreshEntity(item), "The actual plate-test item joins the prepared chunk");
        return item;
    }

    private static int power(GameTestHelper helper, BlockPos pos, Block plate, Entity touching) {
        var state = plate.defaultBlockState();
        helper.getLevel().setBlock(pos, state, 2);
        state.entityInside(helper.getLevel(), pos, touching);
        return helper.getLevel().getBlockState(pos).getValue(WeightedPressurePlateBlock.POWER);
    }

    private static void withPlate(GameTestHelper helper, Function<PlateFixture, Runnable> scenario) {
        PlateFixture fixture = new PlateFixture(helper);
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { fixture.close(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { fixture.close(); }
        });
        helper.startSequence().thenWaitUntil(() -> {
            if (!helper.getLevel().isPositionEntityTicking(fixture.pos))
                for (int tasks = 0; tasks < 64 && helper.getLevel().getChunkSource().pollTask(); tasks++) {}
            helper.assertTrue(helper.getLevel().isPositionEntityTicking(fixture.pos)
                            && helper.getLevel().areEntitiesLoaded(fixture.chunk.toLong()),
                    "The actual pressure-plate fixture reaches native entity-ticking and entity-index readiness");
        }).thenExecute(() -> fixture.assertions = scenario.apply(fixture))
                .thenWaitUntil(fixture::assertItemsVisible).thenExecute(() -> {
            // Pinning and assertions share one callback after the native spatial query has included every item.
            fixture.pinItems();
            try (fixture) { fixture.assertions.run(); }
        }).thenSucceed();
    }

    private static final class PlateFixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos pos;
        final ChunkPos chunk;
        final int ticketKey = PLATE_FIXTURE_IDS.incrementAndGet();
        final List<Entity> entities = new ArrayList<>();
        final List<ItemEntity> items = new ArrayList<>();
        Runnable assertions;
        boolean closed;

        PlateFixture(GameTestHelper helper) {
            this.helper = helper;
            // Stay above neighboring GameTest structure cleanup while keeping the real plate inside one chunk.
            pos = helper.absolutePos(new BlockPos(3, 67, 3));
            chunk = new ChunkPos(pos);
            helper.getLevel().getChunkSource().addRegionTicket(PLATE_TICKET, chunk, 2, ticketKey);
            helper.getLevel().getChunkAt(pos);
            helper.getLevel().setBlock(pos.below(), Blocks.BEDROCK.defaultBlockState(), 2);
            helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }

        <T extends Entity> T track(T entity) {
            entities.add(entity);
            return entity;
        }

        ItemEntity item(int count, boolean ignoresTriggers) {
            ItemEntity item = track(SpecialBlockContactGameTests.item(helper, pos, count, ignoresTriggers));
            items.add(item);
            return item;
        }

        void pinItems() {
            for (ItemEntity item : items) {
                item.moveTo(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
                item.setDeltaMovement(Vec3.ZERO);
                item.setNoGravity(true);
                item.noPhysics = true;
            }
        }

        void assertItemsVisible() {
            pinItems();
            for (ItemEntity item : items) {
                helper.assertTrue(!item.isRemoved()
                                && helper.getLevel().getEntitiesOfClass(ItemEntity.class, item.getBoundingBox()).contains(item),
                        "The raw native spatial query includes each actual pressure-plate item before numeric assertions");
            }
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            entities.forEach(Entity::discard);
            helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            helper.getLevel().setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 2);
            helper.getLevel().getChunkSource().removeRegionTicket(PLATE_TICKET, chunk, 2, ticketKey);
        }
    }
}

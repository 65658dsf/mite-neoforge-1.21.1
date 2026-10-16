package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.curse.CursePlayerEffects;
import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercise native player methods and independent source random-roll boundaries. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class WitchCursePlayerEffectsGameTests {
    private static final AtomicInteger ENDERMAN_FIXTURES = new AtomicInteger();
    private WitchCursePlayerEffectsGameTests() {}

    @GameTest(template = "empty")
    public static void cursedAirResetsCapAtNinetyAndOnlyBreathingDiscoversEffect(GameTestHelper h) throws Exception {
        try (Fixture f = new Fixture(h)) {
            f.curse(R196Curse.CANNOT_HOLD_BREATH);
            f.player.setAirSupply(300);
            h.assertValueEqual(f.player.getAirSupply(), 90, "uf.g ninety tick cap");
            h.assertTrue(!PlayerCurses.state(f.player).known(), "Air resets alone do not discover the effect");
            f.player.setAirSupply(-15);
            h.assertValueEqual(f.player.getAirSupply(), -15, "Do not erase vanilla drowning counter");
            Method decrease = LivingEntity.class.getDeclaredMethod("decreaseAirSupply", int.class);
            decrease.setAccessible(true);
            decrease.invoke(f.player, 90);
            h.assertTrue(PlayerCurses.state(f.player).known(), "First underwater decrement reveals breath curse");
            PlayerCurses.cure(f.player);
            f.player.setAirSupply(300);
            h.assertValueEqual(f.player.getAirSupply(), 300, "Cured player resets native full air");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sprintStartIsRejectedAndCureRestoresNativeSprint(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.curse(R196Curse.CANNOT_RUN);
            h.assertTrue(!PlayerCurses.state(f.player).known(), "Unknown before attempting sprint");
            f.player.setSprinting(true);
            h.assertTrue(!f.player.isSprinting() && PlayerCurses.state(f.player).known(), "Native sprint toggle denied and revealed");
            PlayerCurses.cure(f.player);
            f.player.setSprinting(true);
            h.assertTrue(f.player.isSprinting(), "Cure restores native sprint toggle");
            f.curse(R196Curse.CANNOT_RUN);
            h.assertTrue(!f.player.isSprinting(), "Realizing while sprinting removes active sprint");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void entanglementUsesSourcePlantAndVineFactorsAndPreservesVerticalInput(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.curse(R196Curse.ENTANGLEMENT);
            Vec3 input = new Vec3(1D, .5D, -1D);
            f.set(Blocks.STONE.defaultBlockState());
            h.assertValueEqual(CursePlayerEffects.movement(f.player, input), input, "Ordinary blocks do not entangle");
            h.assertTrue(!PlayerCurses.state(f.player).known(), "No discovery without plants");
            f.set(Blocks.SHORT_GRASS.defaultBlockState());
            h.assertValueEqual(CursePlayerEffects.movement(f.player, Vec3.ZERO), Vec3.ZERO,
                    "Stationary input stays zero instead of inducing movement");
            h.assertTrue(PlayerCurses.state(f.player).known(),
                    "Source of.e discovers entanglement on plants even while stationary");
            Vec3 plant = CursePlayerEffects.movement(f.player, input);
            h.assertTrue(Math.abs(plant.x - .4D) < .000001D && plant.y == .5D
                    && Math.abs(plant.z + .4D) < .000001D, "Plants scale only lateral input to forty percent");
            f.set(Blocks.VINE.defaultBlockState());
            Vec3 vine = CursePlayerEffects.movement(f.player, input);
            h.assertTrue(Math.abs(vine.x - .2D) < .000001D && vine.y == .5D, "Vines scale lateral input to twenty percent");
            h.assertTrue(PlayerCurses.state(f.player).known(), "Moving through plants reveals entanglement");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void armorRealizationDropsAllFourPiecesExactlyOnce(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            f.player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            f.player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
            f.player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
            f.curse(R196Curse.CANNOT_WEAR_ARMOR);
            h.assertTrue(f.player.getInventory().armor.stream().allMatch(ItemStack::isEmpty), "Every humanoid armor slot emptied");
            h.assertValueEqual(f.drops().size(), 4, "Four original stacks dropped including non-armor helmet");
            CursePlayerEffects.dropArmor(f.player);
            h.assertValueEqual(f.drops().size(), 4, "Second pass never duplicates equipment");
            h.assertTrue(PlayerCurses.state(f.player).known(), "Forced armor loss discovers curse");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void armorInventoryAndRightClickAttemptsKeepTheirInput(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.curse(R196Curse.CANNOT_WEAR_ARMOR);
            ItemStack helmet = new ItemStack(Items.IRON_HELMET);
            h.assertTrue(!f.player.inventoryMenu.getSlot(5).mayPlace(helmet), "Direct inventory armor insertion blocked");
            f.player.setItemInHand(InteractionHand.MAIN_HAND, helmet);
            ((ArmorItem) Items.IRON_HELMET).use(f.player.level(), f.player, InteractionHand.MAIN_HAND);
            h.assertTrue(f.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                    && f.player.getMainHandItem() == helmet && helmet.getCount() == 1,
                    "Right click preserves hand item and empty armor slot");
            ItemStack tool = new ItemStack(Items.STICK);
            ItemStack offhandArmor = new ItemStack(Items.IRON_CHESTPLATE);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            f.player.setItemInHand(InteractionHand.OFF_HAND, offhandArmor);
            ((ArmorItem) Items.IRON_CHESTPLATE).use(f.player.level(), f.player, InteractionHand.OFF_HAND);
            h.assertTrue(f.player.getMainHandItem() == tool && f.player.getOffhandItem() == offhandArmor
                            && offhandArmor.getCount() == 1 && f.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                    "Offhand armor cannot be moved into mainhand when canUseSlot rejects its real slot");
            h.assertTrue(f.player.canUseSlot(EquipmentSlot.MAINHAND) && f.player.canUseSlot(EquipmentSlot.OFFHAND),
                    "Held and offhand items remain usable");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void armorCursePreventsNativeDispenserEquipping(GameTestHelper h) {
        // dispenseArmor chooses the first eligible living entity, not a specific player.
        // Keep the already ticking template chunk, but isolate the query vertically from its mobs.
        // Players are always ticking even in hidden sections, whereas AABB queries only traverse
        // accessible sections. A far getChunk/forced ticket plus a fixed wait does not prove that.
        BlockPos isolated = h.absolutePos(new BlockPos(3, 192, 3));
        Fixture fixture = new Fixture(h, isolated, true);
        var cleaned = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup = () -> { if (cleaned.compareAndSet(false, true)) fixture.close(); };
        try {
            fixture.player.connection.teleport(isolated.getX() + .5D, isolated.getY(), isolated.getZ() + .5D, 0F, 0F);
            fixture.player.connection.resetPosition();
            h.getLevel().getChunkSource().move(fixture.player);
            h.runAfterDelay(190, cleanup);
            h.startSequence().thenWaitUntil(() -> {
                h.assertTrue(h.getLevel().isPositionEntityTicking(isolated), "The dispenser fixture chunk reaches native entity-ticking status");
                h.assertTrue(h.getLevel().getEntity(fixture.player.getUUID()) == fixture.player,
                        "The registered survival player is visible by its actual UUID");
                h.assertTrue(h.getLevel().getEntitiesOfClass(LivingEntity.class, new AABB(isolated)).contains(fixture.player),
                        "The native AABB section query can observe the actual fixture player before dispensing");
            }).thenExecute(() -> {
                try { assertDispenserRefusesCursedPlayer(h, fixture); }
                finally { cleanup.run(); }
            }).thenSucceed();
        } catch (RuntimeException | Error failure) { cleanup.run(); throw failure; }
    }

    private static void assertDispenserRefusesCursedPlayer(GameTestHelper h, Fixture f) {
            f.curse(R196Curse.CANNOT_WEAR_ARMOR);
            BlockPos sourcePos = f.pos.west();
            BlockState before = h.getLevel().getBlockState(sourcePos);
            try {
                BlockState state = Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST);
                h.getLevel().setBlock(sourcePos, state, 2);
                ItemStack helmet = new ItemStack(Items.IRON_HELMET);
                h.assertTrue(!f.player.canTakeItem(helmet),
                        "Native dispenser selector rejects the cursed fixture player before splitting any item");
                var query = new AABB(f.pos);
                var occupants = h.getLevel().getEntitiesOfClass(LivingEntity.class, query);
                var eligible = h.getLevel().getEntitiesOfClass(LivingEntity.class, query,
                        EntitySelector.NO_SPECTATORS.and(new EntitySelector.MobCanWearArmorEntitySelector(helmet)));
                var diagnostic = eligible.stream().map(entity -> entity.getType() + "/" + entity.getId()
                        + " at " + entity.blockPosition() + " head=" + entity.getItemBySlot(EquipmentSlot.HEAD)).toList();
                h.assertTrue(occupants.size() == 1 && occupants.contains(f.player),
                        "The isolated native dispenser query contains only the actual survival fixture player");
                var source = new BlockSource(h.getLevel(), sourcePos, state,
                        (DispenserBlockEntity)h.getLevel().getBlockEntity(sourcePos));
                boolean equipped = ArmorItem.dispenseArmor(source, helmet);
                h.assertTrue(!equipped && helmet.getCount() == 1 && f.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                                && f.player.getMainHandItem().isEmpty() && f.player.getOffhandItem().isEmpty(),
                        "Dispenser leaves forbidden armor in its stack; equipped=" + equipped + ", count=" + helmet.getCount()
                                + ", playerHead=" + f.player.getItemBySlot(EquipmentSlot.HEAD)
                                + ", eligibleBefore=" + diagnostic + ", occupants=" + occupants.size());
                PlayerCurses.cure(f.player);
                h.assertTrue(h.getLevel().getEntitiesOfClass(LivingEntity.class, query,
                                EntitySelector.NO_SPECTATORS.and(new EntitySelector.MobCanWearArmorEntitySelector(helmet))).contains(f.player),
                        "The same player becomes eligible in the native dispenser query after curing");
                h.assertTrue(ArmorItem.dispenseArmor(source, helmet) && helmet.isEmpty()
                                && f.player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
                                && f.player.getItemBySlot(EquipmentSlot.HEAD).getCount() == 1,
                        "Positive control: the same real dispenser consumes one helmet and equips the cured player");
            } finally { h.getLevel().setBlock(sourcePos, before, 2); }
    }

    @GameTest(template = "empty")
    public static void armorRestrictionsUseRawIntendedSlotsForHelmetsBlocksAndModernEquipables(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.curse(R196Curse.CANNOT_WEAR_ARMOR);
            for (var item : List.of(Items.IRON_HELMET, Items.CARVED_PUMPKIN, Items.PLAYER_HEAD, Items.SKELETON_SKULL)) {
                ItemStack stack = new ItemStack(item);
                h.assertValueEqual(CursePlayerEffects.rawEquipmentSlot(stack), EquipmentSlot.HEAD,
                        "Raw head identity survives unavailable armor slot: " + item);
                h.assertTrue(!f.player.canTakeItem(stack), "Dispenser refuses head item without falling back to mainhand: " + item);
            }
            ItemStack elytra = new ItemStack(Items.ELYTRA);
            h.assertValueEqual(CursePlayerEffects.rawEquipmentSlot(elytra), EquipmentSlot.CHEST, "Modern elytra is a worn chest item");
            h.assertTrue(!f.player.canTakeItem(elytra), "Modern chest equipables cannot bypass armor curse");
            h.assertValueEqual(CursePlayerEffects.rawEquipmentSlot(new ItemStack(Items.SHIELD)), EquipmentSlot.OFFHAND,
                    "Native shield keeps its offhand intent");
            h.assertValueEqual(CursePlayerEffects.rawEquipmentSlot(new ItemStack(Items.STICK)), EquipmentSlot.MAINHAND,
                    "An ordinary held item remains mainhand");
            h.assertTrue(f.player.canTakeItem(new ItemStack(Items.STICK)) && f.player.canTakeItem(new ItemStack(Items.SHIELD)),
                    "Non-armor mainhand and offhand items keep native selector eligibility");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void armorInsertedDirectlyAfterRealizationDropsOnFollowingPlayerTick(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.curse(R196Curse.CANNOT_WEAR_ARMOR);
            f.player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
            CursePlayerEffects.tick(new PlayerTickEvent.Post(f.player));
            h.assertTrue(f.player.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "Direct mod/command assignment cannot retain armor");
            h.assertValueEqual(f.drops().size(), 1, "Bypass stack dropped once instead of deleted");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void chestCurseBlocksNativeTrappedAndAllMaterialStrongboxes(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.curse(R196Curse.CANNOT_OPEN_CHESTS);
            List<BlockState> chests = new ArrayList<>(List.of(Blocks.CHEST.defaultBlockState(), Blocks.TRAPPED_CHEST.defaultBlockState()));
            MiteFunctionalBlocks.STRONGBOXES.values().forEach(block -> chests.add(block.get().defaultBlockState()));
            for (BlockState state : chests) {
                f.set(state);
                var result = state.useWithoutItem(h.getLevel(), f.player,
                        new BlockHitResult(Vec3.atCenterOf(f.pos), Direction.UP, f.pos, false));
                h.assertTrue(result.consumesAction() && f.player.containerMenu == f.player.inventoryMenu,
                        "Chest activation handled without opening " + state.getBlock());
            }
            h.assertTrue(PlayerCurses.state(f.player).known(), "First chest attempt discovers effect");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sleepCurseRejectsBeginningAndContinuationButLeavesOtherSleepersAlone(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.curse(R196Curse.CANNOT_SLEEP);
            var start = NeoForge.EVENT_BUS.post(new CanPlayerSleepEvent(f.player, f.pos, null));
            h.assertValueEqual(start.getProblem(), Player.BedSleepingProblem.OTHER_PROBLEM, "Native sleep eligibility denied");
            var continuing = NeoForge.EVENT_BUS.post(new CanContinueSleepingEvent(f.player, null));
            h.assertTrue(!continuing.mayContinueSleeping(), "Existing sleep progression denied");
            PlayerCurses.cure(f.player);
            var restored = new CanContinueSleepingEvent(f.player, null);
            CursePlayerEffects.sleeping(restored);
            h.assertTrue(restored.mayContinueSleeping(), "Cured sleeper receives no curse restriction");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void spiderFearRejectsExactlySourceThreeOfFourRolls(GameTestHelper h) {
        fear(h, R196Curse.FEAR_OF_SPIDERS, EntityType.SPIDER);
    }
    @GameTest(template = "empty")
    public static void wolfFearRejectsExactlySourceThreeOfFourRolls(GameTestHelper h) {
        fear(h, R196Curse.FEAR_OF_WOLVES, EntityType.WOLF);
    }
    @GameTest(template = "empty")
    public static void creeperFearRejectsExactlySourceThreeOfFourRolls(GameTestHelper h) {
        fear(h, R196Curse.FEAR_OF_CREEPERS, EntityType.CREEPER);
    }
    @GameTest(template = "empty")
    public static void undeadFearRejectsExactlySourceThreeOfFourRolls(GameTestHelper h) {
        fear(h, R196Curse.FEAR_OF_UNDEAD, EntityType.ZOMBIE);
    }

    private static void fear(GameTestHelper h, R196Curse curse, EntityType<? extends Mob> type) {
        try (Fixture f = new Fixture(h)) {
            Mob target = type.create(h.getLevel());
            h.assertTrue(target != null, "Fear target created");
            f.curse(curse);
            for (int roll = 0; roll < 4; roll++) {
                long seed = seed(4, roll);
                target.getRandom().setSeed(seed);
                var event = NeoForge.EVENT_BUS.post(new AttackEntityEvent(f.player, target));
                h.assertTrue(event.isCanceled() == (roll > 0), "Source nextInt(4)=" + roll + " fear rejection boundary");
                if (roll == 0) h.assertTrue(!PlayerCurses.state(f.player).known(), "Successful quarter does not discover effect");
            }
            h.assertTrue(PlayerCurses.state(f.player).known(), "First failed attempt discovers effect");
            PlayerCurses.cure(f.player);
            target.getRandom().setSeed(seed(4, 3));
            h.assertTrue(!NeoForge.EVENT_BUS.post(new AttackEntityEvent(f.player, target)).isCanceled(), "Cured attack not rejected");
            target.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fearIncludesAddedSpeciesAndExcludesUnrelatedAnimals(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            for (MobKind kind : MobKind.values()) {
                Mob mob = MiteMobs.type(kind).create(h.getLevel());
                R196Curse expected = switch (kind.family()) {
                    case SPIDER -> R196Curse.FEAR_OF_SPIDERS;
                    case WOLF -> R196Curse.FEAR_OF_WOLVES;
                    case CREEPER -> R196Curse.FEAR_OF_CREEPERS;
                    case ZOMBIE, SKELETON -> R196Curse.FEAR_OF_UNDEAD;
                    default -> null;
                };
                // Source biology is independent of the rendering family: the ghoul and
                // invisible stalker are living, while the nightwing bat is undead.
                if (kind == MobKind.WIGHT || kind == MobKind.SHADOW || kind == MobKind.NIGHTWING)
                    expected = R196Curse.FEAR_OF_UNDEAD;
                h.assertTrue(CursePlayerEffects.fearFor(mob) == expected, "Added identity fear mapping " + kind);
                mob.discard();
            }
            f.curse(R196Curse.FEAR_OF_SPIDERS);
            var cow = EntityType.COW.create(h.getLevel());
            h.assertTrue(!NeoForge.EVENT_BUS.post(new AttackEntityEvent(f.player, cow)).isCanceled(), "Fear does not prevent unrelated attacks");
            cow.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fearedMeleeFailureDoesNotDamageTargetOrWearWeapon(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            var wolf = EntityType.WOLF.create(h.getLevel());
            wolf.setNoAi(true);
            wolf.moveTo(f.pos.getCenter());
            f.curse(R196Curse.FEAR_OF_WOLVES);
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            float before = wolf.getHealth();
            wolf.getRandom().setSeed(seed(4, 3));
            f.player.attack(wolf);
            h.assertTrue(wolf.getHealth() == before && sword.getDamageValue() == 0,
                    "Server cancellation precedes damage, weapon wear and on-hit enchantments");
            wolf.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void endermanCurseAggressionHasOneInThreeRollBeforePumpkinImmunity(GameTestHelper h) throws Exception {
        try (Fixture f = new Fixture(h)) {
            EnderMan enderman = EntityType.ENDERMAN.create(h.getLevel());
            enderman.moveTo(f.pos.getX() + 4D, f.pos.getY(), f.pos.getZ());
            f.player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            Method gaze = EnderMan.class.getDeclaredMethod("isLookingAtMe", Player.class);
            gaze.setAccessible(true);
            f.curse(R196Curse.ENDERMEN_ENEMY);
            for (int roll = 1; roll <= 3; roll++) {
                int value = roll % 3;
                enderman.getRandom().setSeed(seed(3, value));
                h.assertTrue((boolean)gaze.invoke(enderman, f.player) == (value == 0),
                        "Pumpkin protects only the ordinary gaze path; curse roll=" + value);
            }
            h.assertTrue(PlayerCurses.state(f.player).known(), "First curse aggression roll discovers effect");
            enderman.setTarget(f.player);
            enderman.getRandom().setSeed(seed(3, 0));
            h.assertTrue(!(boolean)gaze.invoke(enderman, f.player),
                    "An acquired target retains native pumpkin/gaze behavior for freeze and pursuit teleport goals");
            enderman.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void endermanTargetGoalAcquiresCursedPlayerThroughWallButOrdinaryAggressionStillNeedsSight(GameTestHelper h) {
        int fixture = ENDERMAN_FIXTURES.incrementAndGet();
        BlockPos isolated = new BlockPos(-26_000_000 + fixture * 32 + 8, 160, -26_000_000 + 8);
        ChunkPos chunk = new ChunkPos(isolated);
        boolean forcedBefore = h.getLevel().getForcedChunks().contains(chunk.toLong());
        h.getLevel().setChunkForced(chunk.x, chunk.z, true);
        h.getLevel().getChunkAt(isolated);
        h.runAfterDelay(10, () -> {
            try {
                assertEndermanTargetGoalSightPolicy(h, isolated);
                h.succeed();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot inspect real Enderman target goal fixture", failure);
            } finally {
                if (!forcedBefore) h.getLevel().setChunkForced(chunk.x, chunk.z, false);
            }
        });
    }

    private static void assertEndermanTargetGoalSightPolicy(GameTestHelper h, BlockPos isolated) throws ReflectiveOperationException {
        Map<BlockPos, BlockState> wallBefore = new LinkedHashMap<>();
        EnderMan enderman = EntityType.ENDERMAN.create(h.getLevel());
        try (Fixture f = new Fixture(h, isolated, true)) {
            enderman.setNoAi(true);
            enderman.setNoGravity(true);
            enderman.moveTo(f.pos.getX() + 4.5D, f.pos.getY(), f.pos.getZ() + .5D);
            h.assertTrue(h.getLevel().addFreshEntity(enderman), "Register actual native Enderman in accessible fixture chunk");
            f.player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            for (int y = 0; y < 4; y++) for (int z = -1; z <= 1; z++) {
                BlockPos pos = f.pos.offset(2, y, z);
                wallBefore.put(pos, h.getLevel().getBlockState(pos));
                h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            }
            enderman.getSensing().tick();
            h.assertTrue(!enderman.getSensing().hasLineOfSight(f.player), "A real stone wall blocks the native sensing ray");
            h.assertTrue(h.getLevel().players().contains(f.player) && !f.player.isCreative() && !f.player.isSpectator(),
                    "Actual world candidate is a survival player accepted by combat targeting");
            Goal goal = enderman.targetSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                    .filter(candidate -> candidate.getClass().getSimpleName().equals("EndermanLookForPlayerGoal"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Missing real registered native Enderman target goal"));
            Field conditionsField = goal.getClass().getDeclaredField("startAggroTargetConditions");
            conditionsField.setAccessible(true);
            TargetingConditions conditions = (TargetingConditions)conditionsField.get(goal);
            f.curse(R196Curse.ENDERMEN_ENEMY);
            long sourceSeed = seed(3, 0);
            RandomSource oracle = RandomSource.create(sourceSeed);
            oracle.nextInt(3);
            int expectedNext = oracle.nextInt(1_000_003);
            enderman.getRandom().setSeed(sourceSeed);
            h.assertTrue(conditions.test(enderman, f.player), "Successful source roll bypasses both pumpkin and outer sight requirement");
            h.assertValueEqual(enderman.getRandom().nextInt(1_000_003), expectedNext,
                    "One real target candidate consumes exactly one source curse roll");

            // Other test players may still occur earlier in the world's player list. Use bounded
            // seeded attempts so their out-of-range predicate calls cannot shift this fixture's roll.
            boolean acquired = false;
            for (long attempt = 0; attempt < 128 && !acquired; attempt++) {
                enderman.getRandom().setSeed(attempt);
                acquired = goal.canUse();
            }
            Field pendingField = goal.getClass().getDeclaredField("pendingTarget");
            pendingField.setAccessible(true);
            h.assertTrue(acquired && pendingField.get(goal) == f.player,
                    "Real registered Goal.canUse finds the cursed survival player behind wall and pumpkin");

            PlayerCurses.cure(f.player);
            enderman.setPersistentAngerTarget(f.player.getUUID());
            enderman.setRemainingPersistentAngerTime(200);
            h.assertTrue(enderman.isAngryAt(f.player), "Ordinary anger satisfies the original aggression predicate without needing a gaze");
            enderman.getSensing().tick();
            h.assertTrue(!conditions.test(enderman, f.player) && !goal.canUse(),
                    "Uncursed angry players behind the wall still fail native sight targeting");
        } finally {
            enderman.discard();
            wallBefore.forEach((pos, state) -> h.getLevel().setBlock(pos, state, 2));
        }
    }

    private static long seed(int bound, int expected) {
        for (long value = 0; value < 10_000; value++) if (RandomSource.create(value).nextInt(bound) == expected) return value;
        throw new AssertionError("No deterministic random seed");
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerPlayer player;
        final Witch witch;
        final EmbeddedChannel channel;
        final BlockPos pos;
        final BlockState before;
        Fixture(GameTestHelper helper) {
            this(helper, helper.absolutePos(new BlockPos(3, 2, 3)));
        }
        Fixture(GameTestHelper helper, BlockPos position) {
            this(helper, position, false);
        }
        Fixture(GameTestHelper helper, BlockPos position, boolean survival) {
            this.helper = helper;
            pos = position;
            helper.getLevel().getChunkAt(pos);
            before = helper.getLevel().getBlockState(pos);
            if (survival) {
                var profile = new GameProfile(UUID.randomUUID(), "curse-" + UUID.randomUUID().toString().substring(0, 8));
                var cookie = CommonListenerCookie.createInitial(profile, false);
                player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
                var connection = new Connection(PacketFlow.SERVERBOUND);
                channel = new EmbeddedChannel(connection);
                helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
                player.setGameMode(GameType.SURVIVAL);
            } else {
                player = helper.makeMockServerPlayerInLevel();
                channel = null;
            }
            player.moveTo(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
            player.setNoGravity(true);
            witch = EntityType.WITCH.create(helper.getLevel());
            witch.setNoAi(true);
        }
        void curse(R196Curse type) {
            PlayerCurses.cure(player);
            helper.assertTrue(PlayerCurses.schedule(player, witch, type, 0), "Schedule isolated test curse " + type);
            PlayerCurses.reconcile(player);
            helper.assertTrue(PlayerCurses.has(player, type), "Realize test curse " + type);
        }
        void set(BlockState state) { helper.getLevel().setBlock(pos, state, 2); }
        List<ItemEntity> drops() { return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)); }
        @Override public void close() {
            PlayerCurses.cure(player);
            player.setSprinting(false);
            player.closeContainer();
            for (var item : drops()) item.discard();
            helper.getLevel().setBlock(pos, before, 2);
            witch.discard();
            player.getServer().getPlayerList().remove(player);
            if (channel != null) channel.finishAndReleaseAll();
        }
    }
}

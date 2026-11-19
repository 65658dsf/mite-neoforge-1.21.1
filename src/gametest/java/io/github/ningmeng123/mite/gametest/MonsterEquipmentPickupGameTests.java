package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobEquipmentUpgrades;
import io.github.ningmeng123.mite.entity.MonsterEquipmentScavenging;
import io.github.ningmeng123.mite.entity.SeekBetterEquipmentGoal;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual native equipment consumption and active monster navigation. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class MonsterEquipmentPickupGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_equipment_pickup_test", Integer::compareTo);

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void activeZombieActuallyWalksToAndPicksUpHigherQualitySword(GameTestHelper h) {
        ready(h, f -> {
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, sword(Quality.POOR));
            ItemStack incoming = sword(Quality.EXCELLENT);
            incoming.setDamageValue(91);
            incoming.set(DataComponents.CUSTOM_NAME, Component.literal("Recovered player sword"));
            ItemEntity item = f.item(incoming.copy(), 6, 0, 0);
            double start = f.mob.distanceToSqr(item);
            h.assertTrue(f.mob.canPickUpLoot() && !f.mob.isNoAi(), "A joined ordinary zombie can run the installed pickup AI");
            boolean sawPath = false;
            for (int tick = 0; tick < 220 && !item.isRemoved(); ++tick) {
                h.getLevel().tickNonPassenger(f.mob);
                sawPath |= f.mob.getNavigation().getPath() != null;
            }
            h.assertTrue(sawPath && f.mob.distanceToSqr(item) < start - 9D,
                    "The normal goal selector and actual native navigation move towards the distant world item");
            h.assertTrue(item.isRemoved() && ItemStack.matches(f.mob.getMainHandItem(), incoming),
                    "Native collision pickup equips the original superior-quality item with its damage and name");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void equalQualityWearAndLowerQualityMaterialDoNotStartScavenging(GameTestHelper h) {
        ready(h, f -> {
            ItemStack current = sword(Quality.FINE); current.setDamageValue(300);
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, current);
            ItemEntity equal = f.item(sword(Quality.FINE), 4, 0, 0);
            ItemStack low = new ItemStack(MiteTools.get("mite:adamantium_sword"));
            EquipmentDurability.setQuality(low, Quality.POOR);
            ItemEntity lower = f.item(low, 3, 0, 1);
            h.assertFalse(MobEquipmentUpgrades.isUpgrade(f.mob, equal.getItem()),
                    "More remaining durability alone cannot trigger an equal-quality pursuit");
            h.assertFalse(MobEquipmentUpgrades.isUpgrade(f.mob, low),
                    "A stronger metal cannot bypass the strict MITE quality gate");
            h.assertFalse(new SeekBetterEquipmentGoal(f.mob).canUse(), "Neither nearby item starts a pickup route");
            h.assertTrue(f.mob.getMainHandItem() == current && equal.isAlive() && lower.isAlive(),
                    "Rejected pursuits do not mutate equipment or consume world items");
            ItemStack fineCopper = new ItemStack(MiteTools.get("mite:copper_sword"));
            EquipmentDurability.setQuality(fineCopper, Quality.EXCELLENT);
            h.assertTrue(MobEquipmentUpgrades.isUpgrade(f.mob, fineCopper),
                    "Actual higher quality is recognized independently of the metal level");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void nativePickupConsumesExactlyOneAndPreservesComponentsAndGuaranteedDrop(GameTestHelper h) {
        ready(h, f -> {
            f.mob.setNoAi(true);
            ItemStack old = sword(Quality.POOR); old.setDamageValue(27);
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, old); f.mob.setGuaranteedDrop(EquipmentSlot.MAINHAND);
            ItemStack incoming = sword(Quality.FINE); incoming.setDamageValue(81);
            incoming.set(DataComponents.CUSTOM_NAME, Component.literal("One from the dropped stack"));
            try (var capture = new SpawnedEntityCapture(h.getLevel(), e -> e instanceof ItemEntity)) {
                ItemEntity item = f.item(incoming.copy(), 0, 0, 0);
                f.mob.aiStep();
                h.assertTrue(item.isRemoved(), "Native pickup consumes the one legal equipment item exactly once");
                h.assertTrue(ItemStack.matches(f.mob.getMainHandItem(), incoming.copyWithCount(1)),
                        "Equipped count, damage, quality and custom name survive actual pickup");
                h.assertTrue(f.mob.isPersistenceRequired(), "Player equipment pickup prevents despawning");
                CompoundTag saved = f.mob.saveWithoutId(new CompoundTag());
                h.assertTrue(saved.getList("HandDropChances", 5).getFloat(0) > 1F,
                        "The actual picked-up equipment is guaranteed to drop on death");
                h.assertValueEqual(capture.live(ItemEntity.class).stream().filter(e -> ItemStack.matches(e.getItem(), old)).count(), 1L,
                        "Replacing an earlier guaranteed item drops that item exactly once");
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void armorAndShieldsUpgradeTheirOwnSlotsWithoutReplacingTheSword(GameTestHelper h) {
        ready(h, f -> {
            f.mob.setNoAi(true);
            ItemStack weapon = sword(Quality.FINE); f.mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            ItemStack oldHelmet = quality(new ItemStack(Items.IRON_HELMET), Quality.POOR);
            ItemStack helmet = quality(new ItemStack(Items.IRON_HELMET), Quality.EXCELLENT);
            f.mob.setItemSlot(EquipmentSlot.HEAD, oldHelmet);
            h.assertValueEqual(MobEquipmentUpgrades.targetSlot(f.mob, helmet), EquipmentSlot.HEAD,
                    "A helmet is compared against the current helmet");
            h.assertTrue(ItemStack.matches(f.mob.equipItemIfPossible(helmet.copy()), helmet), "Native equip accepts a quality-only armor upgrade");
            ItemStack oldShield = quality(new ItemStack(MiteShields.get("iron")), Quality.POOR);
            ItemStack shield = quality(new ItemStack(MiteShields.get("iron")), Quality.FINE);
            f.mob.setItemSlot(EquipmentSlot.OFFHAND, oldShield);
            h.assertValueEqual(MobEquipmentUpgrades.targetSlot(f.mob, shield), EquipmentSlot.OFFHAND,
                    "Shield quality is compared in the real offhand slot");
            h.assertTrue(ItemStack.matches(f.mob.equipItemIfPossible(shield), shield), "Native equip accepts a quality-only shield upgrade");
            h.assertTrue(f.mob.getMainHandItem() == weapon && ItemStack.matches(f.mob.getItemBySlot(EquipmentSlot.HEAD), helmet)
                            && ItemStack.matches(f.mob.getOffhandItem(), shield),
                    "Both upgrades retain the exact current primary weapon");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void skeletonSpareWeaponsAndShieldsAreNeverOverwrittenByTheOtherCategory(GameTestHelper h) {
        ready(h, f -> {
            var skeleton = f.track(EntityType.SKELETON.create(h.getLevel())); skeleton.setNoAi(true);
            ItemStack bow = quality(new ItemStack(Items.BOW), Quality.POOR);
            ItemStack spare = sword(Quality.POOR), better = sword(Quality.FINE);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow); skeleton.setItemSlot(EquipmentSlot.OFFHAND, spare);
            h.assertValueEqual(MobEquipmentUpgrades.targetSlot(skeleton, better), EquipmentSlot.OFFHAND,
                    "The archer compares an incoming sword to its stored sword");
            h.assertTrue(ItemStack.matches(skeleton.equipItemIfPossible(better), better)
                            && skeleton.getMainHandItem() == bow && ItemStack.matches(skeleton.getOffhandItem(), better),
                    "Higher-quality spare pickup retains the original bow");
            ItemStack shield = quality(new ItemStack(MiteShields.get("iron")), Quality.EXCELLENT);
            h.assertTrue(MobEquipmentUpgrades.targetSlot(skeleton, shield) == null
                            && !MobEquipmentUpgrades.isUpgrade(skeleton, shield), "A shield cannot erase the backup sword");
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, better); skeleton.setItemSlot(EquipmentSlot.OFFHAND, shield);
            ItemStack betterBow = quality(new ItemStack(Items.BOW), Quality.FINE);
            h.assertTrue(MobEquipmentUpgrades.targetSlot(skeleton, betterBow) == null
                            && !MobEquipmentUpgrades.isUpgrade(skeleton, betterBow), "A new bow cannot erase an equipped shield");
            h.assertTrue(skeleton.getMainHandItem() == better && skeleton.getOffhandItem() == shield,
                    "Both rejected categories leave the original hand stacks untouched");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void rejectedArmorCannotFallbackToEmptyHandAndBindingStillLocksTheArmorSlot(GameTestHelper h) {
        ready(h, f -> {
            try (var source = new SourceConfigFixture(false, true)) {
                f.mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                ItemStack current = quality(new ItemStack(Items.IRON_HELMET), Quality.FINE);
                f.mob.setItemSlot(EquipmentSlot.HEAD, current);
                ItemStack poor = quality(new ItemStack(Items.IRON_HELMET), Quality.POOR);
                h.assertTrue(f.mob.equipItemIfPossible(poor).isEmpty() && f.mob.getMainHandItem().isEmpty()
                                && f.mob.getItemBySlot(EquipmentSlot.HEAD) == current,
                        "A refused low-quality helmet cannot fall through to native empty-mainhand storage");
                current.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                        .getHolderOrThrow(Enchantments.BINDING_CURSE), 1);
                ItemStack excellent = quality(new ItemStack(Items.IRON_HELMET), Quality.EXCELLENT);
                h.assertFalse(MobEquipmentUpgrades.isUpgrade(f.mob, excellent), "The real armor-change-prevention enchantment blocks pursuit");
                h.assertTrue(f.mob.equipItemIfPossible(excellent).isEmpty()
                                && f.mob.getItemBySlot(EquipmentSlot.HEAD) == current && f.mob.getMainHandItem().isEmpty(),
                        "Native pickup cannot replace bound armor or use its refused upgrade as a held item");
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void disarmingCooldownBlocksBothPursuitAndNativePickup(GameTestHelper h) {
        ready(h, f -> {
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            f.mob.getPersistentData().putInt("mite_enchantment_disarmed_ticks", 40);
            ItemEntity item = f.item(sword(Quality.FINE), 0, 0, 0);
            h.assertFalse(MobEquipmentUpgrades.isUpgrade(f.mob, item.getItem()), "The real disarming state blocks eligibility");
            h.assertFalse(new SeekBetterEquipmentGoal(f.mob).canUse(), "The pickup goal cannot bypass disarming");
            f.mob.setNoAi(true); f.mob.aiStep();
            h.assertTrue(item.isAlive() && f.mob.getMainHandItem().isEmpty(), "Native collision pickup also respects the same cooldown");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void griefingRuleAndItemPickupDelayStopRoutesAndConsumption(GameTestHelper h) {
        ready(h, f -> {
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, sword(Quality.POOR));
            ItemEntity item = f.item(sword(Quality.FINE), 4, 0, 0); item.setPickUpDelay(100);
            h.assertFalse(new SeekBetterEquipmentGoal(f.mob).canUse(), "A delayed dropped item cannot be pursued");
            item.setNoPickUpDelay();
            boolean original = h.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
            try {
                h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, h.getLevel().getServer());
                h.assertFalse(new SeekBetterEquipmentGoal(f.mob).canUse(), "Disabled native mob griefing also disables equipment pursuit");
                item.setPos(f.mob.position()); f.mob.setNoAi(true); f.mob.aiStep();
                h.assertTrue(item.isAlive() && EquipmentDurability.quality(f.mob.getMainHandItem()) == Quality.POOR,
                        "The disabled rule also prevents native item consumption at collision range");
            } finally {
                h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(original, h.getLevel().getServer());
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void hiddenAndPhysicallyUnreachableItemsCannotStartAnEquipmentRoute(GameTestHelper h) {
        ready(h, f -> {
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, sword(Quality.POOR));
            ItemEntity hidden = f.item(sword(Quality.FINE), 4, 0, 0);
            for (int z = -5; z <= 5; ++z) for (int y = 0; y <= 2; ++y)
                h.getLevel().setBlock(f.origin.offset(2, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
            h.assertFalse(f.mob.hasLineOfSight(hidden), "The dropped equipment is actually behind an opaque wall");
            h.assertFalse(new SeekBetterEquipmentGoal(f.mob).canUse(), "Unseen equipment cannot attract the monster through the wall");
            hidden.discard();
            for (int y = 0; y < 2; ++y)
                h.getLevel().setBlock(f.origin.offset(0, y, 3), Blocks.BEDROCK.defaultBlockState(), 2);
            ItemEntity unreachable = f.item(sword(Quality.FINE), 0, 2, 3);
            h.assertTrue(f.mob.hasLineOfSight(unreachable), "The elevated candidate is visible above its two-block pillar");
            var path = f.mob.getNavigation().createPath(unreachable, 0);
            h.assertTrue(path == null || !path.canReach(), "The candidate is outside the monster's physically reachable floor");
            h.assertFalse(new SeekBetterEquipmentGoal(f.mob).canUse(), "A partial or missing route cannot start a futile chase");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void acquiringACombatTargetImmediatelyInterruptsEquipmentPursuit(GameTestHelper h) {
        ready(h, f -> {
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, sword(Quality.POOR));
            ItemEntity item = f.item(sword(Quality.FINE), 5, 0, 0);
            var goal = new SeekBetterEquipmentGoal(f.mob);
            h.assertTrue(goal.canUse(), "An idle monster accepts the visible reachable quality upgrade");
            goal.start();
            h.assertTrue(goal.canContinueToUse(), "The equipment pursuit has a valid live target before combat");
            var target = f.track(EntityType.COW.create(h.getLevel())); target.setPos(f.mob.position().add(3D, 0D, 2D));
            f.mob.setTarget(target);
            h.assertFalse(goal.canContinueToUse(), "An acquired live combat target immediately interrupts looting");
            goal.stop();
            h.assertFalse(new SeekBetterEquipmentGoal(f.mob).canUse(), "The monster cannot restart equipment pursuit while fighting");
            h.assertTrue(item.isAlive() && EquipmentDurability.quality(f.mob.getMainHandItem()) == Quality.POOR,
                    "A cancelled chase leaves both the ground item and current equipment intact");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void repeatedInstallationIsIdempotentAndDoesNotArmAnimals(GameTestHelper h) {
        ready(h, f -> {
            MonsterEquipmentScavenging.install(f.mob); MonsterEquipmentScavenging.install(f.mob);
            h.assertValueEqual(f.mob.goalSelector.getAvailableGoals().stream().filter(g -> g.getGoal() instanceof SeekBetterEquipmentGoal).count(), 1L,
                    "Join, reload and repeated setup retain one equipment pickup goal");
            var cow = f.track(EntityType.COW.create(h.getLevel()));
            MonsterEquipmentScavenging.install(cow);
            h.assertFalse(MobEquipmentUpgrades.handles(cow), "Animal AI is excluded from humanoid monster upgrades");
            h.assertTrue(cow.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof SeekBetterEquipmentGoal),
                    "Installation cannot add the armed-monster task to an animal");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void outOfRadiusCornerUpgradeCannotDisplaceTheNearbyReachableChoice(GameTestHelper h) {
        ready(h, f -> {
            for (int x = 0; x <= 13; ++x) for (int z = 0; z <= 13; ++z) {
                h.getLevel().setBlock(f.origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
                for (int y = 0; y < 3; ++y) h.getLevel().setBlock(f.origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                h.getLevel().setBlock(f.origin.offset(x, 3, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, sword(Quality.POOR));
            ItemEntity nearby = f.item(sword(Quality.FINE), 4, 0, 0);
            ItemEntity corner = f.item(sword(Quality.MASTERWORK), 12, 0, 12);
            h.assertTrue(f.mob.distanceToSqr(corner) > 144D && f.mob.hasLineOfSight(corner),
                    "The highest-quality candidate is inside the search box but outside its real twelve-block radius");
            var cornerPath = f.mob.getNavigation().createPath(corner, 0);
            h.assertTrue(cornerPath != null && cornerPath.canReach(), "The out-of-radius item has a genuinely reachable native route");
            var goal = new SeekBetterEquipmentGoal(f.mob);
            h.assertTrue(goal.canUse() && goal.getTargetItem() == nearby,
                    "The monster chooses the nearby valid improvement without chasing a higher-quality corner outside its radius");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void piglinAvoidanceKeepsItsBrainRouteAndBruteWithoutAvoidMemoryCanStillSearch(GameTestHelper h) {
        ready(h, f -> {
            ItemEntity item = f.item(quality(new ItemStack(Items.GOLDEN_AXE), Quality.FINE), 4, 0, 0);
            var threat = f.track(EntityType.COW.create(h.getLevel()));
            for (var type : List.of(EntityType.PIGLIN, EntityType.PIGLIN_BRUTE)) {
                var piglin = f.track(type.create(h.getLevel()));
                piglin.setOnGround(true);
                piglin.setItemSlot(EquipmentSlot.MAINHAND, quality(new ItemStack(Items.GOLDEN_AXE), Quality.POOR));
                MonsterEquipmentScavenging.install(piglin);
                var brain = piglin.getBrain();
                if (brain.checkMemory(MemoryModuleType.AVOID_TARGET, MemoryStatus.REGISTERED)) {
                    WalkTarget escape = new WalkTarget(f.origin.offset(-4, 0, 0), 1.2F, 0);
                    brain.setMemory(MemoryModuleType.WALK_TARGET, escape);
                    brain.setMemory(MemoryModuleType.AVOID_TARGET, threat);
                    h.assertFalse(new SeekBetterEquipmentGoal(piglin).canUse(), "A piglin fleeing a real avoid target must not pursue equipment");
                    h.assertTrue(brain.getMemory(MemoryModuleType.WALK_TARGET).orElseThrow() == escape,
                            "Rejected scavenging preserves the brain's existing escape destination");
                    brain.eraseMemory(MemoryModuleType.AVOID_TARGET);
                } else {
                    h.assertTrue(type == EntityType.PIGLIN_BRUTE, "Only the native brute omits the avoidance memory module");
                }
                h.assertTrue(piglin.canPickUpLoot() && !piglin.isNoAi() && !piglin.isUsingItem(),
                        "The post-avoidance monster can run its pickup AI: " + type);
                h.assertFalse(brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET),
                        "The fresh fixture has no unrelated brain combat target: " + type);
                h.assertTrue(MobEquipmentUpgrades.isUpgrade(piglin, item.getItem()),
                        "The real dropped axe upgrades the current MITE quality: " + type);
                h.assertTrue(piglin.wantsToPickUp(item.getItem()),
                        "The native species-specific pickup gate accepts the dropped axe: " + type);
                h.assertTrue(piglin.hasLineOfSight(item), "The post-avoidance candidate is actually visible: " + type);
                var route = piglin.getNavigation().createPath(item, 0);
                h.assertTrue(route != null && route.canReach(),
                        "The grounded post-avoidance fixture has a complete real native path: " + type);
                var available = new SeekBetterEquipmentGoal(piglin);
                h.assertTrue(available.canUse() && available.getTargetItem() == item,
                        "After avoidance clears, the brain finds the real improvement without reading unregistered memory: " + type);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void activePiglinBrainAndGoalSelectorActuallyRecoverTheHigherQualitySword(GameTestHelper h) {
        ready(h, f -> {
            f.mob.setNoAi(true);
            var piglin = f.track(EntityType.PIGLIN.create(h.getLevel()));
            piglin.setImmuneToZombification(true); piglin.setPersistenceRequired(); piglin.setOnGround(true);
            piglin.setDeltaMovement(0D, -.08D, 0D);
            piglin.setItemSlot(EquipmentSlot.MAINHAND, sword(Quality.POOR));
            h.assertTrue(h.getLevel().addFreshEntity(piglin), "The real adult piglin joins the prepared ticking region");
            ItemStack incoming = sword(Quality.FINE); incoming.setDamageValue(37);
            ItemEntity item = f.item(incoming.copy(), 6, 0, 0);
            double start = piglin.distanceToSqr(item);
            boolean sawPath = false;
            for (int tick = 0; tick < 220 && !item.isRemoved(); ++tick) {
                h.getLevel().tickNonPassenger(piglin);
                sawPath |= piglin.getNavigation().getPath() != null;
            }
            h.assertTrue(sawPath && piglin.distanceToSqr(item) < start - 9D,
                    "The live piglin brain cooperates with the installed goal and real movement controller");
            h.assertTrue(item.isRemoved() && ItemStack.matches(piglin.getMainHandItem(), incoming),
                    "Native piglin pickup preserves and equips the actual higher-quality sword");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void piglinsAndBrutesPickUpBetterArmorButRejectArmorBelowGold(GameTestHelper h) {
        ready(h, f -> {
            for (var type : List.of(EntityType.PIGLIN, EntityType.PIGLIN_BRUTE)) {
                var piglin = f.track(type.create(h.getLevel()));
                piglin.setNoAi(true);
                piglin.setCanPickUpLoot(true);
                ItemStack leather = quality(new ItemStack(Items.LEATHER_CHESTPLATE), Quality.EXCELLENT);
                ItemEntity belowGold = f.item(leather, 0, 0, 0);
                piglin.aiStep();
                h.assertTrue(belowGold.isAlive() && piglin.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                                && piglin.getMainHandItem().isEmpty() && piglin.equipItemIfPossible(leather.copy()).isEmpty(),
                        "Armor below gold stays on the ground even when the armor and mainhand slots are empty for " + type);
                belowGold.discard();

                ItemStack gold = quality(new ItemStack(Items.GOLDEN_CHESTPLATE), Quality.AVERAGE);
                ItemStack iron = quality(new ItemStack(Items.IRON_CHESTPLATE), Quality.AVERAGE);
                piglin.setItemSlot(EquipmentSlot.CHEST, gold);
                ItemEntity better = f.item(iron.copy(), 0, 0, 0);
                piglin.aiStep();
                h.assertTrue(better.isRemoved() && ItemStack.matches(piglin.getItemBySlot(EquipmentSlot.CHEST), iron),
                        "Actual native pickup replaces gold with higher-defense iron at equal quality for " + type);

                if (type == EntityType.PIGLIN) {
                    ItemEntity coin = f.item(new ItemStack(Items.GOLD_INGOT), 0, 0, 0);
                    piglin.aiStep();
                    h.assertTrue(coin.isRemoved() && piglin.getOffhandItem().is(Items.GOLD_INGOT)
                                    && piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_ITEM),
                            "The normal piglin still picks up and admires barter currency");
                }
            }
        });
    }

    private static void ready(GameTestHelper h, Consumer<Fixture> test) {
        Fixture f = new Fixture(h);
        h.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { f.close(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { f.close(); }
        });
        h.startSequence().thenWaitUntil(() -> {
            if (f.chunks.stream().anyMatch(chunk -> !h.getLevel().isPositionEntityTicking(chunk.getWorldPosition())))
                for (int tasks = 0; tasks < 64 && h.getLevel().getChunkSource().pollTask(); ++tasks) {}
            h.assertTrue(f.chunks.stream().allMatch(chunk -> h.getLevel().isPositionEntityTicking(chunk.getWorldPosition())),
                    "Every equipment fixture chunk reaches native entity-ticking status");
            h.assertTrue(h.getLevel().getEntity(f.mob.getUUID()) == f.mob,
                    "The actual monster is indexed in the isolated prepared level before testing item searches");
        }).thenExecute(() -> {
            f.mob.setNoAi(false); f.mob.setNoGravity(false); f.mob.setOnGround(true); f.mob.setDeltaMovement(0D, -.08D, 0D);
            try { test.accept(f); } finally { f.close(); }
        }).thenSucceed();
    }

    private static ItemStack sword(Quality quality) { return quality(new ItemStack(Items.IRON_SWORD), quality); }
    private static ItemStack quality(ItemStack stack, Quality quality) { EquipmentDurability.setQuality(stack, quality); return stack; }

    /** Each test owns a roofed floor and native chunk tickets; server time and weather remain untouched. */
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos origin;
        final Zombie mob;
        final List<Entity> entities = new ArrayList<>();
        final List<ChunkPos> chunks = new ArrayList<>();
        final int ticketKey;
        boolean closed;
        Fixture(GameTestHelper h) {
            helper = h;
            ticketKey = IDS.getAndIncrement();
            origin = new BlockPos(320_000 + ticketKey * 64, h.absolutePos(BlockPos.ZERO).getY() + 8, 320_000);
            ChunkPos low = new ChunkPos(origin.offset(-6, 0, -5)), high = new ChunkPos(origin.offset(13, 0, 13));
            for (int x = low.x; x <= high.x; ++x) for (int z = low.z; z <= high.z; ++z) {
                ChunkPos chunk = new ChunkPos(x, z); chunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticketKey);
                for (int cx = x - 2; cx <= x + 2; ++cx) for (int cz = z - 2; cz <= z + 2; ++cz)
                    h.getLevel().getChunk(cx, cz, ChunkStatus.FULL, true);
            }
            for (int x = -6; x <= 8; ++x) for (int z = -5; z <= 5; ++z) {
                h.getLevel().setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
                for (int y = 0; y < 3; ++y) h.getLevel().setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                h.getLevel().setBlock(origin.offset(x, 3, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
            mob = track(EntityType.ZOMBIE.create(h.getLevel()));
            mob.setNoAi(true); mob.setNoGravity(true); mob.setPersistenceRequired(); mob.setOnGround(true);
            mob.setCanPickUpLoot(false);
            h.getLevel().addFreshEntity(mob);
            MonsterEquipmentScavenging.install(mob);
        }
        <T extends Mob> T track(T entity) {
            entity.moveTo(origin.getX() + .5D, origin.getY(), origin.getZ() + .5D, 0F, 0F);
            entities.add(entity); return entity;
        }
        ItemEntity item(ItemStack stack, double x, double y, double z) {
            ItemEntity item = new ItemEntity(helper.getLevel(), origin.getX() + .5D + x, origin.getY() + y, origin.getZ() + .5D + z, stack);
            item.setNoPickUpDelay(); item.setNoGravity(true); item.setDeltaMovement(0D, 0D, 0D);
            entities.add(item);
            helper.assertTrue(helper.getLevel().addFreshEntity(item), "The real equipment item joins normally");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, item.getBoundingBox().inflate(.1D), e -> e == item).contains(item),
                    "The native item query can see each candidate before pursuit and rejection assertions");
            return item;
        }
        @Override public void close() {
            if (closed) return;
            closed = true; mob.getNavigation().stop(); entities.forEach(Entity::discard);
            for (ChunkPos chunk : chunks) helper.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticketKey);
        }
    }
}

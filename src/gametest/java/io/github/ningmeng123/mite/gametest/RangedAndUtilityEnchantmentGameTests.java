package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.ranged.ArrowRecovery;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteWeaponContent;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.NutritionState;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Source arithmetic exercised through actual bow releases, collisions, native harvesting and food ticking. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class RangedAndUtilityEnchantmentGameTests {
    private static Player player(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 12, 2))));
        player.getInventory().clearContent();
        return player;
    }
    private static ItemStack enchanted(GameTestHelper h, Item item, String name, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(R196Enchantments.key(name)), level);
        return stack;
    }
    private static Arrow arrow(GameTestHelper h, Player owner, ItemStack bow) {
        Arrow arrow = (Arrow)((ArrowItem)Items.ARROW).createArrow(h.getLevel(), new ItemStack(Items.ARROW), owner, bow);
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        arrow.setDeltaMovement(3D, 0D, 0D);
        arrow.setCritArrow(false);
        return arrow;
    }
    private static void hit(Arrow arrow, LivingEntity target) {
        try {
            Method method = AbstractArrow.class.getDeclaredMethod("onHitEntity", EntityHitResult.class);
            method.setAccessible(true); method.invoke(arrow, new EntityHitResult(target));
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native arrow impact failed", failure); }
    }
    private static Cow cow(GameTestHelper h, float health) {
        Cow cow = EntityType.COW.create(h.getLevel());
        cow.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(4, 12, 2))));
        cow.setNoAi(true); cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D); cow.setHealth(health);
        h.getLevel().addFreshEntity(cow);
        return cow;
    }
    private static void close(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < .0001D, message + ": " + actual + " vs " + expected);
    }

    @GameTest(template = "empty")
    public static void quicknessFiveProducesFullCriticalShotsAfterTenActualUseTicks(GameTestHelper h) {
        Player player = player(h);
        for (Item item : List.of(Items.BOW, MiteWeaponContent.ANCIENT_METAL_BOW.get(), MiteWeaponContent.MITHRIL_BOW.get())) {
            for (boolean quick : new boolean[]{false, true}) {
                ItemStack bow = quick ? enchanted(h, item, "quickness", 5) : new ItemStack(item);
                ItemStack ammo = new ItemStack(MiteRangedItems.arrow(Material.FLINT), 2);
                ammo.set(DataComponents.CUSTOM_NAME, Component.literal("quickness-" + UUID.randomUUID()));
                player.setItemInHand(InteractionHand.MAIN_HAND, bow);
                player.getInventory().setItem(1, ammo);
                try (var spawned = new SpawnedEntityCapture(h.getLevel(), entity -> entity instanceof Arrow a
                        && a.getBoundingBox().intersects(player.getBoundingBox().inflate(5))
                        && ItemStack.isSameItemSameComponents(a.getPickupItemStackOrigin(), ammo))) {
                    player.startUsingItem(InteractionHand.MAIN_HAND);
                    ((BowItem)item).releaseUsing(bow, h.getLevel(), player, bow.getUseDuration(player) - 10);
                    player.stopUsingItem();
                    var fired = spawned.live(Arrow.class);
                    h.assertTrue(fired.size() == 1 && ammo.getCount() == 1, "Actual survival release creates and consumes exactly one arrow");
                    h.assertTrue(fired.getFirst().isAddedToLevel(), "The captured native arrow was accepted by the real server level");
                    h.assertTrue(fired.getFirst().isCritArrow() == quick, "Ten ticks is full draw only with QuicknessV, including reinforced bows");
                    h.assertTrue(bow.getDamageValue() == 1, "Quickness changes pull time without adding or removing firing wear");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeFireAspectMeleeRetainsSourceExtraTenIgnitionTicks(GameTestHelper h) {
        Player attacker = player(h);
        for (int enchantmentLevel = 1; enchantmentLevel <= 2; enchantmentLevel++) {
            Cow target = cow(h, 200F);
            attacker.setItemInHand(InteractionHand.MAIN_HAND,
                    enchanted(h, Items.IRON_SWORD, "minecraft:fire_aspect", enchantmentLevel));
            attacker.attack(target);
            h.assertTrue(target.getHealth() < 200F, "FireAspect fixture reaches a real successful native player melee attack");
            h.assertTrue(target.getRemainingFireTicks() == (enchantmentLevel == 1 ? 90 : 170),
                    "Source ignition includes ten extra ticks: FireAspectI=90, II=170");
            target.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeFlameBowSpawnsTwoThousandTenTickArrowAndIgnitesVictimForOneHundredTen(GameTestHelper h) {
        Player shooter = player(h);
        ItemStack bow = enchanted(h, Items.BOW, "minecraft:flame", 1);
        ItemStack ammo = new ItemStack(Items.ARROW, 2);
        ammo.set(DataComponents.CUSTOM_NAME, Component.literal("flame-" + UUID.randomUUID()));
        shooter.setItemInHand(InteractionHand.MAIN_HAND, bow);
        shooter.getInventory().setItem(1, ammo);
        try (var spawned = new SpawnedEntityCapture(h.getLevel(), entity -> entity instanceof Arrow arrow
                && arrow.getBoundingBox().intersects(shooter.getBoundingBox().inflate(5))
                && ItemStack.isSameItemSameComponents(arrow.getPickupItemStackOrigin(), ammo))) {
            shooter.startUsingItem(InteractionHand.MAIN_HAND);
            ((BowItem)Items.BOW).releaseUsing(bow, h.getLevel(), shooter, bow.getUseDuration(shooter) - 20);
            shooter.stopUsingItem();
            var arrows = spawned.live(Arrow.class);
            h.assertTrue(arrows.size() == 1 && ammo.getCount() == 1, "The actual survival Flame bow creates and pays for its native arrow");
            Arrow arrow = arrows.getFirst();
            h.assertTrue(arrow.isAddedToLevel(), "The captured native Flame arrow was accepted by the real server level");
            h.assertTrue(arrow.getRemainingFireTicks() == 2010, "Flame source projectile ignition is100seconds plus ten source ticks");
            Cow target = cow(h, 200F);
            hit(arrow, target);
            h.assertTrue(target.getHealth() < 200F, "The actual spawned Flame projectile collides and damages the unarmored target");
            h.assertTrue(target.getRemainingFireTicks() == 110, "A burning bow arrow transfers five seconds plus ten source ignition ticks");
            target.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void poisonFiveAppliesFourHundredTicksOnNativeArrowCollisionButRejectsNonbiologicalTargets(GameTestHelper h) {
        Player player = player(h);
        ItemStack bow = enchanted(h, Items.BOW, "poison", 5);
        Cow cow = cow(h, 200F);
        hit(arrow(h, player, bow), cow);
        var effect = cow.getEffect(MobEffects.POISON);
        h.assertTrue(effect != null && effect.getDuration() == 400 && effect.getAmplifier() == 0, "PoisonV is certain and applies sourcePoisonI for400ticks on a real arrow hit");
        for (LivingEntity target : List.of(EntityType.ZOMBIE.create(h.getLevel()), EntityType.ENDERMAN.create(h.getLevel()), EntityType.IRON_GOLEM.create(h.getLevel()))) {
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D); target.setHealth(200F);
            hit(arrow(h, player, bow), target);
            h.assertTrue(!target.hasEffect(MobEffects.POISON), "Undead, endermen and constructs reject sourcePoison");
        }
        cow.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void arrowRecoveryFiveReturnsEveryFlintArrowAndContainedInventorySurvivesReload(GameTestHelper h) {
        Player player = player(h);
        ItemStack bow = enchanted(h, Items.BOW, "arrow_recovery", 5);
        Cow cow = cow(h, 200F);
        for (int shot = 0; shot < 12; shot++) {
            cow.invulnerableTime = 0;
            hit(arrow(h, player, bow), cow);
        }
        h.assertTrue(cow.getData(ArrowRecovery.CONTAINED).size() == 12, "RecoveryV returns all flint arrows rather than their unenchanted30percent chance");
        CompoundTag saved = cow.saveWithoutId(new CompoundTag());
        Cow loaded = EntityType.COW.create(h.getLevel()); loaded.load(saved);
        h.assertTrue(loaded.getData(ArrowRecovery.CONTAINED).size() == 12, "Guaranteed source recovery remains an entity attachment across saving");
        cow.discard(); loaded.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void fatalRecoveryFiveJoinsTheActualCancellableDeathDropCollection(GameTestHelper h) {
        Player player = player(h);
        ItemStack bow = enchanted(h, Items.BOW, "arrow_recovery", 5);
        for (boolean cancel : new boolean[]{false, true}) {
            Cow target = cow(h, 1F);
            int[] returned = {0};
            Consumer<LivingDropsEvent> listener = event -> {
                if (event.getEntity() != target) return;
                returned[0] = event.getDrops().stream().filter(drop -> drop.getItem().is(Items.ARROW))
                        .mapToInt(drop -> drop.getItem().getCount()).sum();
                if (cancel) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDropsEvent.class, listener);
            try { hit(arrow(h, player, bow), target); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
            h.assertTrue(returned[0] == 1, "The fatal arrow is returned exactly once inside the real death event");
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, target.getBoundingBox().inflate(3));
            int count = drops.stream().filter(drop -> drop.getItem().is(Items.ARROW)).mapToInt(drop -> drop.getItem().getCount()).sum();
            h.assertTrue(count == (cancel ? 0 : 1), "Death-drop cancellation also cancels guaranteed arrow recovery");
            h.assertTrue(!target.hasData(ArrowRecovery.CONTAINED), "Fatal arrow cannot remain queued for a second drop");
            drops.forEach(ItemEntity::discard); target.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void treeFellingThreeUsesTheNativeBreakPathForOnlyThreeAdditionalVerticalLogs(GameTestHelper h) {
        try (Session session = serverPlayer(h); BlockFixture fixture = new BlockFixture(h)) {
            var player = session.player;
            BlockPos base = h.absolutePos(new BlockPos(4, 12, 2));
            BlockPos control = base.offset(0, 0, 4);
            fixture.put(control, Blocks.OAK_LOG.defaultBlockState());
            ItemStack axe = new ItemStack(MiteTools.get("copper_axe"));
            player.setItemInHand(InteractionHand.MAIN_HAND, axe);
            player.setPos(Vec3.atCenterOf(control).add(0D,0D,1D));
            h.assertTrue(player.canInteractWithBlock(control,1D),"Control fixture stands inside actual source block reach");
            h.assertTrue(player.gameMode.destroyBlock(control), "Control harvest is a real ordinary source axe break");
            int oneWear = axe.getDamageValue();
            for (int dy = 0; dy < 6; dy++) fixture.put(base.above(dy), Blocks.OAK_LOG.defaultBlockState());
            fixture.put(base.offset(1, 1, 0), Blocks.OAK_LOG.defaultBlockState());
            axe = enchanted(h, MiteTools.get("copper_axe"), "tree_felling", 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, axe);
            player.setPos(Vec3.atCenterOf(base).add(0D,-2D,1D));
            h.assertTrue(player.canInteractWithBlock(base,1D),"The primary felling log is inside real source reach");
            h.assertFalse(player.canInteractWithBlock(base.above(3),1D),"The highest authorized extra log genuinely needs internal felling reach");
            h.assertTrue(player.gameMode.destroyBlock(base), "Native server harvest accepts the enchanted axe");
            for (int dy = 0; dy < 4; dy++) h.assertTrue(h.getLevel().getBlockState(base.above(dy)).isAir(), "The primary log plus three directly above are removed");
            h.assertTrue(h.getLevel().getBlockState(base.above(4)).is(Blocks.OAK_LOG)
                    && h.getLevel().getBlockState(base.offset(1, 1, 0)).is(Blocks.OAK_LOG), "Height limit and side branches are preserved");
            h.assertTrue(oneWear > 0 && axe.getDamageValue() == oneWear * 4, "Each extra native break spends its own source axe wear");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void treeFellingStopsAtTheFirstCanceledExtraBreakWithoutConsumingItsWear(GameTestHelper h) {
        try (Session session = serverPlayer(h); BlockFixture fixture = new BlockFixture(h)) {
            var player = session.player;
            BlockPos base = h.absolutePos(new BlockPos(4, 12, 2));
            for (int dy = 0; dy < 5; dy++) fixture.put(base.above(dy), Blocks.OAK_LOG.defaultBlockState());
            ItemStack axe = enchanted(h, MiteTools.get("copper_axe"), "tree_felling", 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, axe);
            player.setPos(Vec3.atCenterOf(base).add(0D,-4D,1D));
            h.assertTrue(player.canInteractWithBlock(base,1D),"Primary log is reached normally");
            h.assertFalse(player.canInteractWithBlock(base.above(),1D),"The first extra requires its precise internal authorization");
            int[] attempts={0};
            Consumer<BlockEvent.BreakEvent> deny = event -> {
                if (event.getPlayer() == player && event.getPos().equals(base.above())) {
                    attempts[0]++;
                    h.assertFalse(((io.github.ningmeng123.mite.enchantment.TreeFellingHarvestAccess)player.gameMode)
                            .mite$consumeExtraLogReach(event.getPos()),"One-use authorization was consumed before cancellable protection callbacks");
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BlockEvent.BreakEvent.class, deny);
            try { h.assertTrue(player.gameMode.destroyBlock(base), "The primary log still breaks"); }
            finally { NeoForge.EVENT_BUS.unregister(deny); }
            h.assertValueEqual(attempts[0],1,"The extra reaches the actual cancellable native break event once");
            h.assertFalse(player.gameMode.destroyBlock(base.above()),"Canceled internal felling cannot leak its reach into a later manual destroyBlock");
            for (int dy = 1; dy < 5; dy++) h.assertTrue(h.getLevel().getBlockState(base.above(dy)).is(Blocks.OAK_LOG), "Cancellation stops all further source felling");
            BlockPos control = base.offset(0, 0, 4);
            fixture.put(control, Blocks.OAK_LOG.defaultBlockState());
            ItemStack plain = new ItemStack(MiteTools.get("copper_axe")); player.setItemInHand(InteractionHand.MAIN_HAND, plain);
            player.setPos(Vec3.atCenterOf(control).add(0D,0D,1D));
            h.assertTrue(player.canInteractWithBlock(control,1D),"Plain wear comparison is performed inside actual reach");
            h.assertTrue(player.gameMode.destroyBlock(control),"Control comparison is an actual successful native break");
            h.assertTrue(axe.getDamageValue() == plain.getDamageValue(), "The canceled extra break consumes no durability");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void regenerationUsesMaximumArmorLevelInTheActualFoodProgressAndKeepsHealingCost(GameTestHelper h) {
        Player player = player(h);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(6D); player.setHealth(4F);
        player.setItemSlot(EquipmentSlot.CHEST, enchanted(h, Items.IRON_CHESTPLATE, "regeneration", 5));
        player.setItemSlot(EquipmentSlot.HEAD, enchanted(h, Items.IRON_HELMET, "regeneration", 2));
        NutritionState.set(player, NutritionState.INITIAL);
        player.getFoodData().setFoodLevel(6); player.getFoodData().setSaturation(0F);
        ((DualFoodData)player.getFoodData()).mite$setNutrition(6);
        var natural = h.getLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION);
        boolean previous = natural.get();
        try {
            natural.set(true, h.getLevel().getServer());
            for (int tick = 0; tick < 100; tick++) player.getFoodData().tick(player);
            CompoundTag saved = player.saveWithoutId(new CompoundTag());
            close(h, saved.getFloat("mite_heal_progress"), .182D, "Nutrition6 and highestV accumulate100source increments of.00182");
            h.assertTrue(player.getHealth() == 4F, "The enchantment does not grant an independent instant heal");
            saved.putFloat("mite_heal_progress", .999F);
            player.getFoodData().readAdditionalSaveData(saved);
            float exhaustion = player.getFoodData().getExhaustionLevel();
            player.getFoodData().tick(player);
            h.assertTrue(player.getHealth() == 5F, "Native source heal progress crossing one heals exactly one point");
            close(h, player.getFoodData().getExhaustionLevel() - exhaustion, 1.1523D, "Regeneration charges 1.15 exhaustion plus this tick's 0.0023 metabolism");
        } finally { natural.set(previous, h.getLevel().getServer()); }
        h.succeed();
    }

    private static void armor(LivingEntity target) {
        target.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        target.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    }
    @GameTest(template = "empty")
    public static void piercingFiveSubtractsFiveModernArmorForPlayersWithoutAddingUnarmoredDamage(GameTestHelper h) {
        Player attacker = player(h), target = player(h);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
        float[] losses = new float[2];
        for (int index = 0; index < 2; index++) {
            armor(target); target.tick(); target.setHealth(100F); target.invulnerableTime = 0;
            close(h, target.getAttributeValue(Attributes.ARMOR), 18D, "Real native equipment refresh installs modern full iron armor");
            attacker.setItemInHand(InteractionHand.MAIN_HAND, index == 0 ? new ItemStack(Items.IRON_PICKAXE)
                    : enchanted(h, Items.IRON_PICKAXE, "piercing", 5));
            target.hurt(attacker.damageSources().playerAttack(attacker), 14F);
            losses[index] = 100F - target.getHealth();
        }
        close(h, losses[0], 7.056D, "Raw14 versus iron18/toughness2 uses effective armor12.4");
        close(h, losses[1], 9.856D, "PiercingV makes armor13, effective armor7.4 while retaining toughness");
        var cow = cow(h, 200F);
        cow.hurt(attacker.damageSources().playerAttack(attacker), 10F);
        close(h, 200F - cow.getHealth(), 10D, "Unarmored targets do not receive additional raw damage from Piercing");
        cow.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void piercingAlsoReachesManagedNativeMobArmorDefense(GameTestHelper h) {
        Player attacker = player(h);
        var target = EntityType.ZOMBIE.create(h.getLevel());
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D); target.setHealth(200F); target.setNoAi(true);
        armor(target);
        target.tick();
        close(h, target.getAttributeValue(Attributes.ARMOR), 18D, "Managed native mob installs real iron armor eighteen");
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        target.hurt(attacker.damageSources().playerAttack(attacker), 10F);
        float plain = 200F - target.getHealth();
        armor(target); target.tick(); target.setHealth(200F); target.invulnerableTime = 0;
        attacker.setItemInHand(InteractionHand.MAIN_HAND, enchanted(h, Items.IRON_PICKAXE, "piercing", 5));
        target.hurt(attacker.damageSources().playerAttack(attacker), 10F);
        close(h, plain, 4.4D, "Raw10 versus iron18/toughness2 gives effective armor14");
        close(h, 200F - target.getHealth(), 6.4D, "PiercingV makes armor13 and retains toughness2 without adding raw damage");
        target.discard(); h.succeed();
    }

    private static Session serverPlayer(GameTestHelper h) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "enchantment-test"), false);
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true); player.setOnGround(true);
            player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 12, 2))));
            player.getInventory().clearContent();
            return new Session(player, channel);
        } catch (RuntimeException | Error failure) {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll(); throw failure;
        }
    }
    private record Session(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            try { if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player); }
            finally { channel.finishAndReleaseAll(); }
        }
    }
    private static final class BlockFixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        private BlockFixture(GameTestHelper helper) { this.helper = helper; }
        private void put(BlockPos pos, BlockState state) {
            originals.putIfAbsent(pos, helper.getLevel().getBlockState(pos));
            helper.getLevel().setBlock(pos, state, 3);
        }
        @Override public void close() {
            originals.forEach((pos, state) -> helper.getLevel().setBlock(pos, state, 3));
            if (!originals.isEmpty()) {
                var area = new net.minecraft.world.phys.AABB(originals.keySet().iterator().next()).inflate(10);
                helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, item -> item.getItem().is(Items.OAK_LOG)).forEach(ItemEntity::discard);
            }
        }
    }
}

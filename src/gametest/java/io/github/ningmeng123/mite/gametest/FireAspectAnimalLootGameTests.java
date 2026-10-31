package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.SourceVanillaMobLoot;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real attacks distinguish lethal Fire Aspect from fire applied only after death. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FireAspectAnimalLootGameTests {
    private record Animal(EntityType<? extends Mob> type, Item raw, Item cooked) {}
    private static final List<Animal> ANIMALS = List.of(
            new Animal(EntityType.COW, Items.BEEF, Items.COOKED_BEEF),
            new Animal(EntityType.MOOSHROOM, Items.BEEF, Items.COOKED_BEEF),
            new Animal(EntityType.PIG, Items.PORKCHOP, Items.COOKED_PORKCHOP),
            new Animal(EntityType.SHEEP, Items.MUTTON, Items.COOKED_MUTTON),
            new Animal(EntityType.CHICKEN, Items.CHICKEN, Items.COOKED_CHICKEN),
            new Animal(EntityType.HORSE, Items.BEEF, Items.COOKED_BEEF),
            new Animal(EntityType.DONKEY, Items.BEEF, Items.COOKED_BEEF),
            new Animal(EntityType.MULE, Items.BEEF, Items.COOKED_BEEF),
            new Animal(EntityType.RABBIT, Items.RABBIT, Items.COOKED_RABBIT));

    private static Player player(GameTestHelper h, int fireAspect) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(h.absolutePos(new BlockPos(2, 3, 2)).getCenter());
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20D);
        player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024D);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword(h, fireAspect));
        return player;
    }

    private static ItemStack sword(GameTestHelper h, int fireAspect) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        if (fireAspect > 0) sword.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.FIRE_ASPECT), fireAspect);
        return sword;
    }

    private static Mob animal(GameTestHelper h, Animal animal) {
        Mob mob = animal.type.create(h.getLevel());
        mob.moveTo(h.absolutePos(new BlockPos(4, 3, 2)).getCenter());
        mob.setNoAi(true);
        mob.setNoGravity(true);
        if (mob instanceof AgeableMob ageable) ageable.setAge(0);
        mob.setHealth(1F);
        h.getLevel().addFreshEntity(mob);
        SourceVanillaMobLoot.setLivestockNeeds(mob, 1F, 1F, 1F);
        mob.getRandom().setSeed(196L);
        h.assertTrue(!mob.isBaby() && !mob.isOnFire(), "Fixture is an adult animal not yet burning");
        return mob;
    }

    private static List<ItemStack> death(GameTestHelper h, Mob mob, Runnable attack, boolean cancelDrops) {
        List<ItemStack> drops = new ArrayList<>();
        int[] events = {0};
        Consumer<LivingDropsEvent> listener = event -> {
            if (event.getEntity() != mob) return;
            events[0]++;
            event.getDrops().forEach(drop -> drops.add(drop.getItem().copy()));
            if (cancelDrops) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDropsEvent.class, listener);
        try (var world = new SpawnedEntityCapture(h.getLevel(), entity -> entity instanceof ItemEntity)) {
            attack.run();
            h.assertTrue(!mob.isAlive() && events[0] == 1, "A real lethal hit emits exactly one LivingDrops transaction");
            int worldCount = world.live(ItemEntity.class).stream().mapToInt(item -> item.getItem().getCount()).sum();
            int eventCount = drops.stream().mapToInt(ItemStack::getCount).sum();
            h.assertTrue(worldCount == (cancelDrops ? 0 : eventCount),
                    "Cancellation controls every source drop; no late cooked item bypasses the transaction");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            mob.discard();
        }
        return drops;
    }

    private static int count(List<ItemStack> drops, Item item) {
        return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void meat(GameTestHelper h, Animal animal, List<ItemStack> drops, boolean cooked) {
        h.assertTrue(count(drops, cooked ? animal.cooked : animal.raw) > 0
                        && count(drops, cooked ? animal.raw : animal.cooked) == 0,
                animal.type + " drops only the expected " + (cooked ? "cooked" : "raw") + " meat");
        if (animal.type == EntityType.CHICKEN)
            h.assertValueEqual(count(drops, cooked ? animal.cooked : animal.raw), 1,
                    "Chicken drops exactly one meat for real melee, fire, arrow and indirect death paths");
        if (cooked && animal.type == EntityType.CHICKEN)
            h.assertTrue(count(drops, Items.FEATHER) == 0, "The source burning chicken branch does not also emit feathers");
        if (cooked && animal.type == EntityType.SHEEP)
            h.assertTrue(count(drops, Items.WHITE_WOOL) == 0, "The source burning sheep branch does not also emit wool");
    }

    @GameTest(template = "empty")
    public static void lethalFireAspectOneAndTwoCookAllEightPreviouslyColdAnimalTypes(GameTestHelper h) {
        for (int level : new int[]{1, 2}) for (Animal type : ANIMALS) {
            Player attacker = player(h, level);
            Mob mob = animal(h, type);
            meat(h, type, death(h, mob, () -> attacker.attack(mob), false), true);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void plainSwordLethalHitKeepsAllEightAnimalMeatsRaw(GameTestHelper h) {
        for (Animal type : ANIMALS) {
            Player attacker = player(h, 0);
            Mob mob = animal(h, type);
            meat(h, type, death(h, mob, () -> attacker.attack(mob), false), false);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void earlierNonlethalFireAspectAndEnvironmentalBurningStillCookNormally(GameTestHelper h) {
        for (Animal type : ANIMALS) {
            Player attacker = player(h, 1);
            Mob mob = animal(h, type);
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
            mob.setHealth(200F);
            attacker.attack(mob);
            h.assertTrue(mob.isAlive() && mob.isOnFire(), "Nonlethal Fire Aspect actually ignites the target");
            mob.setHealth(1F);
            mob.invulnerableTime = 0;
            attacker.setItemInHand(InteractionHand.MAIN_HAND, sword(h, 0));
            meat(h, type, death(h, mob, () -> attacker.attack(mob), false), true);

            Mob burning = animal(h, type);
            burning.igniteForSeconds(10F);
            burning.setLastHurtByPlayer(attacker);
            meat(h, type, death(h, burning, () -> burning.hurt(burning.damageSources().onFire(), 100F), false), true);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledFireAspectDeathDropsNeverLeakCookedMeatIntoWorld(GameTestHelper h) {
        for (Animal type : ANIMALS) {
            Player attacker = player(h, 2);
            Mob mob = animal(h, type);
            meat(h, type, death(h, mob, () -> attacker.attack(mob), true), true);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectedAndCanceledFireAspectHitsDoNotProduceDeathLoot(GameTestHelper h) {
        Player attacker = player(h, 1);
        for (boolean immune : new boolean[]{false, true}) {
            Mob mob = animal(h, ANIMALS.getFirst());
            mob.setInvulnerable(immune);
            int[] drops = {0};
            Consumer<LivingDropsEvent> capture = event -> { if (event.getEntity() == mob) drops[0]++; };
            Consumer<LivingIncomingDamageEvent> cancel = event -> { if (event.getEntity() == mob && !immune) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(LivingDropsEvent.class, capture);
            NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, cancel);
            try (var world = new SpawnedEntityCapture(h.getLevel(), entity -> entity instanceof ItemEntity)) {
                attacker.attack(mob);
                h.assertTrue(mob.isAlive() && mob.getHealth() == 1F && drops[0] == 0 && world.live(ItemEntity.class).isEmpty(),
                        "Rejected damage cannot create a death transaction or raw/cooked drops");
            } finally {
                NeoForge.EVENT_BUS.unregister(capture);
                NeoForge.EVENT_BUS.unregister(cancel);
                mob.discard();
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fireAspectInShootersHandDoesNotCookUnlitArrowsOrIndirectEnvironmentalKills(GameTestHelper h) {
        Player attacker = player(h, 2);
        for (Animal type : ANIMALS) {
            Mob arrowTarget = animal(h, type);
            Arrow arrow = (Arrow)((ArrowItem)Items.ARROW).createArrow(h.getLevel(), new ItemStack(Items.ARROW), attacker, new ItemStack(Items.BOW));
            arrow.setDeltaMovement(3D, 0D, 0D);
            h.assertTrue(!arrow.isOnFire(), "The actual projectile is not flaming");
            meat(h, type, death(h, arrowTarget, () -> hit(arrow, arrowTarget), false), false);
            arrow.discard();

            Mob indirect = animal(h, type);
            indirect.setLastHurtByPlayer(attacker);
            DamageSource attributedEnvironment = new DamageSource(indirect.damageSources().generic().typeHolder(), null, attacker);
            meat(h, type, death(h, indirect, () -> indirect.hurt(attributedEnvironment, 100F), false), false);
        }
        h.succeed();
    }

    private static void hit(Arrow arrow, Mob target) {
        try {
            Method hit = AbstractArrow.class.getDeclaredMethod("onHitEntity", EntityHitResult.class);
            hit.setAccessible(true);
            hit.invoke(arrow, new EntityHitResult(target));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Native unlit arrow hit failed", error);
        }
    }
}

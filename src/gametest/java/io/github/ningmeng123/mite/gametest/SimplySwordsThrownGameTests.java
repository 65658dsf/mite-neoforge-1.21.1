package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Optional real projectile hits, native pickup and persisted weapon data without a compile-time SS dependency. @author NingMeng123 */
@GameTestHolder("mite_simplyswords")
@PrefixGameTestTemplate(false)
public final class SimplySwordsThrownGameTests {
    private static final String SPEAR = "net.sweenus.simplyswords.entity.ThrownSpearEntity";
    private SimplySwordsThrownGameTests() {}

    private static Player owner(GameTestHelper h, GameType mode) {
        Player player = h.makeMockPlayer(mode);
        player.setPos(h.absolutePos(new BlockPos(1, 3, 1)).getCenter()); player.setNoGravity(true);
        return player;
    }

    private static Mob target(GameTestHelper h) {
        // Simply Swords deliberately ignores the entire "passive" category in its default ability config.
        // A hostile target exercises its actual approved-damage branch without changing that user setting.
        Mob target = EntityType.ZOMBIE.create(h.getLevel());
        target.setNoAi(true); target.setNoGravity(true);
        target.moveTo(h.absolutePos(new BlockPos(3, 3, 3)).getCenter());
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D); target.setHealth(200F);
        return target;
    }

    private static AbstractArrow spear(GameTestHelper h, Player owner, ItemStack stack) throws ReflectiveOperationException {
        Class<?> type = Class.forName(SPEAR);
        AbstractArrow arrow = (AbstractArrow)type.getConstructor(Level.class, LivingEntity.class, ItemStack.class)
                .newInstance(h.getLevel(), owner, stack);
        type.getField("primaryBaseDamage").setFloat(arrow, 4F);
        arrow.setNoGravity(true); return arrow;
    }

    private static void hit(AbstractArrow spear, LivingEntity target) throws ReflectiveOperationException {
        Method hit = Class.forName(SPEAR).getDeclaredMethod("onHitEntity", EntityHitResult.class);
        hit.setAccessible(true); hit.invoke(spear, new EntityHitResult(target));
    }

    private static boolean pickup(AbstractArrow spear, Player player) throws ReflectiveOperationException {
        spear.setNoPhysics(true); spear.pickup = AbstractArrow.Pickup.ALLOWED;
        Method pickup = Class.forName(SPEAR).getDeclaredMethod("tryPickup", Player.class);
        pickup.setAccessible(true); return (boolean)pickup.invoke(spear, player);
    }

    @GameTest(template = "empty")
    public static void actualHitsWearTheRealSpearAndNativePickupKeepsFiftyPoints(GameTestHelper h) throws ReflectiveOperationException {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        for (String material : List.of("iron", "copper", "rusted_iron", "vibranium")) {
            Player owner = owner(h, GameType.SURVIVAL); Mob target = target(h);
            ItemStack stack = new ItemStack(SimplySwordsCompat.weapon(material, "spear")); stack.setDamageValue(7);
            AbstractArrow spear = spear(h, owner, stack); float health = target.getHealth();
            hit(spear, target);
            h.assertTrue(target.getHealth() < health, "The original projectile damage really landed");
            h.assertValueEqual(stack.getDamageValue(), 57, "One successful native hit spends the MITE sword budget of fifty points");
            h.assertTrue(ItemStack.matches(stack, spear.getPickupItemStackOrigin()), "The SS synchronized weapon matches the wounded real stack");
            h.assertTrue(pickup(spear, owner), "The original SS/AbstractArrow pickup path succeeds");
            h.assertTrue(owner.getInventory().items.stream().anyMatch(item -> ItemStack.matches(item, stack)), "The recovered spear retains all fifty points of wear");
            spear.discard(); target.discard(); owner.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void missedOrRejectedHitsSpendNothingAndDuplicateTargetsDoNotWearTwice(GameTestHelper h) throws ReflectiveOperationException {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        Player owner = owner(h, GameType.SURVIVAL); Mob target = target(h);
        ItemStack stack = new ItemStack(SimplySwordsCompat.weapon("copper", "spear"));
        AbstractArrow spear = spear(h, owner, stack);
        spear.setPos(h.absolutePos(new BlockPos(1, 12, 1)).getCenter()); spear.setDeltaMovement(Vec3.ZERO); spear.tick();
        h.assertValueEqual(stack.getDamageValue(), 0, "An airborne spear that misses does not wear every tick");
        target.setInvulnerable(true); hit(spear, target);
        h.assertValueEqual(stack.getDamageValue(), 0, "Rejected damage never charges weapon wear");
        target.setInvulnerable(false); hit(spear, target);
        h.assertValueEqual(stack.getDamageValue(), 50, "The subsequent first approved target is charged once");
        target.invulnerableTime = 0; hit(spear, target);
        h.assertValueEqual(stack.getDamageValue(), 50, "The source pierced-target guard prevents charging the same victim twice");
        spear.discard(); target.discard(); owner.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void hitWearSurvivesProjectileNbtAndTheOriginalPickupPath(GameTestHelper h) throws ReflectiveOperationException {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        Player owner = owner(h, GameType.SURVIVAL); Mob target = target(h);
        ItemStack stack = new ItemStack(SimplySwordsCompat.weapon("mithril", "spear")); stack.setDamageValue(11);
        AbstractArrow spear = spear(h, owner, stack); hit(spear, target);
        CompoundTag saved = spear.saveWithoutId(new CompoundTag());
        AbstractArrow restored = (AbstractArrow)spear.getType().create(h.getLevel()); restored.load(saved); restored.setOwner(owner);
        h.assertValueEqual(restored.getPickupItemStackOrigin().getDamageValue(), 61, "Reloading restores the worn SS synchronized spear stack");
        h.assertTrue(pickup(restored, owner), "A reloaded projectile still uses native pickup");
        h.assertTrue(owner.getInventory().items.stream().anyMatch(item -> item.is(stack.getItem()) && item.getDamageValue() == 61),
                "Saved hit wear persists in the weapon the player actually recovers");
        spear.discard(); restored.discard(); target.discard(); owner.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeThrowsStayUnwornAndBrokenSpearsCannotReturnAnotherWeapon(GameTestHelper h) throws ReflectiveOperationException {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        for (GameType mode : List.of(GameType.CREATIVE, GameType.SURVIVAL)) {
            Player owner = owner(h, mode); Mob target = target(h);
            ItemStack stack = new ItemStack(SimplySwordsCompat.weapon("rusted_iron", "spear"));
            int originalDamage = stack.getMaxDamage() - 25; stack.setDamageValue(originalDamage);
            AbstractArrow spear = spear(h, owner, stack); hit(spear, target);
            if (mode == GameType.CREATIVE) h.assertValueEqual(stack.getDamageValue(), originalDamage, "Creative throwing retains the existing wear exemption");
            else h.assertTrue(stack.isEmpty() && spear.isRemoved(), "A broken spear disappears instead of receiving AbstractArrow's default pickup weapon");
            spear.discard(); target.discard(); owner.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void savedDiamondAndNetheriteSpearsAreDiscardedInsteadOfRestoringTheDefaultWeapon(GameTestHelper h) throws ReflectiveOperationException {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        for (String material : List.of("diamond", "netherite")) {
            Player owner = owner(h, GameType.SURVIVAL);
            AbstractArrow original = spear(h, owner, new ItemStack(SimplySwordsCompat.weapon(material, "spear")));
            AbstractArrow restored = (AbstractArrow)original.getType().create(h.getLevel());
            try {
                CompoundTag saved = original.saveWithoutId(new CompoundTag());
                h.assertTrue(saved.contains("item"), "The original SS projectile writes its native saved weapon: " + material);
                restored.load(saved); restored.setOwner(owner);
                h.assertTrue(restored.isRemoved(), "Loading a retired saved spear discards the projectile immediately: " + material);
                h.assertTrue(((ItemStack)Class.forName(SPEAR).getField("stack").get(restored)).isEmpty(),
                        "Filtered saved weapon cannot leave SS's fallback unique spear in the synchronized field: " + material);
                h.assertFalse(pickup(restored, owner), "The discarded saved spear cannot return a fallback weapon through native pickup: " + material);
                h.assertTrue(owner.getInventory().isEmpty(), "Loading and pickup cannot introduce any retired or fallback weapon: " + material);
            } finally { original.discard(); restored.discard(); owner.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void directlyConstructedRetiredSpearsCannotTickDamageOrEnterThePickupInventory(GameTestHelper h) throws ReflectiveOperationException {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        for (String material : List.of("diamond", "netherite")) {
            Player owner = owner(h, GameType.SURVIVAL); Mob target = target(h);
            AbstractArrow ticking = spear(h, owner, new ItemStack(SimplySwordsCompat.weapon(material, "spear")));
            AbstractArrow attacking = spear(h, owner, new ItemStack(SimplySwordsCompat.weapon(material, "spear")));
            AbstractArrow recovered = spear(h, owner, new ItemStack(SimplySwordsCompat.weapon(material, "spear")));
            try {
                ticking.setPos(h.absolutePos(new BlockPos(1, 12, 1)).getCenter());
                ticking.setDeltaMovement(Vec3.ZERO); ticking.tick();
                h.assertTrue(ticking.isRemoved(), "The native projectile tick retires a directly injected spear: " + material);
                float health = target.getHealth();
                hit(attacking, target);
                h.assertValueEqual(target.getHealth(), health, "A retired spear hit is rejected before native damage: " + material);
                h.assertTrue(attacking.isRemoved(), "Rejected retired spear attacks discard the projectile: " + material);
                h.assertFalse(pickup(recovered, owner), "Native pickup rejects a retired spear before adding it to inventory: " + material);
                h.assertTrue(recovered.isRemoved() && owner.getInventory().isEmpty(),
                        "Native pickup discards the spear and never adds its original or default weapon: " + material);
            } finally { ticking.discard(); attacking.discard(); recovered.discard(); target.discard(); owner.discard(); }
        }
        h.succeed();
    }
}

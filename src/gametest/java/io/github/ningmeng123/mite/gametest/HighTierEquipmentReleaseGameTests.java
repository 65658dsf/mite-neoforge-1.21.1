package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.mixin.SourceMobDropChanceAccess;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real replacement and player attack routes cannot release natural high-tier gear. @author NingMeng123 */
@GameTestHolder("mite_equipment_drop")
@PrefixGameTestTemplate(false)
public final class HighTierEquipmentReleaseGameTests {
    private static final List<EquipmentSlot> NATIVE_SLOTS = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD);
    private static final List<String> MATERIALS = List.of("ancient_metal", "mithril", "adamantium", "vibranium");

    private HighTierEquipmentReleaseGameTests() {}

    @GameTest(template = "empty")
    public static void nativeReplacementUsesTheOldSlotChanceBeforeMarkingTheNewEquipmentPickedUp(GameTestHelper h) {
        for (String material : MATERIALS) for (EquipmentSlot slot : NATIVE_SLOTS)
            for (boolean picked : List.of(false, true)) for (boolean enchanted : List.of(false, true))
                try (Fixture f = new Fixture(h, EntityType.ZOMBIE)) {
                    Item item = R196MobEquipment.item("mite:" + material + "_" + suffix(slot));
                    ItemStack original = equipment(h, item, Quality.AVERAGE, 37, enchanted);
                    ItemStack incoming = equipment(h, item, Quality.FINE, 19, enchanted);
                    ItemStack expectedIncoming = incoming.copy();
                    f.mob.setItemSlot(slot, original);
                    f.mob.setDropChance(slot, picked ? 2F : 1F);
                    f.mob.getRandom().setSeed(196L);
                    RandomSource expected = RandomSource.create(196L);
                    expected.nextFloat();
                    ItemStack equipped = f.mob.equipItemIfPossible(incoming);
                    h.assertTrue(!equipped.isEmpty() && ItemStack.matches(f.mob.getItemBySlot(slot), expectedIncoming),
                            "The material restriction still allows native higher-quality pickup: " + material + " / " + slot);
                    h.assertTrue(((SourceMobDropChanceAccess)f.mob).mite$equipmentDropChance(slot) > 1F,
                            "The newly equipped item receives its ordinary picked-up guarantee");
                    assertOldRelease(h, f, original, material.equals("ancient_metal") || picked,
                            "Native replacement must inspect the old stored slot chance, not the new 2F marker");
                    h.assertValueEqual(f.mob.getRandom().nextFloat(), expected.nextFloat(),
                            "The pure material decision retains the single original native replacement roll");
                }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryAndSourceSkeletonDualWeaponReplacementUsesTheCorrectOldHandChance(GameTestHelper h) {
        for (EntityType<? extends Mob> type : List.of(EntityType.SKELETON, MiteMobs.type(MobKind.LONGDEAD)))
            for (String material : MATERIALS) for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND))
                for (boolean picked : List.of(false, true)) for (boolean enchanted : List.of(false, true))
                    try (Fixture f = new Fixture(h, type)) {
                        Item item = R196MobEquipment.item("mite:" + material + "_sword");
                        ItemStack original = equipment(h, item, Quality.AVERAGE, 37, enchanted);
                        ItemStack incoming = equipment(h, item, Quality.FINE, 19, enchanted);
                        EquipmentSlot other = slot == EquipmentSlot.MAINHAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
                        ItemStack bow = new ItemStack(Items.BOW);
                        f.mob.setItemSlot(slot, original);
                        f.mob.setItemSlot(other, bow);
                        f.mob.setDropChance(slot, picked ? 2F : 1F);
                        // A picked-up bow in the other hand must not make a natural sword returnable.
                        f.mob.setDropChance(other, 2F);
                        f.mob.getRandom().setSeed(196L);
                        RandomSource expected = RandomSource.create(196L);
                        expected.nextFloat();
                        ItemStack equipped = f.mob.equipItemIfPossible(incoming);
                        h.assertTrue(!equipped.isEmpty() && (ItemStack.matches(f.mob.getMainHandItem(), incoming)
                                        || ItemStack.matches(f.mob.getOffhandItem(), incoming)),
                                "The actual ordinary/source skeleton route still equips the higher-quality blade");
                        h.assertTrue(f.mob.getMainHandItem() == bow || f.mob.getOffhandItem() == bow,
                                "Replacement preserves the exact bow in the other hand, including later weapon swapping");
                        EquipmentSlot newSlot = ItemStack.matches(f.mob.getMainHandItem(), incoming)
                                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                        h.assertTrue(((SourceMobDropChanceAccess)f.mob).mite$equipmentDropChance(newSlot) > 1F,
                                "The replacement blade keeps its guarantee after the skeleton's hand policy runs");
                        assertOldRelease(h, f, original, material.equals("ancient_metal") || picked,
                                "The source skeleton route reads the previous blade's own hand chance");
                        h.assertValueEqual(f.mob.getRandom().nextFloat(), expected.nextFloat(),
                                "Skeleton replacement consumes only its existing probability draw");
                    }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realPlayerDisarmingKeepsItsCombatEffectButCannotReleaseNaturalHighTierEquipment(GameTestHelper h) {
        Player attacker = h.makeMockPlayer(GameType.SURVIVAL);
        attacker.setPos(h.absolutePos(new BlockPos(1, 3, 1)).getCenter());
        attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20D);
        attacker.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024D);
        ItemStack weapon = new ItemStack(Items.IRON_SWORD);
        weapon.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(R196Enchantments.key("disarming")), 5);
        attacker.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        List<Item> equipment = MATERIALS.stream().map(material -> R196MobEquipment.item("mite:" + material + "_sword")).toList();
        for (EntityType<? extends Mob> type : List.of(EntityType.ZOMBIE, EntityType.SKELETON,
                MiteMobs.type(MobKind.LONGDEAD), EntityType.COW)) for (Item item : equipment)
            for (boolean picked : List.of(false, true)) for (boolean enchanted : List.of(false, true))
                try (Fixture f = new Fixture(h, type)) {
                    ItemStack original = equipment(h, item, Quality.FINE, 37, enchanted);
                    f.mob.setItemSlot(EquipmentSlot.MAINHAND, original);
                    f.mob.setDropChance(EquipmentSlot.MAINHAND, picked ? 2F : 1F);
                    f.mob.setCanPickUpLoot(true);
                    f.mob.setInvulnerable(true);
                    attacker.attack(f.mob);
                    h.assertTrue(f.mob.getMainHandItem().isEmpty() && SourceEnchantmentCombat.disarmed(f.mob),
                            "Actual player attack still clears the successfully disarmed weapon and starts its cooldown");
                    h.assertValueEqual(f.mob.getPersistentData().getInt("mite_enchantment_disarmed_ticks"), 40,
                            "Suppressing the world item retains the original forty-tick combat effect");
                    h.assertFalse(f.mob.canPickUpLoot(), "The real disarming state still blocks immediate pickup");
                    boolean expected = picked || type == EntityType.COW || item == R196MobEquipment.item("mite:ancient_metal_sword");
                    assertOldRelease(h, f, original, expected,
                            "Only naturally generated high-tier monster equipment is suppressed during actual disarming");
                }
        h.succeed();
    }

    private static void assertOldRelease(GameTestHelper h, Fixture f, ItemStack original, boolean expected, String context) {
        h.assertValueEqual(f.drops.size(), expected ? 1 : 0, context);
        if (expected) h.assertTrue(ItemStack.matches(f.drops.getFirst().getItem(), original),
                "Released equipment retains its exact material, quality, enchantments and wear");
    }

    private static String suffix(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> "sword";
            case OFFHAND -> "shield";
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> throw new AssertionError("Unsupported native replacement fixture " + slot);
        };
    }

    private static ItemStack equipment(GameTestHelper h, Item item, Quality quality, int damage, boolean enchanted) {
        ItemStack stack = new ItemStack(item);
        EquipmentDurability.setQuality(stack, quality);
        stack.setDamageValue(damage);
        if (enchanted) stack.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.UNBREAKING), 1);
        return stack;
    }

    private static final class Fixture implements AutoCloseable {
        private final Mob mob;
        private final List<ItemEntity> drops = new ArrayList<>();

        private Fixture(GameTestHelper h, EntityType<? extends Mob> type) {
            mob = type.create(h.getLevel());
            if (mob instanceof R196Skeleton skeleton) skeleton.setSkeletonType(0);
            for (EquipmentSlot slot : EquipmentSlot.values()) mob.setItemSlot(slot, ItemStack.EMPTY);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setPos(h.absolutePos(new BlockPos(3, 3, 1)).getCenter());
            mob.captureDrops(drops);
        }

        @Override public void close() {
            mob.captureDrops(null);
            drops.forEach(ItemEntity::discard);
            mob.discard();
        }
    }
}

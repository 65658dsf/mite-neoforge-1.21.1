package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsMobEquipment;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.mixin.MobSpawnTypeAccessor;
import io.github.ningmeng123.mite.mixin.SourceMobDropChanceAccess;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.SimplySwordsMobRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The same tests run with and without the optional mod, exercising actual native item stacks and events. @author NingMeng123 */
@GameTestHolder("mite_simplyswords")
@PrefixGameTestTemplate(false)
public final class SimplySwordsMobGameTests {
    private SimplySwordsMobGameTests() {}

    private static Mob create(GameTestHelper h, EntityType<?> type) {
        Mob mob = (Mob)type.create(h.getLevel());
        mob.setNoAi(true); mob.setNoGravity(true); mob.moveTo(h.absolutePos(new BlockPos(2, 3, 2)).getCenter());
        ((MobSpawnTypeAccessor)mob).mite$setSpawnType(MobSpawnType.NATURAL);
        // This suite isolates optional weapons from the independently tested shield spawn roll.
        mob.getPersistentData().putBoolean(MobShields.SPAWN_ROLL, true);
        return mob;
    }

    private static long successSeed() {
        for (long seed = 0; seed < 100_000; seed++)
            if (RandomSource.create(seed).nextFloat() < SimplySwordsMobRules.EQUIP_CHANCE) return seed;
        throw new AssertionError("No weapon probability fixture");
    }

    @GameTest(template = "empty")
    public static void optionalModControlsSpawnItemsWithoutChangingAbsentModRng(GameTestHelper h) {
        Mob mob = create(h, EntityType.ZOMBIE); mob.getRandom().setSeed(successSeed());
        boolean generated = SimplySwordsMobEquipment.populate(mob);
        h.assertTrue(generated == SimplySwordsCompat.enabled(), "The optional mod alone enables the equipment generator");
        if (SimplySwordsCompat.enabled()) {
            h.assertTrue(SimplySwordsCompat.definition(mob.getMainHandItem()) != null, "The result is a managed Simply Swords weapon");
            h.assertTrue(mob.getMainHandItem().getDamageValue() > 0, "Unarmed spawn equipment receives source wear");
        } else {
            h.assertTrue(mob.getMainHandItem().isEmpty() && !mob.getPersistentData().contains(SimplySwordsMobEquipment.DATA_KEY),
                    "An absent optional mod changes neither equipment nor entity data");
            h.assertValueEqual(mob.getRandom().nextLong(), RandomSource.create(successSeed()).nextLong(), "An absent mod consumes no random draws");
        }
        mob.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void firstTickSeesFinalEquipmentAndPreservesMaterialQualityEnchantmentsWearAndDrops(GameTestHelper h) {
        for (String material : List.of("rusted_iron", "copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium", "vibranium")) {
            Mob mob = create(h, EntityType.ZOMBIE);
            SimplySwordsMobEquipment.join(new EntityJoinLevelEvent(mob, h.getLevel(), false));
            h.assertTrue(mob.getMainHandItem().isEmpty(), "Join listeners do not perform the weapon roll prematurely");
            String id = material.equals("gold") ? "minecraft:golden_sword" : material.equals("iron") ? "minecraft:iron_sword" : "mite:" + material + "_sword";
            ItemStack original = new ItemStack(MiteTools.get(id));
            h.assertTrue(original.has(DataComponents.LORE) && original.get(DataComponents.LORE).lines().isEmpty(),
                    "Default empty lore components do not make ordinary generated weapons customized");
            EquipmentDurability.setQuality(original, Quality.POOR);
            original.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS), 2);
            original.setDamageValue(original.getMaxDamage() * 3 / 4);
            ItemStack shield = material.equals("vibranium") ? new ItemStack(MiteShields.get("vibranium")) : ItemStack.EMPTY;
            if (!shield.isEmpty()) mob.setItemSlot(EquipmentSlot.OFFHAND, shield);
            mob.setItemSlot(EquipmentSlot.MAINHAND, original); mob.setDropChance(EquipmentSlot.MAINHAND, .071F);
            mob.getRandom().setSeed(successSeed());
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(mob));
            ItemStack result = mob.getMainHandItem();
            if (SimplySwordsCompat.enabled()) {
                h.assertTrue(result != original && material.equals(SimplySwordsCompat.material(result)), "The first registered tick uses the final native weapon's metal");
                h.assertTrue(EquipmentDurability.quality(result) == Quality.POOR, "The generated source quality is retained");
                h.assertTrue(result.get(DataComponents.ENCHANTMENTS).equals(original.get(DataComponents.ENCHANTMENTS)), "Existing native enchantments are retained");
                double before = original.getDamageValue() / (double)original.getMaxDamage();
                double after = result.getDamageValue() / (double)result.getMaxDamage();
                h.assertTrue(Math.abs(before - after) <= 1D / result.getMaxDamage(), "Material family changes preserve the exact wear fraction up to rounding");
            } else h.assertTrue(result == original, "Without Simply Swords the first tick retains the original real stack");
            h.assertTrue(((SourceMobDropChanceAccess)mob).mite$equipmentDropChance(EquipmentSlot.MAINHAND) == .071F, "Slot drop chances are unchanged");
            if (!shield.isEmpty()) h.assertTrue(mob.getOffhandItem() == shield, "A vibranium blade conversion retains the same actual vibranium shield stack");
            mob.getRandom().setSeed(713L); SimplySwordsMobEquipment.tick(new EntityTickEvent.Pre(mob));
            h.assertTrue(mob.getMainHandItem() == result, "Later ticks never replace a decided result");
            h.assertValueEqual(mob.getRandom().nextLong(), RandomSource.create(713L).nextLong(), "A repeated decision consumes no RNG");
            mob.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unarmedLateWorldFallbackCanRollVibraniumAndNeverRerollsAnExistingDecision(GameTestHelper h) {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        long seed = -1L;
        for (long candidate = 0; candidate < 100_000; candidate++) {
            RandomSource random = RandomSource.create(candidate);
            if (random.nextFloat() < SimplySwordsMobRules.EQUIP_CHANCE
                    && SimplySwordsMobRules.selectMaterial(1280L, false, random.nextDouble()).equals("vibranium")) {
                seed = candidate; break;
            }
        }
        h.assertTrue(seed >= 0L, "A positive late-world probability provides a reproducible vibranium fallback roll");
        Mob mob = create(h, EntityType.VINDICATOR);
        ItemStack shield = new ItemStack(MiteShields.get("vibranium"));
        mob.setItemSlot(EquipmentSlot.OFFHAND, shield);
        CompoundTag decision = new CompoundTag();
        decision.putInt("version", 2); decision.putLong("highest_day", 1280L); decision.putBoolean("blood_date", false);
        mob.getPersistentData().put(SimplySwordsMobEquipment.DATA_KEY, decision);
        mob.getRandom().setSeed(seed);
        h.assertTrue(SimplySwordsMobEquipment.populate(mob), "A new late-world unarmed spawn can receive a fallback weapon");
        h.assertValueEqual(SimplySwordsCompat.material(mob.getMainHandItem()), "vibranium", "The fallback creates the actual registered vibranium weapon family");
        h.assertTrue(mob.getOffhandItem() == shield, "Fallback weapon choice preserves the explicitly equipped vibranium shield");
        ItemStack weapon = mob.getMainHandItem();
        mob.getRandom().setSeed(713L);
        h.assertFalse(SimplySwordsMobEquipment.populate(mob), "The saved completion flag prevents a second late-world roll");
        h.assertTrue(mob.getMainHandItem() == weapon, "The completed decision retains the exact generated blade stack");
        h.assertValueEqual(mob.getRandom().nextLong(), RandomSource.create(713L).nextLong(), "A completed progression decision consumes no random draws");
        mob.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void reloadedOldAndAlreadyDecidedEntitiesNeverGainAnotherWeapon(GameTestHelper h) {
        Mob old = create(h, EntityType.ZOMBIE);
        SimplySwordsMobEquipment.join(new EntityJoinLevelEvent(old, h.getLevel(), true));
        old.getRandom().setSeed(successSeed());
        h.assertFalse(SimplySwordsMobEquipment.populate(old), "Old loaded entities are never retroactively equipped");
        h.assertTrue(old.getMainHandItem().isEmpty(), "An old empty hand stays empty");
        h.assertValueEqual(old.getRandom().nextLong(), RandomSource.create(successSeed()).nextLong(), "Loading does not spend a spawn draw");
        Mob fresh = create(h, EntityType.ZOMBIE); fresh.getRandom().setSeed(successSeed());
        SimplySwordsMobEquipment.populate(fresh);
        ItemStack previous = fresh.getMainHandItem().copy();
        CompoundTag saved = fresh.saveWithoutId(new CompoundTag());
        Mob restored = create(h, EntityType.ZOMBIE); restored.load(saved);
        SimplySwordsMobEquipment.join(new EntityJoinLevelEvent(restored, h.getLevel(), true));
        restored.getRandom().setSeed(successSeed());
        h.assertFalse(SimplySwordsMobEquipment.populate(restored), "A saved nonempty or empty spawn decision survives reloading");
        h.assertTrue(ItemStack.matches(previous, restored.getMainHandItem()), "Reloading preserves the real item components");
        old.discard(); fresh.discard(); restored.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void rangedSpecialCustomizedAndForcedWeaponsArePreservedWithoutARoll(GameTestHelper h) {
        for (var item : List.of(Items.BOW, Items.CROSSBOW, Items.TRIDENT)) {
            Mob mob = create(h, EntityType.SKELETON); ItemStack weapon = new ItemStack(item);
            mob.setItemSlot(EquipmentSlot.MAINHAND, weapon); mob.getRandom().setSeed(413L);
            h.assertFalse(SimplySwordsMobEquipment.populate(mob), "Ranged weapons are preserved");
            h.assertTrue(mob.getMainHandItem() == weapon, "The bow/crossbow/trident remains the actual original stack");
            h.assertValueEqual(mob.getRandom().nextLong(), RandomSource.create(413L).nextLong(), "Ineligible ranged loadouts consume no RNG");
            mob.discard();
        }
        for (String reserved : List.of("guaranteed", "named", "lore")) {
            Mob mob = create(h, EntityType.ZOMBIE); ItemStack weapon = new ItemStack(Items.IRON_SWORD);
            if (reserved.equals("named")) weapon.set(DataComponents.CUSTOM_NAME, Component.literal("Reserved weapon"));
            else if (reserved.equals("lore")) weapon.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Reserved lore"))));
            else mob.setDropChance(EquipmentSlot.MAINHAND, 2F);
            mob.setItemSlot(EquipmentSlot.MAINHAND, weapon); mob.getRandom().setSeed(successSeed());
            h.assertFalse(SimplySwordsMobEquipment.populate(mob), "Forced/picked-up and customized weapons are preserved");
            h.assertTrue(mob.getMainHandItem() == weapon, "Reserved equipment is unchanged"); mob.discard();
        }
        for (MobKind kind : List.of(MobKind.REVENANT, MobKind.LONGDEAD, MobKind.LONGDEAD_GUARDIAN, MobKind.BONE_LORD, MobKind.ANCIENT_BONE_LORD)) {
            Mob special = create(h, MiteMobs.type(kind)); special.getRandom().setSeed(successSeed());
            h.assertFalse(SimplySwordsMobEquipment.populate(special), "Special source identities retain their dedicated equipment paths");
            h.assertFalse(special.getPersistentData().contains(SimplySwordsMobEquipment.DATA_KEY), "Dedicated source mobs never enter the optional spawn decision");
            special.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void meleeSkeletonKeepsItsStoredBowAndCommandSpawnsStayExplicit(GameTestHelper h) {
        Mob skeleton = create(h, EntityType.SKELETON);
        ItemStack bow = new ItemStack(Items.BOW), sword = new ItemStack(Items.IRON_SWORD);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, sword); skeleton.setItemSlot(EquipmentSlot.OFFHAND, bow);
        skeleton.getRandom().setSeed(successSeed()); SimplySwordsMobEquipment.populate(skeleton);
        h.assertTrue(skeleton.getOffhandItem() == bow, "A melee skeleton never loses or overwrites its stored bow");
        h.assertTrue(SimplySwordsCompat.enabled() ? "iron".equals(SimplySwordsCompat.material(skeleton.getMainHandItem()))
                : skeleton.getMainHandItem() == sword, "Only the melee main hand can receive a same-metal weapon family");
        Mob summoned = create(h, EntityType.ZOMBIE);
        ((MobSpawnTypeAccessor)summoned).mite$setSpawnType(MobSpawnType.COMMAND);
        summoned.getRandom().setSeed(successSeed());
        h.assertFalse(SimplySwordsMobEquipment.populate(summoned), "Command-created entities keep explicitly supplied loadouts");
        h.assertTrue(summoned.getMainHandItem().isEmpty(), "A command spawn is not armed implicitly");
        skeleton.discard(); summoned.discard(); h.succeed();
    }
}

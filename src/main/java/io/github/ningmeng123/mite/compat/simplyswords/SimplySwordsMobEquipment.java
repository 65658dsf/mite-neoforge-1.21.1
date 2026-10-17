package io.github.ningmeng123.mite.compat.simplyswords;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.entity.BloodMoonEquipment;
import io.github.ningmeng123.mite.entity.R196Mob;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.mixin.SourceMobDropChanceAccess;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.SimplySwordsMobRules;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasion;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Converts ordinary spawn weapons after all native/source equipment initialization. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class SimplySwordsMobEquipment {
    public static final String DATA_KEY = "mite:simplyswords_equipment";
    private static final Set<EntityType<?>> TYPES = Set.of(EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER,
            EntityType.ZOMBIFIED_PIGLIN, EntityType.HUSK, EntityType.DROWNED, EntityType.SKELETON,
            EntityType.WITHER_SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.PIGLIN,
            EntityType.PIGLIN_BRUTE, EntityType.VINDICATOR);
    private static final Set<String> METALS = Set.of("rusted_iron", "copper", "silver", "gold", "iron",
            "ancient_metal", "mithril", "adamantium", "vibranium");
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private SimplySwordsMobEquipment() {}

    /** Source guardians/lords/revenants retain their dedicated weapons and hidden weapon storage. */
    public static boolean handles(Mob mob) {
        return !(mob instanceof R196Mob) && TYPES.contains(mob.getType());
    }

    private static CompoundTag decision(Mob mob) {
        CompoundTag root = mob.getPersistentData();
        if (root.contains(DATA_KEY)) return root.getCompound(DATA_KEY);
        CompoundTag result = new CompoundTag();
        result.putInt("version", 2);
        if (mob.level() instanceof ServerLevel level) result.putLong("highest_day", BloodMoonInvasion.highestDay(level));
        result.putBoolean("blood_date", BloodMoonEquipment.bloodDate(mob));
        root.put(DATA_KEY, result);
        return result;
    }

    private static boolean replaceable(Mob mob, ItemStack held) {
        if (held.isEmpty()) return true;
        // A picked-up, forced, named or customized stack is not ordinary generated equipment.
        return MiteTools.definition(held) != null && held.getCount() == 1
                && ((SourceMobDropChanceAccess)mob).mite$equipmentDropChance(EquipmentSlot.MAINHAND) <= 1F
                && !held.has(DataComponents.CUSTOM_NAME) && held.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().isEmpty()
                && !held.has(DataComponents.CUSTOM_DATA) && !held.has(DataComponents.UNBREAKABLE)
                && !EnchantmentHelper.has(held, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
    }

    private static String establishedMaterial(Mob mob) {
        var held = MiteTools.definition(mob.getMainHandItem());
        if (held != null && METALS.contains(held.material())) return held.material();
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = mob.getItemBySlot(slot);
            var armor = MiteArmorContent.definition(stack.getItem());
            if (armor != null && METALS.contains(armor.material())) return armor.material();
            if (stack.is(Items.IRON_HELMET) || stack.is(Items.IRON_CHESTPLATE) || stack.is(Items.IRON_LEGGINGS)
                    || stack.is(Items.IRON_BOOTS) || stack.is(Items.CHAINMAIL_HELMET) || stack.is(Items.CHAINMAIL_CHESTPLATE)
                    || stack.is(Items.CHAINMAIL_LEGGINGS) || stack.is(Items.CHAINMAIL_BOOTS)) return "iron";
            if (stack.is(Items.GOLDEN_HELMET) || stack.is(Items.GOLDEN_CHESTPLATE)
                    || stack.is(Items.GOLDEN_LEGGINGS) || stack.is(Items.GOLDEN_BOOTS)) return "gold";
        }
        return null;
    }

    /** Existing generated stacks retain quality, enchantments, wear fraction and their native slot drop chance. */
    private static ItemStack replacement(Mob mob, ItemStack previous, ItemStack next) {
        if (previous.isEmpty()) return R196MobEquipment.generated(mob, next, true);
        EquipmentDurability.setQuality(next, EquipmentDurability.quality(previous));
        if (previous.has(DataComponents.ENCHANTMENTS)) next.set(DataComponents.ENCHANTMENTS, previous.get(DataComponents.ENCHANTMENTS));
        if (previous.isDamageableItem() && next.isDamageableItem())
            next.setDamageValue(Math.min(next.getMaxDamage() - 1,
                    (int)((long)previous.getDamageValue() * next.getMaxDamage() / previous.getMaxDamage())));
        return next;
    }

    /** An absent optional mod consumes no RNG and writes no data; accepted and empty decisions are permanent. */
    public static boolean populate(Mob mob) {
        if (!SimplySwordsCompat.enabled() || !handles(mob) || !(mob.level() instanceof ServerLevel) || !mob.isAlive()) return false;
        CompoundTag result = decision(mob);
        if (result.getBoolean("decided")) return false;
        result.putBoolean("decided", true);
        if (!MobShields.spawnReason(mob.getSpawnType()) || !replaceable(mob, mob.getMainHandItem())) return false;
        var families = SimplySwordsCompat.families();
        if (families.isEmpty() || mob.getRandom().nextFloat() >= SimplySwordsMobRules.EQUIP_CHANCE) return false;
        String material = establishedMaterial(mob);
        if (material == null) material = SimplySwordsMobRules.selectMaterial(result.getLong("highest_day"),
                result.getBoolean("blood_date"), mob.getRandom().nextDouble());
        String family = families.get(mob.getRandom().nextInt(families.size()));
        var item = SimplySwordsCompat.weapon(material, family);
        if (item == Items.AIR) return false;
        ItemStack generated = replacement(mob, mob.getMainHandItem(), new ItemStack(item));
        mob.setItemSlot(EquipmentSlot.MAINHAND, generated);
        result.putBoolean("generated", true); result.putString("material", material); result.putString("family", family);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (!SimplySwordsCompat.enabled() || event.isCanceled() || event.getLevel().isClientSide
                || !(event.getEntity() instanceof Mob mob) || !handles(mob)) return;
        CompoundTag result = decision(mob);
        if (event.loadedFromDisk()) { result.putBoolean("decided", true); result.putBoolean("loaded", true); }
    }

    /** LOWEST runs after shield equipment; all join listeners and spawn finalizers have already completed. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Mob mob) populate(mob);
    }
}

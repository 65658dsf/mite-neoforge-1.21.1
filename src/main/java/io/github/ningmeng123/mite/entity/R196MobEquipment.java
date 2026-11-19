package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.item.EquipmentQuality;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import io.github.ningmeng123.mite.world.DifficultySystem;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.Enchantments;

/** Source abw.I tension, og.enchantEquipment and ye.randomizeForMob. @author NingMeng123 */
public final class R196MobEquipment {
    private R196MobEquipment() {}
    public static Item item(String id) {
        ResourceLocation key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalArgumentException("Missing source equipment " + key);
        return BuiltInRegistries.ITEM.get(key);
    }
    public static float tension(Mob mob) {
        return DifficultySystem.tension(mob.level(), mob.blockPosition());
    }
    public static ItemStack generated(Mob mob, String id, boolean enchant) {
        return generated(mob, new ItemStack(item(id)), enchant);
    }
    public static ItemStack generated(Mob mob, ItemStack stack, boolean enchant) {
        stack = MobEquipmentEnchantments.generated(mob, stack, enchant);
        EquipmentQuality.applyGeneratedMobQuality(stack);
        if (stack.isDamageableItem()) {
            float fraction = (stack.isEnchanted() ? .5F : .7F) + .3F * mob.getRandom().nextFloat();
            stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, (int)(stack.getMaxDamage() * fraction)));
        }
        return stack;
    }
    public static void armor(Mob mob, String prefix) {
        mob.setItemSlot(EquipmentSlot.FEET, SpecialMobEquipment.generated(mob, "mite:" + prefix + "boots", true));
        mob.setItemSlot(EquipmentSlot.LEGS, SpecialMobEquipment.generated(mob, "mite:" + prefix + "leggings", true));
        mob.setItemSlot(EquipmentSlot.CHEST, SpecialMobEquipment.generated(mob, "mite:" + prefix + "chestplate", true));
        mob.setItemSlot(EquipmentSlot.HEAD, SpecialMobEquipment.generated(mob, "mite:" + prefix + "helmet", true));
    }
    /** Source og.bw draws one material tier and can stop while descending from helmet to boots. */
    public static void randomArmor(Mob mob) {
        if (BloodMoonEquipment.populate(mob)) return;
        var random = mob.getRandom();
        if (!(random.nextFloat() < BloodMoonEquipment.legacyChance(mob, .15F * tension(mob)))) return;
        int tier = random.nextInt(2);
        for (int i = 0; i < 3; ++i) if (random.nextFloat() < .095F) ++tier;
        float stop = mob.level().getDifficulty() == Difficulty.HARD ? .1F : .25F;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        String[] suffixes = {"helmet", "chestplate", "leggings", "boots"};
        String prefix = switch (tier) {
            case 0 -> "minecraft:leather_"; case 1 -> "mite:rusted_iron_chain_";
            case 2 -> "mite:copper_chain_"; case 3 -> "mite:rusted_iron_"; default -> "mite:copper_";
        };
        if (PiglinArmor.handles(mob) && (tier == 0 || tier == 1 || tier == 3))
            prefix = PiglinArmor.MINIMUM_PREFIX;
        for (int i = 0; i < slots.length; ++i) {
            if (i > 0 && random.nextFloat() < stop) break;
            if (mob.getItemBySlot(slots[i]).isEmpty()) mob.setItemSlot(slots[i], generated(mob, prefix + suffixes[i], true));
        }
    }
    /** Tournament equipment reductions are disabled in the ordinary R196 survival mode. */
    public static String lordWeapon(Mob mob, boolean ancient) {
        long day = (mob.level().getDayTime() + 6_000L) / 24_000L + 1L;
        int choices = 2 + (ancient || day >= 10 ? 1 : 0) + (ancient || day >= 20 ? 1 : 0);
        int roll = mob.getRandom().nextInt(choices);
        String suffix = roll < 2 ? "sword" : roll == 2 ? "battle_axe" : "war_hammer";
        return "mite:" + (ancient ? "ancient_metal_" : "rusted_iron_") + suffix;
    }
    /** og.a keeps the actual stack and separates held, picked-up, forced and ordinary armor drops. */
    public static void dropEquipment(Mob mob, DamageSource source, boolean recent, boolean massiveFall, boolean longdead,
            ToDoubleFunction<EquipmentSlot> chances) {
        int looting = source.getEntity() instanceof LivingEntity living ? io.github.ningmeng123.mite.enchantment.R196Enchantments.level(living.getMainHandItem(), living.level(), "minecraft:looting") : 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.FEET, EquipmentSlot.LEGS,
                EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.OFFHAND, EquipmentSlot.BODY}) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty() || EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) continue;
            float chance = (float)chances.applyAsDouble(slot);
            boolean held = slot == EquipmentSlot.MAINHAND, picked = held && chance > 1F, forced = chance > 1F;
            if (massiveFall && chance < 1F && mob.getRandom().nextFloat() < .9F && (!held || !picked)) continue;
            if (EquipmentQuality.hasPoorHeldDropPenalty(stack, slot, chance,
                    mob.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER)
                    && mob.getRandom().nextFloat() < .75F) continue;
            if (longdead && held && !picked && EquipmentDurability.quality(stack).ordinal()
                    >= io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality.AVERAGE.ordinal()) held = false;
            if ((!held || !recent && !picked && !(mob.getRandom().nextFloat() < .05F))
                    && (!recent && !forced || !(mob.getRandom().nextFloat() - looting * .01F < chance))) continue;
            if (!MobEquipmentDrops.keep(mob, chance, stack)) continue;
            mob.spawnAtLocation(stack); mob.setItemSlot(slot, ItemStack.EMPTY);
        }
    }
}

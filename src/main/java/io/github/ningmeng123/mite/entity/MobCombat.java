package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.item.SilverArmor;
import io.github.ningmeng123.mite.item.SourceArmorDefense;
import io.github.ningmeng123.mite.progression.ProgressionThrownWeapon;
import io.github.ningmeng123.mite.ranged.MaterialArrowView;
import io.github.ningmeng123.mite.rules.SourceArmorDefenseRules;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/** Source nc damage aspects and source defenses share one representation across the new families. @author NingMeng123 */
public final class MobCombat {
    private MobCombat() {}
    private static final ThreadLocal<Deque<LivingEntity>> DEFENDING = ThreadLocal.withInitial(ArrayDeque::new);
    public static int ticksWithOffset(Entity entity) { return entity.tickCount + entity.getId() * 47; }
    public static float silverResistance(LivingEntity entity) { return SilverArmor.coverage(entity) * .5F; }
    public static boolean silver(DamageSource source) {
        Entity direct = source.getDirectEntity();
        // Simply Swords spears inherit the arrow material view from AbstractArrow.
        if (direct instanceof ProgressionThrownWeapon thrown) return silverItem(thrown.mite$progressionWeapon());
        if (direct instanceof MaterialArrowView arrow) return "silver".equals(arrow.mite$arrowMaterial());
        return silverItem(weapon(source));
    }
    public static boolean silverItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return (id.getNamespace().equals("mite") && id.getPath().startsWith("silver_"))
                || "silver".equals(SimplySwordsCompat.material(stack));
    }
    public static ItemStack weapon(DamageSource source) {
        ItemStack weapon = source.getWeaponItem();
        return weapon == null && source.getEntity() instanceof LivingEntity attacker ? attacker.getMainHandItem() : weapon;
    }
    public static boolean magic(DamageSource source) {
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.THORNS)) return true;
        ItemStack weapon = weapon(source);
        return weapon != null && weapon.isEnchanted();
    }
    public static boolean fire(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE) || source.getDirectEntity() instanceof AbstractArrow arrow && arrow.isOnFire();
    }
    public static boolean sunlight(DamageSource source) { return source.is(key("sunlight")); }
    public static boolean acid(DamageSource source) { return source.is(key("acid")); }
    public static boolean pepsin(DamageSource source) { return source.is(key("pepsin")); }
    public static boolean water(DamageSource source) { return source.is(key("water")) || source.is(DamageTypes.DROWN); }
    public static DamageSource source(LivingEntity entity, String path) {
        return new DamageSource(entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key(path)));
    }
    private static ResourceKey<DamageType> key(String path) { return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("mite", path)); }

    public static void enterDefense(LivingEntity entity) { DEFENDING.get().push(entity); }
    public static void exitDefense(LivingEntity entity) {
        Deque<LivingEntity> stack = DEFENDING.get();
        if (stack.pop() != entity) throw new IllegalStateException("Mob defense scope order");
        if (stack.isEmpty()) DEFENDING.remove();
    }
    public static boolean defending(LivingEntity entity) { return DEFENDING.get().peek() == entity; }
    /** Legacy barding is a separate BODY mechanic, so horses retain their source defense path. */
    public static float absorb(LivingEntity entity, DamageSource source, float incoming) {
        var flags = SourceArmorDefense.flags(source);
        int resistance = !source.is(DamageTypeTags.BYPASSES_EFFECTS) && entity.hasEffect(MobEffects.DAMAGE_RESISTANCE)
                && !source.is(DamageTypeTags.BYPASSES_RESISTANCE) ? entity.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1 : 0;
        int fireResistance = entity.hasEffect(MobEffects.FIRE_RESISTANCE) && !source.is(DamageTypeTags.BYPASSES_EFFECTS)
                ? entity.getEffect(MobEffects.FIRE_RESISTANCE).getAmplifier() + 1 : 0;
        var defense = SourceArmorDefenseRules.defense(SourceArmorDefense.pieces(entity), flags, resistance, fireResistance);
        float natural = naturalDefense(entity, source);
        // rs.getWornItems includes horse barding; wh adds its integer protection directly.
        // It is a BODY item in the modern engine, not one of the four humanoid pieces.
        if (!flags.absolute() && !flags.unblockable() && !flags.bypassesMundaneArmor()
                && entity instanceof net.minecraft.world.entity.animal.horse.AbstractHorse) {
            ItemStack body=entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.BODY);
            natural+=io.github.ningmeng123.mite.registry.MiteArmorContent.horseProtection(body);
        }
        // Damage.applyArmorCalculations only grants uf (players) the 20% full-block roll.
        // NPCs always retain the source one-point floor, even when protection exceeds the hit.
        if (flags.absolute() || incoming <= 0F) return incoming;
        float remaining = Math.max(incoming - Math.max(defense.total() + natural
                - io.github.ningmeng123.mite.enchantment.R196Enchantments.level(weapon(source), entity.level(), "piercing"), 0F), 1F);
        return Math.max(remaining - SourceArmorDefense.bardingToughnessBuffer(entity, source, incoming), 1F);
    }

    /** Species-specific flat defense precedes native equipment mitigation and never substitutes for armor attributes. */
    public static float naturalArmorInput(LivingEntity entity, DamageSource source, float incoming) {
        if (!(incoming > 0F)) return incoming;
        float natural = naturalDefense(entity, source);
        return natural > 0F ? Math.max(incoming - natural, 1F) : incoming;
    }

    private static float naturalDefense(LivingEntity entity, DamageSource source) {
        var flags = SourceArmorDefense.flags(source);
        if (flags.absolute()) return 0F;
        if (entity.getType() == net.minecraft.world.entity.EntityType.WITHER) return 4F;
        if (entity.getType() == net.minecraft.world.entity.EntityType.WITCH && magic(source)
                && source.getDirectEntity() != null && source.getDirectEntity() != source.getEntity()) return 10F;
        if (flags.bypassesMundaneArmor()) return 0F;
        return entity instanceof R196Mob mob ? mob.naturalDefense(source)
                : entity instanceof net.minecraft.world.entity.monster.Slime slime ? R196Cube.vanillaNaturalDefense(slime, source) : 0F;
    }
    public static boolean managedDefense(LivingEntity entity) {
        return entity instanceof R196Mob || io.github.ningmeng123.mite.rules.R196VanillaMobRules.managed(entity.getType()) || entity.getType() == net.minecraft.world.entity.EntityType.SLIME
                || entity.getType() == net.minecraft.world.entity.EntityType.MAGMA_CUBE;
    }
}

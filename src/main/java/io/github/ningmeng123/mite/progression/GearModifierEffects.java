package io.github.ningmeng123.mite.progression;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.mixin.MobEffectDurationAccessor;
import io.github.ningmeng123.mite.rules.OreBlockRules;
import io.github.ningmeng123.mite.rules.OreCookingExperienceRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** ITE modifiers remain independent from the ordinary/legacy-affix enchantment pipelines. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class GearModifierEffects {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private GearModifierEffects() {}

    public static float miningSpeed(Player player, BlockState state, float original) {
        ItemStack tool = player.getMainHandItem();
        String material = GearProgression.material(tool);
        return GearModifierRules.miningSpeed(original, GearModifierRules.materialEfficiency(material),
                GearModifier.TOOL_EFFICIENCY.value(tool), GearModifier.TOOL_UNNATURAL.value(tool),
                GearModifierRules.materialHarvestLevel(material) - SourceBlockMaterialExperience.minimumHarvestLevel(state),
                GearModifier.TOOL_AQUADYNAMIC.value(tool), player.isInWaterOrRain());
    }

    public static double submergedSpeed(Player player, double original) {
        return GearModifier.TOOL_AQUADYNAMIC.level(player.getMainHandItem()) > 0 ? Math.max(1D, original) : original;
    }

    public static int durabilityWear(ItemStack stack, int amount) {
        boolean armor = stack.getItem() instanceof ArmorItem;
        return GearModifierRules.wear(amount, (armor ? GearModifier.ARMOR_DURABILITY : GearModifier.TOOL_DURABILITY).value(stack), armor);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        float damage = GearModifier.TOOL_DAMAGE.value(stack);
        if (damage > 0F) event.addModifier(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(GearModifier.TOOL_DAMAGE.id(), damage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        float protection = GearModifier.ARMOR_PROTECTION.value(stack);
        if (protection > 0F && stack.getItem() instanceof ArmorItem armor) {
            // EquipmentDurability's normal-priority handler applies the native wear factor afterwards.
            var id = GearModifier.ARMOR_PROTECTION.id().withSuffix("/" + armor.getType().getName());
            event.addModifier(Attributes.ARMOR, new AttributeModifier(id, protection, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.bySlot(armor.getEquipmentSlot()));
        }
    }

    /** Keep only ITE's extra protection fractional; unrelated armor retains native integer rounding. */
    public static float generalProtectionArmor(LivingEntity wearer, float nativeArmor) {
        AttributeInstance equipped = wearer.getAttribute(Attributes.ARMOR);
        if (equipped == null) return nativeArmor;
        String modifierPrefix = GearModifier.ARMOR_PROTECTION.id() + "/";
        var protection = equipped.getModifiers().stream()
                .filter(modifier -> modifier.id().toString().startsWith(modifierPrefix)).toList();
        if (protection.isEmpty()) return nativeArmor;
        // Rebuild without our modifiers so durability factors, other attribute operations and clamps are respected.
        AttributeInstance ordinary = new AttributeInstance(equipped.getAttribute(), ignored -> {});
        ordinary.setBaseValue(equipped.getBaseValue());
        for (AttributeModifier modifier : equipped.getModifiers())
            if (!protection.contains(modifier)) ordinary.addTransientModifier(modifier);
        double full = equipped.getValue(), baseline = ordinary.getValue();
        return nativeArmor + (float)(Mth.floor(baseline) + (full - baseline) - Mth.floor(full));
    }

    /** Additional armor points use the same native toughness/Breach computation as ordinary armor. */
    public static float conditionalArmor(LivingEntity wearer, DamageSource source) {
        float protection = 0F;
        boolean undead = source.getEntity() instanceof LivingEntity attacker && attacker.getType().is(EntityTypeTags.UNDEAD);
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = wearer.getItemBySlot(slot);
            if (source.is(DamageTypeTags.IS_FIRE)) protection += GearModifier.ARMOR_FIRE_PROTECTION.value(stack);
            else if (source.is(DamageTypeTags.IS_EXPLOSION)) protection += GearModifier.ARMOR_EXPLOSION_PROTECTION.value(stack);
            else if (source.is(DamageTypeTags.IS_PROJECTILE)) protection += GearModifier.ARMOR_PROJECTILE_PROTECTION.value(stack);
            if (undead) protection += GearModifier.ARMOR_BLESSED.value(stack);
        }
        return protection;
    }

    /** Both multipliers belong to the real primary melee hit, after raw source melee damage. */
    public static float meleeDamage(Player player, float damage) {
        float healthFraction = player.getMaxHealth() > 0F ? player.getHealth() / player.getMaxHealth() : 1F;
        float indomitable = GearModifierRules.indomitable(healthFraction,
                GearModifier.ARMOR_INDOMITABLE.level(player.getItemBySlot(EquipmentSlot.CHEST)) > 0);
        float demon = player.level().dimension().equals(Level.OVERWORLD) ? 1F
                : 1F + GearModifier.TOOL_DEMON_POWER.value(player.getMainHandItem());
        return damage * indomitable * demon;
    }

    /** Called only after a real primary melee hit has removed health; sweep copies cannot borrow weaving. */
    public static void slowOnHealthLoss(Player attacker, LivingEntity target) {
        if (target.level().isClientSide) return;
        int rank = GearModifier.TOOL_SLOWDOWN.level(attacker.getMainHandItem());
        if (rank > 0) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                GearModifierRules.slowTicks(rank), GearModifierRules.slowAmplifier(rank)), attacker);
    }

    @SubscribeEvent
    public static void steady(LivingKnockBackEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof Player)) return;
        float multiplier = 1F;
        for (EquipmentSlot slot : ARMOR) multiplier *= Math.max(0F,
                1F - GearModifier.ARMOR_STEADY.value(event.getEntity().getItemBySlot(slot)));
        event.setStrength(event.getStrength() * multiplier);
    }

    public static MobEffectInstance incomingEffect(LivingEntity owner, MobEffectInstance effect) {
        if (!(owner instanceof Player) || owner.level().isClientSide || effect.isInfiniteDuration()
                || effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) return effect;
        float immunity = GearModifier.ARMOR_IMMUNITY.value(owner.getItemBySlot(EquipmentSlot.HEAD));
        if (!(immunity > 0F)) return effect;
        MobEffectInstance copy = copyEffect(effect);
        ((MobEffectDurationAccessor)(Object)copy).mite$setDuration(GearModifierRules.effectDuration(effect.getDuration(), immunity));
        return copy;
    }

    private static MobEffectInstance copyEffect(MobEffectInstance effect) {
        MobEffectInstance copy = new MobEffectInstance(effect);
        copy.copyBlendState(effect);
        MobEffectInstance hidden = ((MobEffectDurationAccessor)(Object)effect).mite$getHiddenEffect();
        if (hidden != null) ((MobEffectDurationAccessor)(Object)copy).mite$setHiddenEffect(copyEffect(hidden));
        return copy;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void geology(BlockDropsEvent event) {
        float geology = GearModifier.TOOL_GEOLOGY.value(event.getTool());
        if (event.isCanceled() || !(geology > 0F) || !(event.getBreaker() instanceof Player player)
                || player.isCreative() || player.isSpectator()) return;
        BlockState state = event.getState();
        // Reference BlockOre metadata 1 is its player-placed bit. Existing gem/coal XP never gets geology XP.
        if (state.hasProperty(OreBlockRules.PLACED) && state.getValue(OreBlockRules.PLACED)
                || state.is(BlockTags.COAL_ORES) || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES) || state.is(BlockTags.REDSTONE_ORES)
                || state.is(BlockTags.LAPIS_ORES) || state.is(Blocks.NETHER_QUARTZ_ORE)) return;
        var recipe = event.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                new SingleRecipeInput(new ItemStack(state.getBlock())), event.getLevel());
        if (recipe.isEmpty()) return;
        ItemStack result = recipe.get().value().getResultItem(event.getLevel().registryAccess());
        int sourceReward = OreCookingExperienceRules.rewardPerItem(BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
        float reward = sourceReward >= 0 ? sourceReward : recipe.get().value().getExperience();
        int extra = (int)(reward * geology);
        if (extra > 0) event.setDroppedExperience((int)Math.min(Integer.MAX_VALUE, (long)event.getDroppedExperience() + extra));
    }
}

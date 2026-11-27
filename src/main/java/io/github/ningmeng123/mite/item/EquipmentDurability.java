package io.github.ningmeng123.mite.item;

import com.mojang.serialization.Codec;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.mining.GrassMining;
import io.github.ningmeng123.mite.mining.MiningProgress;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ArmorProfile;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ShieldProfile;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolProfile;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Shared source tool/armor profiles; authoritative wear retains native event and damage plumbing. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class EquipmentDurability {
    public interface Profiled { ToolProfile durabilityProfile(); }
    /** Quality-bearing source equipment that is neither a mining tool nor humanoid armor. */
    public interface QualityProfiled { Quality maximumQuality(); }

    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Mite.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> QUALITY =
            COMPONENTS.registerComponentType("quality", b -> b.persistent(Codec.intRange(0, 7))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
    private static final Map<Item, ToolProfile> TOOLS = new IdentityHashMap<>();
    private static final Map<Item, ArmorProfile> ARMOR = new IdentityHashMap<>();
    private static final Map<Item, ShieldProfile> SHIELDS = new IdentityHashMap<>();
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final ThreadLocal<LivingEntity> ARMOR_WEARER = new ThreadLocal<>();

    public static final class ArmorAttributeScope implements AutoCloseable {
        private final LivingEntity previous;
        private ArmorAttributeScope(LivingEntity wearer) {
            previous = ARMOR_WEARER.get();
            ARMOR_WEARER.set(wearer);
        }
        @Override public void close() {
            if (previous == null) ARMOR_WEARER.remove(); else ARMOR_WEARER.set(previous);
        }
    }
    public static ArmorAttributeScope armorAttributeScope(LivingEntity wearer) { return new ArmorAttributeScope(wearer); }

    private EquipmentDurability() {}

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
        registerArmorSet(1F, 2, false, Quality.FINE, Items.LEATHER_BOOTS, Items.LEATHER_LEGGINGS,
                Items.LEATHER_CHESTPLATE, Items.LEATHER_HELMET);
        registerArmorSet(8F, 8, true, Quality.MASTERWORK, Items.CHAINMAIL_BOOTS, Items.CHAINMAIL_LEGGINGS,
                Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_HELMET);
        registerArmorSet(8F, 8, false, Quality.MASTERWORK, Items.IRON_BOOTS, Items.IRON_LEGGINGS,
                Items.IRON_CHESTPLATE, Items.IRON_HELMET);
        registerArmorSet(4F, 6, false, Quality.SUPERB, Items.GOLDEN_BOOTS, Items.GOLDEN_LEGGINGS,
                Items.GOLDEN_CHESTPLATE, Items.GOLDEN_HELMET);
        // Explicit modern mappings: these equipment sets do not exist in R196.
        registerArmorSet(16F, 9, false, Quality.SUPERB, Items.DIAMOND_BOOTS, Items.DIAMOND_LEGGINGS,
                Items.DIAMOND_CHESTPLATE, Items.DIAMOND_HELMET);
        registerArmorSet(32F, 10, false, Quality.LEGENDARY, Items.NETHERITE_BOOTS, Items.NETHERITE_LEGGINGS,
                Items.NETHERITE_CHESTPLATE, Items.NETHERITE_HELMET);
        bus.addListener(EquipmentDurability::modifyArmorDefaults);
    }

    public static void registerTool(Item item, ToolProfile profile) { TOOLS.put(item, profile); }
    public static void registerArmor(Item item, ArmorProfile profile) { ARMOR.put(item, profile); }
    public static void registerShield(Item item, ShieldProfile profile) { SHIELDS.put(item, profile); }

    private static void registerArmorSet(float material, int protection, boolean chain, Quality maximum, Item... items) {
        int[] components = {4, 7, 8, 5};
        for (int i = 0; i < items.length; i++)
            registerArmor(items[i], new ArmorProfile(components[i], material, protection, chain, maximum));
    }

    private static void modifyArmorDefaults(ModifyDefaultComponentsEvent event) {
        // wp (R196 ItemBow) has quality but is not an xj mining/melee tool.
        event.modify(Items.BOW, b -> b.set(DataComponents.MAX_DAMAGE, 32));
        ARMOR.forEach((item, profile) -> event.modify(item, b -> b
                .set(DataComponents.MAX_DAMAGE, EquipmentDurabilityRules.baseArmorDurability(profile))
                .set(DataComponents.ATTRIBUTE_MODIFIERS, armorAttributes(item))));
        // Retained turtle/animal armor and addon humanoid armor share the same actual equipped attributes.
        for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM)
            if (item instanceof ArmorItem && !ARMOR.containsKey(item))
                event.modify(item, builder -> builder.set(DataComponents.ATTRIBUTE_MODIFIERS, armorAttributes(item)));
    }

    private static ItemAttributeModifiers armorAttributes(Item item) {
        if (!(item instanceof ArmorItem armor)) return item.getDefaultAttributeModifiers();
        if (item instanceof R196ArmorItem source) return R196ArmorItem.attributes(source.definition());
        if (item instanceof net.minecraft.world.item.AnimalArmorItem animal) {
            var definition = io.github.ningmeng123.mite.registry.MiteArmorContent.definition(item);
            if (definition != null && definition.horse())
                return io.github.ningmeng123.mite.registry.MiteArmorContent.horseAttributes(definition);
            float toughness = materialStats(item, armor).toughness();
            int protection = animal.getBodyType() == net.minecraft.world.item.AnimalArmorItem.BodyType.EQUESTRIAN
                    ? io.github.ningmeng123.mite.registry.MiteArmorContent.horseProtection(new ItemStack(item))
                    : armor.getMaterial().value().getDefense(ArmorItem.Type.BODY);
            var id = ResourceLocation.withDefaultNamespace("armor.body");
            var body = ItemAttributeModifiers.builder();
            for (var entry : item.getDefaultAttributeModifiers().modifiers())
                if (!entry.attribute().equals(Attributes.ARMOR) && !entry.attribute().equals(Attributes.ARMOR_TOUGHNESS))
                    body.add(entry.attribute(), entry.modifier(), entry.slot());
            return body.add(Attributes.ARMOR,
                    new AttributeModifier(id, protection, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.BODY)
                    .add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, toughness, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.BODY).build();
        }
        var stats = materialStats(item, armor);
        var builder = ItemAttributeModifiers.builder();
        // Keep independent native attributes, including netherite knockback resistance.
        for (var entry : item.getDefaultAttributeModifiers().modifiers())
            if (!entry.attribute().equals(Attributes.ARMOR) && !entry.attribute().equals(Attributes.ARMOR_TOUGHNESS))
                builder.add(entry.attribute(), entry.modifier(), entry.slot());
        var id = ResourceLocation.withDefaultNamespace("armor." + armor.getType().getName());
        var slot = EquipmentSlotGroup.bySlot(armor.getEquipmentSlot());
        int components = switch (armor.getType()) {
            case HELMET -> 5; case CHESTPLATE -> 8; case LEGGINGS -> 7; case BOOTS -> 4;
            default -> throw new IllegalArgumentException("Unexpected humanoid armor type: " + armor.getType());
        };
        builder.add(Attributes.ARMOR, new AttributeModifier(id, stats.protection(components), AttributeModifier.Operation.ADD_VALUE), slot);
        builder.add(Attributes.ARMOR_TOUGHNESS,
                new AttributeModifier(id, stats.toughness(components), AttributeModifier.Operation.ADD_VALUE), slot);
        return builder.build();
    }

    private static io.github.ningmeng123.mite.rules.ArmorBalanceRules.Stats materialStats(Item item, ArmorItem armor) {
        var definition = io.github.ningmeng123.mite.registry.MiteArmorContent.definition(item);
        String material = definition == null ? armor.getMaterial().unwrapKey().map(key -> key.location().getPath()).orElse("") : definition.material();
        boolean chain = definition == null ? material.equals("chainmail") : definition.chainmail();
        if (material.equals("chainmail")) material = "iron";
        try { return io.github.ningmeng123.mite.rules.ArmorBalanceRules.stats(material, chain); }
        catch (IllegalArgumentException unsupported) {
            // Unknown addon armor gets a conservative positive baseline without inventing armor on weapons.
            var nativeMaterial = armor.getMaterial().value();
            return new io.github.ningmeng123.mite.rules.ArmorBalanceRules.Stats(
                    Math.min(4, nativeMaterial.getDefense(ArmorItem.Type.HELMET)),
                    Math.min(8, nativeMaterial.getDefense(ArmorItem.Type.CHESTPLATE)),
                    Math.min(6, nativeMaterial.getDefense(ArmorItem.Type.LEGGINGS)),
                    Math.min(4, nativeMaterial.getDefense(ArmorItem.Type.BOOTS)),
                    Math.clamp(nativeMaterial.toughness(), .25F, 3F));
        }
    }

    public static ToolProfile toolProfile(ItemStack stack) {
        if (stack.getItem() instanceof Profiled profiled) return profiled.durabilityProfile();
        ToolProfile nativeProfile = TOOLS.get(stack.getItem());
        var external = io.github.ningmeng123.mite.registry.MiteTools.definition(stack);
        return nativeProfile != null ? nativeProfile : external == null ? null : external.profile();
    }

    public static ArmorProfile armorProfile(ItemStack stack) {
        ArmorProfile nativeProfile = ARMOR.get(stack.getItem());
        var external = io.github.ningmeng123.mite.api.MiteExtensions.externalArmor(stack.getItem());
        return nativeProfile != null ? nativeProfile : external == null ? null : external.profile();
    }
    public static ShieldProfile shieldProfile(ItemStack stack) { return SHIELDS.get(stack.getItem()); }
    public static boolean isManaged(ItemStack stack) {
        return toolProfile(stack) != null || armorProfile(stack) != null || shieldProfile(stack) != null || stack.is(Items.BOW)
                || stack.getItem() instanceof QualityProfiled;
    }

    public static Quality maximumQuality(ItemStack stack) {
        if (stack.getItem() instanceof QualityProfiled profiled) return profiled.maximumQuality();
        if (stack.is(Items.BOW)) return Quality.FINE;
        ToolProfile tool = toolProfile(stack);
        if (tool != null) return tool.maxQuality();
        ShieldProfile shield = shieldProfile(stack);
        if (shield != null) return shield.maxQuality();
        ArmorProfile armor = armorProfile(stack);
        return armor == null ? Quality.AVERAGE : armor.maxQuality();
    }

    public static Quality quality(ItemStack stack) {
        int ordinal = stack.getOrDefault(QUALITY.get(), Quality.AVERAGE.ordinal());
        return Quality.fromOrdinal(Math.min(ordinal, maximumQuality(stack).ordinal()));
    }

    public static void setQuality(ItemStack stack, Quality quality) {
        if (isManaged(stack)) stack.set(QUALITY.get(), Math.min(quality.ordinal(), maximumQuality(stack).ordinal()));
    }

    public static int qualityAdjustedMaximum(ItemStack stack, int base) {
        return isManaged(stack) && base > 0 ? EquipmentDurabilityRules.maxDurability(base, quality(stack)) : base;
    }

    public static boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        ToolProfile profile = toolProfile(stack);
        if (profile == null) return false;
        if (level.isClientSide || !stack.isDamageableItem() || exempt(miner)) return true;
        // R196 arv.isPortable=true; includes the explicitly implemented material workbenches.
        if (state.getBlock() instanceof CraftingTableBlock) return true;
        if (miner instanceof Player player && io.github.ningmeng123.mite.mining.FunctionalBlockMining.portable(player, state, level, pos)) return true;
        float hardness = MiningProgress.effectiveHardness(state, state.getDestroySpeed(level, pos));
        if (!(hardness > 0F)) return true;
        // As in vanilla, mining speed and drop eligibility are independent of block wear.
        boolean sandstone = state.is(Blocks.SANDSTONE) || state.is(Blocks.CUT_SANDSTONE)
                || state.is(Blocks.CHISELED_SANDSTONE) || state.is(Blocks.SMOOTH_SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.CUT_RED_SANDSTONE)
                || state.is(Blocks.CHISELED_RED_SANDSTONE) || state.is(Blocks.SMOOTH_RED_SANDSTONE)
                || state.is(Blocks.SANDSTONE_SLAB) || state.is(Blocks.RED_SANDSTONE_SLAB)
                || state.is(Blocks.CUT_SANDSTONE_SLAB) || state.is(Blocks.CUT_RED_SANDSTONE_SLAB)
                || state.is(Blocks.SMOOTH_SANDSTONE_SLAB) || state.is(Blocks.SMOOTH_RED_SANDSTONE_SLAB)
                || state.is(Blocks.SANDSTONE_STAIRS) || state.is(Blocks.RED_SANDSTONE_STAIRS)
                || state.is(Blocks.SMOOTH_SANDSTONE_STAIRS) || state.is(Blocks.SMOOTH_RED_SANDSTONE_STAIRS)
                || state.is(Blocks.SANDSTONE_WALL) || state.is(Blocks.RED_SANDSTONE_WALL);
        boolean specialPlant = profile.family() == ToolFamily.SCYTHE
                ? GrassMining.matches(state) || state.is(Blocks.WHEAT)
                : state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || GrassMining.matches(state)
                        || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS) || state.is(Blocks.VINE);
        hurt(stack, EquipmentDurabilityRules.blockWear(hardness, profile, sandstone, specialPlant),
                miner, EquipmentSlot.MAINHAND);
        return true;
    }

    public static void postHurtEnemy(ItemStack stack, LivingEntity attacker) {
        ToolProfile profile = toolProfile(stack);
        if (profile == null || exempt(attacker)) return;
        boolean sharpness = attacker.level().registryAccess().registry(Registries.ENCHANTMENT)
                .flatMap(r -> r.getHolder(Enchantments.SHARPNESS)).map(stack::getEnchantmentLevel).orElse(0) > 0;
        boolean vampiric = io.github.ningmeng123.mite.enchantment.R196Enchantments.level(stack, attacker.level(), "vampiric") > 0;
        hurt(stack, EquipmentDurabilityRules.attackWear(profile, vampiric, sharpness), attacker, EquipmentSlot.MAINHAND);
    }

    public static void hurt(ItemStack stack, int amount, LivingEntity owner, EquipmentSlot slot) {
        if (amount > 0 && !exempt(owner)) stack.hurtAndBreak(amount, owner, slot);
    }

    /** Preserve NeoForge's cancellable armor event, with R196's budget and random slot allocation. */
    public static boolean hurtArmor(Player player, DamageSource source, float incoming) {
        boolean managed = false;
        for (EquipmentSlot slot : ARMOR_SLOTS) managed |= armorProfile(player.getItemBySlot(slot)) != null;
        if (!managed || !onlyManagedArmor(player)) return false;
        if (!(player.level() instanceof ServerLevel) || exempt(player) || !(incoming > 0F)
                || source.is(DamageTypeTags.BYPASSES_ARMOR)) return true;
        float protection = 0F;
        for (int i = 0; i < 4; i++) {
            ItemStack stack = player.getItemBySlot(ARMOR_SLOTS[i]);
            ArmorProfile profile = armorProfile(stack);
            if (profile != null) {
                int protectionLevel = player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                        .getHolder(Enchantments.PROTECTION).map(holder ->
                                io.github.ningmeng123.mite.progression.EnchantmentSources.levels(stack, holder).totalLevel()).orElse(0);
                protection += EquipmentDurabilityRules.armorProtection(profile, stack.getDamageValue(), stack.getMaxDamage(), protectionLevel);
            }
        }
        protection = EquipmentDurabilityRules.roundedArmorProtection(protection);
        return hurtArmorSourceBudget(player, source, Math.min(protection, incoming));
    }

    /** The legacy durability budget is independent of modern armor/toughness mitigation. */
    public static boolean hurtArmorSourceBudget(Player player, DamageSource source, float budget) {
        if (!(player.level() instanceof ServerLevel) || exempt(player) || !onlyManagedArmor(player)) return true;
        boolean[] occupied = new boolean[4];
        for (int i = 0; i < 4; i++) {
            ItemStack stack = player.getItemBySlot(ARMOR_SLOTS[i]);
            occupied[i] = !stack.isEmpty() && stack.getItem() instanceof ArmorItem;
        }
        int[] wear = EquipmentDurabilityRules.distributeArmorWear(budget,
                occupied, player.getRandom()::nextInt);
        EnumMap<EquipmentSlot, ArmorHurtEvent.ArmorEntry> entries = new EnumMap<>(EquipmentSlot.class);
        for (int i = 0; i < 4; i++) {
            ItemStack stack = player.getItemBySlot(ARMOR_SLOTS[i]);
            if (stack.isEmpty()) continue;
            int amount = armorProfile(stack) != null ? wear[i] : (int)Math.max(1F, budget / 4F);
            if (!(stack.getItem() instanceof ArmorItem) || !stack.canBeHurtBy(source)) amount = 0;
            entries.put(ARMOR_SLOTS[i], new ArmorHurtEvent.ArmorEntry(stack, amount));
        }
        ArmorHurtEvent event = NeoForge.EVENT_BUS.post(new ArmorHurtEvent(entries, player, source));
        if (event.isCanceled()) return true;
        event.getArmorMap().forEach((slot, entry) -> {
            ItemStack stack = entry.armorItemStack;
            if (entry.newDamage <= 0F) return;
            boolean lava = source.is(DamageTypes.LAVA);
            boolean leather = stack.is(Items.LEATHER_BOOTS) || stack.is(Items.LEATHER_LEGGINGS)
                    || stack.is(Items.LEATHER_CHESTPLATE) || stack.is(Items.LEATHER_HELMET);
            int amount = (int)entry.newDamage;
            if (lava && !leather && armorProfile(stack) != null)
                amount = (int)Math.min((long)amount * 10L, Integer.MAX_VALUE);
            hurt(stack, amount, player, slot);
        });
        return true;
    }

    /** Armor and toughness retain full material values at every remaining durability. */
    @SubscribeEvent
    public static void wornArmorAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        boolean explicit = stack.getComponentsPatch().get(DataComponents.ATTRIBUTE_MODIFIERS) != null;
        if (explicit && stack.getItem() instanceof ArmorItem) normalizeSavedArmorBase(event);
    }

    private static boolean nativeArmorId(ResourceLocation id, ArmorItem armor) {
        return id.equals(ResourceLocation.withDefaultNamespace("armor." + armor.getType().getName()));
    }

    /** Normalize recognized saved base stats without rewriting NBT or dropping custom modifier identities. */
    private static void normalizeSavedArmorBase(ItemAttributeModifierEvent event) {
        ArmorItem armor = (ArmorItem)event.getItemStack().getItem();
        var defaults = armorAttributes(armor);
        for (var base : defaults.modifiers()) {
            if (!(base.attribute().equals(Attributes.ARMOR) || base.attribute().equals(Attributes.ARMOR_TOUGHNESS))
                    || !nativeArmorId(base.modifier().id(), armor)) continue;
            // A saved component can repeat one canonical ID in ANY, MAINHAND and the armor slot.
            // replaceModifier only replaces the final indexed entry, so remove every recognized base first.
            boolean removed = event.removeIf(entry -> entry.attribute().equals(base.attribute())
                    && entry.modifier().id().equals(base.modifier().id())
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE);
            if (removed) event.addModifier(base.attribute(), base.modifier(), base.slot());
        }
        boolean positive = event.getModifiers().stream().anyMatch(entry -> entry.attribute().equals(Attributes.ARMOR_TOUGHNESS)
                && entry.slot().test(armor.getEquipmentSlot()) && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE
                && entry.modifier().amount() > 0D);
        if (!positive) defaults.modifiers().stream().filter(entry -> entry.attribute().equals(Attributes.ARMOR_TOUGHNESS))
                .forEach(entry -> event.addModifier(entry.attribute(), entry.modifier(), entry.slot()));
    }

    private static boolean onlyManagedArmor(Player player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof ArmorItem && armorProfile(stack) == null) return false;
        }
        return true;
    }

    private static boolean exempt(LivingEntity owner) {
        return owner instanceof Player player && (player.isCreative() || player.isSpectator());
    }
}

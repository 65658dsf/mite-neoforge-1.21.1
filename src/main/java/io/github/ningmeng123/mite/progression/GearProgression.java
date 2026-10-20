package io.github.ningmeng123.mite.progression;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.combat.ShieldGuard;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.enchantment.ConfiguredEnchantments;
import io.github.ningmeng123.mite.item.R196ReinforcedBowItem;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Authoritative growth service; never writes the ordinary enchantments component. @author NingMeng123 */
public final class GearProgression {
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Mite.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GearProgress>> PROGRESS =
            COMPONENTS.registerComponentType("gear_progress", builder -> builder.persistent(GearProgress.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(GearProgress.CODEC)));
    private GearProgression() {}
    public static void register(IEventBus bus) { COMPONENTS.register(bus); }
    public static GearProgress state(ItemStack stack) { return stack.getOrDefault(PROGRESS.get(), GearProgress.EMPTY); }
    public static UUID id(ItemStack stack) {
        UUID id = state(stack).id();
        return GearProgress.UNASSIGNED.equals(id) ? null : id;
    }
    public static UUID ensureId(ItemStack stack) {
        if (maxLevel(stack) == 0) return null;
        GearProgress state = state(stack);
        if (!GearProgress.UNASSIGNED.equals(state.id())) return state.id();
        UUID id = UUID.randomUUID();
        stack.set(PROGRESS.get(), new GearProgress(id, state.level(), state.experienceMillis(), state.affixes(), 1));
        return id;
    }
    public static int affixLevel(ItemStack stack, ResourceLocation enchantment) {
        return stack.isEmpty() ? 0 : ConfiguredEnchantments.effectiveLevel(enchantment.toString(),
                state(stack).affixes().getOrDefault(enchantment, 0));
    }
    public static boolean canGain(Player player) {
        return player instanceof ServerPlayer && !player.isCreative() && !player.isSpectator();
    }
    public static String material(ItemStack stack) {
        if (stack.isEmpty() || RemovedDiamondEquipment.isRemoved(stack)) return null;
        var simply = SimplySwordsCompat.definition(stack);
        if (simply != null) return simply.material();
        var tool = MiteTools.definition(stack);
        if (tool != null) return tool.material();
        var armor = MiteArmorContent.definition(stack.getItem());
        if (armor != null) return armor.horse() ? null : armor.material();
        var shield = MiteShields.definition(stack);
        if (shield != null) return shield.material();
        if (stack.getItem() instanceof R196ReinforcedBowItem bow) return bow.material();
        if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem) return "wood";
        if (stack.is(Items.TRIDENT) || stack.is(Items.MACE)) return "special";
        if (stack.getItem() instanceof AnimalArmorItem) return null;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!itemId.getNamespace().equals("minecraft")) return null;
        if (!(stack.getItem() instanceof ArmorItem) && R196EnchantmentCatalog.family(stack) == null) return null;
        String name = itemId.getPath();
        if (name.startsWith("golden_")) return "gold";
        if (name.startsWith("chainmail_")) return "iron";
        if (name.startsWith("wooden_")) return "wood";
        for (String material : List.of("leather", "stone", "iron", "netherite"))
            if (name.startsWith(material + "_")) return material;
        return null;
    }
    public static int maxLevel(ItemStack stack) { return Math.clamp(io.github.ningmeng123.mite.api.MiteExtensions.policy().gearMaximumLevel(stack, GearProgressionRules.maximumLevel(material(stack))), 0, 28); }
    public static boolean isArmor(ItemStack stack) {
        return maxLevel(stack) > 0 && stack.getItem() instanceof ArmorItem && !(stack.getItem() instanceof AnimalArmorItem);
    }
    public static boolean isShield(ItemStack stack) { return maxLevel(stack) > 0 && MiteShields.definition(stack) != null; }
    public static boolean canTill(ItemStack stack) {
        ToolFamily family = R196EnchantmentCatalog.family(stack);
        return maxLevel(stack) > 0 && (family == ToolFamily.HOE || family == ToolFamily.MATTOCK);
    }
    public static boolean canKill(ItemStack stack) {
        if (maxLevel(stack) == 0) return false;
        if (SimplySwordsCompat.definition(stack) != null || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem || stack.is(Items.TRIDENT) || stack.is(Items.MACE)) return true;
        ToolFamily family = R196EnchantmentCatalog.family(stack);
        return family != null && switch (family) {
            case SWORD, DAGGER, KNIFE, CLUB, CUDGEL, AXE, HATCHET, BATTLE_AXE, WAR_HAMMER, SCYTHE -> true;
            default -> false;
        };
    }
    public static boolean canMine(ItemStack stack, BlockState state, Level level, BlockPos pos) {
        if (maxLevel(stack) == 0 || SimplySwordsCompat.definition(stack) != null) return false;
        ToolFamily family = R196EnchantmentCatalog.family(stack);
        return family != null && switch (family) {
            case PICKAXE, AXE, HATCHET, BATTLE_AXE, WAR_HAMMER, SHOVEL, MATTOCK, SHEARS, SCYTHE, KNIFE, DAGGER ->
                    stack.getDestroySpeed(state) > 1F && (!state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state));
            default -> false;
        };
    }

    public static long nextLevelExperienceMillis(ItemStack stack, int level) {
        int armorSlot = stack.getItem() instanceof ArmorItem armor && !(armor instanceof AnimalArmorItem)
                ? switch (armor.getType()) {
                    case HELMET -> 0;
                    case CHESTPLATE -> 1;
                    case LEGGINGS -> 2;
                    case BOOTS -> 3;
                    default -> -1;
                } : -1;
        return GearProgressionRules.nextLevelExperienceMillis(material(stack), level, usesWeaponExperience(stack), armorSlot);
    }

    /** Experience follows the item's earning role independently of its modifier candidates. */
    private static boolean usesWeaponExperience(ItemStack stack) {
        if (SimplySwordsCompat.definition(stack) != null || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem || stack.is(Items.TRIDENT) || stack.is(Items.MACE)) return true;
        ToolFamily family = R196EnchantmentCatalog.family(stack);
        return family != null && switch (family) {
            case SWORD, DAGGER, KNIFE, CLUB, CUDGEL, BATTLE_AXE, WAR_HAMMER -> true;
            default -> false;
        };
    }

    /** The book-support leather exception is shared here without changing the enchanting table. */
    public static boolean supportsAffix(ItemStack stack, Holder<Enchantment> enchantment) {
        if (!ConfiguredEnchantments.enabled(enchantment)) return false;
        if (enchantment.is(EnchantmentTags.CURSE) || enchantment.is(net.minecraft.world.item.enchantment.Enchantments.MENDING)) return false;
        if (enchantment.unwrapKey().map(key -> io.github.ningmeng123.mite.api.MiteExtensions.externalEnchantments().stream().anyMatch(definition -> definition.resourceId().equals(key.location().toString()))).orElse(false))
            return enchantment.unwrapKey().map(key -> io.github.ningmeng123.mite.api.MiteExtensions.policy().enchantmentApplicable(key.location().toString(),stack,false)).orElse(false);
        if (enchantment.value().canEnchant(stack)) return true;
        return enchantment.unwrapKey().map(key -> key.location().equals(ResourceLocation.withDefaultNamespace("unbreaking")))
                .orElse(false) && isArmor(stack) && "leather".equals(material(stack));
    }

    /** Historical enchantment-affix introspection; these entries are never rolled by new upgrades. */
    public static List<Holder<Enchantment>> legacyCandidates(ItemStack stack, Level level) {
        List<Holder<Enchantment>> candidates = new ArrayList<>();
        level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                .filter(holder -> supportsAffix(stack, holder) && EnchantmentSources.isSupported(holder)
                        && affixDefinition(stack, holder).weight() > 0
                        && affixLevel(stack, holder.unwrapKey().orElseThrow().location())
                                < affixDefinition(stack, holder).maximumLevel()).forEach(candidates::add);
        candidates.sort(Comparator.comparing(holder -> holder.unwrapKey().orElseThrow().location().toString()));
        return candidates;
    }

    public static List<GearModifier> modifierCandidates(ItemStack stack) {
        return GearModifier.candidates(stack, state(stack).affixes());
    }

    public static List<GearModifier> candidates(ItemStack stack, Level level) { return modifierCandidates(stack); }

    public static GearAffixRules.Definition affixDefinition(ItemStack stack, Holder<Enchantment> enchantment) {
        var definition = io.github.ningmeng123.mite.api.MiteExtensions.policy().affixDefinition(stack,enchantment,GearAffixRules.definition(enchantment.unwrapKey().orElseThrow().location().toString(), isArmor(stack),
                enchantment.value().getWeight(), enchantment.value().getMaxLevel()));
        return new GearAffixRules.Definition(definition.weight(), io.github.ningmeng123.mite.config.MiteConfig.enchantmentMaxLevel(
                enchantment.unwrapKey().orElseThrow().location().toString(), definition.maximumLevel()));
    }

    public static void award(ItemStack stack, long experienceMillis, ServerPlayer player) {
        if (!canGain(player) || experienceMillis <= 0 || stack.isEmpty()) return;
        experienceMillis = Math.max(0L,io.github.ningmeng123.mite.api.MiteExtensions.policy().gearExperience(stack,experienceMillis));
        if (experienceMillis==0) return;
        int cap = maxLevel(stack);
        GearProgress before = state(stack);
        if (cap == 0 || before.level() >= cap) return;
        UUID id = ensureId(stack);
        int level = before.level();
        long experience = GearProgressionRules.saturatedAdd(before.experienceMillis(), experienceMillis);
        var affixes = new HashMap<>(before.affixes());
        while (level < cap) {
            long required = nextLevelExperienceMillis(stack, level);
            if (experience < required) break;
            experience -= required;
            level++;
            List<GearModifier> candidates = GearModifier.candidates(stack, affixes);
            if (!candidates.isEmpty()) {
                int[] weights = candidates.stream().mapToInt(GearModifier::weight).toArray();
                GearModifier selected = candidates.get(GearAffixRules.selectIndex(weights,
                        player.getRandom().nextInt(GearAffixRules.totalWeight(weights))));
                affixes.merge(selected.id(), 1, Integer::sum);
            }
        }
        if (level >= cap) experience = 0;
        stack.set(PROGRESS.get(), new GearProgress(id, level, experience, affixes, 1));
        if (level != before.level()) player.displayClientMessage(
                Component.translatable("message.mite.gear_level_up", stack.getHoverName(), level, cap), true);
        player.getInventory().setChanged();
    }

    /** Maintenance never merges, transfers or rerolls the sacrificial item's progression. */
    public static void preserve(ItemStack source, ItemStack result) {
        io.github.ningmeng123.mite.combat.ShieldGuard.preserve(source, result);
        if (!source.isEmpty() && !result.isEmpty()) io.github.ningmeng123.mite.api.MiteExtensions.preserve(source, result);
        if (source.isEmpty() || result.isEmpty()) return;
        GearProgress progress = source.get(PROGRESS.get());
        // An optional extension can lower a saved item's cap to zero; maintenance still keeps its history.
        if (progress == null && maxLevel(result) == 0) return;
        if (progress == null) result.remove(PROGRESS.get()); else result.set(PROGRESS.get(), progress);
    }

    /** XP-only changes must not restart hand animations or mining; other components still matter. */
    public static boolean growthChanged(ItemStack first, ItemStack second) {
        return !java.util.Objects.equals(first.get(PROGRESS.get()), second.get(PROGRESS.get()));
    }
    public static boolean guardChanged(ItemStack first, ItemStack second) {
        return !java.util.Objects.equals(first.get(ShieldGuard.GUARD.get()), second.get(ShieldGuard.GUARD.get()));
    }
    public static boolean sameIdentity(ItemStack first, ItemStack second) {
        UUID previous = id(first), next = id(second);
        if (previous != null) return previous.equals(next);
        return next == null || state(first).equals(GearProgress.EMPTY);
    }
    public static boolean sameIgnoringGrowth(ItemStack first, ItemStack second) {
        if (!first.is(second.getItem()) || !sameIdentity(first, second)) return false;
        ItemStack left = first.copy(), right = second.copy();
        left.remove(PROGRESS.get());
        right.remove(PROGRESS.get());
        return ItemStack.isSameItemSameComponents(left, right);
    }

    /** Display-only bookkeeping never changes mining's stricter component comparison. */
    public static boolean sameIgnoringProgressionDisplay(ItemStack first, ItemStack second) {
        if (!first.is(second.getItem()) || !sameIdentity(first, second)) return false;
        ItemStack left = first.copy(), right = second.copy();
        left.remove(PROGRESS.get()); right.remove(PROGRESS.get());
        left.remove(ShieldGuard.GUARD.get()); right.remove(ShieldGuard.GUARD.get());
        return ItemStack.isSameItemSameComponents(left, right);
    }

    public static boolean keepProgressionHandPose(ItemStack previous, ItemStack next, boolean slotChanged) {
        return !slotChanged && (growthChanged(previous, next) || guardChanged(previous, next))
                && previous.getCount() == next.getCount() && sameIgnoringProgressionDisplay(previous, next);
    }
}

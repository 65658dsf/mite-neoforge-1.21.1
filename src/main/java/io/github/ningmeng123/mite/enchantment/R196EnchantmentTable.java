package io.github.ningmeng123.mite.enchantment;

import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.FunctionalBlockRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** vm/aaw source table arithmetic and weighted draws; modern-only equipment retains its native route. @author NingMeng123 */
public final class R196EnchantmentTable {
    private R196EnchantmentTable() {}

    public static boolean isSourceEquipment(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem()) return false;
        var item = stack.getItem();
        if (item instanceof CrossbowItem || item instanceof TridentItem || item instanceof MaceItem
                || item instanceof ShieldItem && io.github.ningmeng123.mite.registry.MiteShields.definition(stack) == null
                || item instanceof ElytraItem) return false;
        return R196EnchantmentCatalog.definitions().stream().anyMatch(definition -> definition.supported(stack));
    }

    public static boolean water(ItemStack stack) {
        return stack.is(Items.POTION) && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
    }

    public static boolean special(ItemStack stack) { return water(stack) || stack.is(Items.GOLDEN_APPLE); }
    public static boolean sourceRoute(ItemStack stack) {
        return stack.isEmpty() || stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK) || special(stack) || isSourceEquipment(stack);
    }
    public static boolean enchantable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(Items.BOOK) || special(stack)) return true;
        return isSourceEquipment(stack) && stack.getMaxStackSize() == 1 && enchantability(stack) > 0 && !stack.isEnchanted();
    }
    public static int enchantability(ItemStack stack) {
        if (stack.is(Items.BOOK)) return 30;
        if (stack.is(Items.BOW)) return 10;
        if (stack.is(Items.IRON_HELMET) || stack.is(Items.IRON_CHESTPLATE)
                || stack.is(Items.IRON_LEGGINGS) || stack.is(Items.IRON_BOOTS)) return 30;
        if (stack.is(Items.LEATHER_HELMET) || stack.is(Items.LEATHER_CHESTPLATE)
                || stack.is(Items.LEATHER_LEGGINGS) || stack.is(Items.LEATHER_BOOTS)) return 10;
        var armor = MiteArmorContent.definition(stack.getItem());
        if (armor != null) return armor.enchantability();
        var rod = MiteRangedItems.rodMaterial(stack);
        // xv has both wood and hook materials and chooses the most enchantable one.
        if (rod != null) return Math.max(10, rod.enchantability);
        return stack.getEnchantmentValue();
    }
    /** User-requested modern cost: choices one, two and three consume that many lapis. */
    public static boolean hasLapis(ItemStack stack, int slot) {
        return slot >= 0 && slot < 3 && stack.is(Items.LAPIS_LAZULI) && stack.getCount() >= slot + 1;
    }
    public static int experienceCost(int energy) { return Math.multiplyExact(Math.max(0, energy), 100); }
    public static boolean isTable(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE)
                || level.getBlockState(pos).is(MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get());
    }
    public static boolean emerald(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get());
    }
    public static boolean passable(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        var state = level.getBlockState(pos);
        // Source tests solid material, including non-full solids; fluid never transmits power.
        return state.getFluidState().isEmpty() && (state.isAir() || !state.blocksMotion());
    }
    public static int accessibleBookshelves(Level level, BlockPos table) {
        int shelves = 0;
        for (int dy = 0; dy <= 1; dy++) {
            if (dy == 1 && !passable(level, table.above())) break;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2 || Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                boolean accessible;
                if (dx == 0 || dz == 0) {
                    accessible = passable(level, table.offset(dx / 2, dy, dz / 2));
                } else {
                    int sx = Integer.signum(dx), sz = Integer.signum(dz);
                    accessible = passable(level, table.offset(sx, dy, sz))
                            && (passable(level, table.offset(sx, dy, 0)) || passable(level, table.offset(0, dy, sz)));
                }
                if (accessible && level.getBlockState(table.offset(dx, dy, dz)).is(Blocks.BOOKSHELF)) shelves++;
            }
        }
        return shelves;
    }
    public static int energy(ItemStack stack, int shelves, boolean emerald, int slot, float noise) {
        if (!enchantable(stack)) return 0;
        if (special(stack)) return 2;
        return FunctionalBlockRules.enchantmentLevels(shelves, emerald, enchantability(stack), slot, noise);
    }

    private record Candidate(R196EnchantmentCatalog.Definition definition, EnchantmentInstance enchantment) {}

    /** Already-compressed table energy enters this method exactly once, unlike loot/mob helper inputs. */
    public static List<EnchantmentInstance> select(RegistryAccess access, ItemStack stack, int energy, RandomSource random) {
        return select(stack, energy, random, access.registryOrThrow(Registries.ENCHANTMENT).holders()
                .map(holder -> (Holder<Enchantment>)holder).toList());
    }

    /** Source loot/mob entry point applies material compression before, rather than twice during, the draw. */
    public static List<EnchantmentInstance> randomEnchantments(ItemStack stack, int rawEnergy, RandomSource random, List<Holder<Enchantment>> allowed) {
        int energy = FunctionalBlockRules.enchantabilityAdjustedPower(rawEnergy, enchantability(stack));
        return energy < 1 ? List.of() : select(stack, energy, random, allowed);
    }

    private static List<EnchantmentInstance> select(ItemStack stack, int energy, RandomSource random, List<Holder<Enchantment>> allowed) {
        if (energy < 1 || enchantability(stack) < 1) return List.of();
        int remaining = Math.max(1, (int)(energy * (1F + (random.nextFloat() - .5F) * .5F)));
        List<Candidate> selected = new ArrayList<>();
        Map<String, Holder<Enchantment>> holders = allowed.stream().filter(holder -> holder.unwrapKey().isPresent())
                .collect(Collectors.toMap(holder -> holder.unwrapKey().orElseThrow().location().toString(), Function.identity(), (first, second) -> first));
        while (remaining > 0) {
            List<Candidate> candidates = new ArrayList<>();
            for (var definition : R196EnchantmentCatalog.definitions()) {
                if (!stack.is(Items.BOOK) && !definition.supported(stack)) continue;
                if (selected.stream().anyMatch(previous -> !R196EnchantmentCatalog.compatible(previous.definition().id(), definition.id()))) continue;
                var holder = holders.get(definition.resourceId());
                if (holder == null) continue;
                for (int level = definition.maxLevel(); level >= 1; level--) {
                    if (definition.minCost(level) <= remaining) {
                        candidates.add(new Candidate(definition, new EnchantmentInstance(holder, level)));
                        break;
                    }
                }
            }
            if (candidates.isEmpty()) break;
            int draw = random.nextInt(candidates.stream().mapToInt(candidate -> candidate.definition().weight()).sum());
            Candidate chosen = candidates.getFirst();
            for (Candidate candidate : candidates) {
                draw -= candidate.definition().weight();
                if (draw < 0) { chosen = candidate; break; }
            }
            if (selected.size() < 2 && candidates.size() > 1 && chosen.definition().maxLevel() > 1 && random.nextInt(2) == 0)
                chosen = new Candidate(chosen.definition(), new EnchantmentInstance(chosen.enchantment().enchantment, random.nextInt(chosen.enchantment().level) + 1));
            selected.add(chosen);
            remaining -= chosen.definition().minCost(chosen.enchantment().level) + 5;
            if (remaining < 5 || selected.size() > 2) break;
        }
        List<EnchantmentInstance> shuffled = new ArrayList<>();
        while (!selected.isEmpty()) shuffled.add(selected.remove(random.nextInt(selected.size())).enchantment());
        return shuffled;
    }

    public static ItemStack output(RegistryAccess access, ItemStack input, int energy, RandomSource random) {
        if (water(input)) return new ItemStack(Items.EXPERIENCE_BOTTLE);
        if (input.is(Items.GOLDEN_APPLE)) return new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
        List<EnchantmentInstance> selected = select(access, input, energy, random);
        if (selected.isEmpty()) return ItemStack.EMPTY;
        if (input.is(Items.BOOK)) selected = List.of(selected.get(random.nextInt(selected.size())));
        ItemStack result = input.is(Items.BOOK) ? input.transmuteCopy(Items.ENCHANTED_BOOK, 1) : input.copyWithCount(1);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var entry : selected) enchantments.set(entry.enchantment, entry.level);
        result.set(input.is(Items.BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        return result;
    }
}

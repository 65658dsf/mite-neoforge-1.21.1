package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import io.github.ningmeng123.mite.rules.SourceVillagerTradeRules;
import io.github.ningmeng123.mite.rules.SourceVillagerTradeRules.Listing;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/** Adds source stock to modern professions without replacing native offers or trade transactions. @author NingMeng123 */
public final class SourceVillagerTrades {
    public static final String STATE = "mite_source_villager_trades";
    private SourceVillagerTrades() {}

    public static void ensure(Villager villager, MerchantOffers offers) {
        if (villager.level().isClientSide || villager.isBaby()) return;
        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        if (professionId == null || !professionId.getNamespace().equals("minecraft")) return;
        List<Listing> pool = SourceVillagerTradeRules.listings(professionId.getPath());
        if (pool.isEmpty()) return;
        CompoundTag state = villager.getPersistentData().getCompound(STATE);
        if (!state.getString("profession").equals(professionId.toString())) {
            state = new CompoundTag();
            state.putString("profession", professionId.toString());
        }
        int level = villager.getVillagerData().getLevel();
        if (state.getInt("level") >= level) return;
        ListTag known = state.getList("known", 8);
        // One source q(1) attempt per modern rank; existing villagers receive their attained ranks once.
        for (int rank=state.getInt("level")+1; rank<=level; rank++) {
            List<Listing> candidates = new ArrayList<>();
            for (Listing listing : pool) if (villager.getRandom().nextFloat()
                    < SourceVillagerTradeRules.probability(listing.probability(), known.size())) candidates.add(listing);
            if (candidates.isEmpty()) {
                Listing fallback = new Listing("fallback_gold",3,professionId.getPath(),SourceVillagerTradeRules.Kind.BUY,
                        "minecraft:gold_ingot",1F,4,8);
                candidates.add(fallback);
            }
            // Uniform selection from the accepted pool is equivalent to source shuffle + first.
            Listing chosen = candidates.get(villager.getRandom().nextInt(candidates.size()));
            StringTag key = StringTag.valueOf(chosen.id());
            if (known.contains(key)) continue;
            MerchantOffer offer = create(villager, chosen, villager.getRandom());
            if (offer != null) { offers.add(offer); known.add(key); }
        }
        state.putInt("level", level);
        state.put("known", known);
        villager.getPersistentData().put(STATE,state);
    }

    public static MerchantOffer create(Villager villager, Listing listing, RandomSource random) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(listing.item()));
        if (item == Items.AIR) throw new IllegalStateException("Missing source villager item: "+listing.item());
        return switch (listing.kind()) {
            case BUY -> offer(new ItemCost(item,SourceVillagerTradeRules.purchaseCount(listing,
                    new ItemStack(item).getMaxStackSize(),random::nextInt)),Optional.empty(),new ItemStack(Items.EMERALD));
            case SELL -> {
                int price = SourceVillagerTradeRules.count(listing.minimum(),listing.maximum(),random::nextInt);
                ItemStack output = new ItemStack(item,price < 0 ? Math.min(-price,new ItemStack(item).getMaxStackSize()) : 1);
                yield offer(new ItemCost(Items.EMERALD,price < 0 ? 1 : Math.min(price,new ItemStack(Items.EMERALD).getMaxStackSize())),
                        Optional.empty(),output);
            }
            case GRAVEL -> offer(new ItemCost(Items.GRAVEL,4),Optional.of(new ItemCost(Items.EMERALD)),
                    new ItemStack(Items.FLINT,SourceVillagerTradeRules.count(4,6,random::nextInt)));
            case BOOK -> {
                var definitions = R196EnchantmentCatalog.definitions();
                var definition = definitions.get(random.nextInt(definitions.size()));
                int level = random.nextInt(definition.maxLevel())+1;
                int emeralds = 2+random.nextInt(5+level*10)+3*level;
                var payment = SourceVillagerTradeRules.bookPayment(emeralds,new ItemStack(Items.EMERALD).getMaxStackSize());
                Holder<Enchantment> holder = villager.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                        .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT,ResourceLocation.parse(definition.resourceId())));
                var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                enchantments.set(holder,level);
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                book.set(DataComponents.STORED_ENCHANTMENTS,enchantments.toImmutable());
                yield payment.consumesBook()
                        ? offer(new ItemCost(Items.BOOK),Optional.of(new ItemCost(Items.EMERALD,payment.secondEmeralds())),book)
                        : offer(new ItemCost(Items.EMERALD,payment.firstEmeralds()),
                                Optional.of(new ItemCost(Items.EMERALD,payment.secondEmeralds())),book);
            }
            case ENCHANT -> {
                int price = SourceVillagerTradeRules.count(2,5,random::nextInt);
                ItemStack output = new ItemStack(item);
                List<Holder<Enchantment>> allowed = villager.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                        .holders().map(holder -> (Holder<Enchantment>)holder).toList();
                var selected = R196EnchantmentTable.randomEnchantments(output,5+random.nextInt(15),random,allowed);
                var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                for (var entry : selected) enchantments.set(entry.enchantment,entry.level);
                output.set(DataComponents.ENCHANTMENTS,enchantments.toImmutable());
                yield offer(new ItemCost(item),Optional.of(new ItemCost(Items.EMERALD,price)),output);
            }
        };
    }

    private static MerchantOffer offer(ItemCost first, Optional<ItemCost> second, ItemStack output) {
        // Source offers start with seven uses. Modern restock, merchant XP and reputation discounts remain native.
        return new MerchantOffer(first,second,output,7,1,0F);
    }
}

package io.github.ningmeng123.mite.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import java.util.Set;

/** Source manufacturing metadata and vessel accounting shared by all food recipes. @author NingMeng123 */
public interface FoodManufacturingRecipe {
    Metadata manufacturing();

    record Metadata(float difficulty, String requiredBench, int sourceOrder, boolean outputContainsBowl, boolean anyMilkBuckets) {
        public Metadata {
            if (!Float.isFinite(difficulty) || difficulty < 0F) throw new IllegalArgumentException("Invalid food difficulty");
            if (requiredBench == null) requiredBench = "";
            if (!requiredBench.isEmpty() && WorkbenchRules.materialCapacity(requiredBench) <= 0F)
                throw new IllegalArgumentException("Unknown manufacturing material: " + requiredBench);
            if (sourceOrder < 0) throw new IllegalArgumentException("Missing source recipe order");
        }

        public static final Codec<Metadata> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("difficulty").forGetter(Metadata::difficulty),
                Codec.STRING.optionalFieldOf("required_bench", "").forGetter(Metadata::requiredBench),
                Codec.INT.fieldOf("source_order").forGetter(Metadata::sourceOrder),
                Codec.BOOL.optionalFieldOf("output_contains_bowl", false).forGetter(Metadata::outputContainsBowl),
                Codec.BOOL.optionalFieldOf("any_milk_buckets", false).forGetter(Metadata::anyMilkBuckets)
        ).apply(instance, Metadata::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Metadata> STREAM_CODEC = new StreamCodec<>() {
            @Override public Metadata decode(RegistryFriendlyByteBuf buffer) {
                return new Metadata(buffer.readFloat(), buffer.readUtf(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean());
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, Metadata value) {
                buffer.writeFloat(value.difficulty());
                buffer.writeUtf(value.requiredBench());
                buffer.writeVarInt(value.sourceOrder());
                buffer.writeBoolean(value.outputContainsBowl());
                buffer.writeBoolean(value.anyMilkBuckets());
            }
        };

        public TimedCraftingRecipes.Rule rule() {
            return new TimedCraftingRecipes.Rule(difficulty, requiredBench.isEmpty() ? null : requiredBench, sourceOrder);
        }
    }

    Set<String> SOURCE_MILK_BUCKETS = Set.of("copper_milk_bucket", "silver_milk_bucket", "gold_milk_bucket",
            "mithril_milk_bucket", "adamantium_milk_bucket", "ancient_metal_milk_bucket");

    /** aaj:30-69 accepts mixed metals when every source ingredient is a milk bucket. */
    static boolean isSourceMilkBucket(ItemStack stack) {
        if (stack.is(Items.MILK_BUCKET)) return true;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("mite") && SOURCE_MILK_BUCKETS.contains(id.getPath());
    }

    /**
     * wd:121-122 suppresses the empty vessel when the product already contains the same vessel class.
     * Buckets still return when converted to bowls; milk/water bowls return when used for solid foods.
     */
    static NonNullList<ItemStack> remainders(CraftingInput input, Metadata metadata) {
        NonNullList<ItemStack> result = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack ingredient = input.getItem(slot);
            if (!ingredient.hasCraftingRemainingItem()) continue;
            ItemStack remaining = ingredient.getCraftingRemainingItem();
            if (metadata.outputContainsBowl() && remaining.is(Items.BOWL)) continue;
            result.set(slot, remaining);
        }
        return result;
    }
}

package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.R196ArrowItem;
import io.github.ningmeng123.mite.item.R196FishingRodItem;
import io.github.ningmeng123.mite.item.R196CarrotOnAStickItem;
import io.github.ningmeng123.mite.crafting.RangedRodConversionRecipe;
import io.github.ningmeng123.mite.ranged.ArrowRecovery;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Registered R196 ammunition and nine fishing hook materials, with retained vanilla IDs. @author NingMeng123 */
public final class MiteRangedItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Mite.MOD_ID);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RangedRodConversionRecipe>> ROD_CONVERSION =
            SERIALIZERS.register("rod_conversion", RangedRodConversionRecipe.Serializer::new);
    static {
        for (Material material : Material.values()) {
            if (material != Material.FLINT) ITEMS.registerItem(arrowId(material).getPath(), props -> new R196ArrowItem(material, props));
            if (material.hasRod() && material != Material.IRON)
                ITEMS.registerItem(rodId(material).getPath(), props -> new R196FishingRodItem(material, props));
            if (material.hasRod() && material != Material.IRON)
                ITEMS.registerItem(carrotRodId(material).getPath(), props -> new R196CarrotOnAStickItem(material, props));
        }
    }
    private MiteRangedItems() {}
    public static ResourceLocation arrowId(Material material) {
        return material == Material.FLINT ? ResourceLocation.withDefaultNamespace("arrow")
                : ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, (material == Material.GOLD ? "golden" : material.id) + "_arrow");
    }
    public static ResourceLocation rodId(Material material) {
        if (!material.hasRod()) throw new IllegalArgumentException("R196 has no rusted-iron fishing rod");
        return material == Material.IRON ? ResourceLocation.withDefaultNamespace("fishing_rod")
                : ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "fishing_rod_" + material.id + "_hook");
    }
    public static Item arrow(Material material) { return BuiltInRegistries.ITEM.get(arrowId(material)); }
    public static Item rod(Material material) { return BuiltInRegistries.ITEM.get(rodId(material)); }
    public static ResourceLocation carrotRodId(Material material) {
        if (!material.hasRod()) throw new IllegalArgumentException("No rusted carrot rod");
        return material == Material.IRON ? ResourceLocation.withDefaultNamespace("carrot_on_a_stick")
                : ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "carrot_on_a_stick_" + material.id + "_hook");
    }
    public static Item carrotRod(Material material) { return BuiltInRegistries.ITEM.get(carrotRodId(material)); }
    public static Material carrotRodMaterial(ItemStack stack) {
        if (stack.is(Items.CARROT_ON_A_STICK)) return Material.IRON;
        return stack.getItem() instanceof R196CarrotOnAStickItem rod ? rod.material() : null;
    }
    public static List<Material> rods() { return Arrays.stream(Material.values()).filter(Material::hasRod).toList(); }
    public static Material arrowMaterial(ItemStack stack) {
        if (stack.is(Items.ARROW)) return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).equals(PotionContents.EMPTY)
                ? Material.FLINT : null;
        return stack.getItem() instanceof R196ArrowItem arrow ? arrow.material() : null;
    }
    public static Material rodMaterial(ItemStack stack) {
        if (stack.is(Items.FISHING_ROD)) return Material.IRON;
        return stack.getItem() instanceof R196FishingRodItem rod ? rod.material() : null;
    }
    public static Item arrowhead(Material material) {
        String id = switch (material) {
            case FLINT -> "mite:flint_chip";
            case OBSIDIAN -> "mite:obsidian_shard";
            case GOLD -> "minecraft:gold_nugget";
            case IRON -> "minecraft:iron_nugget";
            case RUSTED_IRON -> throw new IllegalArgumentException("No R196 rusted arrow recipe");
            default -> "mite:" + material.id + "_nugget";
        };
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }
    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        SERIALIZERS.register(bus);
        ArrowRecovery.register(bus);
        bus.addListener(MiteRangedItems::defaults);
        bus.addListener(MiteRangedItems::setup);
    }
    private static void defaults(ModifyDefaultComponentsEvent event) {
        event.modify(Items.ARROW, builder -> builder.set(DataComponents.MAX_STACK_SIZE, 16));
        event.modify(Items.FISHING_ROD, builder -> builder.set(DataComponents.MAX_DAMAGE, 16));
    }
    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (Material material : Material.values()) if (material != Material.FLINT) DispenserBlock.registerProjectileBehavior(arrow(material));
        });
    }
}

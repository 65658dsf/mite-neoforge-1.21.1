package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.phantom.PhantomBladeItem;
import io.github.ningmeng123.mite.phantom.PhantomBladeProjectile;
import io.github.ningmeng123.mite.phantom.PhantomBladeRecipe;
import io.github.ningmeng123.mite.phantom.PhantomBladeSwarm;
import io.github.ningmeng123.mite.rules.PhantomBladeRules;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All nine metal formation cores and their synchronized, nonpersistent visual/combat entities. @author NingMeng123 */
public final class MitePhantomBlades {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Mite.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Mite.MOD_ID);
    public static final Map<String, DeferredItem<PhantomBladeItem>> BLADES = new LinkedHashMap<>();
    public static final DeferredHolder<EntityType<?>, EntityType<PhantomBladeSwarm>> SWARM = ENTITIES.register("phantom_blade_swarm", () ->
            EntityType.Builder.<PhantomBladeSwarm>of(PhantomBladeSwarm::new, MobCategory.MISC).sized(.1F, .1F)
                    .clientTrackingRange(8).updateInterval(1).noSave().noSummon().build("mite:phantom_blade_swarm"));
    public static final DeferredHolder<EntityType<?>, EntityType<PhantomBladeProjectile>> PROJECTILE = ENTITIES.register("phantom_blade_projectile", () ->
            EntityType.Builder.<PhantomBladeProjectile>of(PhantomBladeProjectile::new, MobCategory.MISC).sized(.2F, .2F)
                    .clientTrackingRange(8).updateInterval(1).noSave().noSummon().build("mite:phantom_blade_projectile"));
    public static final DeferredHolder<RecipeSerializer<?>, PhantomBladeRecipe.Serializer> CRAFTING =
            RECIPES.register("phantom_blade", PhantomBladeRecipe.Serializer::new);
    static {
        for (var profile : PhantomBladeRules.profiles()) {
            String material = profile.material();
            BLADES.put(material, ITEMS.registerItem(material+"_phantom_blade", properties -> new PhantomBladeItem(material,
                    (material.equals("vibranium") ? properties.fireResistant() : properties)
                            .rarity(profile.rank() >= 5 ? Rarity.EPIC : Rarity.UNCOMMON))));
        }
    }
    private MitePhantomBlades() {}
    public static Item get(String material) {
        var item = BLADES.get(material);
        return item == null ? Items.AIR : item.get();
    }
    public static Item sword(String material) {
        return switch (material) {
            case "iron" -> Items.IRON_SWORD;
            case "gold" -> Items.GOLDEN_SWORD;
            default -> MiteTools.get(material+"_sword");
        };
    }
    public static void register(IEventBus bus) { ITEMS.register(bus); ENTITIES.register(bus); RECIPES.register(bus); }
}

package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.GelatinousSphere;
import io.github.ningmeng123.mite.item.GelatinousSphereItem;
import io.github.ningmeng123.mite.item.R196ReinforcedBowItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Six remaining source weapon identities and their real thrown sphere entity. @author NingMeng123 */
public final class MiteWeaponContent {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Mite.MOD_ID);
    public static final DeferredItem<R196ReinforcedBowItem> ANCIENT_METAL_BOW = ITEMS.registerItem("ancient_metal_bow", p -> new R196ReinforcedBowItem("ancient_metal", p));
    public static final DeferredItem<R196ReinforcedBowItem> MITHRIL_BOW = ITEMS.registerItem("mithril_bow", p -> new R196ReinforcedBowItem("mithril", p));
    public static final DeferredItem<GelatinousSphereItem> OCHRE_JELLY = ITEMS.registerItem("ochre_jelly", p -> new GelatinousSphereItem(1, p));
    public static final DeferredItem<GelatinousSphereItem> CRIMSON_BLOB = ITEMS.registerItem("crimson_blob", p -> new GelatinousSphereItem(2, p));
    public static final DeferredItem<GelatinousSphereItem> GRAY_OOZE = ITEMS.registerItem("grey_gelatinous_sphere", p -> new GelatinousSphereItem(3, p));
    public static final DeferredItem<GelatinousSphereItem> BLACK_PUDDING = ITEMS.registerItem("black_pudding", p -> new GelatinousSphereItem(4, p));
    public static final DeferredHolder<EntityType<?>, EntityType<GelatinousSphere>> SPHERE = ENTITIES.register("gelatinous_sphere", () ->
            EntityType.Builder.<GelatinousSphere>of(GelatinousSphere::new, MobCategory.MISC).sized(.25F, .25F).clientTrackingRange(4).updateInterval(10).build("mite:gelatinous_sphere"));
    private MiteWeaponContent() {}
    public static int sphereSubtype(ItemStack stack) {
        return stack.is(Items.SLIME_BALL) ? 0 : stack.getItem() instanceof GelatinousSphereItem sphere ? sphere.subtype() : -1;
    }
    public static void register(IEventBus bus) {
        ITEMS.register(bus); ENTITIES.register(bus);
        bus.addListener(MiteWeaponContent::defaults);
    }
    private static void defaults(ModifyDefaultComponentsEvent event) {
        event.modify(Items.SLIME_BALL, b -> b.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 16));
    }
}

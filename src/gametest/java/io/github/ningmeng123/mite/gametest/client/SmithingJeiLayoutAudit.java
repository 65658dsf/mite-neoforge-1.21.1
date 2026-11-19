package io.github.ningmeng123.mite.gametest.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.integration.jei.SmithingJeiRecipe;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Real optional JEI smithing layout audit without Minecraft, a world, rendering or a window. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SmithingJeiLayoutAudit {
    private static final List<String> TABLE_MATERIALS = List.of("ancient_metal", "mithril", "adamantium", "vibranium");

    private SmithingJeiLayoutAudit() {}

    @SubscribeEvent
    public static void gather(GatherDataEvent event) {
        if (!ModList.get().isLoaded("jei")) return;
        try {
            audit();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Real optional JEI smithing layout audit failed", failure);
        }
    }

    private static void audit() throws ReflectiveOperationException {
        Class<?> typeClass = api("mezz.jei.api.recipe.RecipeType");
        Class<?> pluginClass = api("io.github.ningmeng123.mite.integration.jei.MiteJeiPlugin");
        Object plugin = pluginClass.getConstructor().newInstance();
        Field field = pluginClass.getDeclaredField("SMITHING_TYPE");
        field.setAccessible(true);
        Object type = field.get(null);
        check(typeClass.isInstance(type)
                        && ResourceLocation.parse("mite:material_upgrade").equals(typeClass.getMethod("getUid").invoke(type))
                        && typeClass.getMethod("getRecipeClass").invoke(type) == SmithingJeiRecipe.class,
                "The plugin declares the real mite:material_upgrade recipe type");

        List<ItemStack> icons = new ArrayList<>();
        int[] arrowRequests = {0};
        Class<?> guiType = api("mezz.jei.api.helpers.IGuiHelper");
        Object gui = proxy(guiType, (self, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, arguments);
            if (method.getName().equals("createDrawableItemStack")) icons.add(((ItemStack)arguments[0]).copy());
            else if (method.getName().equals("getRecipeArrow")) arrowRequests[0]++;
            else if (!method.getName().equals("getRecipeFlameFilled"))
                throw new IllegalStateException("Unexpected JEI drawable operation: " + method);
            return null;
        });
        Class<?> categoryClass = api("io.github.ningmeng123.mite.integration.jei.SmithingJeiCategory");
        Object category = categoryClass.getConstructor(typeClass, guiType).newInstance(type, gui);
        check(type.equals(categoryClass.getMethod("getRecipeType").invoke(category)), "The category uses the plugin's actual type");
        check(Component.translatable("jei.mite.material_upgrade").equals(categoryClass.getMethod("getTitle").invoke(category)),
                "The material upgrade category uses its translated title");
        check(categoryClass.getMethod("getWidth").invoke(category).equals(164)
                        && categoryClass.getMethod("getHeight").invoke(category).equals(88),
                "The smithing layout retains its declared compact dimensions");
        check(icons.size() == 1 && icons.getFirst().is(Items.SMITHING_TABLE) && arrowRequests[0] == 1,
                "The constructor requests a smithing table icon and one arrow without rendering");

        SmithingJeiRecipe page = page();
        Class<?> builderType = api("mezz.jei.api.gui.builder.IRecipeLayoutBuilder");
        List<Slot> slots = new ArrayList<>();
        Object builder = proxy(builderType, (self, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, arguments);
            if (method.isDefault()) return InvocationHandler.invokeDefault(self, method, arguments);
            if (method.getName().equals("addSlot") && arguments.length == 1) {
                Slot slot = new Slot(((Enum<?>)arguments[0]).name());
                slots.add(slot);
                return slot.builder();
            }
            throw new IllegalStateException("Unexpected smithing layout operation: " + method);
        });
        categoryClass.getMethod("setRecipe", builderType, SmithingJeiRecipe.class, api("mezz.jei.api.recipe.IFocusGroup"))
                .invoke(category, builder, page, null);
        check(slots.size() == 5 && slots.stream().filter(slot -> slot.role.equals("INPUT")).count() == 3
                        && slots.get(3).role.equals("OUTPUT") && slots.getLast().role.equals("CATALYST"),
                "The real JEI API creates three input slots, one output slot, and a recipe-specific catalyst");
        for (int slot = 0; slot < 4; slot++) {
            int horizontal = new int[]{7, 35, 63, 129}[slot];
            check(slots.get(slot).horizontal == horizontal && slots.get(slot).vertical == 14,
                    "Every actual input and output retains its intended position");
        }
        check(matches(slots.get(0), page.templates().getFirst()) && matches(slots.get(1), page.bases().getFirst())
                        && matches(slots.get(2), item("mite:mithril_ingot").copyWithCount(5))
                        && matches(slots.get(3), item("mite:mithril_helmet")),
                "The real layout displays one template, one ancient metal helmet, five mithril ingots and one mithril helmet");
        check(matches(slots.getLast(), item("mite:mithril_smithing_table")) && slots.getLast().horizontal == 7
                        && slots.getLast().vertical == 39, "The displayed catalyst is the target material's smithing table");
        check(page.source().id().equals(categoryClass.getMethod("getRegistryName", SmithingJeiRecipe.class).invoke(category, page)),
                "The category retains the source recipe identity");
        verifyWorkbenchCategories(pluginClass, plugin, gui);
        verifyCatalysts(pluginClass, plugin, type);
        Mite.LOGGER.info("[MITE smithing JEI layout audit] passed-real-JEI-api: type=mite:material_upgrade, dimensions=164x88, inputs=3, outputs=1, mithril-ingots=5, output=mite:mithril_helmet, catalyst=mite:mithril_smithing_table, Minecraft-instance-created=false, window-created=false, rendered=false");
    }

    private static SmithingJeiRecipe page() {
        ItemStack template = item("mite:equipment_upgrade_smithing_template");
        ItemStack base = item("mite:ancient_metal_helmet");
        ItemStack ingots = item("mite:mithril_ingot").copyWithCount(5);
        ItemStack output = item("mite:mithril_helmet");
        RecipeHolder<SmithingRecipe> source = new RecipeHolder<>(ResourceLocation.parse("mite:equipment_upgrade/ancient_metal_helmet_to_mithril"),
                new SmithingTransformRecipe(Ingredient.of(template), Ingredient.of(base), Ingredient.of(ingots), output));
        return new SmithingJeiRecipe(source, List.of(template), List.of(base), List.of(ingots), output, 200);
    }

    private static void verifyWorkbenchCategories(Class<?> pluginClass, Object plugin, Object gui) throws ReflectiveOperationException {
        Class<?> helpersType = api("mezz.jei.api.helpers.IJeiHelpers");
        Object helpers = proxy(helpersType, (self, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, arguments);
            if (method.getName().equals("getGuiHelper")) return gui;
            throw new IllegalStateException("Unexpected JEI helper request: " + method);
        });
        Class<?> registrationType = api("mezz.jei.api.registration.IRecipeCategoryRegistration");
        Class<?> categoryType = api("mezz.jei.api.recipe.category.IRecipeCategory");
        Method getRecipeType = categoryType.getMethod("getRecipeType");
        Method getUid = api("mezz.jei.api.recipe.RecipeType").getMethod("getUid");
        Map<ResourceLocation, Object> registered = new LinkedHashMap<>();
        Object registration = proxy(registrationType, (self, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, arguments);
            if (method.getName().equals("getJeiHelpers")) return helpers;
            if (method.getName().equals("addRecipeCategories")) {
                for (Object category : (Object[]) arguments[0]) {
                    ResourceLocation id = (ResourceLocation)getUid.invoke(getRecipeType.invoke(category));
                    check(registered.put(id, category) == null, "Each JEI category registers once: " + id);
                }
                return null;
            }
            throw new IllegalStateException("Unexpected JEI category registration operation: " + method);
        });
        pluginClass.getMethod("registerCategories", registrationType).invoke(plugin, registration);
        check(!registered.containsKey(ResourceLocation.parse("mite:smithing_table_crafting")),
                "The standalone smithing table crafting tab is absent");
        for (String material : TABLE_MATERIALS) {
            Object category = registered.get(workbenchCategoryId(material));
            check(category != null && item("mite:" + material + "_crafting_table").getHoverName()
                            .equals(categoryType.getMethod("getTitle").invoke(category)),
                    "The existing " + material + " workbench tab retains its title");
        }
    }

    private static void verifyCatalysts(Class<?> pluginClass, Object plugin, Object type) throws ReflectiveOperationException {
        Class<?> registrationType = api("mezz.jei.api.registration.IRecipeCatalystRegistration");
        Method getUid = api("mezz.jei.api.recipe.RecipeType").getMethod("getUid");
        Object nativeType = api("mezz.jei.api.constants.RecipeTypes").getField("SMITHING").get(null);
        List<ItemStack> nativeCatalysts = new ArrayList<>();
        Map<ResourceLocation, List<ItemStack>> workbenchCatalysts = new LinkedHashMap<>();
        int[] otherCalls = {0};
        Object registration = proxy(registrationType, (self, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, arguments);
            if (method.isDefault()) return InvocationHandler.invokeDefault(self, method, arguments);
            if (method.getName().equals("addRecipeCatalysts") && arguments.length == 3 && arguments[2] instanceof List<?> values) {
                check(!type.equals(arguments[0]), "Smithing upgrades index catalysts per recipe");
                if (nativeType.equals(arguments[0])) {
                    for (Object value : values) nativeCatalysts.add(((ItemStack)value).copy());
                } else {
                    ResourceLocation id = (ResourceLocation)getUid.invoke(arguments[0]);
                    if (TABLE_MATERIALS.stream().map(SmithingJeiLayoutAudit::workbenchCategoryId).anyMatch(id::equals)) {
                        List<ItemStack> catalysts = workbenchCatalysts.computeIfAbsent(id, ignored -> new ArrayList<>());
                        for (Object value : values) catalysts.add(((ItemStack)value).copy());
                    }
                }
                otherCalls[0]++;
                return null;
            }
            throw new IllegalStateException("Unexpected plugin catalyst operation: " + method);
        });
        pluginClass.getMethod("registerRecipeCatalysts", registrationType).invoke(plugin, registration);
        check(nativeCatalysts.size() == 4 && TABLE_MATERIALS.stream()
                        .map(material -> item("mite:" + material + "_smithing_table"))
                        .allMatch(expected -> nativeCatalysts.stream().anyMatch(actual -> ItemStack.matches(actual, expected))),
                "All four MITE smithing tables are catalysts for native smithing recipes");
        for (String material : TABLE_MATERIALS) {
            List<ItemStack> catalysts = workbenchCatalysts.getOrDefault(workbenchCategoryId(material), List.of());
            check(catalysts.size() == 1 && ItemStack.matches(catalysts.getFirst(), item("mite:" + material + "_crafting_table")),
                    "Only the matching workbench is a category-wide catalyst for " + material);
        }
        check(otherCalls[0] > 0, "The actual plugin also retains its other workstation catalyst registrations");
    }

    private static ResourceLocation workbenchCategoryId(String material) {
        return ResourceLocation.parse("mite:workbench_mite_" + material + "_crafting_table");
    }

    private static final class Slot {
        private final String role;
        private final List<ItemStack> ingredients = new ArrayList<>();
        private int horizontal = Integer.MIN_VALUE;
        private int vertical = Integer.MIN_VALUE;
        private Slot(String role) { this.role = role; }

        private Object builder() throws ReflectiveOperationException {
            return proxy(api("mezz.jei.api.gui.builder.IRecipeSlotBuilder"), (self, method, arguments) -> {
                if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, arguments);
                if (method.isDefault()) return InvocationHandler.invokeDefault(self, method, arguments);
                if (method.getName().equals("setPosition") && arguments.length == 2) {
                    horizontal = (Integer)arguments[0]; vertical = (Integer)arguments[1]; return self;
                }
                if (method.getName().equals("addIngredient") && arguments.length == 2 && arguments[1] instanceof ItemStack stack) {
                    ingredients.add(stack.copy()); return self;
                }
                if (method.getName().equals("addIngredients") && arguments.length == 2 && arguments[1] instanceof List<?> values) {
                    for (Object value : values) {
                        check(value instanceof ItemStack, "Layout ingredients are item stacks");
                        ingredients.add(((ItemStack)value).copy());
                    }
                    return self;
                }
                if (method.getName().equals("setStandardSlotBackground") || method.getName().equals("setOutputSlotBackground")
                        || method.getName().equals("addRichTooltipCallback")) return self;
                throw new IllegalStateException("Unexpected smithing slot operation: " + method);
            });
        }
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).filter(item -> item != Items.AIR).orElseThrow());
    }
    private static boolean matches(Slot slot, ItemStack expected) {
        return slot.ingredients.size() == 1 && ItemStack.matches(slot.ingredients.getFirst(), expected);
    }
    private static Class<?> api(String name) throws ClassNotFoundException {
        return Class.forName(name, true, SmithingJeiLayoutAudit.class.getClassLoader());
    }
    private static Object proxy(Class<?> contract, InvocationHandler handler) {
        return Proxy.newProxyInstance(SmithingJeiLayoutAudit.class.getClassLoader(), new Class<?>[]{contract}, handler);
    }
    private static Object objectMethod(Object self, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == arguments[0];
            case "toString" -> "MITE headless smithing JEI audit proxy";
            default -> throw new IllegalStateException("Unexpected Object method: " + method);
        };
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

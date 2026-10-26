package io.github.ningmeng123.mite.gametest.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.integration.jei.EnchantingJeiRecipe;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Checks actual JEI ingredient roles and table catalysts without opening a game window. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class EnchantingJeiLayoutAudit {
    private EnchantingJeiLayoutAudit() {}

    @SubscribeEvent
    public static void gather(GatherDataEvent event) {
        if (!ModList.get().isLoaded("jei")) return;
        try {
            audit();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Real optional JEI enchanting layout audit failed", failure);
        }
    }

    private static void audit() throws ReflectiveOperationException {
        Class<?> typeClass = api("mezz.jei.api.recipe.RecipeType");
        Class<?> pluginClass = api("io.github.ningmeng123.mite.integration.jei.MiteJeiPlugin");
        Object plugin = pluginClass.getConstructor().newInstance();
        var field = pluginClass.getDeclaredField("ENCHANTING_TYPE");
        field.setAccessible(true);
        Object type = field.get(null);
        check(ResourceLocation.parse("mite:enchanting").equals(typeClass.getMethod("getUid").invoke(type))
                        && typeClass.getMethod("getRecipeClass").invoke(type) == EnchantingJeiRecipe.class,
                "The plugin registers the real enchanting display type");

        List<ItemStack> icons = new ArrayList<>();
        Class<?> guiType = api("mezz.jei.api.helpers.IGuiHelper");
        Object gui = proxy(guiType, (self, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, arguments);
            if (method.getName().equals("createDrawableItemStack")) icons.add(((ItemStack)arguments[0]).copy());
            else if (!method.getName().equals("getRecipeArrow")) throw new IllegalStateException("Unexpected enchanting drawable: " + method);
            return null;
        });
        Class<?> categoryClass = api("io.github.ningmeng123.mite.integration.jei.EnchantingJeiCategory");
        Object category = categoryClass.getConstructor(typeClass, guiType).newInstance(type, gui);
        check(type.equals(categoryClass.getMethod("getRecipeType").invoke(category))
                        && Component.translatable("jei.mite.enchanting").equals(categoryClass.getMethod("getTitle").invoke(category)),
                "The category uses the registered type and translated title");
        check(icons.size() == 1 && icons.getFirst().is(Items.ENCHANTING_TABLE), "The category icon is an enchanting table");

        EnchantingJeiRecipe page = EnchantingJeiRecipe.goldenApple();
        check(page.input().is(Items.GOLDEN_APPLE) && page.input().getCount() == 1
                        && page.output().is(Items.ENCHANTED_GOLDEN_APPLE) && page.output().getCount() == 1
                        && page.experienceCost() == 200,
                "The page shows one golden apple converted into one enchanted golden apple for 200 XP points");
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
            throw new IllegalStateException("Unexpected enchanting layout: " + method);
        });
        categoryClass.getMethod("setRecipe", builderType, EnchantingJeiRecipe.class, api("mezz.jei.api.recipe.IFocusGroup"))
                .invoke(category, builder, page, null);
        check(slots.size() == 2 && slots.getFirst().role.equals("INPUT") && slots.getLast().role.equals("OUTPUT")
                        && slots.getFirst().ingredients.size() == 1 && ItemStack.matches(slots.getFirst().ingredients.getFirst(), page.input())
                        && slots.getLast().ingredients.size() == 1 && ItemStack.matches(slots.getLast().ingredients.getFirst(), page.output()),
                "JEI indexes the apple as input and enchanted apple as output, with no lapis input");
        check(ResourceLocation.parse("mite:enchanting/golden_apple").equals(
                        categoryClass.getMethod("getRegistryName", EnchantingJeiRecipe.class).invoke(category, page)),
                "The conversion retains a stable JEI recipe identity");

        List<ItemStack> catalysts = new ArrayList<>();
        Class<?> registrationType = api("mezz.jei.api.registration.IRecipeCatalystRegistration");
        Object registration = proxy(registrationType, (self, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, arguments);
            if (method.isDefault()) return InvocationHandler.invokeDefault(self, method, arguments);
            if (method.getName().equals("addRecipeCatalysts") && arguments.length == 3 && arguments[2] instanceof List<?> values) {
                if (type.equals(arguments[0])) for (Object value : values) catalysts.add(((ItemStack)value).copy());
                return null;
            }
            throw new IllegalStateException("Unexpected catalyst registration: " + method);
        });
        pluginClass.getMethod("registerRecipeCatalysts", registrationType).invoke(plugin, registration);
        check(catalysts.size() == 2 && catalysts.stream().anyMatch(stack -> stack.is(Items.ENCHANTING_TABLE))
                        && catalysts.stream().anyMatch(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem())
                        .equals(ResourceLocation.parse("mite:emerald_enchanting_table"))),
                "Both the ordinary and emerald enchanting tables index the conversion as a use");
        Mite.LOGGER.info("[MITE enchanting JEI layout audit] passed-real-JEI-api: type=mite:enchanting, inputs=1, outputs=1, XP-points=200, lapis-inputs=0, enchanting-table-catalysts=2, window-created=false, rendered=false");
    }

    private static final class Slot {
        private final String role;
        private final List<ItemStack> ingredients = new ArrayList<>();
        private Slot(String role) { this.role = role; }

        private Object builder() throws ReflectiveOperationException {
            return proxy(api("mezz.jei.api.gui.builder.IRecipeSlotBuilder"), (self, method, arguments) -> {
                if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, arguments);
                if (method.isDefault()) return InvocationHandler.invokeDefault(self, method, arguments);
                if (method.getName().equals("addIngredient") && arguments.length == 2 && arguments[1] instanceof ItemStack stack) {
                    ingredients.add(stack.copy()); return self;
                }
                if (method.getName().equals("setPosition") || method.getName().equals("setStandardSlotBackground")
                        || method.getName().equals("setOutputSlotBackground")) return self;
                throw new IllegalStateException("Unexpected enchanting slot: " + method);
            });
        }
    }

    private static Class<?> api(String name) throws ClassNotFoundException {
        return Class.forName(name, true, EnchantingJeiLayoutAudit.class.getClassLoader());
    }
    private static Object proxy(Class<?> contract, InvocationHandler handler) {
        return Proxy.newProxyInstance(EnchantingJeiLayoutAudit.class.getClassLoader(), new Class<?>[]{contract}, handler);
    }
    private static Object objectMethod(Object self, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == arguments[0];
            case "toString" -> "MITE headless enchanting JEI audit proxy";
            default -> throw new IllegalStateException("Unexpected Object method: " + method);
        };
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

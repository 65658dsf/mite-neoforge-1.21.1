package io.github.ningmeng123.mite.gametest.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.integration.jei.AnvilJeiRecipe;
import io.github.ningmeng123.mite.integration.jei.AnvilJeiRecipes;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
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

/** Real optional JEI API layout audit; no Minecraft instance, world, window, draw or input. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AnvilJeiLayoutAudit {
    private static final Set<String> ALL_ANVILS = Set.of("mite:copper_anvil", "mite:silver_anvil", "mite:golden_anvil",
            "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil", "mite:ancient_metal_anvil",
            "mite:mithril_anvil", "mite:adamantium_anvil");
    private static final Set<String> IRON_ANVILS = Set.of("minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil",
            "mite:ancient_metal_anvil", "mite:mithril_anvil", "mite:adamantium_anvil");
    private AnvilJeiLayoutAudit() {}

    @SubscribeEvent
    public static void gather(GatherDataEvent event) {
        if (!ModList.get().isLoaded("jei")) return;
        try {
            audit();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Real optional JEI anvil layout audit failed", failure);
        }
    }

    private static void audit() throws ReflectiveOperationException {
        Class<?> recipeType = api("mezz.jei.api.recipe.RecipeType");
        Class<?> pluginClass = api("io.github.ningmeng123.mite.integration.jei.MiteJeiPlugin");
        Object plugin = pluginClass.getConstructor().newInstance();
        Field field = pluginClass.getDeclaredField("ANVIL_TYPE");
        field.setAccessible(true);
        Object type = field.get(null);
        check(recipeType.isInstance(type), "The unified anvil field is an actual JEI RecipeType");
        check(ResourceLocation.parse("mite:equipment_repair").equals(recipeType.getMethod("getUid").invoke(type)),
                "All equipment repairs must share the single mite:equipment_repair category UID");
        List<Object> declaredAnvilTypes = new ArrayList<>();
        for (Field declared : pluginClass.getDeclaredFields()) {
            if (!Modifier.isStatic(declared.getModifiers())) continue;
            declared.setAccessible(true);
            Object value = declared.get(null);
            if (recipeType.isInstance(value)) collectAnvilType(recipeType, value, declaredAnvilTypes);
            else if (value instanceof Map<?, ?> map) for (Object entry : map.values())
                if (recipeType.isInstance(entry)) collectAnvilType(recipeType, entry, declaredAnvilTypes);
        }
        check(declaredAnvilTypes.size() == 1 && declaredAnvilTypes.getFirst().equals(type),
                "There must be exactly one repair recipe type, without tier-specific top tabs");

        Class<?> guiHelper = api("mezz.jei.api.helpers.IGuiHelper");
        Object gui = proxy(guiHelper, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, args);
            // These methods only create drawables. Null placeholders deliberately cannot render anything.
            return zero(method.getReturnType());
        });
        Class<?> categoryClass = api("io.github.ningmeng123.mite.integration.jei.AnvilJeiCategory");
        Object category = categoryClass.getConstructor(recipeType, ItemStack.class, guiHelper)
                .newInstance(type, new ItemStack(Items.ANVIL), gui);
        Component title = (Component)categoryClass.getMethod("getTitle").invoke(category);
        check(title.equals(new ItemStack(Items.ANVIL).getHoverName()), "The sole top tab is named exactly the native anvil, without a material tier or minimum suffix");
        check(type.equals(categoryClass.getMethod("getRecipeType").invoke(category)), "Category uses the actual unified plugin recipe type");
        check(categoryClass.getMethod("getWidth").invoke(category).equals(164)
                        && categoryClass.getMethod("getHeight").invoke(category).equals(96),
                "Repair contents retain their compact native-style dimensions without an in-recipe anvil grid");

        var index = AnvilJeiRecipes.build();
        int layouts = 0;
        layouts += verifyLayouts(categoryClass, category, index, "mite:copper_pickaxe", ALL_ANVILS);
        layouts += verifyLayouts(categoryClass, category, index, "minecraft:iron_pickaxe", IRON_ANVILS);
        layouts += verifyLayouts(categoryClass, category, index, "mite:mithril_pickaxe", Set.of("mite:mithril_anvil", "mite:adamantium_anvil"));
        layouts += verifyLayouts(categoryClass, category, index, "mite:adamantium_pickaxe", Set.of("mite:adamantium_anvil"));
        check(layouts == 8, "Both material and combination operations were exercised at all four independent capacity boundaries");
        int sidebarCases = verifyNativeSidebar(category, index);

        Class<?> registrationType = api("mezz.jei.api.registration.IRecipeCatalystRegistration");
        int[] otherCatalysts = {0};
        Object registration = proxy(registrationType, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, args);
            if (method.isDefault()) return InvocationHandler.invokeDefault(self, method, args);
            if (method.getName().startsWith("addRecipeCatalyst")) {
                for (Object arg : args) check(!containsType(arg, type),
                        "The unified category must not register global anvil catalysts that expose high-tier repairs to weak anvils");
                otherCatalysts[0]++;
                return null;
            }
            return zero(method.getReturnType());
        });
        pluginClass.getMethod("registerRecipeCatalysts", registrationType).invoke(plugin, registration);
        check(otherCatalysts[0] > 0, "Real plugin catalyst registration was called and retained furnace/workbench catalysts");
        Mite.LOGGER.info("[MITE anvil JEI layout audit] passed-real-JEI-api: unified-type=mite:equipment_repair, top-title={}, layouts={}, invisible-catalysts-per-tier=9/6/2/1, visible-catalyst-slots=0, inputs=2, outputs=1, global-anvil-catalysts=0, other-global-catalyst-calls={}, native-sidebar-cases={}, Minecraft-instance-created=false, window-created=false, rendered=false",
                title.getString(), layouts, otherCatalysts[0], sidebarCases);
    }

    private static void collectAnvilType(Class<?> recipeType, Object value, List<Object> target) throws ReflectiveOperationException {
        if (recipeType.getMethod("getRecipeClass").invoke(value) == AnvilJeiRecipe.class) target.add(value);
    }

    private static int verifyLayouts(Class<?> categoryClass, Object category, AnvilJeiRecipes.Index index,
            String itemId, Set<String> expectedAnvils) throws ReflectiveOperationException {
        var pages = index.recipes().stream().filter(page -> page.itemId().equals(ResourceLocation.parse(itemId))).toList();
        check(pages.size() == 2, "Both real repair operations are present for " + itemId);
        Class<?> builderType = api("mezz.jei.api.gui.builder.IRecipeLayoutBuilder");
        Class<?> focusType = api("mezz.jei.api.recipe.IFocusGroup");
        for (var page : pages) {
            List<Slot> slots = new ArrayList<>();
            List<Slot> invisible = new ArrayList<>();
            Object builder = proxy(builderType, (self, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, args);
                // Execute JEI's real addInputSlot/addOutputSlot/addSlot(x,y) defaults.
                if (method.isDefault()) return InvocationHandler.invokeDefault(self, method, args);
                if (method.getName().equals("addSlot") && args.length == 1) {
                    Slot slot = new Slot(((Enum<?>)args[0]).name());
                    slots.add(slot);
                    return slot.builder();
                }
                if (method.getName().equals("addInvisibleIngredients") && args.length == 1) {
                    Slot slot = new Slot(((Enum<?>)args[0]).name());
                    invisible.add(slot);
                    return slot.builder();
                }
                throw new IllegalStateException("Unexpected real recipe layout operation: " + method);
            });
            categoryClass.getMethod("setRecipe", builderType, AnvilJeiRecipe.class, focusType).invoke(category, builder, page, null);
            var inputs = slots.stream().filter(slot -> slot.role.equals("INPUT")).toList();
            var outputs = slots.stream().filter(slot -> slot.role.equals("OUTPUT")).toList();
            check(inputs.size() == 2 && outputs.size() == 1 && slots.size() == 3,
                    "The real layout has only two visible inputs and one output, without visible anvil slots: " + itemId);
            check(invisible.size() == 1 && invisible.getFirst().role.equals("CATALYST"),
                    "Applicable anvils are indexed as one invisible CATALYST ingredient group for real U lookups: " + itemId);
            var catalysts = invisible.getFirst().ingredients;
            check(catalysts.size() == expectedAnvils.size(), "The invisible lookup index contains exactly the eligible anvils: " + itemId);
            Set<String> actual = new HashSet<>();
            for (ItemStack anvil : catalysts) {
                check(anvil.getCount() == 1, "Each lookup catalyst is one anvil");
                check(actual.add(BuiltInRegistries.ITEM.getKey(anvil.getItem()).toString()), "Invisible anvil catalysts are not duplicated");
            }
            check(actual.equals(expectedAnvils), "Real invisible JEI CATALYST ingredients exactly match the independent allowed-anvil set: " + itemId);
            check(inputs.get(0).x == 10 && inputs.get(0).y == 15 && inputs.get(1).x == 60 && inputs.get(1).y == 15
                            && outputs.getFirst().x == 127 && outputs.getFirst().y == 15,
                    "Inputs and output retain the compact repair layout, without displacement for a catalyst grid");
            check(inputs.stream().anyMatch(slot -> matches(slot, page.left()))
                            && inputs.stream().anyMatch(slot -> matches(slot, page.right()))
                            && matches(outputs.getFirst(), page.output()),
                    "Real layout displays the exact damaged ingredients and source repair output: " + itemId);
        }
        return pages.size();
    }

    /** Invoke the actually transformed JEI logic on a constructor-free test fixture, never a screen. */
    private static int verifyNativeSidebar(Object category, AnvilJeiRecipes.Index index) throws ReflectiveOperationException {
        Class<?> recipeType = api("mezz.jei.api.recipe.RecipeType");
        Object otherType = recipeType.getMethod("create", String.class, String.class, Class.class)
                .invoke(null, "mite", "audit_ordinary_category", String.class);
        Object otherCategory = proxy(api("mezz.jei.api.recipe.category.IRecipeCategory"), (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, args);
            if (method.getName().equals("getRecipeType")) return otherType;
            if (method.getName().equals("getTitle")) return Component.literal("ordinary audit category");
            return zero(method.getReturnType());
        });
        var copper = materialPage(index, "mite:copper_pickaxe");
        var iron = materialPage(index, "minecraft:iron_pickaxe");
        var mithril = materialPage(index, "mite:mithril_pickaxe");
        var adamantium = materialPage(index, "mite:adamantium_pickaxe");
        Set<String> mithrilAnvils = Set.of("mite:mithril_anvil", "mite:adamantium_anvil");
        Set<String> adamantiumAnvil = Set.of("mite:adamantium_anvil");
        int cases = 0;
        for (var page : List.of(copper, iron, mithril, adamantium)) {
            Set<String> expected = page == copper ? ALL_ANVILS : page == iron ? IRON_ANVILS
                    : page == mithril ? mithrilAnvils : adamantiumAnvil;
            for (var operation : index.recipes().stream().filter(row -> row.itemId().equals(page.itemId())).toList()) {
                assertSidebar(logic(category, List.of(operation), List.of(operation), category, category, 0, 1), expected, false,
                        "real sorted single " + operation.itemId() + "/" + operation.kind());
                cases++;
            }
        }
        assertSidebar(logic(category, List.of(copper, iron), List.of(copper, iron), category, category, 0, 2), IRON_ANVILS, false,
                "mixed copper and iron page takes the intersection"); cases++;
        assertSidebar(logic(category, List.of(copper, mithril), List.of(copper, mithril), category, category, 0, 2), mithrilAnvils, false,
                "mixed copper and mithril page takes the intersection"); cases++;
        assertSidebar(logic(category, List.of(copper, adamantium), List.of(copper, adamantium), category, category, 0, 2), adamantiumAnvil, false,
                "mixed page never claims a weak anvil repairs every visible item"); cases++;
        assertSidebar(logic(category, List.of(copper, adamantium), List.of(adamantium, copper), category, category, 0, 1), adamantiumAnvil, false,
                "actual sorted cache takes precedence over unsorted focused recipes"); cases++;
        assertSidebar(logic(category, List.of(copper, iron, mithril, adamantium), List.of(copper, iron, mithril, adamantium),
                category, category, 2, 1), mithrilAnvils, false, "native visible-page index reads the actual third recipe"); cases++;
        assertSidebar(logic(category, List.of(copper, iron, mithril, adamantium), null, category, category, 3, 1), adamantiumAnvil, false,
                "null-cache fallback respects the focused recipe index"); cases++;
        assertSidebar(logic(category, List.of(mithril), List.of(), category, category, 0, 1), mithrilAnvils, false,
                "empty cache falls back to the focused recipes"); cases++;
        assertSidebar(logic(category, List.of(mithril), List.of(copper), otherCategory, category, 0, 1), mithrilAnvils, false,
                "old-category cache cannot advertise stale low-tier anvils"); cases++;
        assertSidebar(logic(category, List.of(mithril), List.of(copper), category, otherCategory, 0, 1), mithrilAnvils, false,
                "cached row category must be the exact selected category instance"); cases++;
        assertSidebar(logic(category, List.of(mithril), List.of("unrelated cached recipe"), category, category, 0, 1), mithrilAnvils, false,
                "untyped cached recipe must not be interpreted as an anvil repair"); cases++;
        assertSidebar(logic(category, List.of(), null, category, category, 0, 1), Set.of(), false,
                "no visible or focused recipe produces an empty sidebar"); cases++;
        assertSidebar(logic(category, List.of(copper), null, category, category, 4, 1), Set.of(), false,
                "past-end focused index does not show previous-page anvils"); cases++;
        assertSidebar(logic(otherCategory, List.of("ordinary recipe"), null, otherCategory, otherCategory, 0, 1),
                Set.of("minecraft:stick"), true, "ordinary categories retain JEI's original catalyst lookup"); cases++;
        Mite.LOGGER.info("[MITE anvil JEI native-sidebar audit] passed-transformed-RecipeGuiLogic: cases={}, native-groups-per-tier=9/6/2/1, one-anvil-per-native-group=true, sorted-visible-cache=true, mixed-page-intersection=true, page-index-and-cache-fallbacks=true, ordinary-category-original-lookup=true, screen-created=false, rendered=false", cases);
        return cases;
    }

    private static AnvilJeiRecipe materialPage(AnvilJeiRecipes.Index index, String id) {
        return index.recipes().stream().filter(page -> page.itemId().equals(ResourceLocation.parse(id))
                && page.kind() == AnvilJeiRecipe.Kind.MATERIAL).findFirst().orElseThrow();
    }

    private record LogicFixture(Object logic, int[] originalLookups) {}

    private static LogicFixture logic(Object category, List<?> focusedPages, List<?> cachedPages,
            Object cachedCategory, Object rowCategory, int recipeIndex, int perPage) throws ReflectiveOperationException {
        Class<?> logicType = api("mezz.jei.gui.recipes.RecipeGuiLogic");
        Class<?> unsafeType = api("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object logic = unsafeType.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), logicType);
        Object focused = proxy(api("mezz.jei.gui.recipes.lookups.IFocusedRecipes"), (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, args);
            if (method.getName().equals("getRecipeCategory")) return category;
            if (method.getName().equals("getRecipes")) return focusedPages;
            throw new IllegalStateException("Unexpected focused recipe access " + method);
        });
        Object state = proxy(api("mezz.jei.gui.recipes.lookups.ILookupState"), (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, args);
            if (method.isDefault()) return InvocationHandler.invokeDefault(self, method, args);
            return switch (method.getName()) {
                case "getFocusedRecipes" -> focused;
                case "getRecipeIndex" -> recipeIndex;
                case "getRecipesPerPage" -> perPage;
                case "getRecipeCategories" -> List.of(category);
                default -> throw new IllegalStateException("Unexpected native lookup state access " + method);
            };
        });
        field(logicType, logic, "state", state);
        field(logicType, logic, "cachedRecipeCategory", cachedCategory);
        if (cachedPages != null) {
            List<Object> rows = new ArrayList<>();
            for (Object page : cachedPages) {
                Object drawable = proxy(api("mezz.jei.api.gui.IRecipeLayoutDrawable"), (self, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, args);
                    if (method.getName().equals("getRecipe")) return page;
                    if (method.getName().equals("getRecipeCategory")) return rowCategory;
                    throw new IllegalStateException("Unexpected cached drawable access " + method);
                });
                rows.add(proxy(api("mezz.jei.gui.recipes.IRecipeLayoutWithButtons"), (self, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, args);
                    if (method.getName().equals("getRecipeLayout")) return drawable;
                    throw new IllegalStateException("Unexpected cached row access " + method);
                }));
            }
            Object cache = proxy(api("mezz.jei.gui.recipes.layouts.IRecipeLayoutList"), (self, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, args);
                return switch (method.getName()) {
                    case "size" -> rows.size();
                    case "subList" -> rows.subList((Integer)args[0], (Integer)args[1]);
                    case "findFirst" -> rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
                    default -> throw new IllegalStateException("Unexpected native layout cache access " + method);
                };
            });
            field(logicType, logic, "cachedRecipeLayoutsWithButtons", cache);
        }
        int[] originalLookups = {0};
        Object lookup = proxy(api("mezz.jei.api.recipe.IRecipeCatalystLookup"), (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, args);
            if (method.getName().equals("getGroups")) return Stream.<Consumer<Object>>of(acceptor -> {
                try {
                    api("mezz.jei.api.gui.builder.IIngredientAcceptor").getMethod("addItemStack", ItemStack.class)
                            .invoke(acceptor, new ItemStack(Items.STICK));
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            });
            throw new IllegalStateException("Unexpected original JEI catalyst lookup " + method);
        });
        Object manager = proxy(api("mezz.jei.api.recipe.IRecipeManager"), (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, args);
            if (method.getName().equals("createRecipeCatalystLookup")) {
                originalLookups[0]++;
                return lookup;
            }
            throw new IllegalStateException("Unexpected recipe manager operation " + method);
        });
        field(logicType, logic, "recipeManager", manager);
        return new LogicFixture(logic, originalLookups);
    }

    private static void field(Class<?> owner, Object instance, String name, Object value) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    @SuppressWarnings("unchecked")
    private static void assertSidebar(LogicFixture fixture, Set<String> expected, boolean original, String scenario)
            throws ReflectiveOperationException {
        Object result = fixture.logic().getClass().getMethod("getRecipeCatalystGroups").invoke(fixture.logic());
        check(result instanceof Stream<?>, "Actual native catalyst method returns JEI's normal group stream: " + scenario);
        List<?> groups;
        try (Stream<?> stream = (Stream<?>)result) { groups = stream.toList(); }
        Set<String> actual = new HashSet<>();
        for (Object group : groups) {
            check(group instanceof Consumer<?>, "Sidebar entries are real JEI ingredient consumer groups: " + scenario);
            Slot captured = new Slot("CATALYST");
            ((Consumer<Object>)group).accept(captured.builder());
            check(captured.ingredients.size() == 1, "Each native outside sidebar row contains exactly one anvil: " + scenario);
            check(actual.add(BuiltInRegistries.ITEM.getKey(captured.ingredients.getFirst().getItem()).toString()),
                    "Native sidebar entries are unique: " + scenario);
        }
        check(actual.equals(expected) && groups.size() == expected.size(), "Exact native outside sidebar anvils: " + scenario
                + "; expected=" + expected + "; actual=" + actual);
        check(fixture.originalLookups()[0] == (original ? 1 : 0), "Only ordinary categories execute the original global catalyst lookup: " + scenario);
    }

    private static boolean matches(Slot slot, ItemStack expected) {
        return slot.ingredients.size() == 1 && ItemStack.matches(slot.ingredients.getFirst(), expected);
    }

    private static final class Slot {
        private final String role;
        private final List<ItemStack> ingredients = new ArrayList<>();
        private int x = Integer.MIN_VALUE;
        private int y = Integer.MIN_VALUE;
        private Slot(String role) { this.role = role; }

        private Object builder() throws ReflectiveOperationException {
            return proxy(api("mezz.jei.api.gui.builder.IRecipeSlotBuilder"), (self, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, args);
                // Execute the actual API's addItemStack -> addIngredient and default bridge methods.
                if (method.isDefault()) return InvocationHandler.invokeDefault(self, method, args);
                if (method.getName().equals("setPosition") && args.length == 2) {
                    x = (Integer)args[0]; y = (Integer)args[1]; return self;
                }
                if (method.getName().equals("addIngredient") && args.length == 2 && args[1] instanceof ItemStack stack) {
                    ingredients.add(stack.copy()); return self;
                }
                if (method.getName().equals("addIngredients") && args.length == 2 && args[1] instanceof List<?> values) {
                    for (Object value : values) {
                        check(value instanceof ItemStack, "Audit only expects item-stack recipe ingredients");
                        ingredients.add(((ItemStack)value).copy());
                    }
                    return self;
                }
                if (method.getName().equals("setStandardSlotBackground") || method.getName().equals("setOutputSlotBackground")
                        || method.getName().equals("setSlotName") || method.getName().equals("addRichTooltipCallback")) return self;
                if (method.getName().equals("getWidth") || method.getName().equals("getHeight")) return 16;
                throw new IllegalStateException("Unexpected real recipe slot operation: " + method);
            });
        }
    }

    private static boolean containsType(Object value, Object type) {
        if (type.equals(value)) return true;
        if (value == null) return false;
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) if (containsType(Array.get(value, i), type)) return true;
        } else if (value instanceof Iterable<?> values) for (Object element : values) if (containsType(element, type)) return true;
        return false;
    }
    private static Class<?> api(String name) throws ClassNotFoundException {
        return Class.forName(name, true, AnvilJeiLayoutAudit.class.getClassLoader());
    }
    private static Object proxy(Class<?> contract, InvocationHandler handler) {
        return Proxy.newProxyInstance(AnvilJeiLayoutAudit.class.getClassLoader(), new Class<?>[]{contract}, handler);
    }
    private static Object objectMethod(Object self, Method method, Object[] args) {
        return switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            case "toString" -> "MITE headless JEI audit proxy";
            default -> throw new IllegalStateException("Unexpected Object method " + method);
        };
    }
    private static Object zero(Class<?> result) {
        if (!result.isPrimitive() || result == void.class) return null;
        if (result == boolean.class) return false;
        if (result == char.class) return (char)0;
        if (result == byte.class) return (byte)0;
        if (result == short.class) return (short)0;
        if (result == int.class) return 0;
        if (result == long.class) return 0L;
        if (result == float.class) return 0F;
        if (result == double.class) return 0D;
        throw new IllegalStateException("Unexpected primitive return type " + result);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.SourceVanillaMobLoot;
import io.github.ningmeng123.mite.entity.animal.Livestock032;
import io.github.ningmeng123.mite.entity.animal.Livestock032View;
import io.github.ningmeng123.mite.entity.animal.LivestockRules032;
import io.github.ningmeng123.mite.integration.LivestockInspection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Read-only diagnosis follows the same synchronized needs as livestock skins. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class LivestockInspectionGameTests {
    private static final String PREFIX = "tooltip.mite.livestock.";
    private static final List<EntityType<? extends Animal>> TYPES =
            List.of(EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN, EntityType.MOOSHROOM);

    private LivestockInspectionGameTests() {}

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type) {
        T entity = type.create(h.getLevel());
        BlockPos pos = h.absolutePos(new BlockPos(1, 10, 1));
        entity.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        entity.setNoAi(true); entity.setNoGravity(true);
        h.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void watched(Mob animal, float food, float water, float freedom, int recovery) {
        var view = (Livestock032View)animal;
        view.mite$setLivestockNeeds(new LivestockRules032.Needs(food, water, freedom));
        view.mite$setRecoveryTicks(recovery);
    }

    @GameTest(template = "empty")
    public static void eachRealDeficiencyIsIndependentAndTheExactQuarterIsHealthy(GameTestHelper h) {
        var animal = mob(h, EntityType.COW);
        String[] causes = {"cause_food", "cause_water", "cause_freedom"};
        for (int index = 0; index < 3; index++) {
            float[] values = {1, 1, 1}; values[index] = Math.nextDown(.25F);
            watched(animal, values[0], values[1], values[2], LivestockRules032.RECOVERY_TICKS);
            var snapshot = LivestockInspection.inspect(animal);
            List<Component> lines = LivestockInspection.lines(snapshot);
            h.assertTrue(!snapshot.needsMet() && !snapshot.well(), "The last float below a quarter is genuinely unwell");
            h.assertValueEqual(lines.size(), 2, "One deficiency produces only its status and actual cause");
            h.assertValueEqual(status(h, lines), PREFIX + "health.sick", "Unmet needs are sick rather than already recovering");
            for (int cause = 0; cause < 3; cause++) h.assertTrue(has(lines, causes[cause]) == (cause == index),
                    "Full resources cannot mask or invent an independent cause");
            h.assertValueEqual(integerArgument(h, line(h, lines, causes[index])), 24,
                    "A value just below the illness threshold must not round up to a misleading 25 percent");
            h.assertFalse(has(lines, "recovery"), "A paused recovery clock is not advertised while needs remain deficient");
            values[index] = .25F; watched(animal, values[0], values[1], values[2], 0);
            h.assertTrue(LivestockInspection.inspect(animal).well() && LivestockInspection.lines(LivestockInspection.inspect(animal)).isEmpty(),
                    "Exactly one quarter meets the real wellness threshold and adds no illness tooltip");
        }
        animal.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void simultaneousFoodWaterAndSpaceProblemsAreAllReported(GameTestHelper h) {
        var pig = mob(h, EntityType.PIG);
        watched(pig, 0, .125F, Math.nextDown(.25F), LivestockRules032.RECOVERY_TICKS);
        List<Component> lines = LivestockInspection.lines(LivestockInspection.inspect(pig));
        h.assertValueEqual(lines.size(), 4, "One health heading retains all three simultaneous causes");
        h.assertValueEqual(integerArgument(h, line(h, lines, "cause_food")), 0, "Food reports its real depleted value");
        h.assertValueEqual(integerArgument(h, line(h, lines, "cause_water")), 12, "Water reports its own value independently of food");
        h.assertValueEqual(integerArgument(h, line(h, lines, "cause_freedom")), 24, "Space does not disappear behind another deficiency");
        pig.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualRecoveryCountsOnlyHealthyNeedsAndDisappearsOnItsLastTick(GameTestHelper h) {
        var cow = mob(h, EntityType.COW);
        Livestock032.needs(cow, new LivestockRules032.Needs(.1F, .8F, .8F));
        for (int tick = 0; tick < 40; tick++) Livestock032.tickRecovery(cow);
        h.assertValueEqual(LivestockInspection.inspect(cow).recoveryTicks(), 3600, "Illness leaves the real recovery clock paused");
        Livestock032.needs(cow, new LivestockRules032.Needs(.8F, .8F, .8F));
        var initial = LivestockInspection.lines(LivestockInspection.inspect(cow));
        h.assertValueEqual(status(h, initial), PREFIX + "health.recovering", "Restoring every resource starts recovery rather than instant health");
        h.assertValueEqual(initial.size(), 2, "Recovery is distinct from the three cured causes");
        h.assertValueEqual(integerArgument(h, line(h, initial, "recovery")), 180, "Three minutes of native recovery uses twenty ticks per second");
        for (int tick = 0; tick < 3599; tick++) Livestock032.tickRecovery(cow);
        var penultimate = LivestockInspection.lines(LivestockInspection.inspect(cow));
        h.assertValueEqual(integerArgument(h, line(h, penultimate, "recovery")), 1, "A remaining positive fraction of a second rounds up");
        h.assertFalse(LivestockInspection.inspect(cow).well(), "The final recovery tick is still unwell");
        Livestock032.tickRecovery(cow);
        h.assertTrue(LivestockInspection.inspect(cow).well() && LivestockInspection.lines(LivestockInspection.inspect(cow)).isEmpty(),
                "The exact last tick clears the viewer together with actual wellness");
        cow.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void inspectionReadsExistingWatchersWithoutInitializingOrMutatingPersistentState(GameTestHelper h) {
        var sheep = mob(h, EntityType.SHEEP);
        sheep.getPersistentData().remove(SourceVanillaMobLoot.STATE);
        watched(sheep, .1F, .8F, .8F, 3600);
        CompoundTag before = sheep.getPersistentData().copy();
        for (int repetition = 0; repetition < 8; repetition++) {
            var snapshot = LivestockInspection.inspect(sheep);
            h.assertTrue(snapshot.food() == .1F && snapshot.water() == .8F && snapshot.recoveryTicks() == 3600,
                    "The viewer uses values already synchronized for body rendering even when persistent needs are absent");
            LivestockInspection.lines(snapshot);
        }
        h.assertValueEqual(sheep.getPersistentData(), before, "Looking never initializes needs, rerolls production or alters a recovery timer");
        var stale = new CompoundTag(); stale.putFloat("food", 0); stale.putFloat("water", 0); stale.putFloat("freedom", 0); stale.putInt("recovery_ticks", 3600);
        sheep.getPersistentData().put(SourceVanillaMobLoot.STATE, stale);
        watched(sheep, 1, 1, 1, 0);
        before = sheep.getPersistentData().copy();
        h.assertTrue(LivestockInspection.lines(LivestockInspection.inspect(sheep)).isEmpty(),
                "Stale server-only NBT does not override the client watcher values or invent a disease");
        h.assertValueEqual(sheep.getPersistentData(), before, "Even a deliberately divergent stored state stays untouched by display");
        sheep.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyFiveLivingSourceSpeciesReceiveDiagnosis(GameTestHelper h) {
        for (var type : TYPES) {
            var animal = mob(h, type);
            watched(animal, .25F, .25F, .25F, 0);
            h.assertTrue(LivestockInspection.inspect(animal) != null && LivestockInspection.inspect(animal).well(),
                    "All five source livestock types include the exact healthy boundary, including modern mooshrooms");
            watched(animal, .1F, 1, 1, 3600);
            h.assertTrue(has(LivestockInspection.lines(LivestockInspection.inspect(animal)), "cause_food"),
                    "Each source species receives its real deficiency");
            animal.setHealth(0);
            h.assertTrue(LivestockInspection.inspect(animal) == null, "Dead targets do not retain an illness diagnosis");
            animal.discard();
        }
        for (var type : List.of(EntityType.HORSE, EntityType.WOLF, EntityType.ZOMBIE)) {
            var unsupported = mob(h, type);
            h.assertTrue(LivestockInspection.inspect(unsupported) == null, "Foreign wellness models do not inherit source livestock causes");
            unsupported.discard();
        }
        var removed = mob(h, EntityType.CHICKEN); removed.discard();
        h.assertTrue(LivestockInspection.inspect(removed) == null && LivestockInspection.inspect(null) == null
                        && LivestockInspection.lines(null).isEmpty(),
                "Removed and absent targets produce no stale viewer lines");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void jadeRemainsOptionalAndItsActualCallbackUsesOnlyWatchedEntityData(GameTestHelper h) {
        if (ModList.get().isLoaded("jade")) RuntimeChecks.inspect(h);
        else {
            try {
                Class.forName("snownee.jade.api.IWailaPlugin", false, LivestockInspectionGameTests.class.getClassLoader());
                h.fail("The compile-only Jade API must not become a required runtime dependency");
            } catch (ClassNotFoundException expected) { /* The nested Jade checks remain unresolved. */ }
        }
        h.succeed();
    }

    private static String key(Component line) {
        return line.getContents() instanceof TranslatableContents translated ? translated.getKey() : "";
    }

    private static boolean has(List<Component> lines, String suffix) {
        return lines.stream().anyMatch(line -> key(line).equals(PREFIX + suffix));
    }

    private static Component line(GameTestHelper h, List<Component> lines, String suffix) {
        var found = lines.stream().filter(line -> key(line).equals(PREFIX + suffix)).toList();
        h.assertValueEqual(found.size(), 1, "Expected exactly one " + suffix + " line");
        return found.getFirst();
    }

    private static int integerArgument(GameTestHelper h, Component line) {
        Object[] args = ((TranslatableContents)line.getContents()).getArgs();
        h.assertTrue(args.length == 1 && args[0] instanceof Integer, "Viewer line has one integer percentage or seconds argument");
        return (Integer)args[0];
    }

    private static String status(GameTestHelper h, List<Component> lines) {
        Object[] args = ((TranslatableContents)line(h, lines, "health").getContents()).getArgs();
        h.assertTrue(args.length == 1 && args[0] instanceof Component, "Health remains a localized nested component");
        return key((Component)args[0]);
    }

    /** The no-Jade dedicated server never resolves the optional API referenced in this nested class. */
    private static final class RuntimeChecks {
        private static final io.github.ningmeng123.mite.integration.jade.LivestockJadeProvider PROVIDER =
                io.github.ningmeng123.mite.integration.jade.LivestockJadeProvider.INSTANCE;

        private static void inspect(GameTestHelper h) {
            var cow = mob(h, EntityType.COW); watched(cow, .125F, 1, .1F, 3600);
            CompoundTag before = cow.getPersistentData().copy();
            List<Component> shown = new ArrayList<>();
            var accessor = proxy(snownee.jade.api.EntityAccessor.class, (target, method, args) -> {
                if (method.getName().equals("getEntity")) return cow;
                if (method.getName().equals("getServerData")) throw new IllegalStateException("Already synchronized needs must not request an extra NBT payload");
                return defaults(target, method, args);
            });
            var tooltip = proxy(snownee.jade.api.ITooltip.class, (target, method, args) -> {
                if (method.getName().equals("add") && args.length == 2 && args[0] instanceof Component line) {
                    h.assertValueEqual(args[1], PROVIDER.getUid(), "Livestock lines use only their own Jade tag");
                    shown.add(line); return null;
                }
                return defaults(target, method, args);
            });
            PROVIDER.appendTooltip(tooltip, accessor, null);
            h.assertValueEqual(shown, LivestockInspection.lines(LivestockInspection.inspect(cow)),
                    "The real Jade callback displays both actual causes from synchronized entity data");
            h.assertValueEqual(cow.getPersistentData(), before, "The complete API callback stays read-only");
            shown.clear(); watched(cow, 1, 1, 1, 0); PROVIDER.appendTooltip(tooltip, accessor, null);
            h.assertTrue(shown.isEmpty(), "Healthy animals retain Jade's ordinary name and hearts without extra disease lines");

            // Client registration declares Screen in its signatures and is exercised
            // by CropJadeAudit in the client environment, never proxied on a server.
            int[] serverProviders = {0};
            var common = proxy(snownee.jade.api.IWailaCommonRegistration.class, (target, method, args) -> {
                if (method.getName().equals("registerEntityDataProvider") && args[0] == PROVIDER) serverProviders[0]++;
                if (method.getName().startsWith("register")) return null;
                return defaults(target, method, args);
            });
            var plugin = new io.github.ningmeng123.mite.integration.jade.MiteJadePlugin();
            plugin.register(common);
            h.assertValueEqual(serverProviders[0], 0, "Livestock adds no redundant Jade entity network data provider");
            cow.discard();
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> api, java.lang.reflect.InvocationHandler handler) {
            return (T)java.lang.reflect.Proxy.newProxyInstance(LivestockInspectionGameTests.class.getClassLoader(), new Class<?>[]{api}, handler);
        }

        private static Object defaults(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
            if (method.getName().equals("toString")) return "LivestockInspectionGameTests proxy";
            if (method.getName().equals("hashCode")) return System.identityHashCode(target);
            if (method.getName().equals("equals")) return target == args[0];
            if (method.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(target, method, args);
            if (method.getReturnType() == void.class) return null;
            throw new UnsupportedOperationException("Unexpected Jade API call " + method);
        }
    }
}

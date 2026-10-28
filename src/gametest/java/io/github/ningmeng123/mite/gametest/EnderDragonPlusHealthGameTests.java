package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.compat.enderdragonplus.EnderDragonPlusHealthCompat;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real addon/EDP event ordering and persisted dragon health migration. @author NingMeng123 */
@GameTestHolder("mite_enderdragonplus")
@PrefixGameTestTemplate(false)
public final class EnderDragonPlusHealthGameTests {
    private static final ResourceLocation EXTRA_HEALTH = ResourceLocation.fromNamespaceAndPath("mite_test", "dragon_health");

    private EnderDragonPlusHealthGameTests() {}

    private static EnderDragon dragon(GameTestHelper helper) throws ReflectiveOperationException {
        EnderDragon dragon = EntityType.ENDER_DRAGON.create(helper.getLevel());
        helper.assertTrue(dragon != null, "A native dragon can be constructed");
        dragon.setNoAi(true); dragon.setNoGravity(true);
        // Match /summon admission: these fixtures are explicit bosses, independent of first-day grace.
        dragon.getPersistentData().putBoolean("modern_ite:explicit_command_spawn", true);
        Object brain = brain(dragon);
        brain.getClass().getMethod("markEnhanced").invoke(brain);
        helper.assertTrue((Boolean)brain.getClass().getMethod("active").invoke(brain),
                "The real EDP brain is enhanced even when special summoning is required");
        return dragon;
    }

    private static Object brain(EnderDragon dragon) throws ReflectiveOperationException {
        return Class.forName("com.enderdragonplus.dragon.DragonBrainHolder").getMethod("edp$getBrain").invoke(dragon);
    }

    private static void postJoin(GameTestHelper helper, EnderDragon dragon, boolean loaded) throws ReflectiveOperationException {
        double edpMaximum = edpHealth().get();
        boolean[] observed = {false};
        Consumer<EntityJoinLevelEvent> afterEdp = event -> {
            if (event.getEntity() != dragon) return;
            observed[0] = true;
            close(helper, dragon.getAttributeBaseValue(Attributes.MAX_HEALTH), edpMaximum,
                    "The real NORMAL-priority EDP join handler applies its configured health before compatibility restores ITE");
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, EntityJoinLevelEvent.class, afterEdp);
        var join = new EntityJoinLevelEvent(dragon, helper.getLevel(), loaded);
        try { NeoForge.EVENT_BUS.post(join); }
        finally { NeoForge.EVENT_BUS.unregister(afterEdp); }
        helper.assertFalse(join.isCanceled(), "The explicit dragon passes normal spawn admission before health initialization");
        helper.assertTrue(observed[0], "The intermediate observer saw the actual join event");
    }

    private static void requireMods(GameTestHelper helper) {
        helper.assertTrue(EnderDragonPlusHealthCompat.enabled(), "This integration namespace requires modern_ite and enderdragonplus");
    }

    private static ModConfigSpec.DoubleValue edpHealth() throws ReflectiveOperationException {
        return (ModConfigSpec.DoubleValue)Class.forName("com.enderdragonplus.EdpConfig").getField("MAX_HEALTH").get(null);
    }

    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .01D, message + ": " + actual + " vs " + expected);
    }

    @GameTest(template = "empty")
    public static void freshDragonsKeepTheIteProfileAfterBothRealJoinHandlers(GameTestHelper helper) throws Exception {
        requireMods(helper);
        var setting = edpHealth(); double old = setting.get();
        var specialSummon = (ModConfigSpec.BooleanValue)Class.forName("com.enderdragonplus.EdpConfig")
                .getField("REQUIRE_SPECIAL_SUMMON").get(null);
        boolean oldSpecialSummon = specialSummon.get(); specialSummon.set(true);
        try {
            for (double edpMaximum : new double[]{400D, 600D}) {
                setting.set(edpMaximum);
                EnderDragon dragon = dragon(helper);
                try {
                    postJoin(helper, dragon, false);
                    close(helper, dragon.getAttributeBaseValue(Attributes.MAX_HEALTH), 7500D, "EDP cannot replace the ITE 7500-HP profile");
                    close(helper, dragon.getMaxHealth(), 7500D, "The effective maximum is synchronized through native attributes");
                    close(helper, dragon.getHealth(), 7500D, "A newly spawned dragon starts at full ITE health");
                    helper.assertTrue(dragon.getPersistentData().getBoolean("modern_ite:boss"), "The actual ITE initializer participated");
                    helper.assertTrue(Class.forName("com.enderdragonplus.dragon.DragonBrainHolder").isInstance(dragon), "The actual EDP dragon mixin remains installed");
                } finally { dragon.discard(); }
            }
        } finally { setting.set(old); specialSummon.set(oldSpecialSummon); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedFourHundredHpDragonsKeepWoundsModifiersAndCombatProgressOnRepeatedLoads(GameTestHelper helper) throws Exception {
        requireMods(helper);
        var setting = edpHealth(); double old = setting.get(); setting.set(400D);
        EnderDragon dragon = dragon(helper);
        try {
            dragon.getPersistentData().putBoolean("modern_ite:source_profile", true);
            dragon.getPersistentData().putBoolean("modern_ite:boss", true);
            dragon.getPersistentData().putBoolean("modern_ite:dragon_half", true);
            dragon.getPersistentData().putInt("mite_test:combat_progress", 42);
            dragon.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400D);
            dragon.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(EXTRA_HEALTH, .5D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            dragon.setHealth(150F); // One quarter of the old 600 effective HP, including the independent modifier.
            for (int load = 0; load < 3; load++) {
                CompoundTag saved = new CompoundTag(); dragon.saveWithoutId(saved);
                saved.putString("id", "minecraft:ender_dragon"); dragon.discard();
                dragon = (EnderDragon)EntityType.loadEntityRecursive(saved, helper.getLevel(), Function.identity());
                helper.assertTrue(dragon != null, "The dragon reconstructs through native NBT loading");
                helper.assertTrue((Boolean)brain(dragon).getClass().getMethod("active").invoke(brain(dragon)),
                        "EDP enhanced state survives the native NBT round trip");
                postJoin(helper, dragon, true);
                close(helper, dragon.getAttributeBaseValue(Attributes.MAX_HEALTH), 7500D, "Saved 400-HP profiles migrate to the addon base");
                close(helper, dragon.getMaxHealth(), 11250D, "An independent permanent health modifier survives");
                close(helper, dragon.getHealth(), 2812.5D, "Reload keeps exactly one quarter health without healing or compounding");
                helper.assertTrue(dragon.getPersistentData().getBoolean("modern_ite:dragon_half"), "The ITE phase marker survives");
                helper.assertValueEqual(dragon.getPersistentData().getInt("mite_test:combat_progress"), 42, "Unrelated combat data survives");
            }
        } finally { if (dragon != null) dragon.discard(); setting.set(old); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explicitSavedHealthAndUnprofiledDragonsRemainUntouched(GameTestHelper helper) throws Exception {
        requireMods(helper);
        var setting = edpHealth(); double old = setting.get(); setting.set(400D);
        EnderDragon custom = dragon(helper), ordinary = dragon(helper);
        try {
            custom.getPersistentData().putBoolean("modern_ite:source_profile", true);
            custom.getPersistentData().putBoolean("modern_ite:boss", true);
            custom.getAttribute(Attributes.MAX_HEALTH).setBaseValue(9000D); custom.setHealth(4500F);
            postJoin(helper, custom, true);
            close(helper, custom.getMaxHealth(), 9000D, "Explicit saved non-EDP health remains authoritative");
            close(helper, custom.getHealth(), 4500D, "Custom health keeps its saved wound fraction");
            ordinary.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400D); ordinary.setHealth(123F);
            EnderDragonPlusHealthCompat.restore(new EntityJoinLevelEvent(ordinary, helper.getLevel(), true));
            close(helper, ordinary.getMaxHealth(), 400D, "A dragon without the addon profile is outside this compatibility");
            close(helper, ordinary.getHealth(), 123D, "An unprofiled dragon is not healed");
        } finally { custom.discard(); ordinary.discard(); setting.set(old); }
        helper.succeed();
    }
}

package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Executes exported wrapper bytecode without constructing a renderer or loading a world. @author NingMeng123 */
final class LunarPrecipitationAudit {
    private static final String FIXTURE = "io/github/ningmeng123/mite/gametest/client/LunarPrecipitationBytecodeFixture";
    private static final String LEVEL = "Lnet/minecraft/client/multiplayer/ClientLevel;";
    private static final String BIOME = "Lnet/minecraft/world/level/biome/Biome;";
    private static final String OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
    private LunarPrecipitationAudit() {}

    static void verify(ClassNode renderer, MethodNode gate, MethodNode precipitation, JsonObject report) throws Exception {
        check(gate.desc.equals("(" + BIOME + OPERATION + ")Z"), "The rain gate must receive the actual biome receiver");
        check(precipitation.desc.equals("(" + BIOME + "Lnet/minecraft/core/BlockPos;" + OPERATION
                + ")Lnet/minecraft/world/level/biome/Biome$Precipitation;"), "Pinned precipitation wrapper descriptor");
        Class<?> fixtureType = fixture(renderer, gate, precipitation);
        Object fixture = fixtureType.getConstructor().newInstance();
        Method gateCall = fixtureType.getMethod("rainGate", Biome.class, Operation.class);
        Method precipitationCall = fixtureType.getMethod("precipitation", Biome.class, BlockPos.class, Operation.class);
        Field fixtureLevel = fixtureType.getField("level");
        Operation<Boolean> forbiddenGate = arguments -> { throw new AssertionError("Culled weather queried native hasPrecipitation"); };
        Operation<Biome.Precipitation> forbiddenPrecipitation = arguments -> { throw new AssertionError("Culled weather queried native getPrecipitationAt"); };
        JsonArray cases = new JsonArray();

        check(Boolean.FALSE.equals(invoke(gateCall, fixture, null, forbiddenGate)), "A null biome must keep the rain gate closed without a level");
        check(invoke(precipitationCall, fixture, null, BlockPos.ZERO, forbiddenPrecipitation) == Biome.Precipitation.NONE,
                "A null biome must skip native precipitation without a level");
        cases.add("null_biome_without_level_skips_both_native_operations");

        ClientLevel level = levelFixture();
        fixtureLevel.set(fixture, level);
        ClientLevel.ClientLevelData data = level.getLevelData();
        Biome desert = biome(false, 2F), coldDry = biome(false, -0.5F);
        Biome rain = biome(true, 0.8F), snow = biome(true, -0.5F);
        Operation<Boolean> nativeGate = arguments -> {
            check(arguments.length == 1, "The native rain gate receives only its biome receiver");
            return ((Biome)arguments[0]).hasPrecipitation();
        };
        Operation<Biome.Precipitation> nativePrecipitation = arguments -> {
            check(arguments.length == 2 && arguments[1] == BlockPos.ZERO, "Native precipitation preserves its position argument");
            return ((Biome)arguments[0]).getPrecipitationAt((BlockPos)arguments[1]);
        };

        for (long time : new long[]{0L, 744003L, 750000L, 762000L, 3066000L}) {
            data.setDayTime(time);
            check(Boolean.FALSE.equals(invoke(gateCall, fixture, null, forbiddenGate)), "Null biome remains culled at day time " + time);
            check(invoke(precipitationCall, fixture, null, BlockPos.ZERO, forbiddenPrecipitation) == Biome.Precipitation.NONE,
                    "Null biome remains precipitation-free at day time " + time);
        }
        cases.add("null_biome_stays_culled_on_crash_tick_blood_day_blood_night_and_blue_moon");

        for (long time : new long[]{6000L, 744003L, 750000L, 3066000L}) {
            data.setDayTime(time);
            check(Boolean.TRUE.equals(invoke(gateCall, fixture, rain, nativeGate)), "Valid native rain gate remains open");
            check(invoke(precipitationCall, fixture, rain, BlockPos.ZERO, nativePrecipitation) == Biome.Precipitation.RAIN,
                    "The actual wrapper preserves native rain at day time " + time);
            check(invoke(precipitationCall, fixture, snow, BlockPos.ZERO, nativePrecipitation) == Biome.Precipitation.SNOW,
                    "The actual wrapper preserves native snow at day time " + time);
        }
        cases.add("native_rain_and_snow_preserved_on_ordinary_blood_and_blue_dates");

        data.setDayTime(744003L);
        check(Boolean.TRUE.equals(invoke(gateCall, fixture, desert, nativeGate)), "The crash date still allows visible desert blood rain");
        check(invoke(precipitationCall, fixture, desert, BlockPos.ZERO, nativePrecipitation) == Biome.Precipitation.RAIN,
                "Visible warm arid biomes receive blood rain");
        check(invoke(precipitationCall, fixture, coldDry, BlockPos.ZERO, nativePrecipitation) == Biome.Precipitation.SNOW,
                "Visible cold arid biomes retain the blood-period snow rule");
        cases.add("visible_warm_and_cold_arid_biomes_keep_blood_precipitation");

        for (long time : new long[]{6000L, 3066000L}) {
            data.setDayTime(time);
            check(Boolean.FALSE.equals(invoke(gateCall, fixture, desert, nativeGate)), "Ordinary and blue dates do not enable desert rain");
            check(invoke(precipitationCall, fixture, desert, BlockPos.ZERO, nativePrecipitation) == Biome.Precipitation.NONE,
                    "Ordinary and blue dates retain native arid weather");
        }
        cases.add("ordinary_and_blue_dates_preserve_arid_weather");

        data.setDayTime(744003L);
        field(Level.class, "dimension").set(level, Level.NETHER);
        check(Boolean.FALSE.equals(invoke(gateCall, fixture, desert, nativeGate)), "Other dimensions do not gain the blood rain gate");
        check(invoke(precipitationCall, fixture, desert, BlockPos.ZERO, nativePrecipitation) == Biome.Precipitation.NONE,
                "Other dimensions do not gain blood precipitation");
        cases.add("non_overworld_dimension_preserves_native_weather");

        JsonObject fact = new JsonObject();
        fact.addProperty("status", "passed_executed_transformed_wrapper_bytecode");
        fact.addProperty("gate_source_method", gate.name + gate.desc);
        fact.addProperty("precipitation_source_method", precipitation.name + precipitation.desc);
        fact.addProperty("renderer_class_initialized_or_constructed", false);
        fact.addProperty("constructorless_client_level_calendar_fixture_used", true);
        fact.addProperty("level_fixture_constructor_invoked", false);
        fact.addProperty("minecraft_instance_or_world_load_invoked", false);
        fact.add("cases", cases);
        report.add("precipitation_culling_regression", fact);
    }

    private static Class<?> fixture(ClassNode renderer, MethodNode gate, MethodNode precipitation) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, FIXTURE, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "level", LEVEL, null, null).visitEnd();
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(0, 0); constructor.visitEnd();
        copyWrapper(renderer, gate, "rainGate", writer);
        copyWrapper(renderer, precipitation, "precipitation", writer);
        writer.visitEnd();
        return new FixtureLoader().define(writer.toByteArray());
    }

    private static void copyWrapper(ClassNode renderer, MethodNode original, String name, ClassWriter writer) {
        MethodNode copy = new MethodNode(); original.accept(copy);
        copy.name = name; copy.desc = original.desc; copy.access = Opcodes.ACC_PUBLIC;
        copy.visibleAnnotations = null; copy.invisibleAnnotations = null;
        for (AbstractInsnNode instruction : copy.instructions) {
            if (instruction instanceof FieldInsnNode field && field.owner.equals(renderer.name)) {
                check(field.getOpcode() == Opcodes.GETFIELD && field.name.equals("level") && field.desc.equals(LEVEL),
                        "Extracted wrappers must depend only on the renderer's level field");
                field.owner = FIXTURE;
            } else if (instruction instanceof MethodInsnNode call)
                check(!call.owner.equals(renderer.name), "Wrapper extraction must not call or initialize the renderer");
        }
        copy.accept(writer);
    }

    private static ClientLevel levelFixture() throws Exception {
        // Only the two calendar inputs are populated; no world, renderer, connection or Minecraft constructor runs.
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = field(unsafeType, "theUnsafe");
        ClientLevel level = (ClientLevel)unsafeType.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), ClientLevel.class);
        ClientLevel.ClientLevelData data = new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, false);
        field(Level.class, "levelData").set(level, data);
        field(ClientLevel.class, "clientLevelData").set(level, data);
        field(Level.class, "dimension").set(level, Level.OVERWORLD);
        return level;
    }

    private static Biome biome(boolean precipitation, float temperature) {
        return new Biome.BiomeBuilder().hasPrecipitation(precipitation).temperature(temperature).downfall(0F)
                .specialEffects(new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build())
                .generationSettings(BiomeGenerationSettings.EMPTY).mobSpawnSettings(MobSpawnSettings.EMPTY).build();
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name); result.setAccessible(true); return result;
    }

    private static Object invoke(Method method, Object fixture, Object... arguments) throws Exception {
        try { return method.invoke(fixture, arguments); }
        catch (InvocationTargetException failure) { throw new IllegalStateException("Actual transformed wrapper failed: " + method.getName(), failure.getCause()); }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private static final class FixtureLoader extends ClassLoader {
        FixtureLoader() { super(LunarPrecipitationAudit.class.getClassLoader()); }
        Class<?> define(byte[] bytes) { return defineClass(FIXTURE.replace('/', '.'), bytes, 0, bytes.length); }
    }
}

package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.block.MaterialAnvilBlock;
import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Normal runs prove optional linkage; an isolated Jade run checks real server entities and API callbacks. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class AnvilJadeGameTests {
    private AnvilJadeGameTests() {}

    @GameTest(template = "empty")
    public static void optionalAnvilJadeUsesActualServerWearWithoutMutatingAnyMaterialOrStage(GameTestHelper helper) {
        if (ModList.get().isLoaded("jade")) RuntimeChecks.inspect(helper);
        else {
            try {
                Class.forName("snownee.jade.api.IWailaPlugin", false, AnvilJadeGameTests.class.getClassLoader());
                helper.fail("The compile-only Jade API must not become a required runtime dependency");
            } catch (ClassNotFoundException expected) { /* The optional implementation remains unresolved. */ }
        }
        helper.succeed();
    }

    /** Never resolved by a no-Jade server; no optional API appears on the discovered outer test class. */
    private static final class RuntimeChecks {
        private static final io.github.ningmeng123.mite.integration.jade.AnvilJadeProvider PROVIDER =
                io.github.ningmeng123.mite.integration.jade.AnvilJadeProvider.INSTANCE;

        private static void inspect(GameTestHelper helper) {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            List<BlockState> states = new ArrayList<>(List.of(Blocks.ANVIL.defaultBlockState(),
                    Blocks.CHIPPED_ANVIL.defaultBlockState(), Blocks.DAMAGED_ANVIL.defaultBlockState()));
            for (var block : MiteFunctionalBlocks.ANVILS.values()) for (int stage = 0; stage < 3; stage++)
                states.add(block.get().defaultBlockState().setValue(MaterialAnvilBlock.DAMAGE_STAGE, stage));
            helper.assertValueEqual(states.size(), 21, "Seven materials have three supported wear appearances");
            for (BlockState placed : states) {
                helper.getLevel().removeBlockEntity(pos);
                helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(pos, placed);
                var anvil = (MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(pos);
                helper.assertTrue(anvil != null, "Native and additional anvil states have real wear entities");
                anvil.addDamage(37);
                anvil = (MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(pos);
                BlockState state = helper.getLevel().getBlockState(pos);
                CompoundTag before = anvil.saveWithFullMetadata(helper.getLevel().registryAccess());
                CompoundTag data = new CompoundTag();
                var accessor = accessor(helper, pos, state, data, false);
                helper.assertTrue(PROVIDER.shouldRequestData(accessor), "Every supported anvil requests authoritative data");
                for (int repeat = 0; repeat < 8; repeat++) PROVIDER.appendServerData(data, accessor);
                var snapshot = io.github.ningmeng123.mite.integration.jade.AnvilJadeProvider.snapshot(accessor);
                helper.assertTrue(snapshot != null && snapshot.maximum() == anvil.durability()
                                && snapshot.remaining() == anvil.durability() - anvil.damage(),
                        "The API reads exact entity damage, including the extra 37 wear within each appearance stage");
                List<Component> lines = new ArrayList<>();
                PROVIDER.appendTooltip(tooltip(lines), accessor, null);
                helper.assertTrue(lines.size() == 1 && lines.getFirst().getContents() instanceof TranslatableContents translated
                                && translated.getKey().equals("tooltip.mite.durability")
                                && List.of(translated.getArgs()).equals(List.of(snapshot.remaining(), snapshot.maximum())),
                        "The actual Jade callback emits exactly one localized remaining/maximum line");
                helper.assertTrue(before.equals(anvil.saveWithFullMetadata(helper.getLevel().registryAccess()))
                                && helper.getLevel().getBlockState(pos) == state,
                        "Repeated inspection and tooltip callbacks never mutate entity wear, components or block appearance");
                assertNoData(helper, accessor(helper, pos, state, new CompoundTag(), true), "Fake targets do not expose real anvil wear");
                helper.getLevel().setBlockAndUpdate(pos, state.setValue(AnvilBlock.FACING, Direction.EAST));
                BlockState otherState = helper.getLevel().getBlockState(pos);
                if (otherState == state) helper.getLevel().setBlockAndUpdate(pos, state.setValue(AnvilBlock.FACING, Direction.WEST));
                assertNoData(helper, accessor(helper, pos, state, new CompoundTag(), false), "A stale accessor state cannot collect another target state");
            }
            BlockState state = helper.getLevel().getBlockState(pos);
            var anvil = (MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(pos);
            CompoundTag exhausted = anvil.saveWithFullMetadata(helper.getLevel().registryAccess());
            exhausted.putInt("mite_damage", anvil.durability()); anvil.loadWithComponents(exhausted, helper.getLevel().registryAccess());
            assertNoData(helper, accessor(helper, pos, state, new CompoundTag(), false), "A loaded exhausted entity supplies no display data");
            helper.getLevel().removeBlockEntity(pos);
            assertNoData(helper, accessor(helper, pos, state, new CompoundTag(), false), "A missing or removed target entity supplies no display data");
            BlockPos unloaded = pos.offset(400000, 0, 400000);
            helper.assertFalse(helper.getLevel().hasChunkAt(unloaded), "The stale remote target is in an unloaded chunk");
            assertNoData(helper, accessor(helper, unloaded, state, new CompoundTag(), false), "Inspection never loads a remote stale target");
            helper.assertFalse(helper.getLevel().hasChunkAt(unloaded), "Rejected inspection leaves the remote chunk unloaded");
        }

        private static void assertNoData(GameTestHelper helper, snownee.jade.api.BlockAccessor accessor, String reason) {
            CompoundTag data = accessor.getServerData();
            data.put(io.github.ningmeng123.mite.integration.jade.AnvilJadeProvider.DATA_KEY, new CompoundTag());
            PROVIDER.appendServerData(data, accessor);
            helper.assertTrue(data.isEmpty(), reason);
        }

        private static snownee.jade.api.BlockAccessor accessor(GameTestHelper helper, BlockPos pos, BlockState state, CompoundTag data, boolean fake) {
            return proxy(snownee.jade.api.BlockAccessor.class, (target, method, args) -> switch (method.getName()) {
                case "getLevel" -> helper.getLevel();
                case "getPosition" -> pos;
                case "getBlockState" -> state;
                case "getBlock" -> state.getBlock();
                case "getServerData" -> data;
                case "isFakeBlock" -> fake;
                case "getBlockEntity" -> throw new IllegalStateException("Server inspection must resolve the real Level entity rather than trust a cached accessor entity");
                default -> defaults(target, method, args);
            });
        }

        private static snownee.jade.api.ITooltip tooltip(List<Component> lines) {
            return proxy(snownee.jade.api.ITooltip.class, (target, method, args) -> {
                if (method.getName().equals("add") && args.length == 2 && args[0] instanceof Component line) {
                    if (!args[1].equals(PROVIDER.getUid())) throw new IllegalStateException("Anvil lines use only their own Jade tag");
                    lines.add(line); return null;
                }
                return defaults(target, method, args);
            });
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> api, java.lang.reflect.InvocationHandler handler) {
            return (T) java.lang.reflect.Proxy.newProxyInstance(AnvilJadeGameTests.class.getClassLoader(), new Class<?>[]{api}, handler);
        }

        private static Object defaults(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
            if (method.getName().equals("toString")) return "AnvilJadeGameTests proxy";
            if (method.getName().equals("hashCode")) return System.identityHashCode(target);
            if (method.getName().equals("equals")) return target == args[0];
            if (method.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(target, method, args);
            if (method.getReturnType() == void.class) return null;
            throw new UnsupportedOperationException("Unexpected Jade API call " + method);
        }
    }
}

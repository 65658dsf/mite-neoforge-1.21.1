package io.github.ningmeng123.mite.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.AutoHarvestState;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.KeyModifier;

/** Source auto-harvest adds a held-input predicate, never a synthetic click or action packet. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class AutoHarvestInput {
    private static final AutoHarvestState<BlockState> STATE = new AutoHarvestState<>(AutoHarvestInput::matches);
    private static Object previousPlayer, previousLevel, previousGameMode;
    private static InputConstants.Key previousAttack, previousUse;
    private static KeyModifier previousAttackModifier, previousUseModifier;
    private static int previousSlot = -1;

    private AutoHarvestInput() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) { context(Minecraft.getInstance()); }

    public static void beginInput(Minecraft minecraft) {
        if (context(minecraft)) STATE.update(minecraft.options.keyAttack.isDown(), minecraft.options.keyUse.isDown(),
                target(minecraft), Util.getMillis());
    }

    public static boolean wantsContinue(Minecraft minecraft) {
        return context(minecraft) && !minecraft.player.isUsingItem()
                && STATE.wantsContinue(target(minecraft), Util.getMillis());
    }

    public static boolean consumeUse(Minecraft minecraft) {
        if (!context(minecraft)) return false;
        // R196 suppresses use while attack is physically held, even before a target is armed.
        if (minecraft.options.keyAttack.isDown()) return true;
        STATE.clear();
        return false;
    }

    public static void recordAttempt(Minecraft minecraft, boolean held) {
        if (held && context(minecraft) && !minecraft.player.isUsingItem()) STATE.recordAttempt(Util.getMillis());
    }

    public static void reset() {
        STATE.clear();
        previousPlayer = previousLevel = previousGameMode = null;
        previousAttack = previousUse = null;
        previousAttackModifier = previousUseModifier = null;
        previousSlot = -1;
    }

    private static boolean context(Minecraft minecraft) {
        var player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gameMode == null || minecraft.options == null
                || minecraft.screen != null || minecraft.getOverlay() != null || !minecraft.isWindowActive()
                || minecraft.isPaused() || !minecraft.mouseHandler.isMouseGrabbed() || !player.isAlive()
                || player.isRemoved() || player.isSleeping() || player.isSpectator() || !FoodConsumption.hasEnergy(player)) {
            reset();
            return false;
        }
        var attack = minecraft.options.keyAttack;
        var use = minecraft.options.keyUse;
        int slot = player.getInventory().selected;
        if (previousPlayer != player || previousLevel != minecraft.level || previousGameMode != minecraft.gameMode
                || previousSlot != slot || !attack.getKey().equals(previousAttack) || !use.getKey().equals(previousUse)
                || previousAttackModifier != attack.getKeyModifier() || previousUseModifier != use.getKeyModifier()) {
            STATE.clear();
            previousPlayer = player; previousLevel = minecraft.level; previousGameMode = minecraft.gameMode;
            previousSlot = slot; previousAttack = attack.getKey(); previousUse = use.getKey();
            previousAttackModifier = attack.getKeyModifier(); previousUseModifier = use.getKeyModifier();
        }
        return true;
    }

    private static BlockState target(Minecraft minecraft) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
        return state.isAir() ? null : state;
    }

    /** Modern states replace old metadata; preserve the source's dirt pair and common flattened variants. */
    public static boolean matches(BlockState original, BlockState current) {
        // Double slabs had a separate legacy block ID, unlike top/bottom metadata.
        if (doubleSlab(original) != doubleSlab(current)) return false;
        if (original.getBlock() == current.getBlock()) return true;
        if ((original.is(Blocks.DIRT) || original.is(Blocks.GRASS_BLOCK))
                && (current.is(Blocks.DIRT) || current.is(Blocks.GRASS_BLOCK))) return true;
        var first = BuiltInRegistries.BLOCK.getKey(original.getBlock());
        var second = BuiltInRegistries.BLOCK.getKey(current.getBlock());
        return first.getNamespace().equals("minecraft") && second.getNamespace().equals("minecraft")
                && family(original, first.getPath()).equals(family(current, second.getPath()));
    }

    private static String family(BlockState state, String id) {
        // These variants shared one block ID in 1.6.4; only old tall-grass metadata was sensitive.
        for (String wood : List.of("oak", "spruce", "birch", "jungle")) {
            if (id.equals(wood + "_log") || id.equals(wood + "_wood")) return "legacy:log";
            if (id.equals(wood + "_leaves")) return "legacy:leaves";
            if (id.equals(wood + "_planks")) return "legacy:planks";
            if (id.equals(wood + "_sapling")) return "legacy:sapling";
            if (id.equals(wood + "_slab")) return "legacy:wood_slab:" + doubleSlab(state);
        }
        if (id.endsWith("_wool")) return "legacy:wool";
        if (id.endsWith("_carpet")) for (DyeColor color : DyeColor.values())
            if (id.equals(color.getName() + "_carpet")) return "legacy:carpet";
        if (id.endsWith("_terracotta") && !id.endsWith("_glazed_terracotta")) return "legacy:stained_clay";
        if (List.of("sandstone", "chiseled_sandstone", "cut_sandstone").contains(id)) return "legacy:sandstone";
        if (List.of("stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks").contains(id)) return "legacy:stone_bricks";
        if (List.of("cobblestone_wall", "mossy_cobblestone_wall").contains(id)) return "legacy:cobble_wall";
        if (List.of("quartz_block", "chiseled_quartz_block", "quartz_pillar").contains(id)) return "legacy:quartz";
        if (List.of("smooth_stone_slab", "sandstone_slab", "petrified_oak_slab", "cobblestone_slab",
                "brick_slab", "stone_brick_slab", "nether_brick_slab", "quartz_slab").contains(id))
            return "legacy:stone_slab:" + doubleSlab(state);
        return id;
    }

    private static boolean doubleSlab(BlockState state) {
        return state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE;
    }
}

package io.github.ningmeng123.mite.mining;

import io.github.ningmeng123.mite.rules.MiningRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Adapts R196 mining progress without changing registered hardness or blast resistance. @author NingMeng123 */
public final class MiningProgress {
    private MiningProgress() {
    }

    public static float effectiveHardness(BlockState state, float raw) {
        if (raw < 0.0F) {
            return raw;
        }
        if (state.is(Blocks.GRASS_BLOCK)) {
            return 0.5F;
        }
        if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) {
            return 0.4F;
        }
        // Original four log species use 1.2; modern vanilla logs/stems follow the same stage.
        if (state.is(BlockTags.LOGS) && BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft")) {
            return 1.2F;
        }
        return raw;
    }

    public static float destroyProgress(Player player, BlockState state, BlockGetter level, BlockPos pos) {
        float rawHardness = state.getDestroySpeed(level, pos);
        if (rawHardness < 0.0F) {
            return 0.0F;
        }
        if (LogHarvestRules.isBlocked(player, state)) {
            return 0.0F;
        }
        // This already includes modern tool/environment factors and our BreakSpeed level factor.
        float strength = player.getDigSpeed(state, pos);
        if (!(strength > 0.0F)) {
            // Modern event compatibility takes priority over R196's zero-hardness instant break:
            // a canceled BreakSpeed event (or zero speed) must still prevent progress.
            return 0.0F;
        }
        return MiningRules.destroyProgress(strength, effectiveHardness(state, rawHardness));
    }
}

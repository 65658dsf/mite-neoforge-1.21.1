package io.github.ningmeng123.mite.worldgen;

import java.time.LocalDate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/** Bat's Y63 restriction was overworld-only in R196 ro#getCanSpawnHere. @author NingMeng123 */
public final class R196UnderworldSpawns {
    private R196UnderworldSpawns() {}

    public static boolean ghastUnlocked(long highestOverworldDay) { return highestOverworldDay > 96L; }

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(EntityType.BAT, R196UnderworldSpawns::bat, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    public static boolean bat(EntityType<Bat> type, LevelAccessor level, MobSpawnType reason,
                              BlockPos position, RandomSource random) {
        if (!(level instanceof ServerLevelAccessor accessor)
                || !accessor.getLevel().dimension().equals(MiteWorldDimensions.UNDERWORLD)) {
            return Bat.checkBatSpawnRules(type, level, reason, position, random);
        }
        int light = level.getMaxLocalRawBrightness(position);
        LocalDate date = LocalDate.now();
        boolean halloween = date.getMonthValue() == 10 && date.getDayOfMonth() >= 20
                || date.getMonthValue() == 11 && date.getDayOfMonth() <= 3;
        if (!halloween && random.nextBoolean()) return false;
        BlockPos.MutableBlockPos below = position.mutable();
        while (below.getY() > level.getMinBuildHeight()) {
            below.move(0, -1, 0);
            if (level.getBlockState(below).isSolidRender(level, below)) break;
            light = Math.max(light, level.getMaxLocalRawBrightness(below));
        }
        return light <= random.nextInt(halloween ? 7 : 4)
                && Bat.checkMobSpawnRules(type, level, reason, position, random);
    }
}

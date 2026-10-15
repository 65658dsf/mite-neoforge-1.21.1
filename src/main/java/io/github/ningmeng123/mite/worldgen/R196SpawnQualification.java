package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Mob;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import java.time.LocalDate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Actual positioned source entities share collision, original light and class-specific qualifications. @author NingMeng123 */
public final class R196SpawnQualification {
    private R196SpawnQualification() {}
    public static boolean qualifies(Mob mob, ServerLevelAccessor access, boolean lightCheck, boolean spawner) {
        String name = R196MobSpawning.sourceClass(mob);
        if (name == null) return false;
        var level = access.getLevel(); BlockPos pos = mob.blockPosition(); RandomSource random = mob.getRandom();
        double sourceHeight = R196MobSpawning.underworld(level) ? R196SpawnRules.sourceY(pos.getY(), true) : mob.getY();
        boolean bat = name.equals("ro") || name.equals("EntityVampireBat") || name.equals("EntityGiantVampireBat") || name.equals("EntityNightwing");
        boolean animal = R196MobSpawning.category(mob) == R196SpawnRules.Category.ANIMAL;
        boolean monster = R196MobSpawning.category(mob) == R196SpawnRules.Category.MONSTER;
        boolean cube = name.equals("ts") || name.equals("tl") || mob instanceof R196Mob r196 && r196.miteKind().family() == MobKind.Family.CUBE;
        if (!level.isUnobstructed(mob)) return false;
        if (name.equals("sc")) {
            if (sourceHeight <= 45 || sourceHeight >= 63 || !onlyWater(access, mob.getBoundingBox().inflate(2))) return false;
            return true; // se#getCanSpawnHere does not run the ordinary block/liquid collision checks.
        }
        if (!access.noCollision(mob)) return false;
        boolean leavesException = name.equals("EntityWoodSpider") || name.equals("EntityBlackWidowSpider") || name.equals("rx");
        if (!leavesException && access.getBlockState(pos.below()).is(BlockTags.LEAVES)) return false;
        boolean shallow = monster && !name.equals("td") && !name.equals("tj") && !name.equals("tg") && !name.equals("sk")
                && !name.equals("EntityDemonSpider") && !name.equals("EntityFireElemental") && !name.equals("EntityHellhound");
        if (shallow) {
            if (access.getFluidState(pos.above()).is(FluidTags.WATER) || access.getBlockState(pos.above()).is(Blocks.LILY_PAD)
                    || access.getFluidState(pos).is(FluidTags.WATER) && access.getBlockState(pos.above()).blocksMotion()
                    || anyFluid(access, mob.getBoundingBox(), true)) return false;
        } else if (anyFluid(access, mob.getBoundingBox(), false)) return false;

        if (name.equals("rx")) return random.nextInt(3) != 0 && sourceHeight >= 63
                && (access.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK) || access.getBlockState(pos.below()).is(BlockTags.LEAVES));
        if (bat) {
            if (name.equals("EntityNightwing") && lightCheck && sunlight(mob, access)) return false;
            if (level.dimension().equals(Level.OVERWORLD) && pos.getY() >= 63
                    && (name.equals("ro") || !BloodMoonInvasion.unlocked(level))) return false;
            LocalDate date = LocalDate.now(); boolean halloween = date.getMonthValue() == 10 && date.getDayOfMonth() >= 20 || date.getMonthValue() == 11 && date.getDayOfMonth() <= 3;
            if (!halloween && random.nextBoolean()) return false;
            int brightest = access.getMaxLocalRawBrightness(pos);
            for (BlockPos.MutableBlockPos below = pos.mutable().move(Direction.DOWN); below.getY() > access.getMinBuildHeight(); below.move(Direction.DOWN)) {
                if (access.getBlockState(below).isSolidRender(access, below)) break;
                brightest = Math.max(brightest, access.getMaxLocalRawBrightness(below));
            }
            return brightest <= random.nextInt(halloween ? 7 : 4);
        }
        if (animal) return access.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK) && (!lightCheck || access.getMaxLocalRawBrightness(pos) > 8);
        if (name.equals("EntityEarthElemental") || name.equals("EntityClayGolem")) {
            var below = access.getBlockState(pos.below());
            if (!below.is(Blocks.STONE) && !below.is(Blocks.DEEPSLATE) && !below.is(Blocks.NETHERRACK) && !below.is(Blocks.CLAY) && !below.is(Blocks.END_STONE)) return false;
        }
        if (name.equals("ts")) {
            if (!(mob instanceof Slime slime)) return false;
            if (level.getChunkSource().getGenerator().getClass().getSimpleName().equals("FlatLevelSource") && random.nextInt(4) != 1) return false;
            if (slime.getSize() != 1 && level.getDifficulty() == Difficulty.PEACEFUL) return false;
            var moon = R196SpawnRules.moon(level.getDayTime(), level.dimension().equals(Level.OVERWORLD));
            float[] fractions = {1F, .75F, .5F, .25F, 0F, .25F, .5F, .75F};
            boolean swamp = R196MobSpawning.biomeId(level.getBiome(pos), level) == 6;
            if (swamp && sourceHeight > 50 && sourceHeight < 70 && random.nextFloat() < .5F && random.nextFloat() < fractions[moon.phase()]
                    && access.getMaxLocalRawBrightness(pos) <= random.nextInt(8)) return true;
            return random.nextInt(10) == 0 && net.minecraft.world.level.levelgen.WorldgenRandom.seedSlimeChunk(pos.getX() >> 4, pos.getZ() >> 4,
                    level.getSeed(), 987234911L).nextInt(10) == 0 && sourceHeight < 40;
        }
        if (name.equals("EntityOoze") || name.equals("EntityPudding")) {
            var below = access.getBlockState(pos.below()); if (!below.is(Blocks.STONE) && !below.is(Blocks.DEEPSLATE)) return false;
        }
        if (name.equals("tj")) return random.nextInt(20) == 0 && level.getDifficulty() != Difficulty.PEACEFUL;
        if (name.equals("tn") || name.equals("tl")) return level.getDifficulty() != Difficulty.PEACEFUL;
        if (monster && !cube && level.getDifficulty() == Difficulty.PEACEFUL) return false;
        if (name.equals("EntityHellhound") && R196SpawnRules.daytime((int)Math.floorMod(level.getDayTime(), 24000)) && R196OutdoorChecks.openSky(level, pos)) return false;
        boolean freeLight = name.equals("td") || name.equals("tq") || name.equals("EntityNetherspawn") || name.equals("EntityCopperspine")
                || name.equals("EntityHoarySilverfish") || name.equals("EntityFireElemental")
                || (name.equals("EntityEarthElemental") || name.equals("EntityClayGolem")) && level.dimension().equals(Level.NETHER);
        if (lightCheck && !freeLight && !mobLight(mob, access, spawner)) return false;
        if ((name.equals("tq") || name.equals("EntityNetherspawn") || name.equals("EntityCopperspine") || name.equals("EntityHoarySilverfish"))
                && level.players().stream().anyMatch(player -> !player.isSpectator() && player.isAlive() && player.distanceToSqr(mob) < 25)) return false;
        // on#getCanSpawnHere still checks each subclass's path weight. Mob's spawner mode uses 0.7, ordinary 0.5.
        boolean defaultMobPath = monster && !cube && !name.equals("EntityEarthElemental") && !name.equals("EntityClayGolem") && !name.equals("EntityHellhound")
                && !name.equals("tq") && !name.equals("EntityNetherspawn") && !name.equals("EntityCopperspine") && !name.equals("EntityHoarySilverfish");
        return !defaultMobPath || R196SpawnRules.mobPathAllows(access.getMaxLocalRawBrightness(pos), spawner, level.dimension().equals(Level.NETHER));
    }
    public static boolean mobLight(Mob mob, ServerLevelAccessor access, boolean spawner) {
        if (spawner) return !sunlight(mob, access);
        var level = access.getLevel(); BlockPos position = mob.blockPosition(); RandomSource random = mob.getRandom();
        if (access.getBlockState(position).blocksMotion()) position = position.above();
        int sky = access.getBrightness(LightLayer.SKY, position);
        if (sky > random.nextInt(32)) return false;
        boolean blood = R196SpawnRules.moon(level.getDayTime(), level.dimension().equals(Level.OVERWORLD)).blood();
        boolean wetBiome = access.getBiome(position).value().hasPrecipitation();
        int brightness = access.getMaxLocalRawBrightness(position);
        if (level.isThundering() && (wetBiome || blood)) brightness = Math.max(access.getBrightness(LightLayer.BLOCK, position), sky - 10);
        else if (!wetBiome && !blood) {
            float noWeatherDarkening = Mth.clamp(1F - (Mth.cos(level.getSunAngle(1F)) * 2F + .5F), 0F, 1F);
            brightness = Math.max(access.getBrightness(LightLayer.BLOCK, position), sky - (int)(noWeatherDarkening * 11F));
        }
        return brightness <= random.nextInt(R196OutdoorChecks.openSky(level, position) ? 8 : 5);
    }
    private static boolean sunlight(Mob mob, ServerLevelAccessor access) {
        String name = R196MobSpawning.sourceClass(mob);
        if (!R196MobSpawning.isUndeadSource(name) || name.equals("tn") || mob.isBaby() || !mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) return false;
        var level = access.getLevel(); BlockPos eye = BlockPos.containing(mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ());
        BlockPos brightnessPos = BlockPos.containing(mob.getX(), mob.getY() + mob.getBbHeight() * .66D, mob.getZ());
        return R196SpawnRules.daytime((int)Math.floorMod(level.getDayTime(), 24000)) && R196OutdoorChecks.openSky(level, eye) && !level.isRainingAt(eye)
                && mob.isAlive() && R196SpawnRules.brightness(access.getMaxLocalRawBrightness(brightnessPos), level.dimension().equals(Level.NETHER)) > .5F;
    }
    private static boolean anyFluid(ServerLevelAccessor access, AABB box, boolean lavaOnly) {
        for (BlockPos point : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(Math.nextDown(box.maxX)), Mth.floor(Math.nextDown(box.maxY)), Mth.floor(Math.nextDown(box.maxZ)))) {
            var fluid = access.getFluidState(point); if (lavaOnly ? fluid.is(FluidTags.LAVA) : !fluid.isEmpty()) return true;
        }
        return false;
    }
    private static boolean onlyWater(ServerLevelAccessor access, AABB box) {
        for (BlockPos point : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(Math.nextDown(box.maxX)), Mth.floor(Math.nextDown(box.maxY)), Mth.floor(Math.nextDown(box.maxZ))))
            if (!access.getFluidState(point).is(FluidTags.WATER)) return false;
        return true;
    }
}

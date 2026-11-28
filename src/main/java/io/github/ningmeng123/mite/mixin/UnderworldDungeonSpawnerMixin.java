package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.worldgen.MossyDungeonLocator;
import io.github.ningmeng123.mite.worldgen.R196SpawnerAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.MonsterRoomFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Marks ordinary monster rooms and assigns the underworld dungeon roster. @author NingMeng123 */
@Mixin(MonsterRoomFeature.class)
public abstract class UnderworldDungeonSpawnerMixin {
    @WrapOperation(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/SpawnerBlockEntity;setEntityId(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/util/RandomSource;)V"))
    private void mite$initializeDungeonSpawner(SpawnerBlockEntity spawner, EntityType<?> type, RandomSource random,
            Operation<Void> original, @Local(argsOnly = true) FeaturePlaceContext<NoneFeatureConfiguration> context) {
        original.call(spawner, type, random);
        MossyDungeonLocator.markGenerated(spawner);
        context.level().getChunk(spawner.getBlockPos()).setUnsaved(true);
        ((R196SpawnerAccess)spawner.getSpawner()).mite$configureUnderworldDungeon(context.level().getLevel(), spawner.getBlockPos());
    }
}

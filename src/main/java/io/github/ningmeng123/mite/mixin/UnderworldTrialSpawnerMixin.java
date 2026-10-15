package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.UnderworldSpawnerMobs;
import io.github.ningmeng123.mite.worldgen.UnderworldTrialSpawnerAccess;
import io.github.ningmeng123.mite.worldgen.UnderworldTrialSpawnerDataAccess;
import java.util.UUID;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rewrite both trial modes before selection, serialization and rendering, preserving ongoing rounds. @author NingMeng123 */
@Mixin(TrialSpawner.class)
public abstract class UnderworldTrialSpawnerMixin implements UnderworldTrialSpawnerAccess {
    @Shadow @Final @Mutable private TrialSpawnerConfig normalConfig;
    @Shadow @Final @Mutable private TrialSpawnerConfig ominousConfig;
    @Shadow @Final private TrialSpawnerData data;
    @Shadow @Final private TrialSpawner.StateAccessor stateAccessor;

    @Override public void mite$normalizeUnderworld(Level level) {
        if (!UnderworldSpawnerMobs.applies(level)) return;
        TrialSpawnerConfig normal = UnderworldSpawnerMobs.remap(normalConfig);
        TrialSpawnerConfig ominous = UnderworldSpawnerMobs.remap(ominousConfig);
        boolean changed = normal != normalConfig || ominous != ominousConfig;
        normalConfig = normal;
        ominousConfig = ominous;
        var access = (UnderworldTrialSpawnerDataAccess)data;
        changed |= access.mite$normalizeSpawnData();
        if (changed) {
            access.mite$clearDisplay();
            // No network callback here: serialization itself can request the getters below.
            if (stateAccessor instanceof BlockEntity blockEntity && !level.isClientSide) blockEntity.setChanged();
        }
    }

    @Unique private void mite$normalizeFromOwner() {
        if (stateAccessor instanceof BlockEntity blockEntity) mite$normalizeUnderworld(blockEntity.getLevel());
    }

    @Inject(method = {"getConfig", "getNormalConfig", "getOminousConfig", "getOminousConfigForSerialization"}, at = @At("HEAD"))
    private void mite$normalizeConfiguration(CallbackInfoReturnable<TrialSpawnerConfig> cir) { mite$normalizeFromOwner(); }

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void mite$normalizeTrialTick(ServerLevel level, BlockPos pos, boolean ominous, CallbackInfo ci) {
        mite$normalizeUnderworld(level);
    }

    @Inject(method = "spawnMob", at = @At("HEAD"))
    private void mite$normalizeTrialSpawn(ServerLevel level, BlockPos pos, CallbackInfoReturnable<Optional<UUID>> cir) {
        mite$normalizeUnderworld(level);
    }
}

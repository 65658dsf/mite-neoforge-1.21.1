package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.UnderworldSpawnerMobs;
import io.github.ningmeng123.mite.worldgen.UnderworldTrialSpawnerAccess;
import io.github.ningmeng123.mite.worldgen.UnderworldTrialSpawnerDataAccess;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The client cache is invalidated alongside the saved identity, including command-written NBT. @author NingMeng123 */
@Mixin(TrialSpawnerData.class)
public abstract class UnderworldTrialSpawnerDataMixin implements UnderworldTrialSpawnerDataAccess {
    @Shadow protected Optional<SpawnData> nextSpawnData;
    @Shadow protected Entity displayEntity;

    @Override public boolean mite$normalizeSpawnData() {
        if (nextSpawnData.isEmpty()) return false;
        SpawnData original = nextSpawnData.get();
        SpawnData replacement = UnderworldSpawnerMobs.remap(original);
        if (original == replacement) return false;
        nextSpawnData = Optional.of(replacement);
        displayEntity = null;
        return true;
    }

    @Override public void mite$clearDisplay() { displayEntity = null; }

    @Inject(method = "getOrCreateDisplayEntity", at = @At("HEAD"))
    private void mite$normalizeSpinningEntity(TrialSpawner spawner, Level level, TrialSpawnerState state,
            CallbackInfoReturnable<Entity> cir) {
        ((UnderworldTrialSpawnerAccess)(Object)spawner).mite$normalizeUnderworld(level);
    }
}

package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.worldgen.VillageBlacksmithLoot;
import io.github.ningmeng123.mite.worldgen.VillageTemplateAdaptation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Final processed templates retain terrain processors, transforms and normal container persistence. @author NingMeng123 */
@Mixin(StructureTemplate.class)
public abstract class VillageTemplateMixin {
    @Inject(method = "processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Ljava/util/List;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private static void mite$villageContents(ServerLevelAccessor level, BlockPos origin, BlockPos pivot,
            StructurePlaceSettings settings, List<StructureTemplate.StructureBlockInfo> source, StructureTemplate template,
            CallbackInfoReturnable<List<StructureTemplate.StructureBlockInfo>> cir) {
        cir.setReturnValue(VillageTemplateAdaptation.blocks(level, origin, settings, source, cir.getReturnValue()));
    }

    @Inject(method = "processEntityInfos", at = @At("RETURN"), cancellable = true)
    private static void mite$uninhabitedVillage(StructureTemplate template, LevelAccessor level, BlockPos origin,
            StructurePlaceSettings settings, List<StructureTemplate.StructureEntityInfo> source,
            CallbackInfoReturnable<List<StructureTemplate.StructureEntityInfo>> cir) {
        cir.setReturnValue(VillageTemplateAdaptation.entities(level, cir.getReturnValue()));
    }

    @WrapOperation(method = "placeInWorld", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V"))
    private void mite$sourceSmithContents(BlockEntity entity, CompoundTag tag, HolderLookup.Provider registries,
            Operation<Void> original) {
        var generation = VillageTemplateAdaptation.currentLevel();
        boolean replace = generation != null && entity instanceof Container
                && VillageTemplateAdaptation.smithLoot(tag.getString("LootTable"));
        if (!replace) { original.call(entity, tag, registries); return; }
        CompoundTag contents = tag.copy();
        contents.remove("LootTable");
        contents.remove("LootTableSeed");
        contents.remove("Items");
        original.call(entity, contents, registries);
        VillageBlacksmithLoot.fill((Container) entity, entity.getBlockPos().getY(),
                generation.getLevel().dimension(), generation.getLevel().getDayTime(),
                RandomSource.create(tag.getLong("LootTableSeed")));
        entity.setChanged();
    }
}

package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.survival.CropHarvest;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Select explosion seed loot before native global loot modifiers, including no-decay TNT. @author NingMeng123 */
@Mixin(BlockBehaviour.class)
public abstract class CropFruitExplosionMixin {
    @WrapMethod(method="onExplosionHit")
    private void mite$fruitExplosionReason(BlockState state,Level level,BlockPos pos,Explosion explosion,
            BiConsumer<ItemStack,BlockPos> drops,Operation<Void> original) {
        if(CropHarvest.fruit(state)&&level instanceof ServerLevel server)
            CropHarvest.withReason(CropHarvest.Reason.EXPLOSION,server,pos,()->original.call(state,level,pos,explosion,drops));
        else original.call(state,level,pos,explosion,drops);
    }
    @WrapOperation(method="getDrops",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockBehaviour;getLootTable()Lnet/minecraft/resources/ResourceKey;"))
    private ResourceKey<LootTable> mite$fruitSeedLoot(BlockBehaviour block,Operation<ResourceKey<LootTable>> original,
            BlockState state,LootParams.Builder params) {
        if(CropHarvest.fruit(state)&&CropHarvest.reason(params)==CropHarvest.Reason.EXPLOSION)
            return ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID,
                    "crop_explosion/"+(state.is(Blocks.MELON)?"melon":"pumpkin")));
        return original.call(block);
    }
}

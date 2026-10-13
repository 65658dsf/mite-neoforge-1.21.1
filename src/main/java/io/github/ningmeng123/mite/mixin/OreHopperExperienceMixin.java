package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.rules.OreCookingExperienceRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;

/** asi: successful one-item mineral withdrawal by the hopper below pays at the furnace's front. @author NingMeng123 */
@Mixin(HopperBlockEntity.class)
public abstract class OreHopperExperienceMixin {
    @WrapMethod(method = "suckInItems")
    private static boolean mite$mineralHopperExperience(Level world, Hopper hopper,
            Operation<Boolean> original) {
        BlockPos hopperPos = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY(), hopper.getLevelZ());
        if (!(world instanceof ServerLevel level)
                || !(level.getBlockEntity(hopperPos.above()) instanceof AbstractFurnaceBlockEntity furnace)) return original.call(world, hopper);
        ItemStack before = furnace.getItem(2);
        String id = before.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(before.getItem()).toString();
        if (OreCookingExperienceRules.rewardPerItem(id) <= 0) return original.call(world, hopper);
        var item = before.getItem();
        int count = before.getCount();
        // The outer transfer method covers NeoForge's item-handler capability path as well as vanilla inventories.
        boolean transferred = original.call(world, hopper);
        ItemStack after = furnace.getItem(2);
        int removed = count - (after.is(item) ? after.getCount() : 0);
        int reward = OreCookingExperienceRules.rewardForTaken(id, removed);
        if (!transferred || reward <= 0) return transferred;
        var state = level.getBlockState(furnace.getBlockPos());
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        BlockPos front = furnace.getBlockPos().relative(facing);
        ExperienceOrb orb = new ExperienceOrb(level, front.getX() + .5D, front.getY() + .5D, front.getZ() + .5D, reward);
        orb.setDeltaMovement(orb.getDeltaMovement().scale((double)0.2F));
        level.addFreshEntity(orb);
        return transferred;
    }
}

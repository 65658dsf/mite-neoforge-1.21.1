package io.github.ningmeng123.mite.combat;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.mining.SourceMiningRequirements;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.SourcePlayerCombatRules;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Target-dependent R196 reach, shared by selection, real actions and packet validation. @author NingMeng123 */
public final class SourcePlayerReach {
    private static final ResourceLocation LEGACY_TOOL_REACH = ResourceLocation.fromNamespaceAndPath("mite", "tool_reach");
    private SourcePlayerReach() {}

    public static double toolBonus(ItemStack stack) {
        var definition = MiteTools.definition(stack);
        if (definition != null) return definition.reach();
        if (stack.is(Items.STICK) || stack.is(Items.BONE)) return .5D;
        if (stack.is(Items.TRIDENT) || stack.is(Items.MACE)) return .75D;
        var profile = EquipmentDurability.toolProfile(stack);
        if (profile == null) return 0D;
        // Explicit modern tiered-tool mapping to its source family, without inventing new source IDs.
        return switch (profile.family()) {
            case CUDGEL, KNIFE -> .25D;
            case CLUB, DAGGER, HATCHET, SHEARS -> .5D;
            case SCYTHE -> 1D;
            default -> .75D;
        };
    }
    public static boolean effective(ItemStack stack, BlockState state) {
        var definition = MiteTools.definition(stack);
        if (definition != null && definition.harvestLevel() < SourceMiningRequirements.minimumHarvestLevel(state)) return false;
        return !stack.isEmpty() && !stack.is(Items.STICK) && !stack.is(Items.BONE)
                && stack.getDestroySpeed(state) > 1F
                && (!state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state));
    }
    private static double externalRange(Player player, boolean block) {
        var attribute = player.getAttribute(block ? Attributes.BLOCK_INTERACTION_RANGE : Attributes.ENTITY_INTERACTION_RANGE);
        if (attribute == null) return 0D;
        var legacy = attribute.getModifier(LEGACY_TOOL_REACH);
        // Preserve deliberate modern range attributes; obsolete saved mite:tool_reach components cannot double the bonus.
        return attribute.getValue() - (block ? 4.5D : 3D) - (legacy == null ? 0D : legacy.amount());
    }
    public static double blockReach(Player player, BlockState state, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        return Math.max(0D, SourcePlayerCombatRules.blockReach(player.getAbilities().instabuild,
                effective(held, state) ? toolBonus(held) : 0D)
                + (player.getAbilities().instabuild ? 0D : externalRange(player, true)));
    }
    public static double meleeReach(Player player, Entity target) {
        return Math.max(0D, SourcePlayerCombatRules.meleeReach(player.getAbilities().instabuild,
                player.getY() - target.getY(), toolBonus(player.getMainHandItem()), target instanceof AbstractHurtingProjectile)
                + (player.getAbilities().instabuild ? 0D : externalRange(player, false)));
    }
    public static double interactionReach(Player player, Entity target) {
        // Source yc.getReachBonus(entity) is zero for every active item. Modern offhand follows the same rule.
        return Math.max(0D, SourcePlayerCombatRules.interactionReach(player.getAbilities().instabuild,
                player.getY() - target.getY(), target instanceof Boat)
                + (player.getAbilities().instabuild ? 0D : externalRange(player, false)));
    }
    public static boolean canReachBlock(Player player, BlockPos pos, InteractionHand hand) {
        return canReachBlock(player, pos, hand, 1F);
    }
    public static boolean canReachBlock(Player player, BlockPos pos, InteractionHand hand, float partialTick) {
        Vec3 eye = player.getEyePosition(partialTick), center = Vec3.atCenterOf(pos);
        double maximum = Math.max(0D, SourcePlayerCombatRules.blockReach(player.getAbilities().instabuild,
                toolBonus(player.getItemInHand(hand))) + (player.getAbilities().instabuild ? 0D : externalRange(player, true)));
        double distanceSquared = Math.min(center.distanceToSqr(eye.add(0D, -.625D, 0D)),
                center.distanceToSqr(eye.add(0D, -.25D, 0D)));
        // Reject a forged remote coordinate before asking Level for a potentially unloaded chunk.
        if (distanceSquared > maximum * maximum) return false;
        double reach = blockReach(player, player.level().getBlockState(pos), hand);
        return distanceSquared <= reach * reach;
    }
    public static boolean canMelee(Player player, Entity target) {
        return canReachEntityAt(player, target, closestPoint(target.getBoundingBox().inflate(target.getPickRadius()), player.getEyePosition()), true, 1F);
    }
    public static boolean canInteract(Player player, Entity target) {
        return canReachEntityAt(player, target, closestPoint(target.getBoundingBox().inflate(target.getPickRadius()), player.getEyePosition()), false, 1F);
    }
    public static boolean canReachEntityAt(Player player, Entity target, Vec3 point, boolean melee, float partialTick) {
        if (target.level() != player.level() || !finite(point)) return false;
        double reach = melee ? meleeReach(player, target) : interactionReach(player, target);
        return player.getEyePosition(partialTick).distanceToSqr(point) <= reach * reach;
    }
    public static boolean canInteractAt(Player player, Entity target, Vec3 relative) {
        if (!finite(relative)) return false;
        Vec3 point = target.position().add(relative);
        return target.getBoundingBox().inflate(target.getPickRadius() + .00001D).contains(point)
                && canReachEntityAt(player, target, point, false, 1F);
    }
    public static Vec3 closestPoint(AABB box, Vec3 point) {
        return new Vec3(Math.clamp(point.x, box.minX, box.maxX), Math.clamp(point.y, box.minY, box.maxY),
                Math.clamp(point.z, box.minZ, box.maxZ));
    }
    private static boolean finite(Vec3 point) { return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z); }
}

package io.github.ningmeng123.mite.combat;

import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatPlayerReach;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsReach;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.mining.SourceMiningRequirements;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.SourcePlayerCombatRules;
import io.github.ningmeng123.mite.rules.ProgressionRules;
import io.github.ningmeng123.mite.rules.NativeWeaponBalanceRules;
import io.github.ningmeng123.mite.rules.SimplySwordsBalanceRules;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
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
        Double weaponReach = BetterCombatPlayerReach.reach(player);
        double sourceReach = sourceMeleeReach(player, target);
        return weaponReach == null ? sourceReach : Math.min(weaponReach, sourceReach);
    }
    /** Weapon length is independent of its durability family; external range is included exactly once. */
    public static double baseMeleeReach(ItemStack weapon, double itemRange) {
        boolean betterCombat = BetterCombatBridge.enabled();
        var simplySwords = SimplySwordsCompat.definition(weapon);
        if (simplySwords != null) {
            Double metadata = BetterCombatBridge.weaponBaseReach(weapon, itemRange);
            if (metadata == null && !betterCombat) metadata = SimplySwordsReach.baseReach(weapon, itemRange);
            if (hasExplicitRange(weapon)) return metadata == null ? itemRange : metadata;
            double balanced = SimplySwordsBalanceRules.meleeReach(simplySwords.family(), betterCombat) + itemRange - 3D;
            return metadata == null ? balanced : Math.min(metadata, balanced);
        }
        if (weapon.is(Items.TRIDENT)) return (betterCombat ? 3.25D : 2.75D) + itemRange - 3D;
        if (weapon.is(Items.MACE)) return (betterCombat ? 2D : 1.75D) + itemRange - 3D;
        var profile = EquipmentDurability.toolProfile(weapon);
        if (profile != null) return NativeWeaponBalanceRules.meleeReach(profile.family(), betterCombat) + itemRange - 3D;
        if (weapon.getItem() instanceof SwordItem) return 2.25D + itemRange - 3D;
        return 1.5D + toolBonus(weapon) + itemRange - 3D;
    }
    private static boolean hasExplicitRange(ItemStack weapon) {
        return weapon.getAttributeModifiers().modifiers().stream().anyMatch(entry ->
                entry.attribute().is(Attributes.ENTITY_INTERACTION_RANGE) && entry.slot().test(EquipmentSlot.MAINHAND)
                        && !entry.modifier().id().equals(LEGACY_TOOL_REACH));
    }
    public static boolean ordinarySwordOrClub(ItemStack weapon) {
        if (SimplySwordsCompat.definition(weapon) != null) return false;
        var profile = EquipmentDurability.toolProfile(weapon);
        return profile != null ? switch (profile.family()) {
            case SWORD, CLUB, CUDGEL -> true;
            default -> false;
        } : weapon.getItem() instanceof SwordItem;
    }
    /** Neutral-height survival preview for the hovered stack, independent of the currently held item. */
    public static double tooltipMeleeReach(ItemStack weapon) {
        return tooltipMeleeReach(weapon, null);
    }
    /** Include the viewing player's current level while retaining a level-zero preview without a player. */
    public static double tooltipMeleeReach(ItemStack weapon, Player player) {
        var modifiers = ItemAttributeModifiers.builder();
        for (var entry : weapon.getAttributeModifiers().modifiers()) {
            if (entry.attribute().is(Attributes.ENTITY_INTERACTION_RANGE) && !entry.modifier().id().equals(LEGACY_TOOL_REACH))
                modifiers.add(entry.attribute(), entry.modifier(), entry.slot());
        }
        double base = baseMeleeReach(weapon, modifiers.build().compute(3D, EquipmentSlot.MAINHAND));
        return base <= 0D || !Double.isFinite(base) ? 0D : SourcePlayerCombatRules.configuredWeaponReach(base,
                MiteConfig.weaponReachMultiplier(), MiteConfig.weaponReachReduction())
                + ProgressionRules.meleeReachBonusForLevel(player == null ? 0 : player.experienceLevel);
    }
    /** Preserve MITE's weapon, target-height and attribute limits for either attack system. */
    public static double sourceMeleeReach(Player player, Entity target) {
        ItemStack weapon = BetterCombatPlayerReach.attackingWeapon(player);
        double base = baseMeleeReach(weapon, BetterCombatPlayerReach.rangeAttribute(player, LEGACY_TOOL_REACH));
        if (!player.getAbilities().instabuild && (!Double.isFinite(base)
                || base <= 0D && SimplySwordsCompat.definition(weapon) != null)) return 0D;
        return SourcePlayerCombatRules.meleeReach(player.getAbilities().instabuild,
                player.getY() - target.getY(), base - 1.5D, target instanceof AbstractHurtingProjectile, 0D,
                MiteConfig.weaponReachMultiplier(), MiteConfig.weaponReachReduction(),
                MiteConfig.heightReachPenaltyEnabled()) + ProgressionRules.meleeReachBonusForLevel(player.experienceLevel);
    }
    /** Keep the historical ray while allowing configured long weapons to be selected on the client. */
    public static double selectionRange(Player player) {
        Double weaponReach = BetterCombatPlayerReach.reach(player);
        if (weaponReach != null) return Math.max(16D, weaponReach);
        if (player.getAbilities().instabuild) return 16D;
        double base = baseMeleeReach(player.getMainHandItem(), BetterCombatPlayerReach.rangeAttribute(player, LEGACY_TOOL_REACH));
        return Math.max(16D, SourcePlayerCombatRules.meleeReach(player.getAbilities().instabuild,
                2.5D, base - 1.5D, true, 0D,
                MiteConfig.weaponReachMultiplier(), MiteConfig.weaponReachReduction(), MiteConfig.heightReachPenaltyEnabled())
                + ProgressionRules.meleeReachBonusForLevel(player.experienceLevel));
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
    /** A melee target indicator uses the same reach and aimed collision point as an actual client attack. */
    public static boolean canTargetMelee(Player player, EntityHitResult hit, float partialTick) {
        Entity target = hit.getEntity();
        if (player.isSpectator() || !target.isAlive() || !target.isPickable() || !target.isAttackable()
                || !canMelee(player, target)
                || !canReachEntityAt(player, target, hit.getLocation(), true, partialTick)) return false;
        return player.level().clip(new ClipContext(player.getEyePosition(partialTick), hit.getLocation(),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }
    public static boolean canInteract(Player player, Entity target) {
        return canReachEntityAt(player, target, closestPoint(target.getBoundingBox().inflate(target.getPickRadius()), player.getEyePosition()), false, 1F);
    }
    public static boolean canReachEntityAt(Player player, Entity target, Vec3 point, boolean melee, float partialTick) {
        if (target.level() != player.level() || !finite(point)) return false;
        if (melee) {
            Boolean weaponHit = BetterCombatPlayerReach.canMelee(player, target);
            if (weaponHit != null) return weaponHit;
        }
        double reach = melee ? meleeReach(player, target) : interactionReach(player, target);
        return (!melee || reach > 0D) && player.getEyePosition(partialTick).distanceToSqr(point) <= reach * reach;
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

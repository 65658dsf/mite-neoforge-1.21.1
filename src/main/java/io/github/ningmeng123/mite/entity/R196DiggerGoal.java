package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.mining.MiningProgress;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/** Source AnimalWatcher stages and tool gates, using modern navigation and grief hooks. @author NingMeng123 */
public final class R196DiggerGoal extends Goal {
    private static final String SAVE = "mite_digging_031";
    private final PathfinderMob mob;
    private BlockPos target;
    private int progress = -1, cooldown = 40, pause;
    public R196DiggerGoal(PathfinderMob mob) {
        this.mob = mob; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        CompoundTag saved = mob.getPersistentData().getCompound(SAVE);
        if (saved.contains("position")) { target = BlockPos.of(saved.getLong("position")); progress = saved.getInt("progress"); cooldown = saved.getInt("cooldown"); }
    }
    public static boolean watcher(PathfinderMob mob) {
        String source = io.github.ningmeng123.mite.worldgen.R196MobSpawning.sourceClass(mob);
        if (source == null) return false;
        for (var value : io.github.ningmeng123.mite.rules.R196MobRules.sourceData().getAsJsonArray("entities")) {
            var row = value.getAsJsonObject();
            if (source.equals(row.get("source_class").getAsString()))
                return row.getAsJsonArray("hierarchy").asList().stream().anyMatch(v -> "EntityAnimalWatcher".equals(v.getAsString()));
        }
        return false;
    }
    private boolean hasBlockingWeapon() {
        var tool = EquipmentDurability.toolProfile(mob.getMainHandItem());
        return tool != null && (tool.family() == ToolFamily.SWORD || tool.family() == ToolFamily.CLUB || tool.family() == ToolFamily.CUDGEL || tool.family() == ToolFamily.SCYTHE);
    }
    public boolean canDestroy(BlockPos pos, boolean clipping) {
        if (hasBlockingWeapon() || !mob.level().hasChunkAt(pos) || !net.neoforged.neoforge.event.EventHooks.canEntityGrief(mob.level(), mob)) return false;
        var tool = EquipmentDurability.toolProfile(mob.getMainHandItem());
        int foot = mob.blockPosition().getY();
        if (pos.getY() > foot + 1 || pos.getY() < foot && (tool == null || tool.family() != ToolFamily.SHOVEL)) return false;
        if (mob.position().add(0, mob.getBbHeight() * .5, 0).distanceToSqr(pos.getCenter()) > 3.25) return false;
        BlockState state = mob.level().getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(mob.level(), pos) < 0) return false;
        if (clipping) {
            var eye = mob.level().clip(new ClipContext(mob.getEyePosition(), pos.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
            var leg = mob.level().clip(new ClipContext(mob.position().add(0, mob.getBbHeight() * .25, 0), pos.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
            if (eye.getType() != HitResult.Type.MISS && !eye.getBlockPos().equals(pos)
                    && leg.getType() != HitResult.Type.MISS && !leg.getBlockPos().equals(pos)) return false;
        }
        boolean elemental = mob instanceof R196Elemental earth && harvestLevel(state) <= earth.getBlockHarvestLevel();
        boolean effective = !mob.getMainHandItem().isEmpty() && mob.getMainHandItem().getDestroySpeed(state) > 1F;
        boolean basic = state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW) || state.is(Blocks.CLAY) || state.is(Blocks.GLASS) || state.is(Blocks.SPONGE)
                || state.is(Blocks.MELON) || state.is(Blocks.HAY_BLOCK) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.ICE)
                || !state.requiresCorrectToolForDrops() && !state.is(BlockTags.LOGS);
        if (!elemental && !effective && !basic) return false;
        for (var peer : mob.level().getEntitiesOfClass(PathfinderMob.class, mob.getBoundingBox().inflate(4)))
            if (peer != mob && peer.getPersistentData().getCompound(SAVE).getLong("position") == pos.asLong()
                    && peer.getPersistentData().contains(SAVE)) return false;
        return true;
    }
    private int harvestLevel(BlockState state) {
        if (state.is(Blocks.OBSIDIAN)) return 3;
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return 3;
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) return 2;
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) return 1;
        return 0;
    }
    @Override public boolean canUse() {
        // Entity goals are constructed before Forge persistent data is read. Resume saved work lazily.
        if (target == null && mob.getPersistentData().contains(SAVE)) {
            CompoundTag saved = mob.getPersistentData().getCompound(SAVE);
            if (saved.contains("position")) {
                target = BlockPos.of(saved.getLong("position")); progress = Math.clamp(saved.getInt("progress"), -1, 9);
                cooldown = Math.max(1, saved.getInt("cooldown"));
            }
        }
        var prey = mob.getTarget();
        if (prey == null || !prey.isAlive() || hasBlockingWeapon()) return false;
        if (target != null && canDestroy(target, true)) return true;
        if (mob.getRandom().nextInt(20) != 0 || mob.distanceTo(prey) > 16 || !mob.getNavigation().isDone()) return false;
        if (mob.distanceToSqr(prey) > 2) {
            BlockPos base = prey.blockPosition();
            for (int y = base.getY() - 1; y >= mob.blockPosition().getY(); y--) if (choose(new BlockPos(base.getX(), y, base.getZ()))) return true;
        }
        if (mob.distanceTo(prey) > 8) return false;
        var collision = mob.level().clip(new ClipContext(mob.getEyePosition(), prey.position().add(0, prey.getBbHeight() * .5, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        if (collision.getType() == HitResult.Type.BLOCK) {
            if (choose(collision.getBlockPos().above())) return true;
            return choose(collision.getBlockPos());
        }
        return false;
    }
    private boolean choose(BlockPos pos) { if (!canDestroy(pos, true)) return false; target = pos.immutable(); progress = -1; cooldown = 40; return true; }
    @Override public boolean canContinueToUse() { return target != null && mob.getTarget() != null && canDestroy(target, true); }
    public int stageDelay(BlockPos pos) {
        float hardness = MiningProgress.effectiveHardness(mob.level().getBlockState(pos), mob.level().getBlockState(pos).getDestroySpeed(mob.level(), pos));
        int delay = (int)(300F * hardness);
        if (mob instanceof BoneLordFrenzy frenzy && frenzy.mite$boneLordFrenzyTicks() > 0) delay /= 2;
        if (mob instanceof R196Elemental earth) delay /= earth.isNormalClay() ? 4 : earth.isHardenedClay() ? 6 : 8;
        var tool = EquipmentDurability.toolProfile(mob.getMainHandItem());
        if (tool != null) delay = (int)(delay / (1F + mob.getMainHandItem().getDestroySpeed(mob.level().getBlockState(pos)) * .5F));
        if (mob instanceof R196Undead undead && undead.miteKind() == MobKind.GHOUL) delay /= 2;
        return delay;
    }
    @Override public void tick() {
        if (target == null || !(mob.level() instanceof ServerLevel level)) return;
        if (pause > 0) { pause--; save(); return; }
        mob.getLookControl().setLookAt(target.getX() + .5, target.getY() + .5, target.getZ() + .5);
        if (--cooldown > 0) { save(); return; }
        cooldown = stageDelay(target);
        if (!canDestroy(target, true)) { stop(); return; }
        if (++progress >= 10) {
            var state = level.getBlockState(target);
            if (level.destroyBlock(target, true, mob)) {
                if (!mob.getMainHandItem().isEmpty()) io.github.ningmeng123.mite.item.EquipmentDurability.mineBlock(mob.getMainHandItem(), level, state, target, mob);
                level.destroyBlockProgress(mob.getId(), target, -1); progress = -1;
                if (canDestroy(target.above(), true)) { target = target.above(); pause = 10; } else target = null;
            }
        } else level.destroyBlockProgress(mob.getId(), target, progress);
        save();
    }
    private void save() {
        if (target == null) { mob.getPersistentData().remove(SAVE); return; }
        var tag = new CompoundTag(); tag.putLong("position", target.asLong()); tag.putInt("progress", progress); tag.putInt("cooldown", cooldown); mob.getPersistentData().put(SAVE, tag);
    }
    @Override public void stop() {
        if (target != null && mob.level() instanceof ServerLevel level) level.destroyBlockProgress(mob.getId(), target, -1);
        target = null; progress = -1; cooldown = 40; pause = 0; save();
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
}

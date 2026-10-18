package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.curse.WitchCursing;
import io.github.ningmeng123.mite.mixin.SourceMonsterLivingAccess;
import io.github.ningmeng123.mite.rules.R196MobRules;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.worldgen.R196OutdoorChecks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** qy/re/qx source target selection, source-class cooperation and og UUID restoration. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class SourceMonsterTargeting {
    public static final String SAVE = "mite_monster_target_035";
    private static final ThreadLocal<Boolean> GOAL_CHANGE = ThreadLocal.withInitial(() -> false);
    private static final Map<LivingEntity, Long> SEEN_BY_BAT = new WeakHashMap<>();
    private SourceMonsterTargeting() {}

    private static final class Hierarchies {
        static final Map<String, List<String>> VALUES = load();
        private static Map<String, List<String>> load() {
            var result = new HashMap<String, List<String>>();
            for (var value : R196MobRules.sourceData().getAsJsonArray("entities")) {
                var row = value.getAsJsonObject();
                result.put(row.get("source_class").getAsString(), row.getAsJsonArray("hierarchy").asList().stream().map(e -> e.getAsString()).toList());
            }
            return Map.copyOf(result);
        }
    }

    public static boolean sourceIs(Mob mob, String superclass) {
        String name = R196MobSpawning.sourceClass(mob);
        return name != null && Hierarchies.VALUES.getOrDefault(name, List.of(name)).contains(superclass);
    }

    public static double effectiveRange(Mob mob) {
        double range = mob.getAttribute(Attributes.FOLLOW_RANGE) == null ? 16D : mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        return ((SourceMonsterLivingAccess)mob).mite$recentlyHit() > 0 ? range * 2D : range;
    }

    /** Only the confirmed new-AI source families are installed; old on AI remains species-owned. */
    public static void install(PathfinderMob mob) {
        if (!R196MonsterAi.managed(mob)) return;
        boolean creeper = sourceIs(mob, "tf"), zombie = sourceIs(mob, "tw");
        boolean ghoul = sourceIs(mob, "EntityGhoul"), elemental = sourceIs(mob, "EntityEarthElemental");
        boolean alerts = zombie || ghoul || elemental || sourceIs(mob, "EntityWight")
                || sourceIs(mob, "EntityInvisibleStalker") || sourceIs(mob, "EntityShadow") || sourceIs(mob, "EntityFireElemental");
        mob.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal<?>
                || goal instanceof HurtByTargetGoal || goal instanceof Nearest || goal instanceof Revenge);
        mob.targetSelector.addGoal(creeper ? 2 : 1, new Revenge(mob, alerts));
        mob.targetSelector.addGoal(creeper ? 1 : 2, new Nearest(mob, Player.class, 0, true));
        if (zombie || ghoul || elemental) mob.targetSelector.addGoal(2, new Nearest(mob, Villager.class, 0, false));
        if (zombie || ghoul) mob.targetSelector.addGoal(3, new Nearest(mob, Animal.class, 10, true));
    }

    /** The vision policy is separate from physical attack reach; original glass, portals and bars do not hide targets. */
    public static boolean canSee(Mob mob, LivingEntity target, boolean ignoreLeaves) {
        if (target == null) return false;
        Vec3 origin = mob.getEyePosition();
        for (double fraction : new double[]{.25D, .75D}) {
            Vec3 end = target.position().add(0, target.getBbHeight() * fraction, 0);
            boolean clear = true;
            // Raycast.performVsBlocks checks four origin offsets and chooses the nearest obstruction.
            for (Vec3 offset : new Vec3[]{new Vec3(-.0001, 0, 0), new Vec3(.0001, 0, 0), new Vec3(0, 0, -.0001), new Vec3(0, 0, .0001)}) {
                if (!clearVisionRay(mob, origin.add(offset), end, ignoreLeaves)) { clear = false; break; }
            }
            if (clear) return true;
        }
        return false;
    }

    private static boolean clearVisionRay(Mob mob, Vec3 origin, Vec3 end, boolean ignoreLeaves) {
        var foliage = new java.util.HashSet<BlockPos>();
        return mob.level().clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob) {
                @Override public VoxelShape getBlockShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
                    var block = state.getBlock();
                    if (state.is(Blocks.GLASS) || block instanceof StainedGlassBlock || block instanceof IceBlock || block instanceof IronBarsBlock
                            || block instanceof FenceBlock || block instanceof DoorBlock || block instanceof TrapDoorBlock
                            || block instanceof FenceGateBlock || block instanceof NetherPortalBlock
                            || ignoreLeaves && state.is(BlockTags.LEAVES)) return Shapes.empty();
                    if (state.is(BlockTags.LEAVES) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                            || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DANDELION)
                            || state.is(Blocks.POPPY) || block instanceof CropBlock || block instanceof StemBlock
                            || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.VINE)) {
                        // RaycastPolicies.IMPEDE_BY_50 accumulates, rather than drawing a random chance.
                        foliage.add(pos.immutable());
                        return foliage.size() < 2 ? Shapes.empty() : state.getShape(level, pos);
                    }
                    return super.getBlockShape(state, level, pos);
                }
        }).getType() == HitResult.Type.MISS;
    }

    private static boolean checksSight(PathfinderMob mob, LivingEntity target, boolean requested) {
        if (!requested || R196MonsterAi.diggingEnabled(mob)) return false;
        if (sourceIs(mob, "tv")) {
            long now = mob.level().getGameTime();
            if (SEEN_BY_BAT.getOrDefault(target, 0L) > now) return false;
            for (Mob bat : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(32),
                    e -> e.getType() == EntityType.BAT || e instanceof R196Bat)) {
                if (canSee(bat, target, false)) { SEEN_BY_BAT.put(target, now + 20); return false; }
            }
        }
        return true;
    }

    private static boolean bowArcher(Mob mob) {
        return mob instanceof AbstractSkeleton && mob.getMainHandItem().getItem() instanceof BowItem;
    }

    private static boolean targetVisible(Mob mob, LivingEntity target, boolean ignoreLeaves) {
        // Keep source glass/door vision, allow arrow-transparent leaves, and include exposed heads in the shooting ray.
        return bowArcher(mob) ? canSee(mob, target, true) || SourceRangedAttackGoal.clearShot(mob, target)
                : canSee(mob, target, ignoreLeaves);
    }

    private static boolean suitable(PathfinderMob owner, LivingEntity target) {
        if (target == owner || !target.isAlive() || !owner.canAttack(target) || owner.isAlliedTo(target)
                || !owner.isWithinRestriction(target.blockPosition())) return false;
        if (target instanceof Player player && (player.isCreative() || player.isSpectator() || player.getAbilities().invulnerable)) return false;
        if (target instanceof Mob mob && (sourceIs(mob, "tf") || mob.getType() == EntityType.GHAST)) return false;
        return true;
    }

    private static LivingEntity mountedTarget(LivingEntity entity) {
        return entity.getVehicle() instanceof LivingEntity mount && !mount.isInvulnerable() ? mount : entity;
    }

    private static void setGoalTarget(Mob mob, @Nullable LivingEntity target) {
        boolean previous = GOAL_CHANGE.get();
        GOAL_CHANGE.set(true);
        try { mob.setTarget(target); } finally { GOAL_CHANGE.set(previous); }
    }

    private abstract static class SourceTarget extends Goal {
        final PathfinderMob mob;
        final boolean sight;
        LivingEntity target;
        LivingEntity previous;
        int unseen;
        SourceTarget(PathfinderMob mob, boolean sight) { this.mob = mob; this.sight = sight; setFlags(EnumSet.of(Flag.TARGET)); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { unseen = 0; setGoalTarget(mob, target); mob.setNoActionTime(-1200); }
        @Override public boolean canContinueToUse() {
            target = mob.getTarget();
            if (target == null || !target.isAlive() || mob.distanceToSqr(target) > Math.pow(effectiveRange(mob), 2D)) return false;
            if (checksSight(mob, target, sight)) {
                if (targetVisible(mob, target, false)) unseen = 0;
                else if (++unseen > 60) {
                    // A verified route keeps an acquired target through a long detour, but never indefinitely.
                    var melee = SourceMeleeGoal.first(mob);
                    var digger = R196DiggerGoal.first(mob);
                    boolean overhead = digger != null && digger.isBreakingOverheadFor(target);
                    if (!overhead && (unseen > 600 || melee == null || !melee.followsReachableRoute(target))) return false;
                }
            }
            return true;
        }
        @Override public void stop() { previous = mob.getTarget(); setGoalTarget(mob, null); target = null; }
        boolean initiallySuitable(LivingEntity candidate) {
            if (!suitable(mob, candidate) || checksSight(mob, candidate, sight) && !targetVisible(mob, candidate, previous == candidate)) return false;
            if (mob instanceof Witch witch && candidate instanceof ServerPlayer player) WitchCursing.tryCurse(witch, player);
            return true;
        }
    }

    /** qy: six-layer scan, literal chance, mounted targets, high-player fallback and 1/40 nearest-target refresh. */
    public static final class Nearest extends SourceTarget {
        private final Class<? extends LivingEntity> type;
        private final int chance;
        public Nearest(PathfinderMob mob, Class<? extends LivingEntity> type, int chance, boolean sight) {
            super(mob, sight); this.type = type; this.chance = chance;
        }
        public Class<? extends LivingEntity> targetType() { return type; }
        private LivingEntity nearest() {
            double range = effectiveRange(mob), vertical = 6D;
            boolean archer = bowArcher(mob);
            boolean zombieAnimals = sourceIs(mob, "tw") && type == Animal.class;
            if (zombieAnimals) { range *= .5D; vertical *= .5D; }
            double rangeSquared = range * range;
            var candidates = new ArrayList<LivingEntity>();
            candidates.addAll(mob.level().getEntitiesOfClass(type, mob.getBoundingBox().inflate(range, vertical, range),
                    candidate -> (!archer || mob.distanceToSqr(candidate) <= rangeSquared) && initiallySuitable(candidate)));
            if ((candidates.isEmpty() || archer) && type == Player.class && mob.level() instanceof ServerLevel level) {
                boolean digger = R196MonsterAi.diggingEnabled(mob) && R196OutdoorChecks.outdoors(level, mob.blockPosition());
                if (digger || archer) for (Player player : level.players()) {
                    if (!suitable(mob, player)) continue;
                    double dx = player.getX() - mob.getX(), dy = player.getY() - mob.getY(), dz = player.getZ() - mob.getZ();
                    if (digger && dy >= vertical && dy <= 14D && dx * dx + dz * dz < 16D
                            || archer && Math.abs(dy) >= vertical && mob.distanceToSqr(player) <= rangeSquared
                            && targetVisible(mob, player, player == mob.getTarget())) candidates.add(player);
                }
            }
            candidates.sort(Comparator.comparingDouble(mob::distanceToSqr));
            for (LivingEntity candidate : candidates) {
                if (candidate instanceof R196Wolf wolf && wolf.miteKind() == MobKind.HELLHOUND && type == Animal.class) continue;
                if (zombieAnimals && candidate instanceof Wolf && mob.distanceTo(candidate) > 8F) continue;
                return mountedTarget(candidate);
            }
            return null;
        }
        @Override public boolean canUse() {
            var memory = (SourceMonsterTargetMemory)mob;
            LivingEntity queued = memory.mite$retarget();
            memory.mite$retarget(null);
            if (queued != null && queued.isAlive()) { target = queued; return true; }
            if (chance > 0 && mob.getRandom().nextInt(chance) != 0) return false;
            target = nearest();
            return target != null;
        }
        @Override public void start() { target = mountedTarget(target); super.start(); }
        @Override public void tick() {
            if (mob.getRandom().nextInt(40) == 0) {
                LivingEntity candidate = nearest();
                if (candidate != null && candidate != target && targetVisible(mob, candidate, false)) {
                    stop(); target = candidate; start(); return;
                }
            }
            if (target != null) {
                LivingEntity mount = mountedTarget(target);
                if (mount != target) { target = mount; setGoalTarget(mob, mount); }
            }
        }
    }

    /** qx does not require sight, and alerts only the same original runtime class or its source subclasses. */
    public static final class Revenge extends SourceTarget {
        private final boolean alerts;
        private int timestamp;
        public Revenge(PathfinderMob mob, boolean alerts) { super(mob, false); this.alerts = alerts; }
        @Override public boolean canUse() {
            if (mob.getLastHurtByMobTimestamp() == timestamp) return false;
            target = mob.getLastHurtByMob();
            return target != null && initiallySuitable(target);
        }
        @Override public void start() {
            target = mob.getLastHurtByMob();
            timestamp = mob.getLastHurtByMobTimestamp();
            super.start();
            if (!alerts || target == null) return;
            double range = effectiveRange(mob);
            String source = R196MobSpawning.sourceClass(mob);
            var bounds = new AABB(mob.getX(), mob.getY(), mob.getZ(), mob.getX() + 1, mob.getY() + 1, mob.getZ() + 1).inflate(range, 10, range);
            for (PathfinderMob peer : mob.level().getEntitiesOfClass(PathfinderMob.class, bounds)) {
                if (peer != mob && peer.getTarget() == null && sourceIs(peer, source) && !peer.isAlliedTo(target)) setGoalTarget(peer, target);
            }
        }
    }

    /** Called only at the accepted Mob.target field assignment, after all cancelable NeoForge target listeners. */
    public static void acceptedTargetChange(Mob owner, @Nullable LivingEntity previous, @Nullable LivingEntity target) {
        if (GOAL_CHANGE.get() || owner.level().isClientSide || !(owner instanceof PathfinderMob mob)
                || !R196MonsterAi.managed(mob)) return;
        if (previous == null && target != null && target.isAlive()) ((SourceMonsterTargetMemory)mob).mite$retarget(target);
    }

    @SubscribeEvent public static void beforeTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Mob mob) restorePendingTarget(mob);
    }

    public static void restorePendingTarget(Mob mob) {
        if (mob.level().isClientSide || !R196MonsterAi.monster(mob)) return;
        var memory = (SourceMonsterTargetMemory)mob;
        var uuid = memory.mite$pendingTarget();
        if (uuid == null || mob.getTarget() != null || mob.tickCount % 10 != 0) return;
        for (LivingEntity nearby : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(32, 8, 32),
                entity -> entity != mob && entity.getUUID().equals(uuid))) {
            if (!nearby.isAlive()) { memory.mite$pendingTarget(null); return; }
            mob.setTarget(nearby);
            if (mob.getTarget() != null) memory.mite$pendingTarget(null);
            return;
        }
        if (mob.tickCount > 0 && mob.tickCount % 1000 == 0) memory.mite$pendingTarget(null);
    }
}

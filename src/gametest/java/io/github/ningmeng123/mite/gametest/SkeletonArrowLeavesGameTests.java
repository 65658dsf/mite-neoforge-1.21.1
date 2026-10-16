package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.ranged.SkeletonArrowLeaves;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual arrow ticking verifies foliage transparency without dropping entity or solid-block collisions. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SkeletonArrowLeavesGameTests {
    private SkeletonArrowLeavesGameTests() {}

    private static Vec3 point(GameTestHelper helper, double x, double y, double z) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return new Vec3(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
    }

    private static void clear(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 10, 1), new BlockPos(10, 14, 10)))
            helper.setBlock(pos, Blocks.AIR);
    }

    private static void leaves(GameTestHelper helper, int x, int y, int z, Block block) {
        var state = block.defaultBlockState();
        if (state.hasProperty(LeavesBlock.PERSISTENT)) state = state.setValue(LeavesBlock.PERSISTENT, true);
        helper.setBlock(new BlockPos(x, y, z), state);
    }

    private static Arrow arrow(GameTestHelper helper, LivingEntity owner, Vec3 start, Vec3 velocity) {
        Arrow arrow = new Arrow(helper.getLevel(), start.x, start.y, start.z, new ItemStack(Items.ARROW), null);
        if (owner != null) arrow.setOwner(owner);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(velocity);
        return arrow;
    }

    private static boolean embedded(Arrow arrow) {
        CompoundTag tag = new CompoundTag();
        arrow.saveWithoutId(tag);
        return tag.getBoolean("inGround");
    }

    private static AbstractSkeleton skeleton(GameTestHelper helper) {
        AbstractSkeleton skeleton = EntityType.SKELETON.create(helper.getLevel());
        skeleton.moveTo(point(helper, 1.5D, 11D, 2.5D));
        skeleton.setNoAi(true);
        return skeleton;
    }

    @GameTest(template = "skeleton_arrow_leaves")
    public static void everySkeletonFamilyHitsTheEntityBehindSeveralLeafBlocks(GameTestHelper helper) {
        clear(helper);
        for (int x = 3; x <= 6; x++) leaves(helper, x, 11, 2, Blocks.OAK_LEAVES);
        Cow target = EntityType.COW.create(helper.getLevel());
        target.moveTo(point(helper, 7.5D, 11D, 2.5D));
        target.setNoAi(true);
        target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
        target.setHealth(200F);
        helper.getLevel().addFreshEntity(target);
        helper.runAfterDelay(1, () -> {
            List<AbstractSkeleton> skeletons = new ArrayList<>();
            for (EntityType<? extends AbstractSkeleton> type : List.of(EntityType.SKELETON,
                    EntityType.STRAY, EntityType.BOGGED, EntityType.WITHER_SKELETON))
                skeletons.add(type.create(helper.getLevel()));
            for (MobKind kind : List.of(MobKind.LONGDEAD, MobKind.LONGDEAD_GUARDIAN,
                    MobKind.BONE_LORD, MobKind.ANCIENT_BONE_LORD))
                skeletons.add((AbstractSkeleton)MiteMobs.type(kind).create(helper.getLevel()));
            try {
                for (AbstractSkeleton shooter : skeletons) {
                    shooter.moveTo(point(helper, 1.5D, 11D, 2.5D));
                    shooter.setNoAi(true);
                    shooter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                    target.invulnerableTime = 0;
                    target.hurtTime = 0;
                    target.setHealth(200F);
                    target.removeAllEffects();
                    try (var spawned = new SpawnedEntityCapture(helper.getLevel(),
                            entity -> entity instanceof Arrow a && a.getOwner() == shooter)) {
                        shooter.performRangedAttack(target, 1F);
                        List<Arrow> arrows = spawned.live(Arrow.class);
                        helper.assertTrue(arrows.size() == 1, "Actual skeleton attack creates one native or material arrow: " + shooter.getType());
                        Arrow shot = arrows.getFirst();
                        shot.setPos(point(helper, 2.5D, 11.8D, 2.5D));
                        shot.setNoGravity(true);
                        shot.setCritArrow(false);
                        shot.setDeltaMovement(7D, 0D, 0D);
                        shot.tick();
                        helper.assertTrue(target.getHealth() < 200F && shot.isRemoved(),
                                "Native arrow hits the entity behind four leaves in the same tick: " + shooter.getType());
                        if (shooter.getType() == EntityType.STRAY)
                            helper.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Stray arrow retains its slowing effect after foliage");
                        if (shooter.getType() == EntityType.BOGGED)
                            helper.assertTrue(target.hasEffect(MobEffects.POISON), "Bogged arrow retains its poison effect after foliage");
                    }
                }
                for (int x = 3; x <= 6; x++)
                    helper.assertBlockPresent(Blocks.OAK_LEAVES, new BlockPos(x, 11, 2));
            } finally {
                target.discard();
                skeletons.forEach(AbstractSkeleton::discard);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "skeleton_arrow_leaves")
    public static void anArrowStartingInsideLeavesKeepsFlyingAcrossSuccessiveTicks(GameTestHelper helper) {
        clear(helper);
        for (int x = 3; x <= 6; x++) leaves(helper, x, 11, 2, Blocks.SPRUCE_LEAVES);
        Arrow arrow = arrow(helper, skeleton(helper), point(helper, 3.5D, 11.5D, 2.5D), new Vec3(1.5D, 0D, 0D));
        for (int tick = 0; tick < 3; tick++) {
            arrow.tick();
            helper.assertTrue(!embedded(arrow), "Arrow is not marked in-ground when the next tick starts inside a leaf");
        }
        helper.assertTrue(arrow.getX() > point(helper, 7D, 11.5D, 2.5D).x, "Arrow leaves the full foliage layer at its native speed");
        arrow.discard();
        helper.succeed();
    }

    @GameTest(template = "skeleton_arrow_leaves")
    public static void leafTransparencyStillStopsAtTheFirstLogOrStoneOnTheRay(GameTestHelper helper) {
        for (Block obstacle : List.of(Blocks.OAK_LOG, Blocks.STONE)) {
            clear(helper);
            for (int x = 3; x <= 5; x++) leaves(helper, x, 11, 2, Blocks.BIRCH_LEAVES);
            helper.setBlock(new BlockPos(6, 11, 2), obstacle);
            leaves(helper, 7, 11, 2, Blocks.BIRCH_LEAVES);
            Arrow arrow = arrow(helper, skeleton(helper), point(helper, 2.5D, 11.5D, 2.5D), new Vec3(7D, 0D, 0D));
            arrow.tick();
            helper.assertTrue(embedded(arrow), "A solid obstacle after leaves embeds the arrow: " + obstacle);
            double wall = point(helper, 6D, 11.5D, 2.5D).x;
            helper.assertTrue(arrow.getX() < wall && arrow.getX() > wall - .1D,
                    "Ray stops at the first solid obstacle instead of jumping to the end of its motion");
            helper.assertBlockPresent(obstacle, new BlockPos(6, 11, 2));
            arrow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "skeleton_arrow_leaves")
    public static void diagonalArrowsSkipMultipleLeavesAndKeepTheSolidIntersection(GameTestHelper helper) {
        clear(helper);
        for (int x = 3; x <= 5; x++) for (int y = 11; y <= 13; y++) for (int z = 3; z <= 5; z++)
            leaves(helper, x, y, z, Blocks.JUNGLE_LEAVES);
        helper.setBlock(new BlockPos(7, 13, 7), Blocks.STONE);
        Arrow arrow = arrow(helper, skeleton(helper), point(helper, 2.5D, 11.5D, 2.5D), new Vec3(6D, 2D, 6D));
        arrow.tick();
        helper.assertTrue(embedded(arrow), "Diagonal arrow collides with the final stone after traversing a leaf volume");
        Vec3 wall = point(helper, 7D, 13D, 7D);
        helper.assertTrue(arrow.getX() < wall.x && arrow.getX() > wall.x - .1D
                        && arrow.getZ() < wall.z && arrow.getZ() > wall.z - .1D,
                "Slanted native ray retains the actual solid-block intersection");
        arrow.discard();
        helper.succeed();
    }

    @GameTest(template = "skeleton_arrow_leaves")
    public static void playerDispenserAndOtherMobArrowsKeepTheirNativeLeafCollision(GameTestHelper helper) {
        clear(helper);
        leaves(helper, 3, 11, 2, Blocks.OAK_LEAVES);
        LivingEntity[] owners = {helper.makeMockPlayer(GameType.SURVIVAL), null,
                EntityType.COW.create(helper.getLevel())};
        for (LivingEntity owner : owners) {
            Arrow arrow = arrow(helper, owner, point(helper, 2.5D, 11.5D, 2.5D), new Vec3(4D, 0D, 0D));
            arrow.tick();
            helper.assertTrue(embedded(arrow) && arrow.getX() < point(helper, 3D, 11.5D, 2.5D).x,
                    "Unrelated owner retains native leaf collision: " + (owner == null ? "dispenser" : owner.getType()));
            arrow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "skeleton_arrow_leaves")
    public static void everyTaggedLeafIsTransparentAndRemainsIntact(GameTestHelper helper) {
        clear(helper);
        AbstractSkeleton shooter = skeleton(helper);
        int checked = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!block.defaultBlockState().is(BlockTags.LEAVES)) continue;
            leaves(helper, 3, 11, 2, block);
            Arrow arrow = arrow(helper, shooter, point(helper, 2.5D, 11.5D, 2.5D), new Vec3(2D, 0D, 0D));
            arrow.tick();
            helper.assertTrue(!embedded(arrow) && arrow.getX() > point(helper, 4D, 11.5D, 2.5D).x,
                    "Native LEAVES-tag collision is filtered for " + block);
            helper.assertBlockPresent(block, new BlockPos(3, 11, 2));
            arrow.discard();
            checked++;
        }
        helper.assertTrue(checked >= 9, "All modern native leaf families, including azalea and mangrove, were checked");
        helper.succeed();
    }

    private static boolean skeletonOrigin(Arrow arrow) {
        return ((SkeletonArrowLeaves.Origin)arrow).mite$firedBySkeleton();
    }

    private static void transferOrigin(GameTestHelper helper, Arrow source, Arrow peer) {
        var dirty = source.getEntityData().packDirty();
        helper.assertTrue(dirty != null && dirty.size() == 1
                        && dirty.getFirst().serializer() == EntityDataSerializers.BOOLEAN,
                "Changing the shooter emits exactly one Boolean origin field");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buffer,
                    new ClientboundSetEntityDataPacket(source.getId(), dirty));
            var packet = ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buffer);
            helper.assertTrue(packet.id() == source.getId() && !buffer.isReadable(),
                    "Actual entity-data packet preserves the arrow ID and consumes the entire payload");
            peer.getEntityData().assignValues(packet.packedItems());
        } finally {
            buffer.release();
        }
    }

    @GameTest(template = "skeleton_arrow_leaves")
    public static void trackedArrowWithoutItsShooterUsesSyncedOriginAndReceivesOwnerChanges(GameTestHelper helper) {
        clear(helper);
        leaves(helper, 3, 11, 2, Blocks.OAK_LEAVES);
        Vec3 start = point(helper, 2.5D, 11.5D, 2.5D);
        Arrow source = arrow(helper, null, start, new Vec3(2D, 0D, 0D));
        Arrow peer = arrow(helper, null, start, new Vec3(2D, 0D, 0D));
        source.getEntityData().packDirty();
        source.setOwner(skeleton(helper));
        transferOrigin(helper, source, peer);
        helper.assertTrue(peer.getOwner() == null && skeletonOrigin(peer),
                "A peer receives the skeleton origin without receiving or resolving the skeleton entity");
        peer.tick();
        helper.assertTrue(!embedded(peer) && peer.getX() > point(helper, 4D, 11.5D, 2.5D).x,
                "Ownerless tracked peer actually ticks through leaves using the synchronized origin");

        source.setOwner(EntityType.COW.create(helper.getLevel()));
        transferOrigin(helper, source, peer);
        helper.assertTrue(!skeletonOrigin(source) && !skeletonOrigin(peer) && peer.getOwner() == null,
                "Changing to an unrelated shooter transmits false instead of retaining the previous skeleton bit");
        peer.setPos(start);
        peer.setDeltaMovement(2D, 0D, 0D);
        peer.tick();
        helper.assertTrue(embedded(peer), "The same ownerless peer returns to native leaf collision after the false update");
        source.discard();
        peer.discard();
        helper.succeed();
    }

    @GameTest(template = "skeleton_arrow_leaves")
    public static void savedOriginSurvivesShooterRemovalAndReloadWithoutTheOwnerEntity(GameTestHelper helper) {
        clear(helper);
        leaves(helper, 3, 11, 2, Blocks.OAK_LEAVES);
        Vec3 start = point(helper, 2.5D, 11.5D, 2.5D);
        AbstractSkeleton shooter = skeleton(helper);
        Arrow source = arrow(helper, shooter, start, new Vec3(2D, 0D, 0D));
        CompoundTag saved = new CompoundTag();
        source.saveWithoutId(saved);
        helper.assertTrue(saved.getBoolean("mite_skeleton_arrow_origin"), "Native arrow save stores the authoritative origin bit");
        shooter.discard();
        helper.assertTrue(source.getOwner() == null && skeletonOrigin(source),
                "Removing the shooter does not discard the arrow's skeleton provenance");
        source.tick();
        helper.assertTrue(!embedded(source), "The original arrow continues through leaves after its shooter disappears");

        Arrow loaded = new Arrow(EntityType.ARROW, helper.getLevel());
        loaded.load(saved);
        helper.assertTrue(loaded.getOwner() == null && skeletonOrigin(loaded),
                "Reloaded arrow restores the skeleton origin even though no owner can resolve by UUID");
        loaded.tick();
        helper.assertTrue(!embedded(loaded) && loaded.getX() > point(helper, 4D, 11.5D, 2.5D).x,
                "A reloaded orphan arrow retains the actual foliage collision behavior");

        loaded.setOwner(helper.makeMockPlayer(GameType.SURVIVAL));
        helper.assertTrue(!skeletonOrigin(loaded), "Assigning a player clears previously saved skeleton provenance");
        CompoundTag reassigned = new CompoundTag();
        loaded.saveWithoutId(reassigned);
        helper.assertTrue(reassigned.contains("mite_skeleton_arrow_origin")
                        && !reassigned.getBoolean("mite_skeleton_arrow_origin"),
                "An explicit non-skeleton origin is persisted as false");
        loaded.setOwner(skeleton(helper));
        loaded.setOwner(null);
        helper.assertTrue(!skeletonOrigin(loaded), "An explicit null-owner assignment clears the bit as well");
        source.discard();
        loaded.discard();
        helper.succeed();
    }

    @GameTest(template = "skeleton_arrow_leaves")
    public static void legacyArrowOriginResolvesWhenItsSavedShooterBecomesAvailable(GameTestHelper helper) {
        clear(helper);
        leaves(helper, 3, 11, 2, Blocks.OAK_LEAVES);
        AbstractSkeleton shooter = skeleton(helper);
        shooter.setNoGravity(true);
        Arrow source = arrow(helper, shooter, point(helper, 2.5D, 11.5D, 2.5D), new Vec3(2D, 0D, 0D));
        CompoundTag legacy = new CompoundTag();
        source.saveWithoutId(legacy);
        legacy.remove("mite_skeleton_arrow_origin");
        Arrow loaded = new Arrow(EntityType.ARROW, helper.getLevel());
        loaded.load(legacy);
        helper.assertTrue(loaded.getOwner() == null && !skeletonOrigin(loaded),
                "A legacy save has no invented skeleton origin while its UUID remains unresolved");
        CompoundTag unresolved = new CompoundTag();
        loaded.saveWithoutId(unresolved);
        helper.assertTrue(!unresolved.contains("mite_skeleton_arrow_origin"),
                "Saving an unresolved legacy arrow preserves its eligibility for owner migration");
        helper.getLevel().addFreshEntity(shooter);
        helper.runAfterDelay(1, () -> {
            loaded.tick();
            helper.assertTrue(loaded.getOwner() == shooter && skeletonOrigin(loaded) && !embedded(loaded),
                    "Legacy arrow resolves its now-loaded skeleton before the native collision check");
            CompoundTag migrated = new CompoundTag();
            loaded.saveWithoutId(migrated);
            helper.assertTrue(migrated.getBoolean("mite_skeleton_arrow_origin"), "The migration is persisted and synced for subsequent loads");
            source.discard();
            loaded.discard();
            shooter.discard();
            helper.succeed();
        });
    }
}

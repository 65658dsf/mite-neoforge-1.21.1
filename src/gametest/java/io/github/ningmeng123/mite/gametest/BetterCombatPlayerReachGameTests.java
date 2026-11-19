package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatPlayerReach;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsReach;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.rules.ProgressionRules;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real optional Better Combat player requests and the vanilla packet damage path. @author NingMeng123 */
@GameTestHolder("mite_bettercombat")
@PrefixGameTestTemplate(false)
public final class BetterCombatPlayerReachGameTests {
    private BetterCombatPlayerReachGameTests() {}

    private static boolean absent(GameTestHelper h) {
        if (BetterCombatBridge.enabled()) return false;
        h.succeed();
        return true;
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void chickenTargetIndicatorsUseTheActualEmptyHandWeaponAndLevelReach(GameTestHelper h) {
        try (ReachSettings ignored = new ReachSettings(1D, 0D); Session session = session(h)) {
            ServerPlayer player = session.player();
            int originalExperience = player.totalExperience;
            var chicken = EntityType.CHICKEN.create(h.getLevel());
            chicken.setNoAi(true);
            chicken.setNoGravity(true);
            h.getLevel().addFreshEntity(chicken);
            chicken.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
            chicken.setHealth(200F);
            try {
                PlayerExperience.setLevels(player, 0);
                equip(player, ItemStack.EMPTY, ItemStack.EMPTY);
                positionSmallTarget(player, chicken, 1.4D);
                var near = aimedHit(player, chicken);
                h.assertTrue(SourcePlayerReach.canTargetMelee(player, near, 1F), "The close chicken is an actual empty-hand melee target");
                player.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(chicken, false));
                h.assertTrue(chicken.getHealth() < 200F, "The same empty-hand target receives the actual attack packet");
                chicken.setHealth(200F);
                chicken.invulnerableTime = 0;
                positionSmallTarget(player, chicken, 1.6D);
                h.assertTrue(SourcePlayerReach.canInteract(player, chicken), "The chicken remains inside the independent interaction distance");
                h.assertFalse(SourcePlayerReach.canTargetMelee(player, aimedHit(player, chicken), 1F), "An interactable chicken beyond empty-hand reach gets no attack indicator");
                player.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(chicken, false));
                h.assertValueEqual(chicken.getHealth(), 200F, "The same out-of-reach chicken rejects the real attack packet");
                positionSmallTarget(player, chicken, 1.55D);
                PlayerExperience.setLevels(player, 9);
                h.assertFalse(SourcePlayerReach.canTargetMelee(player, aimedHit(player, chicken), 1F), "Level nine still uses base empty-hand reach");
                PlayerExperience.setLevels(player, 10);
                h.assertTrue(SourcePlayerReach.canTargetMelee(player, aimedHit(player, chicken), 1F), "The first ten-level bonus immediately admits the chicken");
                PlayerExperience.setLevels(player, 9);
                h.assertFalse(SourcePlayerReach.canTargetMelee(player, aimedHit(player, chicken), 1F), "Losing the level bonus immediately removes its attack eligibility");
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                positionSmallTarget(player, chicken, 2.1D);
                h.assertTrue(SourcePlayerReach.canTargetMelee(player, aimedHit(player, chicken), 1F), "Equipping a sword uses its own longer melee distance");
                positionSmallTarget(player, chicken, 2.4D);
                h.assertFalse(SourcePlayerReach.canTargetMelee(player, aimedHit(player, chicken), 1F), "The weapon indicator enforces the actual sword boundary");
            } finally {
                PlayerExperience.setTotal(player, originalExperience);
                chicken.discard();
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void meleeTargetIndicatorsRequireTheAimedPointAndAClearBlockRay(GameTestHelper h) {
        try (ReachSettings ignored = new ReachSettings(1D, 0D); Session session = session(h)) {
            ServerPlayer player = session.player();
            equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
            try (Target target = target(h, player, 2.1D, 0D)) {
                var aimed = aimedHit(player, target.entity());
                h.assertTrue(SourcePlayerReach.canTargetMelee(player, aimed, 1F), "The clear aimed collision point is attackable");
                Vec3 farPoint = target.entity().getBoundingBox().getCenter().add(0D, 0D, .3D);
                h.assertFalse(SourcePlayerReach.canTargetMelee(player, new EntityHitResult(target.entity(), farPoint), 1F), "A farther aim point cannot borrow the nearest collision point's range");
                BlockPos obstruction = BlockPos.containing(player.getEyePosition().lerp(aimed.getLocation(), .5D));
                var previous = h.getLevel().getBlockState(obstruction);
                try {
                    h.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
                    h.assertFalse(SourcePlayerReach.canTargetMelee(player, aimed, 1F), "A solid block before the aimed entity suppresses its melee indicator");
                } finally { h.getLevel().setBlockAndUpdate(obstruction, previous); }
                h.assertTrue(SourcePlayerReach.canTargetMelee(player, aimed, 1F), "Removing the blocking geometry restores attack eligibility");
                player.setGameMode(GameType.SPECTATOR);
                h.assertFalse(SourcePlayerReach.canTargetMelee(player, aimed, 1F), "A spectator has no melee target indicator");
                player.setGameMode(GameType.SURVIVAL);
                target.entity().setHealth(0F);
                h.assertFalse(SourcePlayerReach.canTargetMelee(player, aimed, 1F), "A defeated target has no attack indicator");
            }
        }
        h.succeed();
    }

    private static EntityHitResult aimedHit(ServerPlayer player, net.minecraft.world.entity.Entity target) {
        return new EntityHitResult(target, SourcePlayerReach.closestPoint(target.getBoundingBox().inflate(target.getPickRadius()), player.getEyePosition()));
    }

    private static void positionSmallTarget(ServerPlayer player, net.minecraft.world.entity.Entity target, double distance) {
        target.setPos(player.position());
        double vertical = Math.max(0D, player.getEyeY() - target.getBoundingBox().maxY - target.getPickRadius());
        double forward = Math.sqrt(distance * distance - vertical * vertical) + target.getBbWidth() * .5D + target.getPickRadius();
        target.setPos(player.position().add(0D, 0D, forward));
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void bundledSimplySwordsLengthsMatchEveryLiveProfile(GameTestHelper h) {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        String[] families = {"longsword", "twinblade", "rapier", "katana", "sai", "spear", "glaive",
                "warglaive", "cutlass", "claymore", "greataxe", "greathammer", "chakram", "scythe", "halberd"};
        double[] expected = {3.1D, 3.5D, 3D, 3.1D, 2.3D, 4D, 3.5D, 3.1D, 2.75D, 4D, 4D, 4D, 4.25D, 3.5D, 4.5D};
        double[] balanced = BetterCombatBridge.enabled()
                ? new double[]{2.5D, 3.5D, 2.1D, 2.15D, 2D, 3.75D, 3.5D, 2.1D, 2.1D, 3.25D, 3.25D, 3.25D, 2.8D, 3.5D, 4D}
                : new double[]{2.5D, 2.75D, 2D, 2.1D, 2D, 3D, 2.75D, 2D, 2D, 3.25D, 3.25D, 3.25D, 2.8D, 2.75D, 3.25D};
        for (String material : List.of("copper", "vibranium", "gold", "iron", "runic")) {
            for (int index = 0; index < families.length; ++index) {
                var stack = new ItemStack(SimplySwordsCompat.weapon(material, families[index]));
                h.assertFalse(stack.isEmpty(), "The actual weapon is registered: " + material + "/" + families[index]);
                double range = SimplySwordsReach.baseReach(stack, 3D);
                h.assertTrue(Math.abs(range - expected[index]) < .00001D,
                        "Bundled weapon length survives absent parents: " + stack + " = " + range);
                h.assertTrue(Math.abs(SourcePlayerReach.baseMeleeReach(stack, 3D) - balanced[index]) < .00001D,
                        "The balanced family distance applies after resolving its bundled or live profile: " + stack);
            }
        }
        if (BetterCombatBridge.enabled()) {
            for (var item : BuiltInRegistries.ITEM) {
                var stack = new ItemStack(item);
                if (SimplySwordsCompat.definition(stack) == null) continue;
                Double live = BetterCombatBridge.weaponBaseReach(stack, 3D);
                Double bundled = SimplySwordsReach.baseReach(stack, 3D);
                h.assertTrue(live == null ? bundled == null : bundled != null && Math.abs(bundled - live) < .00001D,
                        "Every managed normal/unique weapon agrees with the actual Better Combat registry: " + stack
                                + " bundled=" + bundled + " live=" + live);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void everyChakramMaterialAndSpecialProfileHasTheSameLiveRange(GameTestHelper h) {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        Set<String> expected = Set.of(
                "mite:simplyswords_rusted_iron_chakram", "mite:simplyswords_copper_chakram",
                "mite:simplyswords_silver_chakram", "mite:simplyswords_ancient_metal_chakram",
                "mite:simplyswords_mithril_chakram", "mite:simplyswords_adamantium_chakram",
                "mite:simplyswords_vibranium_chakram", "simplyswords:iron_chakram", "simplyswords:gold_chakram",
                "simplyswords:diamond_chakram", "simplyswords:netherite_chakram", "simplyswords:runic_chakram",
                "simplyswords:chompolotl", "simplyswords:mjolnir", "simplyswords:tempest");
        Set<String> exercised = new HashSet<>();
        double balancedRange = 2.8D;
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) {
            for (var item : BuiltInRegistries.ITEM) {
                var stack = new ItemStack(item);
                var definition = SimplySwordsCompat.definition(stack);
                if (definition == null || !definition.family().equals("chakram")) continue;
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                exercised.add(id);
                Double bundled = SimplySwordsReach.baseReach(stack, 3D);
                h.assertTrue(bundled != null && Math.abs(bundled - 4.25D) < .00001D,
                        "Every registered material/special chakram has the fixed bundled range: " + id + " = " + bundled);
                h.assertTrue(Math.abs(SourcePlayerReach.tooltipMeleeReach(stack) - balancedRange) < .00001D,
                        "The actual tooltip uses the balanced range for the current combat integration: " + id);
                if (BetterCombatBridge.enabled()) {
                    Double live = BetterCombatBridge.weaponBaseReach(stack, 3D);
                    h.assertTrue(live != null && Math.abs(live - 4.25D) < .00001D,
                            "The actual loaded Better Combat registry uses the fixed range: " + id + " = " + live);
                }
            }
        }
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(exercised);
        h.assertTrue(missing.isEmpty(), "Every required material and registered special chakram was exercised; missing="
                + missing + ", actual=" + exercised);
        // SS 1.70.2 ships this JSON and translation but ItemsRegistry never registers an item for it.
        // Retired diamond/netherite and unique items above remain registered and must still be checked.
        h.assertFalse(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("simplyswords:magic_estoc")),
                "The legacy magic_estoc profile is a bundled resource, not a registered fourth special chakram");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void chakramVanillaPacketsRespectTheNewRangeBoundary(GameTestHelper h) {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        double balancedRange = 2.8D;
        try (ReachSettings ignored = new ReachSettings(1D, 0D); Session session = session(h)) {
            for (String material : List.of("iron", "vibranium")) {
                equip(session.player(), new ItemStack(SimplySwordsCompat.weapon(material, "chakram")), ItemStack.EMPTY);
                try (Target near = target(h, session.player(), balancedRange - .1D);
                        Target far = target(h, session.player(), balancedRange + .1D)) {
                    h.assertTrue(Math.abs(SourcePlayerReach.meleeReach(session.player(), near.entity()) - balancedRange) < .00001D,
                            "The equipped " + material + " chakram has its balanced integration-specific reach");
                    vanillaAttack(session.player(), near.entity());
                    h.assertTrue(near.entity().getHealth() < 200F, "The actual packet hits inside the new chakram range");
                    vanillaAttack(session.player(), far.entity());
                    h.assertValueEqual(far.entity().getHealth(), 200F, "The actual packet rejects targets beyond the balanced chakram range");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void balancedNativeAndSimplySwordsFamiliesEnforceTheirSavedPacketBoundaries(GameTestHelper h) {
        boolean betterCombat = BetterCombatBridge.enabled();
        var weapons = new java.util.ArrayList<>(List.of(
                new WeaponRange("minecraft:iron_sword", 2.25D), new WeaponRange("mite:wooden_cudgel", 1.75D),
                new WeaponRange("mite:copper_dagger", 2D), new WeaponRange("mite:copper_battle_axe", betterCombat ? 2.25D : 2D),
                new WeaponRange("mite:copper_war_hammer", 2D), new WeaponRange("mite:copper_scythe", betterCombat ? 3.25D : 2.5D),
                new WeaponRange("minecraft:trident", betterCombat ? 3.25D : 2.75D), new WeaponRange("minecraft:mace", betterCombat ? 2D : 1.75D)));
        if (SimplySwordsCompat.enabled()) {
            String[] families = {"longsword", "twinblade", "rapier", "katana", "sai", "spear", "glaive",
                    "warglaive", "cutlass", "claymore", "greataxe", "greathammer", "chakram", "scythe", "halberd"};
            double[] reaches = betterCombat
                    ? new double[]{2.5D, 3.5D, 2.1D, 2.15D, 2D, 3.75D, 3.5D, 2.1D, 2.1D, 3.25D, 3.25D, 3.25D, 2.8D, 3.5D, 4D}
                    : new double[]{2.5D, 2.75D, 2D, 2.1D, 2D, 3D, 2.75D, 2D, 2D, 3.25D, 3.25D, 3.25D, 2.8D, 2.75D, 3.25D};
            for (int index = 0; index < families.length; index++)
                weapons.add(new WeaponRange("mite:simplyswords_copper_" + families[index], reaches[index]));
        }
        try (ReachSettings ignored = new ReachSettings(1D, 0D); Session session = session(h)) {
            for (var weapon : weapons) {
                var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(weapon.id())));
                h.assertFalse(stack.isEmpty(), "The balanced weapon fixture is actually registered: " + weapon.id());
                stack.set(DataComponents.ATTRIBUTE_MODIFIERS, stack.getAttributeModifiers());
                var saved = stack.save(h.getLevel().registryAccess());
                stack = ItemStack.parse(h.getLevel().registryAccess(), saved).orElseThrow();
                equip(session.player(), stack, ItemStack.EMPTY);
                var beforeRange = stack.save(h.getLevel().registryAccess());
                h.assertTrue(Math.abs(SourcePlayerReach.tooltipMeleeReach(stack) - weapon.reach()) < .00001D,
                        "The saved stack tooltip uses its balanced family distance: " + weapon.id());
                h.assertValueEqual(stack.save(h.getLevel().registryAccess()), beforeRange,
                        "Reading live balanced range preserves all saved item components");
                try (Target near = target(h, session.player(), weapon.reach() - .15D, 0D);
                        Target far = target(h, session.player(), weapon.reach() + .15D, 0D)) {
                    vanillaAttack(session.player(), near.entity());
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "The real vanilla packet hits inside the saved balanced distance: " + weapon.id());
                    vanillaAttack(session.player(), far.entity());
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "The real vanilla packet rejects the point outside the saved balanced distance: " + weapon.id());
                }
                if (BetterCombatBridge.enabled()) {
                    Loaded.requestAtDistance(h, session.player(), weapon.reach() - .15D, 0D, true,
                            "A real Better Combat request reaches inside the same balanced distance: " + weapon.id());
                    Loaded.requestAtDistance(h, session.player(), weapon.reach() + .15D, 0D, false,
                            "A real Better Combat request enforces the same balanced upper bound: " + weapon.id());
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void independentRangeAttributesRemainOutsideTheSimplySwordsFamilyBudget(GameTestHelper h) {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        double neutralReach = 3.25D;
        double extendedReach = neutralReach + .5D;
        try (ReachSettings ignored = new ReachSettings(1D, 0D); Session session = session(h)) {
            var stack = new ItemStack(SimplySwordsCompat.weapon("copper", "greathammer"));
            equip(session.player(), stack, ItemStack.EMPTY);
            var attribute = session.player().getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
            var id = ResourceLocation.parse("mite:independent_balanced_range_test");
            attribute.addTransientModifier(new AttributeModifier(id, .5D, AttributeModifier.Operation.ADD_VALUE));
            try {
                h.assertTrue(Math.abs(SourcePlayerReach.tooltipMeleeReach(stack) - neutralReach) < .00001D,
                        "An unrelated entity modifier never enters the hovered weapon's neutral budget");
                try (Target near = target(h, session.player(), extendedReach - .15D, 0D);
                        Target far = target(h, session.player(), extendedReach + .15D, 0D)) {
                    h.assertTrue(Math.abs(SourcePlayerReach.meleeReach(session.player(), near.entity()) - extendedReach) < .00001D,
                            "The independent half-block attribute is added after the new heavy-weapon budget once");
                    vanillaAttack(session.player(), near.entity());
                    h.assertTrue(near.entity().getHealth() < 200F, "A real vanilla packet uses the independent range bonus");
                    vanillaAttack(session.player(), far.entity());
                    h.assertValueEqual(far.entity().getHealth(), 200F, "The independent bonus still has one final boundary");
                }
                if (BetterCombatBridge.enabled()) {
                    Loaded.requestAtDistance(h, session.player(), extendedReach - .15D, 0D, true,
                            "A real Better Combat request preserves the independent range attribute");
                    Loaded.requestAtDistance(h, session.player(), extendedReach + .15D, 0D, false,
                            "A real Better Combat request does not double the independent range attribute");
                }
            } finally { attribute.removeModifier(id); }
        }
        h.succeed();
    }

    private record WeaponRange(String id, double reach) {}

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void currentExperienceLevelsImmediatelyExtendAndRetractNativeSwordReach(GameTestHelper h) {
        int[] levels = {-1, 0, 9, 10, 19, 20, 50, 99, 100, 110, 99, 9, 0};
        double[] bonuses = {0D, 0D, 0D, .1D, .1D, .2D, .5D, .9D, 1D, 1D, .9D, 0D, 0D};
        try (ReachSettings ignored = new ReachSettings(1D, 0D); Session session = session(h)) {
            ServerPlayer player = session.player();
            int originalTotal = player.totalExperience;
            try {
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                for (int index = 0; index < levels.length; index++) {
                    PlayerExperience.setLevels(player, levels[index]);
                    double expected = 2.25D + bonuses[index];
                    h.assertTrue(Math.abs(SourcePlayerReach.tooltipMeleeReach(player.getMainHandItem(), player) - expected) < .00001D,
                            "The hovered native sword follows the current signed level immediately: " + levels[index]);
                    levelReachBoundary(h, player, expected, "native sword at level " + levels[index]);
                }
            } finally { PlayerExperience.setTotal(player, originalTotal); }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void newAndSavedSimplySwordsFamiliesReceiveTheLevelBonusExactlyOnce(GameTestHelper h) {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        String[] families = {"longsword", "claymore", "greataxe", "greathammer", "chakram", "sai"};
        double[] reaches = {2.5D, 3.25D, 3.25D, 3.25D, 2.8D, 2D};
        int[] levels = {10, 100, 10};
        double[] bonuses = {.1D, 1D, .1D};
        try (ReachSettings ignored = new ReachSettings(1D, 0D); Session session = session(h)) {
            ServerPlayer player = session.player();
            int originalTotal = player.totalExperience;
            try {
                for (int family = 0; family < families.length; family++) {
                    for (boolean saved : new boolean[]{false, true}) {
                        ItemStack stack = new ItemStack(SimplySwordsCompat.weapon("copper", families[family]));
                        if (saved) {
                            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, stack.getAttributeModifiers());
                            stack = ItemStack.parse(h.getLevel().registryAccess(), stack.save(h.getLevel().registryAccess())).orElseThrow();
                        }
                        equip(player, stack, ItemStack.EMPTY);
                        for (int level = 0; level < levels.length; level++) {
                            PlayerExperience.setLevels(player, levels[level]);
                            double expected = reaches[family] + bonuses[level];
                            var beforeTooltip = stack.save(h.getLevel().registryAccess());
                            h.assertTrue(Math.abs(SourcePlayerReach.tooltipMeleeReach(stack) - reaches[family]) < .00001D,
                                    "The playerless preview retains the weapon's own family distance");
                            h.assertTrue(Math.abs(SourcePlayerReach.tooltipMeleeReach(stack, player) - expected) < .00001D,
                                    "The hovered new or saved weapon adds the current player's bonus once");
                            h.assertValueEqual(stack.save(h.getLevel().registryAccess()), beforeTooltip,
                                    "Level-aware tooltip queries preserve the complete saved item");
                            levelReachBoundary(h, player, expected, families[family] + " saved=" + saved + " level=" + levels[level]);
                        }
                    }
                }
            } finally { PlayerExperience.setTotal(player, originalTotal); }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void levelReachIsAddedAfterConfigurationAndPreservesInteractionDistances(GameTestHelper h) {
        try (ReachSettings ignored = new ReachSettings(1.5D, .25D); Session session = session(h)) {
            ServerPlayer player = session.player();
            int originalTotal = player.totalExperience;
            try {
                PlayerExperience.setLevels(player, 0);
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                double blockReach = SourcePlayerReach.blockReach(player, Blocks.STONE.defaultBlockState(), InteractionHand.MAIN_HAND);
                double entityAttribute = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
                double blockAttribute = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
                try (Target target = target(h, player, 1D, 0D)) {
                    double interactionReach = SourcePlayerReach.interactionReach(player, target.entity());
                    for (int level : new int[]{10, 100}) {
                        PlayerExperience.setLevels(player, level);
                        double expected = 3.125D + (level == 10 ? .1D : 1D);
                        h.assertTrue(Math.abs(SourcePlayerReach.tooltipMeleeReach(player.getMainHandItem(), player) - expected) < .00001D,
                                "The fixed level reward is added after the 1.5 multiplier and .25 reduction");
                        levelReachBoundary(h, player, expected, "configured native sword at level " + level);
                        h.assertValueEqual(SourcePlayerReach.blockReach(player, Blocks.STONE.defaultBlockState(), InteractionHand.MAIN_HAND),
                                blockReach, "Experience-level melee reach preserves block interaction distance");
                        h.assertValueEqual(SourcePlayerReach.interactionReach(player, target.entity()), interactionReach,
                                "Experience-level melee reach preserves entity right-click interaction distance");
                        h.assertValueEqual(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), entityAttribute,
                                "The level reward is independent of the general entity interaction attribute");
                        h.assertValueEqual(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), blockAttribute,
                                "The level reward is independent of the general block interaction attribute");
                    }
                }
            } finally { PlayerExperience.setTotal(player, originalTotal); }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void levelReachExtendsEmptyHandsCreativeAttacksAndLongSelectionRays(GameTestHelper h) {
        try (ReachSettings ignored = new ReachSettings(1D, 0D); Session session = session(h)) {
            ServerPlayer player = session.player();
            int originalTotal = player.totalExperience;
            try {
                PlayerExperience.setLevels(player, 100);
                equip(player, ItemStack.EMPTY, ItemStack.EMPTY);
                levelReachBoundary(h, player, 2.5D, "level-100 empty hands");
                player.setGameMode(GameType.CREATIVE);
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                levelReachBoundary(h, player, 6D, "level-100 creative native sword");
                player.setGameMode(GameType.SURVIVAL);
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                try (ReachSettings longSelection = new ReachSettings(10D, 0D)) {
                    PlayerExperience.setLevels(player, 0);
                    double baseline = SourcePlayerReach.selectionRange(player);
                    h.assertTrue(baseline > 16D, "The fixture exercises a configured selection ray beyond its historical minimum");
                    PlayerExperience.setLevels(player, 10);
                    h.assertTrue(Math.abs(SourcePlayerReach.selectionRange(player) - baseline - .1D) < .00001D,
                            "Long target-selection rays include the level reward after configuration");
                    PlayerExperience.setLevels(player, 100);
                    h.assertTrue(Math.abs(SourcePlayerReach.selectionRange(player) - baseline - 1D) < .00001D,
                            "Long target-selection rays include the capped reward exactly once");
                }
            } finally {
                PlayerExperience.setTotal(player, originalTotal);
                player.setGameMode(GameType.SURVIVAL);
            }
        }
        h.succeed();
    }

    private static void levelReachBoundary(GameTestHelper h, ServerPlayer player, double reach, String context) {
        try (Target near = target(h, player, reach - .025D, 0D);
                Target far = target(h, player, reach + .025D, 0D)) {
            var beforeRange = player.getMainHandItem().saveOptional(h.getLevel().registryAccess());
            h.assertTrue(Math.abs(SourcePlayerReach.meleeReach(player, near.entity()) - reach) < .00001D,
                    "The actual level-aware melee distance matches its literal boundary: " + context);
            h.assertValueEqual(player.getMainHandItem().saveOptional(h.getLevel().registryAccess()), beforeRange,
                    "Reading level-aware reach preserves all held item components: " + context);
            vanillaAttack(player, near.entity());
            h.assertTrue(near.entity().getHealth() < 200F, "A real vanilla packet hits immediately inside the level-aware boundary: " + context);
            vanillaAttack(player, far.entity());
            h.assertValueEqual(far.entity().getHealth(), 200F, "A real vanilla packet rejects the point beyond the level-aware boundary: " + context);
        }
        if (BetterCombatBridge.enabled() && !player.getMainHandItem().isEmpty()) {
            double combatReach = player.getAbilities().instabuild ? Loaded.creativeLevelReach(player) : reach;
            Loaded.levelRequestAtDistance(h, player, combatReach - .025D, true,
                    "A real Better Combat request hits immediately inside the level-aware boundary: " + context);
            Loaded.levelRequestAtDistance(h, player, combatReach + .025D, false,
                    "A real Better Combat request rejects the point beyond the level-aware boundary: " + context);
        }
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void absentBetterCombatStillHitsWithSavedRapierGlaiveAndSpear(GameTestHelper h) {
        if (BetterCombatBridge.enabled() || !SimplySwordsCompat.enabled()) { h.succeed(); return; }
        try (ReachSettings ignored = new ReachSettings(1D, 0D); Session session = session(h)) {
            String[] families = {"rapier", "glaive", "spear"};
            double[] reaches = {2D, 2.75D, 3D};
            for (int index = 0; index < families.length; ++index) {
                ItemStack stack = new ItemStack(SimplySwordsCompat.weapon("copper", families[index]));
                // Old explicit damage/speed components have no range; no new item or component migration is needed.
                stack.set(DataComponents.ATTRIBUTE_MODIFIERS, stack.getAttributeModifiers());
                stack = ItemStack.parse(h.getLevel().registryAccess(), stack.save(h.getLevel().registryAccess())).orElseThrow();
                equip(session.player(), stack, ItemStack.EMPTY);
                try (Target near = target(h, session.player(), reaches[index] - .1D);
                        Target far = target(h, session.player(), reaches[index] + .1D)) {
                    h.assertTrue(Math.abs(SourcePlayerReach.meleeReach(session.player(), near.entity()) - reaches[index]) < .00001D,
                            "The saved weapon keeps its full length without Better Combat: " + families[index]);
                    vanillaAttack(session.player(), near.entity());
                    h.assertTrue(near.entity().getHealth() < 200F, "The actual vanilla packet damages within the restored length");
                    vanillaAttack(session.player(), far.entity());
                    h.assertValueEqual(far.entity().getHealth(), 200F, "The actual vanilla packet rejects targets outside the weapon's length");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void absentBetterCombatPreservesRangeComponentsAndIndependentInteractions(GameTestHelper h) {
        if (BetterCombatBridge.enabled() || !SimplySwordsCompat.enabled()) { h.succeed(); return; }
        try (ReachSettings ignored = new ReachSettings(1D, 0D); Session session = session(h)) {
            var stack = new ItemStack(SimplySwordsCompat.weapon("copper", "glaive"));
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                    .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(ResourceLocation.parse("mite:reach_component_test"),
                            .4D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build());
            equip(session.player(), stack, ItemStack.EMPTY);
            var attribute = session.player().getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
            var bonus = ResourceLocation.parse("mite:reach_external_test");
            attribute.addTransientModifier(new AttributeModifier(bonus, .5D, AttributeModifier.Operation.ADD_VALUE));
            try (Target near = target(h, session.player(), 3.8D); Target far = target(h, session.player(), 4D)) {
                h.assertTrue(Math.abs(SourcePlayerReach.meleeReach(session.player(), near.entity()) - 3.9D) < .00001D,
                        "Explicit components replace metadata and external entity-range modifiers are counted once");
                h.assertTrue(Math.abs(SourcePlayerReach.tooltipMeleeReach(stack) - 3.4D) < .00001D,
                        "The hovered weapon preview includes its component without borrowing the held player's modifier");
                vanillaAttack(session.player(), near.entity());
                h.assertTrue(near.entity().getHealth() < 200F, "An actual attack uses the explicit component and external modifier");
                vanillaAttack(session.player(), far.entity());
                h.assertValueEqual(far.entity().getHealth(), 200F, "The same attack still enforces the final cap");
            } finally { attribute.removeModifier(bonus); }
            stack = new ItemStack(SimplySwordsCompat.weapon("copper", "spear"));
            equip(session.player(), stack, ItemStack.EMPTY);
            try (Target target = target(h, session.player(), 3.8D)) {
                h.assertValueEqual(attribute.getValue(), 3D, "Restoring weapon length does not extend the entity interaction attribute");
                h.assertValueEqual(SourcePlayerReach.interactionReach(session.player(), target.entity()), 2.5D,
                        "The long spear does not extend right-click interactions");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void absentBetterCombatSharesConfiguredLengthWithTooltipSelectionAndMobs(GameTestHelper h) {
        if (BetterCombatBridge.enabled() || !SimplySwordsCompat.enabled()) { h.succeed(); return; }
        boolean height = MiteConfig.HEIGHT_REACH_PENALTY.get();
        MiteConfig.HEIGHT_REACH_PENALTY.set(true);
        try (ReachSettings ignored = new ReachSettings(1.5D, .25D); Session session = session(h)) {
            var spear = new ItemStack(SimplySwordsCompat.weapon("copper", "spear"));
            equip(session.player(), spear, ItemStack.EMPTY);
            try (Target target = target(h, session.player(), 5D, 0D)) {
                h.assertValueEqual(SourcePlayerReach.meleeReach(session.player(), target.entity()), 4.25D,
                        "Weapon length receives the configured multiplier and then the reduction");
                h.assertValueEqual(SourcePlayerReach.tooltipMeleeReach(spear), 4.25D, "The neutral-height tooltip reports the actual configured reach");
                target.entity().setPos(target.entity().position().add(0D, -1.5D, 0D));
                h.assertValueEqual(SourcePlayerReach.meleeReach(session.player(), target.entity()), 5D,
                        "Height adjustment remains part of the same configured calculation");
                target.entity().setItemInHand(InteractionHand.MAIN_HAND, spear.copy());
                h.assertTrue(Math.abs(io.github.ningmeng123.mite.combat.MobWeaponReach.sourceLimit(target.entity(),
                        session.player(), session.player().getY()) - 4.05D) < .00001D,
                        "An armed monster uses the same weapon length before its existing .2-block margin");
            }
            MiteConfig.WEAPON_REACH_MULTIPLIER.set(10D);
            MiteConfig.refreshEnchantmentRules();
            h.assertValueEqual(SourcePlayerReach.selectionRange(session.player()), 59.75D,
                    "The client selection ray includes configured long weapons beyond the old 16-block ray");
        } finally {
            MiteConfig.HEIGHT_REACH_PENALTY.set(height);
            MiteConfig.refreshEnchantmentRules();
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void ordinarySwordAndClubRangesFollowTheirBalancedFamiliesAcrossRealPackets(GameTestHelper h) {
        try (ReachSettings ignored = new ReachSettings(1D, 0D); Session session = session(h)) {
            ServerPlayer player = session.player();
            var weapons = new java.util.ArrayList<Item>(List.of(Items.WOODEN_SWORD, Items.IRON_SWORD,
                    io.github.ningmeng123.mite.registry.MiteTools.get("mite:wooden_cudgel"),
                    io.github.ningmeng123.mite.registry.MiteTools.get("mite:copper_sword"),
                    io.github.ningmeng123.mite.registry.MiteTools.get("mite:ancient_metal_sword"),
                    io.github.ningmeng123.mite.registry.MiteTools.get("mite:mithril_sword"),
                    io.github.ningmeng123.mite.registry.MiteTools.get("mite:vibranium_sword")));
            for (var item : BuiltInRegistries.ITEM) {
                var id = BuiltInRegistries.ITEM.getKey(item);
                if (id.getNamespace().equals("modern_ite") && id.getPath().startsWith("club_")) weapons.add(item);
            }
            for (var item : weapons) {
                equip(player, new ItemStack(item), ItemStack.EMPTY);
                double expected = ordinaryFamilyReach(player.getMainHandItem());
                try (Target near = target(h, player, expected - .1D); Target far = target(h, player, expected + .1D)) {
                    h.assertTrue(Math.abs(SourcePlayerReach.meleeReach(player, near.entity()) - expected) < .00001D,
                            "Ordinary swords and club materials use their balanced family distance: " + item);
                    h.assertTrue(Math.abs(SourcePlayerReach.tooltipMeleeReach(player.getMainHandItem()) - expected) < .00001D,
                            "The weapon tooltip matches the actual neutral-height attack range");
                    vanillaAttack(player, near.entity());
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "The actual vanilla attack packet still damages a target inside source reach");
                    vanillaAttack(player, far.entity());
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "The same packet rejects a target beyond the weapon family's reach");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void allOrdinarySwordAndClubIdentitiesKeepOneBalancedBaselineAndExternalBonus(GameTestHelper h) {
        // Bare tier-detection fixtures registered by EquipmentMaterialRecyclingGameTests have no weapon attributes.
        Set<String> recyclingFixtures = Set.of("mite_equipment_recycling:unlisted_blade",
                "mite_equipment_recycling:copper_mithril_iron_blade");
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) {
            for (var item : BuiltInRegistries.ITEM) {
                var stack = new ItemStack(item);
                if (!SourcePlayerReach.ordinarySwordOrClub(stack)) continue;
                double expected = ordinaryFamilyReach(stack);
                h.assertValueEqual(SourcePlayerReach.baseMeleeReach(stack, 3D), expected,
                        "Every native/addon ordinary sword or club has its balanced family baseline: " + item);
                h.assertValueEqual(SourcePlayerReach.baseMeleeReach(stack, 3.5D), expected + .5D,
                        "Deliberate modern range attributes are counted once: " + item);
                boolean bareRecyclingFixture = recyclingFixtures.contains(BuiltInRegistries.ITEM.getKey(item).toString())
                        && stack.getAttributeModifiers().modifiers().stream()
                                .noneMatch(entry -> entry.attribute().is(Attributes.ATTACK_DAMAGE));
                if (BetterCombatBridge.enabled() && !bareRecyclingFixture) Loaded.assertSwordOrClubProfile(h, stack);
            }
        }
        h.succeed();
    }

    private static double ordinaryFamilyReach(ItemStack stack) {
        var profile = io.github.ningmeng123.mite.item.EquipmentDurability.toolProfile(stack);
        if (profile == null) return 2.25D;
        return switch (profile.family()) {
            case CUDGEL -> 1.75D;
            case CLUB -> 2D;
            default -> 2.25D;
        };
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void realSwordRequestKeepsSourceCapAndHeightAdjustment(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.realSword(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void simplySwordsCutlassAndGlaiveKeepTheirDistinctNativeRanges(GameTestHelper h) {
        if (absent(h)) return;
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.simplySwordsWeapons(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void simplySwordsNativeBaseStillReceivesConfigurationAndHeightRules(GameTestHelper h) {
        if (absent(h)) return;
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        Loaded.simplySwordsConfiguredAndHeight(h);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void simplySwordsNativeScaleStillLimitsItsLongWeaponRange(GameTestHelper h) {
        if (absent(h)) return;
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.simplySwordsScaled(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void simplySwordsExplicitRangeComponentOverridesItsNativeBonus(GameTestHelper h) {
        if (absent(h)) return;
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.simplySwordsItemRange(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void liveProfileReloadRetainsBalancedReachAndRespectsShorterMetadata(GameTestHelper h) {
        if (absent(h)) return;
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.reloadedFamilyRange(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void invalidSimplySwordsMetadataCannotAuthorizeAnyAttackPath(GameTestHelper h) {
        if (absent(h)) return;
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.invalidSimplySwordsMetadata(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void attackRangeMultiplierFollowsTheRequestedComboAndDoesNotLeak(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.comboRange(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void dualWieldingUsesTheSelectedHandsRangeAcrossTheRealSwap(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.dualWielding(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void survivalConfigurationAppliesOnceAndCreativeKeepsBothRangeCaps(GameTestHelper h) {
        if (absent(h)) return;
        Loaded.configuredRange(h);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void realPlayerScaleShrinksNativeRangeButCannotExceedSourceCap(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.scaledRange(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void invalidWeaponRangesCannotAuthorizeAnAttack(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.invalidRange(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void configuredRangeAlsoPassesTheOptionalNonVanillaValidationGate(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(2D, .25D)) { Loaded.nonVanillaValidation(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void nestedAttackScopesRestoreAfterExceptionsAndBindOnlyTheirPlayer(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.scopeCleanup(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void zeroConfiguredReachRejectsAnOverlappingTarget(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(0D, 0D)) { Loaded.zeroRange(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void explicitItemEntityRangeOverridesTheWeaponRangeBonus(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.itemRange(h); }
        h.succeed();
    }

    private static void equip(ServerPlayer player, ItemStack main, ItemStack off) {
        Vec3 position = player.position();
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        // Apply the actual equipment components before Better Combat swaps their attributes.
        player.tick();
        player.doTick();
        player.setPos(position);
        player.setDeltaMovement(Vec3.ZERO);
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10D);
        player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024D);
        player.getFoodData().setFoodLevel(6);
        player.setOnGround(true);
        player.fallDistance = 0F;
    }

    private static Target target(GameTestHelper h, ServerPlayer player, double hitboxDistance) {
        return target(h, player, hitboxDistance, null);
    }

    private static Target target(GameTestHelper h, ServerPlayer player, double hitboxDistance, Double feetDifference) {
        Zombie target = EntityType.ZOMBIE.create(h.getLevel());
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setOnGround(true);
        target.getPersistentData().putBoolean(MobShields.SPAWN_ROLL, true);
        target.getPersistentData().putBoolean("modern_ite:explicit_command_spawn", true);
        target.setPos(player.position());
        h.assertTrue(h.getLevel().addFreshEntity(target) && target.isAddedToLevel(), "The deliberate reach-test target actually joins the world");
        // Addons initialize their real health and body shape on join; set the measured fixture afterward.
        target.setBaby(false);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
        target.setHealth(200F);
        double halfWidth = target.getBbWidth() * .5D + target.getPickRadius();
        double feetOffset = feetDifference == null
                ? Math.max(0D, player.getEyeHeight() - target.getBbHeight() * .8D) : -feetDifference;
        target.setPos(player.position().add(0D, feetOffset, 0D));
        double verticalGap = Math.abs(player.getEyeY() - SourcePlayerReach.closestPoint(
                target.getBoundingBox().inflate(target.getPickRadius()), player.getEyePosition()).y);
        h.assertTrue(verticalGap <= hitboxDistance,
                "The requested distance must contain the fixture's vertical separation");
        double horizontalGap = Math.sqrt(Math.max(0D, hitboxDistance * hitboxDistance - verticalGap * verticalGap));
        target.setPos(player.position().add(0D, feetOffset, horizontalGap + halfWidth));
        double actual = player.getEyePosition().distanceTo(SourcePlayerReach.closestPoint(
                target.getBoundingBox().inflate(target.getPickRadius()), player.getEyePosition()));
        h.assertTrue(Math.abs(actual - hitboxDistance) < .00001D,
                "The fixture measures eyes to the target hitbox, independently of its center: " + actual);
        return new Target(target);
    }

    private static void vanillaAttack(ServerPlayer player, Zombie target) {
        target.invulnerableTime = 0;
        player.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(target, false));
    }

    private static Session session(GameTestHelper h) {
        UUID playerId = UUID.randomUUID();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(playerId,
                "bc-reach-" + playerId.toString().substring(0, 6)), false);
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 15, 2))));
            player.setNoGravity(true);
            player.setOnGround(true);
            player.setYRot(0F);
            player.setXRot(0F);
            for (int tick = 0; tick < 60; ++tick) player.tick();
            player.getFoodData().setFoodLevel(6);
            ((DualFoodData) player.getFoodData()).mite$setNutrition(0);
            player.setHealth(player.getMaxHealth());
            return new Session(player, channel);
        } catch (RuntimeException | Error failure) {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) != null)
                player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }

    private record Target(Zombie entity) implements AutoCloseable {
        @Override public void close() { entity.discard(); }
    }

    private record Session(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            try {
                if (player.server.getPlayerList().getPlayer(player.getUUID()) != null)
                    player.server.getPlayerList().remove(player);
            } finally { channel.finishAndReleaseAll(); }
        }
    }

    private static final class ReachSettings implements AutoCloseable {
        private final double multiplier = MiteConfig.WEAPON_REACH_MULTIPLIER.get();
        private final double reduction = MiteConfig.WEAPON_REACH_REDUCTION.get();

        private ReachSettings(double multiplier, double reduction) {
            MiteConfig.WEAPON_REACH_MULTIPLIER.set(multiplier);
            MiteConfig.WEAPON_REACH_REDUCTION.set(reduction);
            MiteConfig.refreshEnchantmentRules();
        }

        @Override public void close() {
            MiteConfig.WEAPON_REACH_MULTIPLIER.set(multiplier);
            MiteConfig.WEAPON_REACH_REDUCTION.set(reduction);
            MiteConfig.refreshEnchantmentRules();
        }
    }

    /** Loading the test holder never resolves optional Better Combat classes on an absent-mod run. */
    private static final class Loaded {
        private static double creativeLevelReach(ServerPlayer player) {
            double raw = net.bettercombat.logic.PlayerAttackHelper.getRangeForItem(player, player.getMainHandItem());
            return Math.min(5D, raw) + ProgressionRules.meleeReachBonusForLevel(player.experienceLevel);
        }

        private static void levelRequestAtDistance(GameTestHelper h, ServerPlayer player, double distance,
                boolean hit, String message) {
            try (Target target = target(h, player, distance, 0D)) {
                var hand = net.bettercombat.logic.PlayerAttackHelper.getCurrentAttack(player, 0);
                h.assertTrue(hand != null, "The level-aware fixture resolves an actual Better Combat hand");
                double raw = net.bettercombat.logic.PlayerAttackHelper.getRangeForItem(player, hand.itemStack());
                double move = hand.attack().rangeMultiplier();
                boolean[] permitted = new boolean[1];
                String[] diagnostic = new String[1];
                Runnable capture = () -> {
                    permitted[0] = Boolean.TRUE.equals(BetterCombatPlayerReach.canMelee(player, target.entity()));
                    Vec3 origin = BetterCombatPlayerReach.origin(player);
                    double actual = origin.distanceTo(SourcePlayerReach.closestPoint(target.entity().getBoundingBox(), origin));
                    diagnostic[0] = "raw=" + raw + " move=" + move + " level=" + player.experienceLevel
                            + " source=" + SourcePlayerReach.sourceMeleeReach(player, target.entity())
                            + " combat=" + BetterCombatPlayerReach.reach(player)
                            + " multiplier=" + MiteConfig.weaponReachMultiplier()
                            + " reduction=" + MiteConfig.weaponReachReduction() + " originDistance=" + actual;
                };
                BetterCombatPlayerReach.duringAttack(player, hand.itemStack(), move, capture);
                boolean beforePermitted = permitted[0];
                String before = diagnostic[0];
                request(player, target.entity(), 0);
                BetterCombatPlayerReach.duringAttack(player, hand.itemStack(), move, capture);
                String details = message + " before[" + before + "] after[" + diagnostic[0] + "]";
                h.assertValueEqual(beforePermitted, hit, "The captured move enforces the expected level-aware geometry: " + details);
                if (hit) h.assertTrue(target.entity().getHealth() < 200F, details);
                else h.assertValueEqual(target.entity().getHealth(), 200F, details);
            }
        }

        private static void assertSwordOrClubProfile(GameTestHelper h, ItemStack stack) {
            var profile = net.bettercombat.logic.WeaponRegistry.getAttributes(stack);
            h.assertTrue(profile != null, "The actual loaded Better Combat registry has an attack for " + stack);
            if (profile == null) return;
            h.assertTrue(Math.abs(net.bettercombat.logic.PlayerAttackHelper.combineAttackRange(profile, 3D) - 3D) < .00001D,
                    "The actual ordinary sword/club profile also has three-block reach: " + stack);
            var tool = io.github.ningmeng123.mite.item.EquipmentDurability.toolProfile(stack);
            if (tool == null || tool.family() != io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily.CLUB
                    && tool.family() != io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily.CUDGEL) return;
            var sword = net.bettercombat.logic.WeaponRegistry.getAttributes(new ItemStack(Items.IRON_SWORD));
            h.assertValueEqual(profile.attacks().length, sword.attacks().length, "Every club receives the ordinary sword's full combo");
            for (int index = 0; index < sword.attacks().length; ++index)
                h.assertValueEqual(profile.attacks()[index].animation(), sword.attacks()[index].animation(),
                        "Every actual club attack uses the sword animation at combo " + index + ": " + stack);
        }
        private static void nonVanillaValidation(GameTestHelper h) {
            boolean vanilla = net.bettercombat.network.Packets.C2S_AttackRequest.UseVanillaPacket;
            boolean validation = net.bettercombat.BetterCombatMod.config.server_target_range_validation;
            net.bettercombat.network.Packets.C2S_AttackRequest.UseVanillaPacket = false;
            net.bettercombat.BetterCombatMod.config.server_target_range_validation = true;
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                try (Target near = target(h, player, 4.1D); Target far = target(h, player, 4.4D)) {
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "The optional BC validation gate admits the source-configured 4.25 block sword reach");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "MITE still enforces the exact configured limit on the non-vanilla attack path");
                }
            } finally {
                net.bettercombat.network.Packets.C2S_AttackRequest.UseVanillaPacket = vanilla;
                net.bettercombat.BetterCombatMod.config.server_target_range_validation = validation;
            }
        }

        private static void request(ServerPlayer player, Zombie target, int combo) {
            target.invulnerableTime = 0;
            net.bettercombat.network.ServerNetwork.handleAttackRequest(
                    new net.bettercombat.network.Packets.C2S_AttackRequest(combo, false,
                            player.getInventory().selected, target.getId(), new int[] { target.getId() }),
                    player.server, player, player.connection);
            // executeIfPossible may queue even on the server thread while it is already running a task.
            // Keep the real packet, target and player alive until the preceding FIFO attack transaction completes.
            var completed = player.server.submit(() -> {});
            long deadline = System.nanoTime() + 5_000_000_000L;
            player.server.managedBlock(() -> completed.isDone() || System.nanoTime() >= deadline);
            if (!completed.isDone()) throw new AssertionError("The queued Better Combat attack did not finish within five seconds");
            completed.join();
        }

        private static void realSword(GameTestHelper h) {
            h.assertTrue(net.bettercombat.network.Packets.C2S_AttackRequest.UseVanillaPacket,
                    "The installed Better Combat uses its normal vanilla attack packet bridge");
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                double range = net.bettercombat.logic.PlayerAttackHelper.getRangeForItem(player, player.getMainHandItem());
                h.assertTrue(Math.abs(range - 3D) < .00001D, "The real sword profile has the default three block range");
                try (Target near = target(h, player, 2.15D);
                        Target capped = target(h, player, 2.35D);
                        Target far = target(h, player, range + .2D)) {
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "The real Better Combat request damages a target inside the source 2.25-block sword reach");
                    request(player, capped.entity(), 0);
                    h.assertValueEqual(capped.entity().getHealth(), 200F,
                            "The native sword profile cannot bypass the source 2.25-block boundary");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "A forged Better Combat request cannot hit outside the actual sword range");
                }
                try (Target downhillNear = target(h, player, 2.6D, 1.5D);
                        Target downhillFar = target(h, player, 2.9D, 1.5D);
                        Target uphillNear = target(h, player, 1.6D, -1.5D);
                        Target uphillFar = target(h, player, 2D, -1.5D)) {
                    h.assertTrue(Math.abs(SourcePlayerReach.sourceMeleeReach(player, downhillNear.entity()) - 2.75D) < .00001D,
                            "The source cap retains the downhill height advantage");
                    h.assertTrue(Math.abs(SourcePlayerReach.sourceMeleeReach(player, uphillNear.entity()) - 1.75D) < .00001D,
                            "The source cap retains the uphill height penalty");
                    request(player, downhillNear.entity(), 0);
                    h.assertTrue(downhillNear.entity().getHealth() < 200F,
                            "Better Combat retains the source downhill sword reach");
                    request(player, downhillFar.entity(), 0);
                    h.assertValueEqual(downhillFar.entity().getHealth(), 200F,
                            "The downhill height bonus still has its source limit");
                    request(player, uphillNear.entity(), 0);
                    h.assertTrue(uphillNear.entity().getHealth() < 200F,
                            "Better Combat can hit inside the source uphill sword reach");
                    request(player, uphillFar.entity(), 0);
                    h.assertValueEqual(uphillFar.entity().getHealth(), 200F,
                            "The native sword profile cannot bypass the source uphill penalty");
                }
                try (Target target = target(h, player, 2.4D)) {
                    vanillaAttack(player, target.entity());
                    h.assertValueEqual(target.entity().getHealth(), 200F,
                            "A normal vanilla packet cannot reuse the completed Better Combat request's reach");
                    player.attack(target.entity());
                    h.assertValueEqual(target.entity().getHealth(), 200F,
                            "A direct attack outside a Better Combat request still obeys the source sword guard");
                }
            }
        }

        private static ItemStack vibraniumWeapon(GameTestHelper h, String family) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mite", "simplyswords_vibranium_" + family);
            h.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "The screenshot's actual vibranium weapon is registered: " + id);
            return new ItemStack(BuiltInRegistries.ITEM.get(id));
        }

        private static void requestAtDistance(GameTestHelper h, ServerPlayer player, double distance,
                Double feetDifference, boolean hit, String message) {
            try (Target target = target(h, player, distance, feetDifference)) {
                request(player, target.entity(), 0);
                if (hit) h.assertTrue(target.entity().getHealth() < 200F, message);
                else h.assertValueEqual(target.entity().getHealth(), 200F, message);
            }
        }

        private static void simplySwordsWeapons(GameTestHelper h) {
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                for (String family : List.of("cutlass", "glaive")) {
                    ItemStack stack = vibraniumWeapon(h, family);
                    equip(player, stack, ItemStack.EMPTY);
                    var hand = net.bettercombat.logic.PlayerAttackHelper.getCurrentAttack(player, 0);
                    h.assertTrue(hand != null, "The actual vibranium " + family + " resolves a Better Combat attack");
                    double expectedRange = family.equals("glaive") ? 3.5D : 2.1D;
                    double nativeRange = net.bettercombat.logic.PlayerAttackHelper.getRangeForItem(player, hand.itemStack());
                    h.assertTrue(Math.abs(nativeRange - (family.equals("glaive") ? 3.5D : 2.75D)) < .00001D,
                            "The real inherited weapon data resolves its own range for " + family + ": " + nativeRange);
                    h.assertTrue(Math.abs(SourcePlayerReach.baseMeleeReach(stack,
                            player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)) - expectedRange) < .00001D,
                            "The balanced Simply Swords family distance caps its native profile: " + family);
                    requestAtDistance(h, player, 2D, 0D, true,
                            "Both screenshot weapons reach inside their balanced family range: " + family);
                    requestAtDistance(h, player, 2.9D, 0D, family.equals("glaive"),
                            "At 2.9 blocks only the longer glaive can cause real damage: " + family);
                    requestAtDistance(h, player, 3.3D, 0D, family.equals("glaive"),
                            "At 3.3 blocks only the glaive retains its real weapon reach: " + family);
                    requestAtDistance(h, player, 3.7D, 0D, false,
                            "Neither weapon can cause real damage beyond its own native range: " + family);
                }
            }
        }

        private static void simplySwordsConfiguredAndHeight(GameTestHelper h) {
            try (ReachSettings ignored = new ReachSettings(.75D, .25D); Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, vibraniumWeapon(h, "glaive"), ItemStack.EMPTY);
                requestAtDistance(h, player, 2.2D, 0D, true,
                        "The glaive's own base receives configuration once: 3.5 * 0.75 - 0.25 = 2.375");
                requestAtDistance(h, player, 2.5D, 0D, false,
                        "The configured glaive still rejects a target beyond 2.375 blocks");
            }
            try (ReachSettings ignored = new ReachSettings(1.5D, .25D); Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, vibraniumWeapon(h, "glaive"), ItemStack.EMPTY);
                requestAtDistance(h, player, 4.8D, 0D, true,
                        "A larger configuration starts with the glaive's native base: 3.5 * 1.5 - 0.25 = 5");
                requestAtDistance(h, player, 5.2D, 0D, false,
                        "The configured glaive does not receive its native bonus twice");
            }
            try (ReachSettings ignored = new ReachSettings(1D, 0D); Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, vibraniumWeapon(h, "glaive"), ItemStack.EMPTY);
                try (Target uphill = target(h, player, 2.8D, -1.5D);
                        Target downhill = target(h, player, 3.3D, 1.5D)) {
                    h.assertTrue(Math.abs(SourcePlayerReach.sourceMeleeReach(player, uphill.entity()) - 3D) < .00001D,
                            "The glaive retains the source half-block uphill penalty after its native base");
                    h.assertTrue(Math.abs(SourcePlayerReach.sourceMeleeReach(player, downhill.entity()) - 4D) < .00001D,
                            "The glaive retains the source half-block downhill advantage after its native base");
                    request(player, uphill.entity(), 0);
                    h.assertTrue(uphill.entity().getHealth() < 200F, "The uphill glaive hits inside its three block source limit");
                    request(player, downhill.entity(), 0);
                    h.assertTrue(downhill.entity().getHealth() < 200F, "The downhill glaive hits within its native 3.5 block range");
                }
                requestAtDistance(h, player, 3.2D, -1.5D, false,
                        "Native long-weapon data cannot bypass the source uphill penalty");
                requestAtDistance(h, player, 3.7D, 1.5D, false,
                        "The downhill source advantage cannot bypass the glaive's actual native range");
            }
        }

        private static void simplySwordsScaled(GameTestHelper h) {
            float previousInfluence = net.bettercombat.BetterCombatMod.config.entity_scale_attack_range_influence;
            net.bettercombat.BetterCombatMod.config.entity_scale_attack_range_influence = 1F;
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, vibraniumWeapon(h, "glaive"), ItemStack.EMPTY);
                player.getAttribute(Attributes.SCALE).setBaseValue(.5D);
                player.refreshDimensions();
                requestAtDistance(h, player, 1.6D, 0D, true,
                        "A half-size glaive user still hits inside the scaled native 1.75 block range");
                requestAtDistance(h, player, 1.9D, 0D, false,
                        "Preserving the glaive's own source base does not bypass Better Combat scale");
            } finally {
                net.bettercombat.BetterCombatMod.config.entity_scale_attack_range_influence = previousInfluence;
            }
        }

        private static void simplySwordsItemRange(GameTestHelper h) {
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                ItemStack stack = vibraniumWeapon(h, "glaive");
                var modifiers = ItemAttributeModifiers.builder();
                stack.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                        (attribute, modifier) -> modifiers.add(attribute, modifier, EquipmentSlotGroup.MAINHAND));
                modifiers.add(Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath("mite", "simplyswords_reach_override_test"),
                                2D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers.build());
                equip(player, stack, ItemStack.EMPTY);
                double effectiveRange = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
                h.assertTrue(Math.abs(effectiveRange - 5D) < .00001D,
                        "The real equipped item range component contributes exactly two blocks to the range attribute");
                h.assertTrue(Math.abs(net.bettercombat.logic.PlayerAttackHelper.getRangeForItem(player, stack) - 5D) < .00001D,
                        "Better Combat gives the explicit item range component precedence over the glaive bonus");
                h.assertTrue(Math.abs(SourcePlayerReach.baseMeleeReach(stack, effectiveRange) - 5D) < .00001D,
                        "The source range likewise overrides the glaive bonus instead of adding it again");
                requestAtDistance(h, player, 4.8D, 0D, true,
                        "The explicit component's five block range produces real damage");
                requestAtDistance(h, player, 5.2D, 0D, false,
                        "The glaive bonus cannot extend the explicit component range to 5.5 blocks");
                equip(player, vibraniumWeapon(h, "glaive"), ItemStack.EMPTY);
                requestAtDistance(h, player, 3.7D, 0D, false,
                        "Removing the extended item restores the ordinary glaive's 3.5 block limit");
            }
        }

        private static void invalidSimplySwordsMetadata(GameTestHelper h) {
            ItemStack stack = vibraniumWeapon(h, "glaive");
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            var previous = net.bettercombat.logic.WeaponRegistry.getAttributes(stack);
            var invalidProfiles = new net.bettercombat.api.WeaponAttributes[] {
                attributes(Double.NaN, attack(1F)),
                attributes(Double.POSITIVE_INFINITY, attack(1F)),
                attributes(Double.NEGATIVE_INFINITY, attack(1F)),
                new net.bettercombat.api.WeaponAttributes(0D, -3D, null, null, false,
                        "mite_zero_ss_range_test", new net.bettercombat.api.WeaponAttributes.Attack[] { attack(1F) }, null),
                new net.bettercombat.api.WeaponAttributes(0D, -4D, null, null, false,
                        "mite_negative_ss_range_test", new net.bettercombat.api.WeaponAttributes.Attack[] { attack(1F) }, null)
            };
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, stack, ItemStack.EMPTY);
                for (var invalid : invalidProfiles) {
                    net.bettercombat.logic.WeaponRegistry.register(id, invalid);
                    double parsed = net.bettercombat.logic.PlayerAttackHelper.getRangeForItem(player, stack);
                    h.assertTrue(!Double.isFinite(parsed) || parsed <= 0D,
                            "The test metadata actually resolves to a non-finite or non-positive weapon range: " + parsed);
                    for (boolean overlap : new boolean[] { false, true }) {
                        try (Target near = target(h, player, overlap ? 0D : 1D, 0D)) {
                            if (overlap) near.entity().setPos(player.position());
                            String context = parsed + (overlap ? " with an overlapping target" : " at one block");
                            h.assertValueEqual(SourcePlayerReach.sourceMeleeReach(player, near.entity()), 0D,
                                    "Invalid Simply Swords metadata clamps source reach to zero instead of borrowing ordinary sword reach: " + context);
                            vanillaAttack(player, near.entity());
                            h.assertValueEqual(near.entity().getHealth(), 200F,
                                    "A normal vanilla attack packet rejects invalid Simply Swords range metadata: " + context);
                            near.entity().invulnerableTime = 0;
                            player.attack(near.entity());
                            h.assertValueEqual(near.entity().getHealth(), 200F,
                                    "A direct player attack rejects invalid metadata outside any Better Combat request scope: " + context);
                            request(player, near.entity(), 0);
                            h.assertValueEqual(near.entity().getHealth(), 200F,
                                    "A real Better Combat request likewise rejects invalid Simply Swords range metadata: " + context);
                        }
                    }
                }
            } finally {
                net.bettercombat.logic.WeaponRegistry.register(id, previous);
            }
        }

        private static void reloadedFamilyRange(GameTestHelper h) {
            ItemStack stack = vibraniumWeapon(h, "greathammer");
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            var previous = net.bettercombat.logic.WeaponRegistry.getAttributes(stack);
            try {
                net.bettercombat.logic.WeaponRegistry.register(id, attributes(1.8D, attack(1F)));
                h.assertTrue(Math.abs(SourcePlayerReach.baseMeleeReach(stack, 3D) - 1.8D) < .00001D,
                        "A deliberately shortened third-party profile remains below the family budget");
                net.bettercombat.logic.WeaponRegistry.loadAttributes(h.getLevel().getServer().getResourceManager());
                var reloaded = net.bettercombat.logic.WeaponRegistry.getAttributes(stack);
                h.assertTrue(reloaded != null
                                && Math.abs(net.bettercombat.logic.PlayerAttackHelper.combineAttackRange(reloaded, 3D) - 4D) < .00001D,
                        "Actual resource loading restores the source four-block greathammer metadata");
                h.assertTrue(Math.abs(SourcePlayerReach.baseMeleeReach(stack, 3D) - 3.25D) < .00001D,
                        "Resource reload keeps the balanced heavy-weapon reach authoritative");
                h.assertTrue(Math.abs(SourcePlayerReach.baseMeleeReach(new ItemStack(
                        io.github.ningmeng123.mite.registry.MiteTools.get("mite:copper_scythe")), 3D) - 3.25D) < .00001D,
                        "Resource reload preserves the source scythe's explicit modern reach budget");
            } finally { net.bettercombat.logic.WeaponRegistry.register(id, previous); }
        }

        private static net.bettercombat.api.WeaponAttributes.Attack attack(float rangeMultiplier) {
            return attack(rangeMultiplier, (net.bettercombat.api.WeaponAttributes.Condition[]) null);
        }

        private static net.bettercombat.api.WeaponAttributes.Attack attack(float rangeMultiplier,
                net.bettercombat.api.WeaponAttributes.Condition... conditions) {
            return new net.bettercombat.api.WeaponAttributes.Attack(conditions,
                    net.bettercombat.api.WeaponAttributes.HitBoxShape.FORWARD_BOX,
                    1D, 1F, rangeMultiplier, 0D, .5D,
                    "bettercombat:one_handed_stab", null, null, List.of());
        }

        private static net.bettercombat.api.WeaponAttributes attributes(double range,
                net.bettercombat.api.WeaponAttributes.Attack... attacks) {
            return new net.bettercombat.api.WeaponAttributes(range, 0D, null, null, false,
                    "mite_player_reach_test", attacks, null);
        }

        private static void comboRange(GameTestHelper h) {
            Item item = Items.IRON_SWORD;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            var previous = net.bettercombat.logic.WeaponRegistry.getAttributes(new ItemStack(item));
            net.bettercombat.logic.WeaponRegistry.register(id, attributes(3D, attack(.6F), attack(1.25F)));
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(item), ItemStack.EMPTY);
                try (Target shortNear = target(h, player, 1.7D);
                        Target shortFar = target(h, player, 1.9D);
                        Target longNear = target(h, player, 2.15D);
                        Target longFar = target(h, player, 2.35D)) {
                    request(player, shortNear.entity(), 0);
                    h.assertTrue(shortNear.entity().getHealth() < 200F, "The shortened combo attack still hits inside 1.8 blocks");
                    request(player, shortFar.entity(), 0);
                    h.assertValueEqual(shortFar.entity().getHealth(), 200F,
                            "The 0.6 range multiplier is enforced for the requested short attack");
                    request(player, longNear.entity(), 1);
                    h.assertTrue(longNear.entity().getHealth() < 200F,
                            "The longer combo attack still hits inside the source 2.25-block sword reach");
                    request(player, longFar.entity(), 1);
                    h.assertValueEqual(longFar.entity().getHealth(), 200F,
                            "A long combo attack cannot exceed the source 2.25-block sword cap");
                    equip(player, ItemStack.EMPTY, ItemStack.EMPTY);
                    vanillaAttack(player, shortFar.entity());
                    h.assertValueEqual(shortFar.entity().getHealth(), 200F,
                            "Completing a Better Combat request leaves no extended range on a later empty-hand vanilla packet");
                }
            } finally { net.bettercombat.logic.WeaponRegistry.register(id, previous); }
        }

        private static void dualWielding(GameTestHelper h) {
            ResourceLocation daggerId = ResourceLocation.fromNamespaceAndPath("mite", "iron_dagger");
            ResourceLocation scytheId = ResourceLocation.fromNamespaceAndPath("mite", "iron_scythe");
            h.assertTrue(BuiltInRegistries.ITEM.containsKey(daggerId) && BuiltInRegistries.ITEM.containsKey(scytheId),
                    "The test uses the registered MITE dagger and scythe with distinct source reaches");
            Item main = BuiltInRegistries.ITEM.get(daggerId), off = BuiltInRegistries.ITEM.get(scytheId);
            ResourceLocation mainId = BuiltInRegistries.ITEM.getKey(main), offId = BuiltInRegistries.ITEM.getKey(off);
            var previousMain = net.bettercombat.logic.WeaponRegistry.getAttributes(new ItemStack(main));
            var previousOff = net.bettercombat.logic.WeaponRegistry.getAttributes(new ItemStack(off));
            net.bettercombat.logic.WeaponRegistry.register(mainId,
                    new net.bettercombat.api.WeaponAttributes(2D, 0D, null, null, false,
                            "mite_short_test", new net.bettercombat.api.WeaponAttributes.Attack[] { attack(1F) }, null));
            net.bettercombat.logic.WeaponRegistry.register(offId,
                    new net.bettercombat.api.WeaponAttributes(4D, 0D, null, null, false,
                            "mite_long_test", new net.bettercombat.api.WeaponAttributes.Attack[] {
                                attack(.25F, net.bettercombat.api.WeaponAttributes.Condition.DUAL_WIELDING_SAME_CATEGORY),
                                attack(1F, net.bettercombat.api.WeaponAttributes.Condition.OFF_HAND_ONLY) }, null));
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(main), new ItemStack(off));
                h.assertTrue(net.bettercombat.logic.PlayerAttackHelper.isDualWielding(player),
                        "The test uses Better Combat's actual dual wielding state");
                try (Target mainNear = target(h, player, 1.9D);
                        Target mainFar = target(h, player, 2.1D);
                        Target offNear = target(h, player, 3.1D);
                        Target offFar = target(h, player, 3.4D)) {
                    request(player, mainNear.entity(), 0);
                    h.assertTrue(mainNear.entity().getHealth() < 200F, "The main hand hits inside its own short range");
                    request(player, mainFar.entity(), 0);
                    h.assertValueEqual(mainFar.entity().getHealth(), 200F, "The main hand cannot borrow the offhand's long reach");
                    request(player, offNear.entity(), 1);
                    h.assertTrue(offNear.entity().getHealth() < 200F,
                            "The captured offhand condition survives the temporary swap and keeps the scythe's balanced 3.25 block reach");
                    request(player, offFar.entity(), 1);
                    h.assertValueEqual(offFar.entity().getHealth(), 200F, "The offhand cannot exceed the scythe's balanced 3.25 block cap");
                    request(player, mainFar.entity(), 2);
                    h.assertValueEqual(mainFar.entity().getHealth(), 200F,
                            "The following main-hand attack does not retain the previous offhand's reach");
                    h.assertTrue(player.getMainHandItem().is(main) && player.getOffhandItem().is(off),
                            "The real request restores both original held items after an offhand attack");
                }
            } finally {
                net.bettercombat.logic.WeaponRegistry.register(mainId, previousMain);
                net.bettercombat.logic.WeaponRegistry.register(offId, previousOff);
            }
        }

        private static void configuredRange(GameTestHelper h) {
            try (ReachSettings ignored = new ReachSettings(.75D, .25D); Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                try (Target near = target(h, player, 1.3D); Target far = target(h, player, 1.6D)) {
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "The configured sword range 2.25 * 0.75 - 0.25 still reaches 1.3 blocks");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "The configured 1.4375-block sword reach rejects 1.6 blocks");
                }
                player.setGameMode(GameType.CREATIVE);
                Vec3 position = player.position();
                player.tick();
                player.setPos(position);
                double creativeRange = Math.min(5D,
                        net.bettercombat.logic.PlayerAttackHelper.getRangeForItem(player, player.getMainHandItem()));
                try (Target near = target(h, player, creativeRange - .2D); Target far = target(h, player, creativeRange + .2D)) {
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "Creative Better Combat retains the smaller native or source range without survival reach settings");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "Creative Better Combat still enforces both native and source range limits");
                }
            }
            try (ReachSettings ignored = new ReachSettings(2D, .25D); Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                try (Target near = target(h, player, 4.1D); Target far = target(h, player, 4.4D)) {
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "Increasing the sword range to 2.25 * 2 - 0.25 survives the actual vanilla packet bridge");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "The increased range is still bounded by the configured 4.25 blocks");
                }
            }
        }

        private static void scaledRange(GameTestHelper h) {
            float previousInfluence = net.bettercombat.BetterCombatMod.config.entity_scale_attack_range_influence;
            net.bettercombat.BetterCombatMod.config.entity_scale_attack_range_influence = 1F;
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                player.getAttribute(Attributes.SCALE).setBaseValue(.5D);
                player.refreshDimensions();
                try (Target near = target(h, player, 1.4D, 0D); Target far = target(h, player, 1.7D, 0D)) {
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "A half-size player can hit inside Better Combat's scaled 1.5 block sword range");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "A half-size player cannot borrow the unscaled source sword range");
                }
                player.getAttribute(Attributes.SCALE).setBaseValue(1.5D);
                player.refreshDimensions();
                try (Target near = target(h, player, 2.15D, 0D); Target far = target(h, player, 2.35D, 0D)) {
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "A larger player's actual scale can still hit inside the source 2.25-block sword reach");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "The scaled native 4.5 block sword range cannot bypass the source 2.25-block cap");
                }
            } finally {
                net.bettercombat.BetterCombatMod.config.entity_scale_attack_range_influence = previousInfluence;
            }
        }

        private static void invalidRange(GameTestHelper h) {
            Item item = Items.END_ROD;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            var previous = net.bettercombat.logic.WeaponRegistry.getAttributes(new ItemStack(item));
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(item), ItemStack.EMPTY);
                for (double invalid : new double[] { Double.NaN, Double.POSITIVE_INFINITY, -3D }) {
                    net.bettercombat.logic.WeaponRegistry.register(id, attributes(invalid, attack(1F)));
                    try (Target target = target(h, player, 1D)) {
                        request(player, target.entity(), 0);
                        h.assertValueEqual(target.entity().getHealth(), 200F,
                                "A non-positive or non-finite weapon range cannot authorize a real attack: " + invalid);
                    }
                }
                for (float invalid : new float[] { Float.NaN, Float.POSITIVE_INFINITY, -1F, 0F }) {
                    net.bettercombat.logic.WeaponRegistry.register(id, attributes(3D, attack(invalid)));
                    try (Target target = target(h, player, 1D)) {
                        request(player, target.entity(), 0);
                        h.assertValueEqual(target.entity().getHealth(), 200F,
                                "A non-positive or non-finite attack range multiplier cannot authorize a real attack: " + invalid);
                    }
                }
            } finally { net.bettercombat.logic.WeaponRegistry.register(id, previous); }
        }

        private static void scopeCleanup(GameTestHelper h) {
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                var other = h.makeMockPlayer(GameType.SURVIVAL);
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                ItemStack sword = player.getMainHandItem();
                h.assertTrue(BetterCombatPlayerReach.reach(player) == null,
                        "No server range scope exists before the actual attack");
                RuntimeException expected = new IllegalStateException("intentional nested attack scope failure");
                BetterCombatPlayerReach.duringAttack(player, sword, 1D, () -> {
                    h.assertTrue(Math.abs(BetterCombatPlayerReach.reach(player) - 3D) < .00001D,
                            "The outer request scope resolves the captured player's sword");
                    h.assertTrue(BetterCombatPlayerReach.reach(other) == null,
                            "An active scope never grants its range to another player on the same thread");
                    try {
                        BetterCombatPlayerReach.duringAttack(other, sword, .5D, () -> {
                            h.assertTrue(BetterCombatPlayerReach.reach(player) == null,
                                    "A nested request remains bound to its own player");
                            h.assertTrue(Math.abs(BetterCombatPlayerReach.reach(other) - 1.5D) < .00001D,
                                    "The nested player's captured move has its own range");
                            throw expected;
                        });
                        h.fail("The nested scope must propagate the actual attack failure");
                    } catch (RuntimeException failure) {
                        if (failure != expected) throw failure;
                    }
                    h.assertTrue(Math.abs(BetterCombatPlayerReach.reach(player) - 3D) < .00001D,
                            "A failed nested attack restores the outer player's previous range");
                    h.assertTrue(BetterCombatPlayerReach.reach(other) == null,
                            "The failed nested player's range is removed");
                });
                h.assertTrue(BetterCombatPlayerReach.reach(player) == null,
                        "The completed outer request removes the thread-local range");
                try {
                    BetterCombatPlayerReach.duringAttack(player, sword, 1D, () -> { throw expected; });
                    h.fail("The outer scope must propagate the actual attack failure");
                } catch (RuntimeException failure) {
                    if (failure != expected) throw failure;
                }
                h.assertTrue(BetterCombatPlayerReach.reach(player) == null,
                        "A failed outer request also removes its range");
            }
        }

        private static void zeroRange(GameTestHelper h) {
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                try (Target overlap = target(h, player, 0D)) {
                    request(player, overlap.entity(), 0);
                    h.assertValueEqual(overlap.entity().getHealth(), 200F,
                            "A zero configured range disables damage even when nearest collision distance is zero");
                }
            }
        }

        private static void itemRange(GameTestHelper h) {
            Item item = Items.PRISMARINE_SHARD;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            var previous = net.bettercombat.logic.WeaponRegistry.getAttributes(new ItemStack(item));
            net.bettercombat.logic.WeaponRegistry.register(id,
                    new net.bettercombat.api.WeaponAttributes(0D, 3D, null, null, false,
                            "mite_item_range_test", new net.bettercombat.api.WeaponAttributes.Attack[] { attack(1F) }, null));
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                ItemStack stack = new ItemStack(item);
                stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().add(
                        Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath("mite", "player_reach_test"),
                                2D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build());
                equip(player, stack, ItemStack.EMPTY);
                double actual = net.bettercombat.logic.PlayerAttackHelper.getRangeForItem(player, stack);
                h.assertTrue(Math.abs(actual - 5D) < .00001D,
                        "Better Combat's actual item-range precedence chooses the attribute's five blocks: " + actual);
                try (Target near = target(h, player, 3.3D); Target far = target(h, player, 3.7D)) {
                    h.assertTrue(Math.abs(SourcePlayerReach.sourceMeleeReach(player, near.entity()) - 3.5D) < .00001D,
                            "The source cap preserves the explicit external attribute on top of the source base reach");
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "The actual attack preserves explicit ENTITY_INTERACTION_RANGE item components within the source cap");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "The weapon profile's native range bonus cannot bypass the source cap or double the explicit attribute");
                }
            } finally { net.bettercombat.logic.WeaponRegistry.register(id, previous); }
        }
    }
}

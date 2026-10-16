package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Mob;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real native health transactions, with literal expectations independent of the production balance table. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ModernArmorDefenseGameTests {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final String[] PARTS = {"boots", "leggings", "chestplate", "helmet"};
    private ModernArmorDefenseGameTests() {}

    @GameTest(template = "empty")
    public static void plateStagesUseModernArmorAndToughnessForPlayersAndZombies(GameTestHelper helper) {
        String[] materials = {"leather", "rusted_iron", "gold", "copper", "silver", "iron", "ancient_metal", "mithril", "netherite", "adamantium"};
        double[] armor = {7, 9, 11, 13, 13, 15, 18, 22, 20, 24};
        double[] toughness = {0, 0, 0, 0, 0, 0, 4, 12, 12, 16};
        // Native formula: raw20 * (1 - clamp(A - 20/(2+T/4), A*.2, 20)/25).
        float[] loss = {18.88F, 18.56F, 18.24F, 17.6F, 17.6F, 16F, 10.933333F, 5.6F, 7.2F, 4F};
        try (Session session = newPlayer(helper)) {
            var player = session.player();
            var zombie = mob(helper, EntityType.ZOMBIE);
            try {
                for (int index = 0; index < materials.length; index++) {
                    equip(player, materials[index], false);
                    equip(zombie, materials[index], false);
                    refresh(player); refresh(zombie);
                    close(helper, player.getAttributeValue(Attributes.ARMOR), armor[index], "Player armor for " + materials[index]);
                    close(helper, zombie.getAttributeValue(Attributes.ARMOR), armor[index], "Zombie armor for " + materials[index]);
                    close(helper, player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), toughness[index], "Player toughness for " + materials[index]);
                    close(helper, zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS), toughness[index], "Zombie toughness for " + materials[index]);
                    close(helper, hit(player, player.damageSources().cactus(), 20F), loss[index], "Actual player damage for " + materials[index]);
                    close(helper, hit(zombie, zombie.damageSources().cactus(), 20F), loss[index], "Actual zombie damage for " + materials[index]);
                    for (var slot : SLOTS) helper.assertValueEqual(zombie.getItemBySlot(slot).getDamageValue(), 0,
                            "Ordinary NPC hits do not invent armor wear");
                }
            } finally { zombie.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eachChainMaterialHasLowerArmorAndToughnessThanItsPlate(GameTestHelper helper) {
        String[] materials = {"rusted_iron", "gold", "copper", "silver", "iron", "ancient_metal", "mithril", "adamantium"};
        double[] armor = {7, 7, 9, 9, 12, 14, 16, 18};
        double[] toughness = {0, 0, 0, 0, 0, 2, 6, 8};
        try (Session session = newPlayer(helper)) {
            for (int index = 0; index < materials.length; index++) {
                equip(session.player(), materials[index], true); refresh(session.player());
                close(helper, session.player().getAttributeValue(Attributes.ARMOR), armor[index], "Chain armor " + materials[index]);
                close(helper, session.player().getAttributeValue(Attributes.ARMOR_TOUGHNESS), toughness[index], "Chain toughness " + materials[index]);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oldWornNpcArmorKeepsFullDefenseAtSeventyNinetyNineAndLastPoint(GameTestHelper helper) {
        var zombie = mob(helper, EntityType.ZOMBIE);
        try {
            for (int wear : new int[]{70, 99, 100}) {
                equip(zombie, "adamantium", false);
                for (var slot : SLOTS) {
                    ItemStack piece = zombie.getItemBySlot(slot);
                    piece.setDamageValue(wear == 100 ? piece.getMaxDamage() - 1 : piece.getMaxDamage() * wear / 100);
                }
                refresh(zombie);
                close(helper, zombie.getAttributeValue(Attributes.ARMOR), 24, "NPC armor must not vanish with generated wear " + wear);
                close(helper, zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 16, "NPC toughness must not vanish with generated wear " + wear);
                close(helper, hit(zombie, zombie.damageSources().cactus(), 20F), 4, "Worn NPC armor actually reduces health damage " + wear);
            }
        } finally { zombie.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wornNpcArmorRestoresFromNativeEntityNbtWithoutLosingAttributes(GameTestHelper helper) {
        var original = mob(helper, EntityType.ZOMBIE);
        var restored = mob(helper, EntityType.ZOMBIE);
        try {
            equip(original, "mithril", false);
            for (var slot : SLOTS) {
                ItemStack piece = original.getItemBySlot(slot); piece.setDamageValue(piece.getMaxDamage() - 1);
            }
            refresh(original);
            CompoundTag saved = original.saveWithoutId(new CompoundTag());
            restored.load(saved); refresh(restored);
            close(helper, restored.getAttributeValue(Attributes.ARMOR), 22, "Reloaded worn mob retains full modern armor");
            close(helper, restored.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 12, "Reloaded worn mob retains full modern toughness");
            close(helper, hit(restored, restored.damageSources().cactus(), 20F), 5.6, "Reloaded armor mitigates a real transaction");
        } finally { original.discard(); restored.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theSameWornArmorChangesDefenseWhenTransferredBetweenNpcAndPlayer(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player();
            var zombie = mob(helper, EntityType.ZOMBIE);
            ItemStack chest = new ItemStack(MiteArmorContent.get("mite:mithril_chestplate"));
            helper.assertValueEqual(chest.getMaxDamage(), 1024, "Transfer uses the same average-quality chestpiece");
            try {
                for (int damage : new int[]{768, 1023}) {
                    double playerArmor = damage == 768 ? 4.5D : 0D;
                    double playerToughness = damage == 768 ? 1.5D : 0D;
                    chest.setDamageValue(damage);
                    zombie.setItemSlot(EquipmentSlot.CHEST, chest); refresh(zombie);
                    helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.CHEST) == chest, "NPC owns the original stack identity");
                    close(helper, zombie.getAttributeValue(Attributes.ARMOR), 9D, "NPC uses full armor before transfer at damage " + damage);
                    close(helper, zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 3D, "NPC uses full toughness before transfer at damage " + damage);
                    double[] outsideAttributes = {0D, 0D};
                    chest.forEachModifier(EquipmentSlot.CHEST, (attribute, modifier) -> {
                        if (attribute.equals(Attributes.ARMOR)) outsideAttributes[0] += modifier.amount();
                        if (attribute.equals(Attributes.ARMOR_TOUGHNESS)) outsideAttributes[1] += modifier.amount();
                    });
                    close(helper, outsideAttributes[0], playerArmor, "NPC equipment scope cannot leak into later standalone item queries");
                    close(helper, outsideAttributes[1], playerToughness, "NPC toughness scope cannot leak into later standalone item queries");

                    zombie.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); refresh(zombie);
                    close(helper, zombie.getAttributeValue(Attributes.ARMOR), 0D, "NPC unequip removes the full armor modifier");
                    close(helper, zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 0D, "NPC unequip removes the full toughness modifier");
                    player.setItemSlot(EquipmentSlot.CHEST, chest); refresh(player);
                    helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST) == chest, "Player receives exactly the same stack without copying");
                    close(helper, player.getAttributeValue(Attributes.ARMOR), playerArmor, "Player applies its own wear factor at damage " + damage);
                    close(helper, player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), playerToughness, "Player applies its own toughness wear factor at damage " + damage);

                    player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); refresh(player);
                    close(helper, player.getAttributeValue(Attributes.ARMOR), 0D, "Player unequip removes its own scaled armor modifier");
                    close(helper, player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 0D, "Player unequip removes its own scaled toughness modifier");
                    zombie.setItemSlot(EquipmentSlot.CHEST, chest); refresh(zombie);
                    helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.CHEST) == chest, "Return transfer preserves the original stack identity");
                    close(helper, zombie.getAttributeValue(Attributes.ARMOR), 9D, "Returned NPC armor is full rather than cached from the player");
                    close(helper, zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 3D, "Returned NPC toughness is full rather than cached from the player");
                    helper.assertValueEqual(chest.getDamageValue(), damage, "Transfers do not repair or damage the stack to obtain the result");
                }
            } finally { zombie.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void skeletonAndAddedUndeadUseActualEquippedAttributes(GameTestHelper helper) {
        var skeleton = mob(helper, EntityType.SKELETON);
        var guardian = MiteMobs.type(MobKind.LONGDEAD_GUARDIAN).create(helper.getLevel());
        helper.assertTrue(guardian != null, "Added guardian fixture exists");
        prepare(helper, guardian);
        try {
            equip(skeleton, "iron", false); equip(guardian, "iron", false);
            refresh(skeleton); refresh(guardian);
            // R196Skeleton.hurt explicitly rejects cactus; use a real, unenchanted non-silver player weapon.
            var attacker = helper.makeMockPlayer(GameType.SURVIVAL);
            attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            var source = guardian.damageSources().playerAttack(attacker);
            helper.assertTrue(source.getWeaponItem() != null && source.getWeaponItem().is(Items.IRON_SWORD)
                    && !source.getWeaponItem().isEnchanted(), "Independent melee fixture has no silver or enchantment multiplier");
            close(helper, hit(skeleton, source, 20F), 16, "Native skeleton uses iron15 instead of profile flat8");
            close(helper, ((R196Mob)guardian).naturalDefense(source), 2,
                    "Guardian retains its independent source natural defense");
            // Natural2 first: raw18; iron15, toughness0 => effective6 => 18*.76.
            close(helper, hit(guardian, source, 20F), 13.68,
                    "Added guardian combines source natural defense and modern armor once");
        } finally { skeleton.discard(); guardian.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void toughnessReducesLargeHitsAndNetheriteKeepsNativeKnockbackResistance(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player();
            equip(player, "netherite", false); refresh(player);
            // Native formula: effective armor = 20 - 40/(2+12/4) = 12; raw40 * (1-12/25) = 20.8.
            close(helper, hit(player, player.damageSources().cactus(), 40F), 20.8, "Netherite20/toughness12 protects a large hit");
            equip(player, "netherite", false); refresh(player);
            var suppress = ResourceLocation.fromNamespaceAndPath("mite", "gametest_toughness_control");
            player.getAttribute(Attributes.ARMOR_TOUGHNESS).addTransientModifier(
                    new AttributeModifier(suppress, -12D, AttributeModifier.Operation.ADD_VALUE));
            try { close(helper, hit(player, player.damageSources().cactus(), 40F), 33.6,
                    "Same armor without toughness suffers a larger real hit"); }
            finally { player.getAttribute(Attributes.ARMOR_TOUGHNESS).removeModifier(suppress); }
            equip(player, "netherite", false); refresh(player);
            close(helper, player.getAttributeValue(Attributes.ARMOR), 20, "Netherite keeps native armor20");
            close(helper, player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 12, "Netherite keeps native toughness12");
            close(helper, player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), .4, "Netherite keeps four native .1 knockback pieces");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void protectionUsesNativeEpfWithoutIncreasingArmorTwice(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player();
            var zombie = mob(helper, EntityType.ZOMBIE);
            try {
                for (LivingEntity target : new LivingEntity[]{player, zombie}) {
                    equip(target, "iron", false);
                    enchantSet(helper, target, Enchantments.PROTECTION, 4); refresh(target);
                    close(helper, target.getAttributeValue(Attributes.ARMOR), 15, "Protection does not also multiply ARMOR");
                    // Iron20hit =>16, then EPF16 =>16*.36.
                    close(helper, hit(target, target.damageSources().cactus(), 20F), 5.76, "Native ProtectionIV applies once to actual health");
                }
            } finally { zombie.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void projectileAndGeneralProtectionRespectTheNativeTwentyEpfCap(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player(); equip(player, "iron", false);
            enchantSet(helper, player, Enchantments.PROTECTION, 4);
            enchantSet(helper, player, Enchantments.PROJECTILE_PROTECTION, 4); refresh(player);
            var arrow = new DamageSource(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.ARROW));
            // Raw25 =>22 after iron15; the command-combined EPF is capped at20 =>22*.2.
            close(helper, hit(player, arrow, 25F), 4.4, "Combined protection cannot exceed native EPF20");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void resistanceIsTheNativeTwentyPercentAfterArmorForPlayersAndNpcs(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var zombie = mob(helper, EntityType.ZOMBIE);
            try {
                for (LivingEntity target : new LivingEntity[]{session.player(), zombie}) {
                    equip(target, "iron", false); refresh(target);
                    target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
                    close(helper, hit(target, target.damageSources().cactus(), 20F), 12.8,
                            "Raw20 =>iron16 =>ResistanceI12.8 without source flat5 or duplicate percentage");
                }
            } finally { zombie.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fireResistanceGivesNativeFireAndLavaImmunityWithManagedArmor(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var zombie = mob(helper, EntityType.ZOMBIE);
            try {
                for (LivingEntity target : new LivingEntity[]{session.player(), zombie}) {
                    equip(target, "iron", false); refresh(target);
                    target.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
                    close(helper, hit(target, target.damageSources().inFire(), 14F), 0, "Managed armor preserves native fire resistance immunity");
                    close(helper, hit(target, target.damageSources().lava(), 14F), 0, "Managed armor preserves native lava resistance immunity");
                }
            } finally { zombie.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breachUsesTheNativeArmorEffectivenessFunctionAndLeavesWearBudgetUnchanged(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player(); equip(player, "iron", false); refresh(player);
            // GameTest disables PvP; a native zombie supplies the weapon without player-versus-player rejection.
            var attacker = mob(helper, EntityType.ZOMBIE);
            try {
                ItemStack mace = new ItemStack(Items.MACE);
                mace.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.BREACH), 2);
                attacker.setItemSlot(EquipmentSlot.MAINHAND, mace);
                var source = new DamageSource(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(DamageTypes.PLAYER_ATTACK), attacker);
                helper.assertTrue(source.getWeaponItem() != null && source.getWeaponItem().is(Items.MACE), "Native source supplies enchanted mace");
                // Raw8: iron15 =>effective11/25=.44; BreachII -.30 => .14 =>8*.86.
                close(helper, hit(player, source, 8F), 6.88, "Breach applies to modern mitigation once");
                int wear = 0; for (var slot : SLOTS) wear += player.getItemBySlot(slot).getDamageValue();
                helper.assertValueEqual(wear, 8, "Native armor effectiveness does not alter the retained R196 durability budget");
            } finally { attacker.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mixedUnprofiledArmorAndAdditionalArmorAttributesReduceActualDamage(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var zombie = mob(helper, EntityType.ZOMBIE);
            try {
                for (LivingEntity target : new LivingEntity[]{session.player(), zombie}) {
                    target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                    target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.TURTLE_HELMET));
                    target.getAttribute(Attributes.ARMOR).addTransientModifier(new AttributeModifier(
                            ResourceLocation.fromNamespaceAndPath("mite", "gametest_additional_armor"), 4D, AttributeModifier.Operation.ADD_VALUE));
                    refresh(target);
                    close(helper, target.getAttributeValue(Attributes.ARMOR), 12, "Iron6+turtle2+external4 all contribute");
                    close(helper, hit(target, target.damageSources().cactus(), 20F), 18.08,
                            "Managed NPCs and players use real attributes for unknown-profile mixed armor");
                }
            } finally { zombie.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledWearAndMidHitRemovalDoNotApplyArmorOrResistanceTwice(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player();
            ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
            player.setItemSlot(EquipmentSlot.CHEST, chest); refresh(player);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
            int[] calls = {0};
            Consumer<ArmorHurtEvent> listener = event -> {
                if (event.getEntity() == player) {
                    calls[0]++; event.setCanceled(true); player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                }
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                // The native equipment attribute refresh occurs on the next living tick: armor6 remains for this transaction.
                close(helper, hit(player, player.damageSources().cactus(), 10F), 7.616,
                        "Single chest6 gives9.52, then native ResistanceI gives7.616");
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
            helper.assertValueEqual(calls[0], 1, "One cancellable armor event per real hit");
            helper.assertValueEqual(chest.getDamageValue(), 0, "Canceling the armor event prevents wear");
            refresh(player);
            close(helper, player.getAttributeValue(Attributes.ARMOR), 0, "Removal refreshes native attributes");
            close(helper, hit(player, player.damageSources().cactus(), 2F), 1.6, "Following bare hit has one native resistance reduction");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void featherFallingKeepsNativePercentageAndArmorBypassDoesNotAddWear(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player(); equip(player, "iron", false);
            player.getItemBySlot(EquipmentSlot.FEET).enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.FEATHER_FALLING), 4); refresh(player);
            close(helper, hit(player, player.damageSources().fall(), 16F), 8.32, "FeatherFallingIV gives EPF12 and 48percent native fall reduction");
            int wear = 0; for (var slot : SLOTS) wear += player.getItemBySlot(slot).getDamageValue();
            helper.assertValueEqual(wear, 0, "Native armor-bypassing fall does not invent ordinary armor wear");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void smallHitsAndAbsorptionHaveNoArtificialNpcOnePointFloor(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var zombie = mob(helper, EntityType.ZOMBIE);
            try {
                for (LivingEntity target : new LivingEntity[]{session.player(), zombie}) {
                    equip(target, "adamantium", false); refresh(target);
                    target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(100D);
                    target.setAbsorptionAmount(0F);
                    close(helper, hit(target, target.damageSources().cactus(), .25F), .05,
                            "A small hit obeys modern eighty-percent armor cap without a one-point floor");
                    target.setAbsorptionAmount(4F);
                    close(helper, target.getAbsorptionAmount(), 4D, "Absorption fixture is not clamped by the native maximum");
                    close(helper, hit(target, target.damageSources().cactus(), .25F), 0,
                            "Native absorption can completely absorb a mitigated NPC or player hit");
                    close(helper, target.getAbsorptionAmount(), 3.95,
                            "Native absorption consumes the actual mitigated damage once");
                }
            } finally { zombie.discard(); }
        }
        helper.succeed();
    }

    private static void equip(LivingEntity owner, String material, boolean chain) {
        for (int slot = 0; slot < 4; slot++) {
            String stem;
            if (chain) stem = switch (material) {
                case "iron" -> "minecraft:chainmail_";
                case "gold" -> "mite:golden_chain_";
                case "ancient_metal" -> "mite:ancient_metal_chainmail_";
                default -> "mite:" + material + "_chain_";
            };
            else stem = switch (material) {
                case "leather", "iron", "netherite" -> "minecraft:" + material + "_";
                case "gold" -> "minecraft:golden_";
                default -> "mite:" + material + "_";
            };
            owner.setItemSlot(SLOTS[slot], new ItemStack(MiteArmorContent.get(stem + PARTS[slot])));
        }
    }
    private static void enchantSet(GameTestHelper helper, LivingEntity owner,
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> enchantment, int level) {
        var holder = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(enchantment);
        for (var slot : SLOTS) owner.getItemBySlot(slot).enchant(holder, level);
    }
    private static void refresh(LivingEntity entity) { if (entity instanceof ServerPlayer player) player.doTick(); else entity.tick(); }
    private static float hit(LivingEntity entity, DamageSource source, float raw) {
        entity.setHealth(entity.getMaxHealth()); entity.invulnerableTime = 0;
        float before = entity.getHealth(); entity.hurt(source, raw); return before - entity.getHealth();
    }
    private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type) {
        T mob = type.create(helper.getLevel()); helper.assertTrue(mob != null, "Native fixture creates " + type);
        prepare(helper, mob); return mob;
    }
    private static void prepare(GameTestHelper helper, Mob mob) {
        mob.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 12, 3))));
        mob.setNoAi(true); mob.setNoGravity(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D); mob.setHealth(200F);
    }
    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .0001D, message + ": expected " + expected + ", got " + actual);
    }
    private static Session newPlayer(GameTestHelper helper) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "modern-armor-test"), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 12, 2))));
            player.setNoGravity(true); player.setOnGround(true);
            PlayerExperience.setTotal(player, 0); player.getFoodData().setFoodLevel(6);
            player.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("mite", "gametest_armor_health"), 194D, AttributeModifier.Operation.ADD_VALUE));
            player.setHealth(player.getMaxHealth());
            for (int tick = 0; tick < 60; tick++) player.tick();
            return new Session(player, channel);
        } catch (RuntimeException | Error failure) {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll(); throw failure;
        }
    }
    private record Session(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            try { if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player); }
            finally { channel.finishAndReleaseAll(); }
        }
    }
}

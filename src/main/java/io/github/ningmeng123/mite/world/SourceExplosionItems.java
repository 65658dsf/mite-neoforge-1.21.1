package io.github.ningmeng123.mite.world;

import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.MiteShards;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** ss.handleExplosion runs once before native entity damage/knockback, with no line-of-sight attenuation. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class SourceExplosionItems {
    public record Profile(int legacyId, boolean immune, Float blockResistance) {}
    private static final Map<ResourceLocation, Profile> PROFILES = load();
    private SourceExplosionItems() {}
    public static Profile profile(ItemStack stack) { return PROFILES.get(BuiltInRegistries.ITEM.getKey(stack.getItem())); }
    public static boolean immune(ItemStack stack) {
        Profile p = profile(stack);
        return !stack.isDamageableItem() && p != null && p.immune();
    }
    public static float resistance(ItemStack stack, float fallback) {
        Profile p = profile(stack); return p != null && p.blockResistance() != null ? p.blockResistance() : fallback;
    }
    @SubscribeEvent public static void detonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        var iterator = event.getAffectedEntities().iterator();
        while (iterator.hasNext()) {
            var entity = iterator.next();
            if (entity instanceof ItemEntity item && handle(item, event.getExplosion())) iterator.remove();
        }
    }
    public static boolean handle(ItemEntity entity, Explosion explosion) {
        if (!(entity.level() instanceof ServerLevel level) || entity.isRemoved()) return false;
        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || profile(stack) == null) return false; // Unmapped third-party items retain their own native policy.
        motion(entity, explosion);
        if (immune(stack)) return true;
        Vec3 offset = entity.position().subtract(explosion.center());
        double squared = SourceExplosionRules.flattenedDistanceSquared(offset.x, offset.y, offset.z);
        float force = SourceExplosionRules.force(explosion.radius(), squared);
        if (profile(stack).legacyId() < 256 && stack.getItem() instanceof BlockItem blockItem) {
            var state = blockItem.getBlock().defaultBlockState();
            if (force * 2F < resistance(stack, state.getExplosionResistance(level, entity.blockPosition(), explosion))) return true;
            for (int i = 0; i < stack.getCount(); i++) {
                var builder = new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, entity.position())
                        .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                        .withParameter(LootContextParams.EXPLOSION_RADIUS, explosion.radius());
                // Carried blocks use their own explosion loot without replacing the block underneath the entity.
                for (ItemStack drop : state.getDrops(builder)) spawn(entity, drop, explosion);
            }
        } else if (stack.isDamageableItem()) {
            float scale = EquipmentDurability.armorProfile(stack) != null || stack.getItem() instanceof ArmorItem ? 2F
                    : EquipmentDurability.toolProfile(stack) != null ? 100F : 1F;
            stack.hurtAndBreak(SourceExplosionRules.equipmentWear(explosion.radius(), squared, scale), level, null, ignored -> {});
            if (stack.isEmpty()) entity.discard();
            else {
                entity.setItem(stack.copy());
                ((io.github.ningmeng123.mite.mixin.ItemCorrosionAccess)(Object)entity).mite$chemicalHealth(
                        Math.max(1, 5*(stack.getMaxDamage()-stack.getDamageValue())/stack.getMaxDamage()));
            }
            return true;
        } else {
            if (force < .2F) return true;
            if (stack.getItem() instanceof BlockItem blockItem) {
                var builder = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, entity.position())
                        .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                        .withParameter(LootContextParams.EXPLOSION_RADIUS, explosion.radius());
                for (ItemStack drop : blockItem.getBlock().defaultBlockState().getDrops(builder)) spawn(entity, drop, explosion);
            }
            Item fragment = stack.is(Items.FLINT) ? MiteItems.FLINT_CHIP.get()
                    : stack.is(Items.EMERALD) ? MiteShards.EMERALD_SHARD.get()
                    : stack.is(Items.DIAMOND) ? MiteShards.DIAMOND_SHARD.get() : null;
            if (fragment != null) for (int i = 0; i < stack.getCount(); i++) spawn(entity, new ItemStack(fragment), explosion);
        }
        stack.getItem().onDestroyed(entity, level.damageSources().explosion(explosion));
        io.github.ningmeng123.mite.advancement.ReferencedBooks.release(level,stack);
        entity.discard();
        return true;
    }
    private static void spawn(ItemEntity origin, ItemStack stack, Explosion explosion) {
        if (stack.isEmpty()) return;
        var item = new ItemEntity(origin.level(), origin.getX(), origin.getY(), origin.getZ(), stack);
        item.setDefaultPickUpDelay(); motion(item, explosion); origin.level().addFreshEntity(item);
    }
    public static void motion(ItemEntity entity, Explosion explosion) {
        Vec3 d = entity.position().subtract(explosion.center());
        double squared = Math.max(1D, SourceExplosionRules.flattenedDistanceSquared(d.x, d.y, d.z));
        float force = SourceExplosionRules.force(explosion.radius(), squared);
        var random = entity.getRandom();
        entity.setDeltaMovement(d.x*force*.175F*(.8F+random.nextFloat()*.4F),
                .4F*Math.sqrt((double)explosion.radius()/Math.pow(squared,.75D))*(.8F+random.nextFloat()*.4F),
                d.z*force*.175F*(.8F+random.nextFloat()*.4F));
        entity.hasImpulse = true;
    }
    private static Map<ResourceLocation, Profile> load() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(SourceExplosionItems.class.getResourceAsStream(
                "/mite/explosion-materials.json")), StandardCharsets.UTF_8)) {
            Map<ResourceLocation, Profile> map = new HashMap<>();
            JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("items").forEach(e -> {
                var row = e.getAsJsonObject();
                map.put(ResourceLocation.parse(row.get("item").getAsString()), new Profile(row.get("legacy_id").getAsInt(),
                        row.get("immune").getAsBoolean(), row.has("block_resistance") ? row.get("block_resistance").getAsFloat() : null));
            });
            return Map.copyOf(map);
        } catch (Exception error) { throw new IllegalStateException("Cannot read source explosion item profiles", error); }
    }
}

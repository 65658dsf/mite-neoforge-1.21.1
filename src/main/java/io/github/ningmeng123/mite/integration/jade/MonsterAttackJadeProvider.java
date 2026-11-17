package io.github.ningmeng123.mite.integration.jade;

import io.github.ningmeng123.mite.entity.MonsterDayScaling;
import io.github.ningmeng123.mite.entity.MonsterMeleeScaling;
import io.github.ningmeng123.mite.entity.R196Cube;
import java.math.BigDecimal;
import java.math.RoundingMode;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Optional server-authoritative melee damage display. @author NingMeng123 */
public enum MonsterAttackJadeProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mite", "monster_attack");
    public static final String DATA_KEY = "mite:monster_attack";

    @Override public ResourceLocation getUid() { return UID; }
    @Override public int getDefaultPriority() { return 100; }

    private static boolean supports(Entity entity) {
        return entity instanceof Mob mob && mob.isAlive()
                && (mob.getType().getCategory() == MobCategory.MONSTER || MonsterDayScaling.handles(mob));
    }

    @Override public boolean shouldRequestData(EntityAccessor accessor) {
        // Attack damage is usually not synchronized to clients, so request by entity kind.
        return supports(accessor.getEntity());
    }

    @Override public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        data.remove(DATA_KEY);
        if (!supports(accessor.getEntity())) return;
        Mob mob = (Mob) accessor.getEntity();
        if (!(mob.level() instanceof ServerLevel) || mob.getAttribute(Attributes.ATTACK_DAMAGE) == null) return;
        double damage = attackDamage(mob);
        if (!Double.isFinite(damage) || damage < 0D) return;
        CompoundTag payload = new CompoundTag();
        payload.putUUID("entity", mob.getUUID());
        payload.putDouble("attack_damage", damage);
        data.put(DATA_KEY, payload);
    }

    private static double attackDamage(Mob mob) {
        if (mob instanceof Slime slime) {
            if (!(slime instanceof R196Cube)) return R196Cube.vanillaContactDamage(slime);
            return mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
        }
        try (MonsterMeleeScaling.Scope ignored = MonsterMeleeScaling.enter(mob)) {
            return MonsterMeleeScaling.attackValue(mob, mob.getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
    }

    @Nullable public static Double snapshot(EntityAccessor accessor) {
        if (!supports(accessor.getEntity()) || !accessor.getServerData().contains(DATA_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag payload = accessor.getServerData().getCompound(DATA_KEY);
        if (!payload.hasUUID("entity") || !payload.getUUID("entity").equals(accessor.getEntity().getUUID())
                || !payload.contains("attack_damage", Tag.TAG_DOUBLE)) return null;
        double damage = payload.getDouble("attack_damage");
        return Double.isFinite(damage) && damage >= 0D ? damage : null;
    }

    public static Component line(double damage) {
        String value = BigDecimal.valueOf(damage).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return Component.translatable("jade.mite.monster_attack", value);
    }

    @Override public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        Double damage = snapshot(accessor);
        if (damage != null) tooltip.add(line(damage), UID);
    }
}

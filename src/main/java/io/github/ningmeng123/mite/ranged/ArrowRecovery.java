package io.github.ningmeng123.mite.ranged;

import com.mojang.serialization.Codec;
import io.github.ningmeng123.mite.Mite;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.damagesource.DamageSource;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** R196 contained arrows survive saves and drop once on death; wall pickup has no random roll. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class ArrowRecovery {
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Mite.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<List<String>>> CONTAINED = TYPES.register(
            "contained_arrows", () -> AttachmentType.<List<String>>builder(() -> List.of()).serialize(Codec.STRING.listOf()).build());
    private static final ThreadLocal<Impact> IMPACT = new ThreadLocal<>();
    private static final class Impact {
        final AbstractArrow arrow;
        final LivingEntity target;
        final Material material;
        boolean deathDropsProcessed;
        Impact(AbstractArrow arrow, LivingEntity target, Material material) { this.arrow = arrow; this.target = target; this.material = material; }
    }
    private ArrowRecovery() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }
    public static void contain(LivingEntity target, ItemStack arrow) {
        if (target.level().isClientSide || arrow.isEmpty() || !target.isAlive()) return;
            List<String> arrows = new ArrayList<>(target.getData(CONTAINED));
            arrows.add(BuiltInRegistries.ITEM.getKey(arrow.getItem()).toString());
            target.setData(CONTAINED, List.copyOf(arrows));
    }
    /** Source contained-arrow recovery must join the actual fatal drop event so cancellation remains atomic. */
    public static boolean hurt(AbstractArrow arrow, Entity target, DamageSource source, float amount) {
        Material material = MiteRangedItems.arrowMaterial(arrow.getPickupItemStackOrigin());
        if (!(target instanceof LivingEntity living) || material == null || arrow.level().isClientSide
                || arrow.pickup != AbstractArrow.Pickup.ALLOWED || arrow.getPierceLevel() > 0)
            return target.hurt(source, amount);
        Impact previous = IMPACT.get();
        Impact impact = new Impact(arrow, living, material);
        IMPACT.set(impact);
        boolean success;
        try { success = target.hurt(source, amount); }
        finally { if (previous == null) IMPACT.remove(); else IMPACT.set(previous); }
        if (success && !impact.deathDropsProcessed && living.isAlive() && arrow.getRandom().nextFloat() < material.recovery)
            contain(living, arrow.getPickupItemStackOrigin());
        return success;
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void drop(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        Impact impact = IMPACT.get();
        if (impact != null && impact.target == entity && !impact.deathDropsProcessed) {
            impact.deathDropsProcessed = true;
            if (impact.arrow.getRandom().nextFloat() < impact.material.recovery)
                event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), impact.arrow.getPickupItemStackOrigin().copyWithCount(1)));
        }
        if (entity.hasData(CONTAINED)) {
            List<String> arrows = entity.getData(CONTAINED);
            entity.removeData(CONTAINED);
            for (String id : arrows) {
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
                if (!stack.isEmpty()) event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack));
            }
        }
    }
}

package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/** Native identities which used tw.addRandomEquipment / tn.addRandomEquipment in R196. @author NingMeng123 */
public final class NativeMobEquipment {
    public record Weapon(String id, int weight) {}
    private static final List<String> PIG_WEAPONS = List.of("minecraft:golden_sword", "minecraft:golden_sword",
            "mite:golden_battle_axe", "minecraft:golden_pickaxe");
    private NativeMobEquipment() {}

    public static boolean handles(EntityType<?> type) {
        return type == EntityType.ZOMBIE || type == EntityType.ZOMBIE_VILLAGER || type == EntityType.ZOMBIFIED_PIGLIN;
    }

    /** abw.getDayOfWorld uses the six-thousand-tick offset, independently of modern difficulty. */
    public static long day(long time) { return (time + 6_000L) / 24_000L + 1L; }

    /** tw:165..193, ordinary survival: tournament exclusions are not active. */
    public static List<Weapon> zombieWeapons(boolean villager, long day) {
        List<Weapon> pool = new ArrayList<>();
        pool.add(new Weapon("minecraft:wooden_shovel", 1));
        pool.add(new Weapon("mite:rusted_iron_shovel", 2));
        if (day >= 10) pool.add(new Weapon("mite:rusted_iron_hatchet", 1));
        if (villager) {
            pool.add(new Weapon("mite:rusted_iron_shears", 1));
            pool.add(new Weapon("mite:rusted_iron_scythe", 1));
            if (day >= 10) {
                pool.add(new Weapon("mite:rusted_iron_hoe", 1));
                pool.add(new Weapon("mite:rusted_iron_mattock", 1));
            }
            if (day >= 20) pool.add(new Weapon("mite:rusted_iron_pickaxe", 1));
        } else {
            pool.add(new Weapon("mite:wooden_cudgel", 1));
            pool.add(new Weapon("minecraft:wooden_sword", 1));
            pool.add(new Weapon("mite:rusted_iron_sword", 1));
            pool.add(new Weapon("mite:rusted_iron_dagger", 1));
        }
        return List.copyOf(pool);
    }

    /** Returns false for modern identities and custom source classes, preserving their own equipment paths. */
    public static boolean populate(Mob mob) {
        if (!handles(mob.getType())) return false;
        if (mob.getType() == EntityType.ZOMBIFIED_PIGLIN) {
            ItemStack weapon = new ItemStack(R196MobEquipment.item(PIG_WEAPONS.get(mob.getRandom().nextInt(4))));
            EquipmentDurability.setQuality(weapon, Quality.POOR);
            mob.setItemSlot(EquipmentSlot.MAINHAND, R196MobEquipment.generated(mob, weapon, false));
            // tn overrides the whole method: it does not call og.bw to generate armor.
            return true;
        }
        boolean villager = mob.getType() == EntityType.ZOMBIE_VILLAGER;
        if (mob.getRandom().nextFloat() < (villager ? .2F : .05F)) {
            List<Weapon> pool = zombieWeapons(villager, day(mob.level().getDayTime()));
            int roll = mob.getRandom().nextInt(pool.stream().mapToInt(Weapon::weight).sum());
            for (Weapon weapon : pool) {
                roll -= weapon.weight();
                if (roll < 0) {
                    mob.setItemSlot(EquipmentSlot.MAINHAND, R196MobEquipment.generated(mob, weapon.id(), true));
                    break;
                }
            }
        }
        // tw invokes the weapon draw before og.bw, and a failed draw does not erase existing equipment.
        R196MobEquipment.randomArmor(mob);
        return true;
    }
}

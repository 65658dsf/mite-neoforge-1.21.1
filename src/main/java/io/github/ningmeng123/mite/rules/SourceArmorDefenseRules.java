package io.github.ningmeng123.mite.rules;

import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ArmorProfile;
import java.util.List;
import java.util.function.Supplier;

/** Damage.java, wh and abg arithmetic; the runtime adapter supplies modern damage classifications. @author NingMeng123 */
public final class SourceArmorDefenseRules {
    public record Piece(ArmorProfile profile, int damage, int maximum, int protection, int fireProtection,
                        int featherFalling, int blastProtection, int projectileProtection) {}
    public record Source(boolean absolute, boolean unblockable, boolean bypassesMundaneArmor,
                         boolean ignoresProtectionEnchantments, boolean fire, boolean fall, boolean explosion, boolean projectile) {}
    public record Defense(float armor, float special, float potions) { public float total() { return armor + special + potions; } }
    private SourceArmorDefenseRules() {}

    public static float pieceProtection(Piece piece) {
        return EquipmentDurabilityRules.armorProtection(piece.profile(), piece.damage(), piece.maximum(), piece.protection());
    }
    public static Defense defense(List<Piece> pieces, Source source, int resistanceLevels, int fireResistanceLevels) {
        if (source.absolute()) return new Defense(0F, 0F, 0F);
        float armor = 0F, special = 0F;
        if (!source.unblockable()) {
            if (!source.bypassesMundaneArmor()) for (Piece piece : pieces) armor += pieceProtection(piece);
            if (!source.ignoresProtectionEnchantments()) {
                // abg order is fire, fall, explosion, projectile; feather falling returns immediately.
                if (source.fire()) for (Piece piece : pieces) special += pieceProtection(piece) * fraction(piece.fireProtection());
                boolean featherReturned = false;
                if (source.fall()) {
                    for (Piece piece : pieces) if (piece.featherFalling() > 0) {
                        special = 15F * fraction(piece.featherFalling()) * EquipmentDurabilityRules.armorProtectionFactor(piece.damage(), piece.maximum(), true);
                        featherReturned = true;
                        break;
                    }
                }
                if (!featherReturned) {
                    if (source.explosion()) for (Piece piece : pieces) special += pieceProtection(piece) * fraction(piece.blastProtection());
                    if (source.projectile()) for (Piece piece : pieces) special += pieceProtection(piece) * fraction(piece.projectileProtection());
                }
            }
        }
        // wh rounds the armor-plus-enchantment sum once, before potion defense is added.
        float rounded = EquipmentDurabilityRules.roundedArmorProtection(armor + special);
        special = rounded - armor;
        float potions = 5F * Math.max(resistanceLevels, 0) + (source.fire() ? 5F * Math.max(fireResistanceLevels, 0) : 0F);
        return new Defense(armor, special, potions);
    }
    private static float fraction(int level) { return (float)Math.max(level, 0) / 4F; }

    public static float damage(float incoming, float totalDefense, float piercing, boolean absolute, Supplier<Float> nextFloat) {
        if (!(incoming > 0F)) return 0F;
        if (absolute) return incoming;
        float effective = Math.max(totalDefense - Math.max(piercing, 0F), 0F);
        if (effective >= incoming) {
            int delta = (int)(effective - incoming);
            // Exact source draw sequence, including one draw when the defense only equals the hit.
            for (int i = -1; i < delta; i++) if (nextFloat.get() < .2F) return 0F;
        }
        return Math.max(incoming - effective, 1F);
    }
}

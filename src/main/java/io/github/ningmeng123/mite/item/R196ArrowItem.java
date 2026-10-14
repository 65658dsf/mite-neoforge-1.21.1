package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import net.minecraft.world.item.ArrowItem;

/** Vanilla Arrow stores the exact ammunition stack for bows, crossbows and dispensers. @author NingMeng123 */
public final class R196ArrowItem extends ArrowItem {
    private final Material material;
    public R196ArrowItem(Material material, Properties properties) {
        super(properties.stacksTo(16)); this.material = material;
    }
    public Material material() { return material; }
}

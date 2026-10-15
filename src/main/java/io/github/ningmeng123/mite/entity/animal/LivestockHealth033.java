package io.github.ningmeng123.mite.entity.animal;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/** R196 bge/bgf/bhi/bhk body skins use the existing source wellness watchers. @author NingMeng123 */
public final class LivestockHealth033 {
    private static final Map<EntityType<?>,ResourceLocation> SICK_SKINS=Map.of(
            EntityType.CHICKEN,skin("chicken"), EntityType.COW,skin("cow"),
            EntityType.PIG,skin("pig"), EntityType.SHEEP,skin("sheep"));
    private LivestockHealth033() {}
    private static ResourceLocation skin(String species) {
        return ResourceLocation.fromNamespaceAndPath("mite","textures/entity/livestock/"+species+"_sick.png");
    }
    /** Null preserves the renderer/resource pack's normal texture, including both modern mooshroom variants. */
    @Nullable public static ResourceLocation sickBodyTexture(Mob mob) {
        ResourceLocation texture=SICK_SKINS.get(mob.getType());
        if(texture==null) return null;
        // Renderers consume only the values already synchronized by Livestock032; no client-side simulation.
        return ((Livestock032View)mob).mite$livestockNeeds().well()?null:texture;
    }
}

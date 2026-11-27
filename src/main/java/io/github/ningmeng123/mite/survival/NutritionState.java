package io.github.ningmeng123.mite.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.item.FoodProfile;
import io.github.ningmeng123.mite.registry.MiteAttachments;
import io.github.ningmeng123.mite.rules.NutritionRules;
import net.minecraft.world.entity.player.Player;

/** Persistent micronutrients and insulin response, including the source hysteresis. @author NingMeng123 */
public record NutritionState(int version, int protein, int fat, int plants, int resistance, int resistanceLevel) {
    public static final int VERSION = 1;
    public static final NutritionState INITIAL = new NutritionState(VERSION, 160000, 160000, 160000, 0, -1);
    public static final Codec<NutritionState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(VERSION, VERSION).fieldOf("version").forGetter(NutritionState::version),
            Codec.intRange(0,160000).fieldOf("protein").forGetter(NutritionState::protein),
            Codec.intRange(0,160000).fieldOf("fat").forGetter(NutritionState::fat),
            Codec.intRange(0,160000).fieldOf("plants").forGetter(NutritionState::plants),
            Codec.intRange(0,192000).fieldOf("resistance").forGetter(NutritionState::resistance),
            Codec.intRange(-1,2).fieldOf("resistance_level").forGetter(NutritionState::resistanceLevel)
    ).apply(i, NutritionState::new));
    public static NutritionState get(Player player) { return player.getData(MiteAttachments.NUTRITION); }
    public static void set(Player player, NutritionState state) { player.setData(MiteAttachments.NUTRITION, state); }
    public boolean malnourished() { return protein == 0 || plants == 0; }
    public NutritionState tick(boolean sprinting) {
        int nutrientCost = sprinting ? 5 : 1;
        int next = Math.max(0, resistance - 1);
        return new NutritionState(VERSION, Math.max(0, protein-nutrientCost), Math.max(0,fat-nutrientCost), Math.max(0,plants-nutrientCost), next,
                NutritionRules.resistanceLevel(next, resistanceLevel));
    }
    public NutritionState ingest(FoodProfile p) {
        int next = Math.clamp((long) resistance + NutritionRules.addedResistance(p.sugar()), 0, 192000);
        return new NutritionState(VERSION, NutritionRules.addMicronutrient(protein,p.nutrition(),p.protein()),
                NutritionRules.addMicronutrient(fat,p.nutrition(),p.fat()),
                Math.clamp((long) plants + p.plantNutrientGain(), 0, NutritionRules.MICRONUTRIENT_MAX), next,
                NutritionRules.resistanceLevel(next,resistanceLevel));
    }
}

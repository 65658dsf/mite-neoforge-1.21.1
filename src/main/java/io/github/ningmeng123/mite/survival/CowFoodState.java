package io.github.ningmeng123.mite.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.registry.MiteAttachments;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Cow;

/** rr and EntityLivestock: milk 0..100, food/water/freedom 0..1. @author NingMeng123 */
public record CowFoodState(int version, int milk, float food, float water, float freedom) {
    public static final Codec<CowFoodState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1,1).fieldOf("version").forGetter(CowFoodState::version),
            Codec.intRange(0,100).fieldOf("milk").forGetter(CowFoodState::milk),
            Codec.floatRange(0F,1F).fieldOf("food").forGetter(CowFoodState::food),
            Codec.floatRange(0F,1F).fieldOf("water").forGetter(CowFoodState::water),
            Codec.floatRange(0F,1F).fieldOf("freedom").forGetter(CowFoodState::freedom)
    ).apply(i,CowFoodState::new));

    public static CowFoodState initial(RandomSource random) {
        return new CowFoodState(1,100,.8F+random.nextFloat()*.2F,.8F+random.nextFloat()*.2F,.8F+random.nextFloat()*.2F);
    }
    public static CowFoodState get(Cow cow) { return cow.getData(MiteAttachments.COW_FOOD); }
    public static void set(Cow cow, CowFoodState state) { cow.setData(MiteAttachments.COW_FOOD,state); }
    public boolean well() { return Math.min(food,Math.min(water,freedom))>=.25F; }
    public boolean thirsty() { return water<.5F; }
    public CowFoodState milk(int amount) { return new CowFoodState(1,Math.clamp(amount,0,100),food,water,freedom); }
    public CowFoodState feed() { return new CowFoodState(1,milk,Math.min(1F,food+.5F),water,freedom); }
    public CowFoodState drink(int volume) { return new CowFoodState(1,milk,food,Math.min(1F,water+.25F*volume),freedom); }
    public CowFoodState update(boolean nearFood, boolean nearWater, boolean rain, boolean crowded, boolean baby) {
        float f=Math.clamp(food+(nearFood?.1F:-.005F),0F,1F);
        float w=Math.clamp(water+(nearWater?.1F:rain?.01F:-.005F),0F,1F);
        float freedomNext=Math.clamp(freedom+(crowded?-.005F:.1F),0F,1F);
        boolean well=Math.min(f,Math.min(w,freedomNext))>=.25F;
        return new CowFoodState(1,Math.min(100,milk+(well&&!baby?1:0)),f,w,freedomNext);
    }
}

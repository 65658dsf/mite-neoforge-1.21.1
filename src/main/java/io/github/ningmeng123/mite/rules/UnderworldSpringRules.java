package io.github.ningmeng123.mite.rules;

import java.util.Random;

/** acu consumes X, nested Y, Z, threshold, then the conditional fluid draw, in this order. @author NingMeng123 */
public final class UnderworldSpringRules {
    public record Candidate(int x,int legacyY,int z,boolean water) {}
    private UnderworldSpringRules() {}
    public static Candidate candidate(Random random,int chunkX,int chunkZ) {
        int x=chunkX*16+random.nextInt(16)+8;
        int y=random.nextInt(random.nextInt(120)+8);
        int z=chunkZ*16+random.nextInt(16)+8;
        boolean water=random.nextInt(32)+16<y || random.nextFloat()>=.95F;
        return new Candidate(x,y,z,water);
    }
    public static int freefallDecayDelay(boolean water,boolean ultraWarm) { return water?8:ultraWarm?16:48; }
}

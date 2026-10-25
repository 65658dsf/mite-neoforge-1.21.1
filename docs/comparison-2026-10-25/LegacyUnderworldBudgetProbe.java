import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import io.github.ningmeng123.mite.rules.R196OreRules.Dimension;
import java.util.*;

/** Analysis only. Infinite replaceable rock, budget before connected growth. Author: NingMeng123. */
public class LegacyUnderworldBudgetProbe {
  static final int N=3000000;
  static int size(Kind k,int y,int min,int max,Random random) {
    int base=R196OreRules.baseSize(k);
    float scale=1F;
    while(random.nextInt(2)==0)scale=(float)((double)scale*((double)random.nextFloat()*.6+(double).7F));
    scale=Math.min(scale,4F);
    int range=max-min;
    if(R196OreRules.depthScaled(k,Dimension.UNDERWORLD)&&range>16){
      float relative=((float)y-(float)min)/(float)range;
      scale*=1F-relative+.5F;
    }
    if(base*scale<=3F&&random.nextInt(2)==0){if(random.nextInt(2)!=0)return 0;scale*=2F;}
    int size=(int)(base*scale);
    if(size<1)return 0;
    if(size==1)return random.nextInt(3)==0?3:0;
    if(size==2)return random.nextInt(3)==0?0:3;
    return Math.min(size,32);
  }
  static record Result(double pass,double perCandidate,double perAccepted) {}
  static Result run(Kind k,int mode) {
    Random r=new Random(19620261025L+mode*310L+k.ordinal());long accepted=0,blocks=0;
    for(int i=0;i<N;i++) {
      int y=mode==2&&k==Kind.ADAMANTIUM?130+r.nextInt(120):R196OreRules.randomHeight(k,Dimension.UNDERWORLD,r);
      int min=mode==0?0:k==Kind.ADAMANTIUM?130:140;
      int max=mode==0?255:225;
      if(y<min)continue;
      accepted++;blocks+=size(k,y,min,max,r);
    }
    return new Result(accepted/(double)N,blocks/(double)N,blocks/(double)accepted);
  }
  public static void main(String[] args) {
    System.out.println("kind,mode,height_pass,budget_per_candidate,budget_per_accepted,frequency_vs_R196,budget_vs_R196_1x,matching_modern_multiplier_before_host");
    for(Kind k:List.of(Kind.COPPER,Kind.SILVER,Kind.GOLD,Kind.IRON,Kind.MITHRIL,Kind.ADAMANTIUM,Kind.DIAMOND,Kind.LAPIS,Kind.REDSTONE)) {
      Result source=run(k,0);
      for(int mode=0;mode<3;mode++) {
        Result v=mode==0?source:run(k,mode);
        double frequency=k==Kind.ADAMANTIUM?(mode==1?1.6:mode==2?.8:1):1;
        double ratio=v.perCandidate*frequency/source.perCandidate;
        System.out.printf(Locale.ROOT,"%s,%s,%.8f,%.8f,%.8f,%.3f,%.8f,%.8f%n",k,new String[]{"R196","ITE","EXTREME"}[mode],v.pass,v.perCandidate,v.perAccepted,frequency,ratio,ratio);
      }
    }
    System.out.println("N=3000000; model excludes chance gate shared by versions, actual rock/caverns/mantle, connected-growth truncation, overlap and structures");
  }
}

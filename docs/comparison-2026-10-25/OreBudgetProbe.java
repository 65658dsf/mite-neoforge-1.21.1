import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import io.github.ningmeng123.mite.rules.R196OreRules.Dimension;
import io.github.ningmeng123.mite.worldgen.R196UnderworldTerrain;
import java.util.*;

/** Analysis only: budget and ideal-flat substrate expectations. Author: NingMeng123. */
public class OreBudgetProbe {
  static final int N=1000000;
  static record Result(double budget,double host) {}
  static Result probe(Kind k,Integer lo,Integer hi,int mode) {
    Random r=new Random(19620261025L+k.ordinal()*31L+mode);
    double sum=0, host=0;
    for(int i=0;i<N;i++) {
      int legacy=R196OreRules.randomHeight(k,Dimension.OVERWORLD,r);
      int y=mode==0?legacy:R196OreRules.modernY(Dimension.OVERWORLD,legacy);
      if(lo!=null) {
        y=lo+r.nextInt(hi-lo+1);
        legacy=y<=64?Math.floorDiv(y+64,2):y;
        legacy=Math.clamp(legacy,R196OreRules.minHeight(k,Dimension.OVERWORLD),R196OreRules.maxHeight(k,Dimension.OVERWORLD));
      }
      int budget=R196OreRules.veinSize(k,Dimension.OVERWORLD,legacy,r);
      sum+=budget;
      if(mode==0?y>=0&&y<=63:mode==1?y>=0&&y<=63:y>=-64&&y<0)host+=budget;
    }
    return new Result(sum/N,host/N);
  }
  public static void main(String[] a) {
    System.out.println("kind,source_budget,source_flat,stone_budget,stone_flat,deep_budget,deep_flat,total_new_flat,newflat_over_oldflat");
    for(Kind k:List.of(Kind.COPPER,Kind.SILVER,Kind.IRON,Kind.GOLD,Kind.MITHRIL,Kind.DIAMOND,Kind.LAPIS,Kind.COAL)) {
      Integer sl=null,sh=null,dl=null,dh=null;
      if(Set.of(Kind.COPPER,Kind.SILVER,Kind.IRON,Kind.GOLD,Kind.LAPIS).contains(k)){sl=-64;sh=168;}
      if(k==Kind.MITHRIL){sl=-64;sh=68;}
      if(k==Kind.COPPER){dl=-64;dh=168;}
      if(k==Kind.GOLD){dl=-64;dh=64;}
      if(k==Kind.LAPIS){dl=-48;dh=64;}
      Result old=probe(k,null,null,0),stone=probe(k,sl,sh,1),deep=probe(k,dl,dh,2);
      double total=stone.host+deep.host;
      System.out.printf(Locale.ROOT,"%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",k,old.budget,old.host,stone.budget,stone.host,deep.budget,deep.host,total,total/old.host);
    }
    long[] stone=new long[256],modified=new long[256];int chunks=0;
    for(long seed:new long[]{0L,196L,20261025L,123456789L}) {
      var terrain=new R196UnderworldTerrain(seed);
      for(int cx=-2;cx<=1;cx++)for(int cz=-2;cz<=1;cz++) {
        chunks++;byte[] b=terrain.generate(cx,cz);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<256;y++) {
          byte block=b[R196UnderworldTerrain.index(x,y,z)];
          if(block==R196UnderworldTerrain.STONE)stone[y]++;
          if(block==R196UnderworldTerrain.STONE || block==R196UnderworldTerrain.BEDROCK&&y<253)modified[y]++;
        }
      }
    }
    System.out.println("underworld_kind,old_stone_chance,new_stone_chance,old_accepted_budget,new_accepted_budget,new_over_old");
    for(Kind k:List.of(Kind.COPPER,Kind.SILVER,Kind.IRON,Kind.GOLD,Kind.MITHRIL,Kind.ADAMANTIUM,Kind.DIAMOND,Kind.LAPIS,Kind.REDSTONE)) {
      Random r=new Random(19625L+k.ordinal());double pold=0,pnew=0,bold=0,bnew=0;
      for(int i=0;i<N;i++) {
        int y=R196OreRules.randomHeight(k,Dimension.UNDERWORLD,r);
        int budget=R196OreRules.veinSize(k,Dimension.UNDERWORLD,y,r);
        double so=stone[y]/(chunks*256D),sn=modified[y]/(chunks*256D);
        pold+=so;pnew+=sn;bold+=so*budget;bnew+=sn*budget;
      }
      System.out.printf(Locale.ROOT,"%s,%.6f,%.6f,%.6f,%.6f,%.6f%n",k,pold/N,pnew/N,bold/N,bnew/N,bnew/bold);
    }
    System.out.println("underworld_chunks="+chunks+",terrain_seeds=4; excludes vein geometry/ore overlap/structures/decoration");
  }
}

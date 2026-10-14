"""Extract R196 decorative identities, recipes and original texture bytes. Author: NingMeng123."""
import hashlib,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def write(path,value):
 p=ROOT/path; p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
def main():
 runtime=json.loads((ROOT/"reference-data/runtime-registry.json").read_text(encoding="utf8"))
 catalog=json.loads((ROOT/"spec/item-catalog-015.json").read_text(encoding="utf8"))
 metals=["copper","silver","gold","ancient_metal","mithril","adamantium"]
 doors=dict(zip([1111,1112,1113,1260,1114,1115],metals)); bars=dict(zip([213,215,199,216,217],["copper","gold","ancient_metal","mithril","adamantium"]))
 blockById={r["id"]:r for r in runtime["blocks"]}
 ids={1111:"minecraft:copper_door"}
 ids.update({id:"mite:"+("golden" if material=="gold" else material)+"_door" for id,material in doors.items() if id!=1111})
 ids.update({id:"mite:"+("golden" if material=="gold" else material)+"_bars" for id,material in bars.items()})
 flowers={1:("minecraft:blue_orchid","light_blue", "orchid"),2:("minecraft:allium","magenta","allium"),5:("minecraft:orange_tulip","orange","tulip"),7:("mite:dahlia","pink","dahlia"),8:("minecraft:oxeye_daisy","light_gray","daisy")}
 entries=[]
 for e in catalog["entries"]:
  if e.get("default_included") and e["category"] in ["红石","装饰品"] and e["name"]!="远古金属块":
   s=e["source_registry"]; source=s["id"]; meta=s["item_metadata"][0]
   if source in ids: modern=ids[source]
   elif source==38 and meta in flowers: modern=flowers[meta][0]
   elif source==32 and meta==1: modern="mite:witherwood_bush"
   elif source==13 and meta==2: modern="mite:nether_gravel"
   else: continue
   bid=s.get("placed_block_id",source);b=blockById[bid]
   entries.append({"mcmod_id":e["mcmod_id"],"name":e["name"],"modern_id":modern,"source_id":source,"source_metadata":meta,"source_class":s["class"],"hardness":b["scalars"]["aqz.cG"],"source_harvest":b["scalars"]["aqz.min_harvest_level"],"max_stack":1 if source in doors else b["scalars"]["aqz.max_stack_size"]})
 recipes=[];timed={}
 ingots={962:"minecraft:copper_ingot",963:"mite:silver_ingot",266:"minecraft:gold_ingot",966:"mite:ancient_metal_ingot",964:"mite:mithril_ingot",965:"mite:adamantium_ingot"}
 for r in runtime["crafting_recipes"]:
  output=r.get("output") or {}; source=output.get("item_id");c=r.get("components") or []
  if source in ids:
   modern=ids[source];ingredient=ingots[c[0]["item_id"]]
   row={"id":modern,"output":modern,"count":output["count"],"pattern":["I"*r["width"]]*r["height"],"ingredient":ingredient,"source_order":r["order"],"difficulty":r["difficulty"],"required_bench":r["bench_material"]["name"]}
  elif source==351 and len(c)==1 and c[0] and c[0]["item_id"]==38 and c[0]["subtype"] in flowers:
   inp,dye,texture=flowers[c[0]["subtype"]];modern="minecraft:"+dye+"_dye_from_"+inp.split(":")[1] if not inp.startswith("mite:") else "mite:pink_dye_from_dahlia"
   row={"id":modern,"output":"minecraft:"+dye+"_dye","count":output["count"],"input":inp,"source_order":r["order"],"difficulty":r["difficulty"],"required_bench":None}
  else: continue
  recipes.append(row);timed[row["id"]]={k:row[k] for k in ["difficulty","required_bench","source_order"]}
 textures=[]
 with zipfile.ZipFile(ROOT/"MITE Resource Pack 1.6.4.zip") as archive:
  def copy(source,target):
   data=archive.read("assets/minecraft/textures/"+source+".png");p=ROOT/"src/main/resources"/target;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
   textures.append({"source":"assets/minecraft/textures/"+source+".png","target":target,"sha256":hashlib.sha256(data).hexdigest()})
  for material in metals:
   for part in ["lower","upper"]: copy("blocks/door_"+material+"_"+part,"assets/mite/textures/block/door_"+material+"_"+part+".png")
   copy("items/doors/"+material,"assets/mite/textures/item/door_"+material+".png")
  for material in bars.values():copy("blocks/"+material+"_bars","assets/mite/textures/block/"+material+"_bars.png")
  for inp,dye,texture in flowers.values(): copy("blocks/flowers/"+texture,"assets/mite/textures/block/flowers/"+texture+".png")
  copy("blocks/witherwood","assets/mite/textures/block/witherwood.png")
  copy("blocks/nether_gravel","assets/mite/textures/block/nether_gravel.png")
 modern_recipes=[{"id":"minecraft:copper_trapdoor","output":"minecraft:copper_trapdoor","count":2,"pattern":["III","I I"," I "],"ingredient":"minecraft:copper_ingot","difficulty":2400.0,"required_bench":"copper","source_order":-1,"source":"modern adaptation; pinned MC 1.21.1 has 6 copper ingots, output 2, conflicting 3x2 shape; difficulty uses source six-ingot copper recipe order 227 as the declared cost benchmark"}]
 for row in modern_recipes:timed[row["id"]]={k:row[k] for k in ["difficulty","required_bench","source_order"]}
 result={"schema_version":1,"author":"NingMeng123","archive_sha256":runtime["archive_sha256"],"source":"reference-data/runtime-registry.json; anz,aqy,BlockFlowerMulti,ant,aoo,afc,aep,adr,acu","entries":entries,"recipes":recipes,"modern_adapted_recipes":modern_recipes,"textures":textures,"modern_adaptations":["Retain modern Nether terrain, replace only gravel surface identity; keep existing ore gravel feature.","Dahlia patches use modern surface height with source temperature rules and 16/64 patch attempts. Allium patch 8 in swamp.","Retain native non-base copper oxidation and waxing while applying copper source metal-door interactions.","Retain native suspicious stew behavior for vanilla flowers; new Dahlia is a BushBlock and does not invent a source stew effect.","Native copper trapdoor remains 6 ingots producing 2; only its shape changes to a unique 3x3 layout to preserve the exact R196 copper bars recipe."]}
 assert len(entries)==18 and len(recipes)==16
 write("spec/decorative-028.json",result);write("src/main/resources/mite/decorative-028.json",{k:result[k] for k in ["author","entries","recipes","modern_adapted_recipes"]});write("src/main/resources/mite/timed-crafting-028-decorative.json",{"author":"NingMeng123","recipes":timed})
 zh={};en={}
 for e in entries:
  if e["modern_id"].startswith("mite:"):
   key="block."+e["modern_id"].replace(":",".");zh[key]=e["name"];en[key]=e["modern_id"].split(":")[1].replace("_"," ").title()
 zh["block.mite.potted_dahlia"]="盆栽大丽花";en["block.mite.potted_dahlia"]="Potted Dahlia"
 write("build/verification/028-decorative-lang.json",{"zh_cn":zh,"en_us":en})
 print(f"Extracted {len(entries)} leaves, {len(recipes)} recipes, {len(textures)} original PNGs")
if __name__=="__main__":main()

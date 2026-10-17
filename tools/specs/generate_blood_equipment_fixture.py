"""Independent exact rational nine-grade/ten-material fixture. Author: NingMeng123."""
from fractions import Fraction as F
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATH = ROOT / "spec/blood-moon-equipment.json"
GRADES = (0, 1, 2, 3, 3, 4, 5, 6, 7, 8)
PREFIXES = ("minecraft:leather_", "minecraft:chainmail_", "minecraft:golden_", "mite:copper_", "mite:silver_",
            "minecraft:iron_", "mite:ancient_metal_", "mite:mithril_", "mite:adamantium_", "mite:vibranium_")

def distribution(stage, blood):
    lower, upper = (F(7,20), F(2,5)) if blood else (F(1,2), F(1,4))
    center = GRADES[stage]
    grade_weights = [lower ** (center-g) if g < center else upper ** (g-center) for g in range(9)]
    total = sum(grade_weights)
    return [grade_weights[g] / total / GRADES.count(g) for g in GRADES]

def daily(day):
    progress = min(F(9), max(F(0), F(day-128,128)))
    lower = int(progress); blend = progress-lower
    first = distribution(lower, False)
    second = distribution(min(lower+1,9), False)
    return progress, [(1-blend)*a+blend*b for a,b in zip(first,second)]

def main():
    data = json.loads(PATH.read_text(encoding="utf-8"))
    data["prefixes"] = list(PREFIXES)
    data["stage"] = "Blood count 2..7 => indices 0,1,2,3,5,6; eighth blood at day320 => adamantium index8; ninth blood at day352 and later => vibranium index9. Existing indices0..8 remain fixed."
    data["material_probability"] = "Normalize nine grade weights once, then split grade3 equally between copper and silver; all ten materials remain possible, including the rare higher tail."
    data["probability_oracle"] = "Independent Python fractions.Fraction fixture with nine grades and ten material columns [0,1,2,3,3,4,5,6,7,8]; ordinary ratios1/2,1/4 and blood7/20,2/5. No production Java or random-selection implementation is imported."
    data["material_grades"] = list(GRADES)
    data["grade_material_counts"] = [GRADES.count(g) for g in range(9)]
    data["grade_names"] = ["leather","chainmail","gold","copper_and_silver","iron","ancient_metal","mithril","adamantium","vibranium"]
    data["equipment_policy"] = data["equipment_policy"].replace("From the eighth actual blood moon (day 320), use highest basis stage 8.", "The eighth actual blood moon (day320) keeps adamantium basis8; from the ninth(day352), use vibranium basis9. Existing saved entities keep their decisions.")
    data["held_equipment"]["tool_prefixes"] = ["mite:rusted_iron_", "mite:rusted_iron_", *PREFIXES[2:]]
    rows = data["stages"]
    if len(rows) == 9:
        rows.append({"stage":9,"blood_moon_counts":[9],"common_material":"振金","prefix":PREFIXES[9],"blood_start_day":352})
    rows[8]["blood_moon_counts"] = [8]
    for stage,row in enumerate(rows):
        row["material_grade"] = GRADES[stage]
        for blood in (False,True):
            label = "blood" if blood else "ordinary"
            armor = F(80+2*stage if blood else 50+5*stage,100)
            held = F(70+3*stage if blood else 40+5*stage,100)
            probabilities = distribution(stage,blood)
            row[label+"_armor_chance"] = float(armor)
            row[label+"_held_chance"] = float(held)
            row[label+"_unarmored_chance"] = float(1-armor)
            row[label+"_conditional_material_chances"] = list(map(float,probabilities))
            row[label+"_conditional_material_fractions"] = list(map(str,probabilities))
            row[label+"_overall_material_chances"] = [float(armor*p) for p in probabilities]
            if blood:
                row["blood_empty_slot_chance"] = float(1-armor)
                for name,value in (("empty",(1-armor)**4),("four",armor**4)):
                    row["blood_all_"+name+"_armor_chance"] = float(value)
                    row["blood_all_"+name+"_armor_fraction"] = str(value)
    calendar = data["ordinary_calendar_progression"]
    calendar["maximum_day"] = 1280
    calendar["progress"] = "clamp((highestDay-128)/128,0,9); adamantium basis8 remains day1152, vibranium basis9 is day1280"
    calendar["material_interpolation"] = "Linearly mix adjacent normalized ten-material basis distributions across nine grades. Copper and silver split grade3 equally; basis3 and4 remain identical while acceptance rates continue growing."
    calendar["anchors"] = []
    for day in (64,128,160,192,200,256,384,512,640,768,896,1024,1152,1153,1279,1280,1281):
        progress, probabilities = daily(day)
        armor,held = (50+5*progress)/100,(40+5*progress)/100
        calendar["anchors"].append({"day":day,"progress":float(progress),"progress_fraction":str(progress),
            "armor_chance":float(armor),"held_chance":float(held),"unarmored_chance":float(1-armor),"empty_held_chance":float(1-held),
            "conditional_material_chances":list(map(float,probabilities)),"conditional_material_fractions":list(map(str,probabilities)),
            "overall_armor_material_chances":[float(armor*p) for p in probabilities],"overall_held_material_chances":[float(held*p) for p in probabilities]})
    PATH.write_text(json.dumps(data,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    for stage in (0,3,8,9):
        probabilities=distribution(stage,True)
        denominator=max(p.denominator for p in probabilities)
        from math import lcm
        denominator=lcm(*(p.denominator for p in probabilities))
        print("blood weights",stage,[int(p*denominator) for p in probabilities],denominator)
    for day in (200,1152,1280):
        progress, probabilities=daily(day);armor=(50+5*progress)/100
        print("day",day,"progress",progress,"vib conditional",probabilities[9],float(probabilities[9])*100,"overallarmor",float(armor*probabilities[9])*100)

if __name__ == "__main__":main()

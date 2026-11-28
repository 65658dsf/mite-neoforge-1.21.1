"""Independent exact rational nine-grade/ten-material fixture. Author: NingMeng123."""
from fractions import Fraction as F
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATH = ROOT / "spec/blood-moon-equipment.json"
GRADES = (0, 1, 2, 3, 3, 4, 5, 6, 7, 8)
PREFIXES = ("minecraft:leather_", "minecraft:chainmail_", "minecraft:golden_", "mite:copper_", "mite:silver_",
            "minecraft:iron_", "mite:ancient_metal_", "mite:mithril_", "mite:adamantium_", "mite:vibranium_")
STAGE_START_DAYS = {0: 16, 1: 32, 3: 48, 5: 64, 6: 96, 7: 112, 8: 128, 9: 144}
FIRST_BLOOD_MOON_BY_STAGE = {1: 1, 5: 2, 6: 3, 9: 4}


def distribution(stage):
    lower, upper = F(7, 20), F(2, 5)
    center = GRADES[stage]
    grade_weights = [lower ** (center - grade) if grade < center else upper ** (grade - center) for grade in range(9)]
    material_weights = [grade_weights[grade] / GRADES.count(grade) for grade in GRADES]
    if stage == 3:
        material_weights[2:5] = [sum(material_weights[2:5]) / 3] * 3
    total = sum(material_weights)
    return [weight / total for weight in material_weights]


def calendar_stage(day):
    return max((stage for stage, start_day in STAGE_START_DAYS.items() if day >= start_day), default=0)


def main():
    data = json.loads(PATH.read_text(encoding="utf-8"))
    data["schema_version"] = 4
    data["prefixes"] = list(PREFIXES)
    data["stage"] = "Use the highest saved world day for ordinary, blood and blue dates. Leather starts at day 16, chainmail at 32, gold/copper/silver at 48, iron at 64, ancient metal at 96, mithril at 112, adamantium at 128 and vibranium at 144. Stage rows retain blood_start_day as the shared calendar threshold and blood_moon_counts as the first default blood moon at that stage, when one occurs. Vibranium is the stage from the fourth blood moon onward."
    data["early_ordinary_legacy_armor_chance"] = .3
    data["first_blood_date_material_and_partial_stop_changed"] = True
    data["ordinary_equipment_factor"] = .5
    data["ordinary_armor_probability"] = "Default per initially empty slot: half the default blood-date chance. Servers can edit each date window and both probabilities independently."
    data["blood_armor_probability"] = "Default per initially empty slot: (80 + 2 * stage) / 100. The first default blood moon on day 32 uses stage 1 and an 82% per-slot chance."
    data["ordinary_held_probability"] = "Empty-main-hand supplementation only: blood_held_probability / 2 at the same highest-date stage"
    data["ordinary_lower_material_weight_ratio"] = .35
    data["ordinary_higher_material_weight_ratio"] = .4
    data["material_weight"] = "center=material_grades[stage]; gradeWeight(g)=g<center ? lower^(center-g) : higher^(g-center); divide each grade weight between its materials. At stage 3, redistribute the combined gold, copper and silver weight of 27/20 equally (9/20 each)."
    data["material_probability"] = "Ordinary and blood dates use the same normalized weights. Copper and silver share their grade; stage 3 also gives gold the same weight as copper and silver. All ten materials remain possible."
    data["overall_material_probability"] = "On ordinary and blood dates, multiply the individual slot acceptance by the conditional material chance. The default ordinary marginal is half the default blood-date marginal."
    data["probability_oracle"] = "Independent Python fractions.Fraction fixture with nine grades and ten material columns [0,1,2,3,3,4,5,6,7,8], shared ratios 7/20 and 2/5, equal redistribution in stage 3, and ordinary acceptance factor 1/2. No production Java or random-selection implementation is imported."
    data["material_grades"] = list(GRADES)
    data["grade_material_counts"] = [GRADES.count(grade) for grade in range(9)]
    data["grade_names"] = ["leather", "chainmail", "gold", "copper_and_silver", "iron", "ancient_metal", "mithril", "adamantium", "vibranium"]
    data["equipment_policy"] = "New eligible humanoid monsters independently roll acceptance and material for each empty slot in a configured date window. Persist each spawn decision including empty results. Dates outside the windows use source materials and partial stopping. Preserve fixed equipment floors, explicit stacks, source quality/wear, existing decisions and loaded entities. Calendar enchantment growth and enchanted natural-gear drop penalties are separate shared generation/drop rules."
    data["held_equipment"]["material"] = "Prefer the first newly generated armor piece, then recognized existing armor; otherwise draw independently from the shared highest-date material distribution."
    data["held_equipment"]["tool_prefixes"] = ["mite:rusted_iron_", "mite:rusted_iron_", *PREFIXES[2:]]
    data["held_equipment"]["generation"] = "R196MobEquipment.generated once per new stack, retaining source quality/wear with calendar enchantment growth; death-drop policy is separate."
    data["stages"][3]["common_material"] = "铜／银／金（同权）"
    for stage, row in enumerate(data["stages"]):
        row["material_grade"] = GRADES[stage]
        row["blood_moon_counts"] = [FIRST_BLOOD_MOON_BY_STAGE[stage]] if stage in FIRST_BLOOD_MOON_BY_STAGE else []
        row["blood_start_day"] = STAGE_START_DAYS.get(stage)
        for blood in (False, True):
            label = "blood" if blood else "ordinary"
            factor = F(1) if blood else F(1, 2)
            armor = F(80 + 2 * stage, 100) * factor
            held = F(70 + 3 * stage, 100) * factor
            probabilities = distribution(stage)
            row[label + "_armor_chance"] = float(armor)
            row[label + "_held_chance"] = float(held)
            row[label + "_unarmored_chance"] = float(1 - armor)
            row[label + "_conditional_material_chances"] = list(map(float, probabilities))
            row[label + "_conditional_material_fractions"] = list(map(str, probabilities))
            row[label + "_overall_material_chances"] = [float(armor * probability) for probability in probabilities]
            if blood:
                row["blood_empty_slot_chance"] = float(1 - armor)
                for name, value in (("empty", (1 - armor) ** 4), ("four", armor ** 4)):
                    row["blood_all_" + name + "_armor_chance"] = float(value)
                    row["blood_all_" + name + "_armor_fraction"] = str(value)
    calendar = data["ordinary_calendar_progression"]
    calendar.pop("growth_start_day", None)
    calendar.pop("days_per_stage", None)
    calendar["introduced_from_day"] = min(STAGE_START_DAYS.values())
    calendar["maximum_day"] = max(STAGE_START_DAYS.values())
    calendar["progress"] = "Use the material stage unlocked by the highest saved calendar day. Shared thresholds apply on ordinary, blood and blue dates. The day 15 anchor describes the bounded probability helper; actual pre-day-16 spawns use the legacy equipment path."
    calendar["material_interpolation"] = "New ordinary spawns use the integer calendar stage. Compatible fractional API inputs blend adjacent shared normalized distributions."
    calendar["history"] = "Persisted highest primary-world calendar date; time rewind and dimensions do not lower it. Blue dates use ordinary equipment acceptance with the material stage of the saved highest date."
    calendar["blood_comparison"] = "Default ordinary per-slot armor and empty-main-hand supplement probabilities are half their blood-date counterparts. The in-game armor table edits the date windows and the two per-slot armor chances. Native weapons, fixed sets and shield generation use their own equipment paths."
    calendar["quality_unchanged"] = "Preserve source quality/wear, fixed equipment floors and explicit main hands. Calendar enchantment growth and death-drop policy apply separately."
    calendar["anchors"] = []
    for day in (15, 16, 31, 32, 47, 48, 63, 64, 65, 79, 80, 95, 96, 111, 112, 127, 128, 143, 144, 159, 160,
                191, 192, 200, 223, 224, 255, 256, 287, 288, 319, 320, 351, 352, 353, 1280):
        stage = calendar_stage(day)
        probabilities = distribution(stage)
        armor, held = F(80 + 2 * stage, 200), F(70 + 3 * stage, 200)
        calendar["anchors"].append({"day": day, "progress": stage, "progress_fraction": str(stage),
            "armor_chance": float(armor), "held_chance": float(held), "unarmored_chance": float(1 - armor), "empty_held_chance": float(1 - held),
            "conditional_material_chances": list(map(float, probabilities)), "conditional_material_fractions": list(map(str, probabilities)),
            "overall_armor_material_chances": [float(armor * probability) for probability in probabilities],
            "overall_held_material_chances": [float(held * probability) for probability in probabilities]})
    PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()

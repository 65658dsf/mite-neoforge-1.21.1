"""Independent exact rational nine-grade/ten-material fixture. Author: NingMeng123."""
from fractions import Fraction as F
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATH = ROOT / "spec/blood-moon-equipment.json"
GRADES = (0, 1, 2, 3, 3, 4, 5, 6, 7, 8)
PREFIXES = ("minecraft:leather_", "minecraft:chainmail_", "minecraft:golden_", "mite:copper_", "mite:silver_",
            "minecraft:iron_", "mite:ancient_metal_", "mite:mithril_", "mite:adamantium_", "mite:vibranium_")


def distribution(stage):
    lower, upper = F(7, 20), F(2, 5)
    center = GRADES[stage]
    grade_weights = [lower ** (center - grade) if grade < center else upper ** (grade - center) for grade in range(9)]
    total = sum(grade_weights)
    return [grade_weights[grade] / total / GRADES.count(grade) for grade in GRADES]


def calendar_stage(day):
    count = max(0, day) // 32 - max(0, day) // 128
    if count < 2:
        return 0
    if count >= 9:
        return 9
    if count == 8:
        return 8
    return (0, 1, 2, 3, 5, 6)[count - 2]


def main():
    data = json.loads(PATH.read_text(encoding="utf-8"))
    data["schema_version"] = 4
    data["prefixes"] = list(PREFIXES)
    data["stage"] = "Both ordinary and blood dates use blood count 2..7 => indices 0,1,2,3,5,6; eighth at day320 => adamantium index8; ninth at day352 and later => vibranium index9. Existing indices0..8 remain fixed."
    data["early_ordinary_legacy_armor_chance"] = .3
    data["ordinary_equipment_factor"] = .5
    data["ordinary_armor_probability"] = "Whole-set acceptance: blood_armor_probability / 2 at the same highest-date stage; legacy entrance30% before day64"
    data["ordinary_held_probability"] = "Empty-main-hand supplementation only: blood_held_probability / 2 at the same highest-date stage"
    data["ordinary_lower_material_weight_ratio"] = .35
    data["ordinary_higher_material_weight_ratio"] = .4
    data["material_probability"] = "The same normalized nine-grade distribution on ordinary and blood dates; split grade3 equally between copper and silver. All ten materials remain possible."
    data["overall_material_probability"] = "Ordinary: whole-set acceptance times conditional material chance for each initially empty slot. Blood: independent single-slot acceptance times material chance. Every ordinary per-slot material marginal is exactly half the same highest-date blood marginal."
    data["probability_oracle"] = "Independent Python fractions.Fraction fixture with nine grades and ten material columns [0,1,2,3,3,4,5,6,7,8]; shared ratios7/20,2/5, ordinary acceptance factor1/2. No production Java or random-selection implementation is imported."
    data["material_grades"] = list(GRADES)
    data["grade_material_counts"] = [GRADES.count(grade) for grade in range(9)]
    data["grade_names"] = ["leather", "chainmail", "gold", "copper_and_silver", "iron", "ancient_metal", "mithril", "adamantium", "vibranium"]
    data["equipment_policy"] = "New eligible humanoid monsters only; persist each spawn decision including empty results. Ordinary dates use one whole-set acceptance/material roll, with acceptance half the same highest-date blood rate and the same material weights. Blood dates independently roll each empty slot. Before day64, ordinary legacy armor entrance is30%, first blood entrance60%, retaining source materials and partial stopping. Preserve fixed equipment floors, explicit stacks, source quality/wear, existing decisions and loaded entities; never reroll them. Calendar enchantment growth and enchanted natural-gear drop penalties are separate shared generation/drop rules."
    data["held_equipment"]["material"] = "Prefer the first newly generated armor piece, then recognized existing armor; otherwise draw independently from the shared highest-date blood-stage material distribution."
    data["held_equipment"]["tool_prefixes"] = ["mite:rusted_iron_", "mite:rusted_iron_", *PREFIXES[2:]]
    data["held_equipment"]["generation"] = "R196MobEquipment.generated once per new stack, retaining source quality/wear with calendar enchantment growth; death-drop policy is separate."
    for stage, row in enumerate(data["stages"]):
        row["material_grade"] = GRADES[stage]
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
    calendar["maximum_day"] = 352
    calendar["progress"] = "max(0, stage(highestDay/32-highestDay/128)); from day64 use the same unlocked stage as blood dates"
    calendar["material_interpolation"] = "New ordinary spawns use the integer shared blood stage. Compatible fractional API inputs blend adjacent shared normalized distributions; no ordinary date interpolation remains."
    calendar["history"] = "Persisted highest primary-world calendar date; time rewind and dimensions do not lower it, and blue dates do not advance the blood count."
    calendar["blood_comparison"] = "For each highest date >=64 and each material, ordinary per-empty-slot armor marginal and empty-main-hand supplement marginal are exactly half their blood-date counterparts. Whole-suit and at-least-one-piece probabilities differ because ordinary acceptance is shared across slots. Native weapons, fixed sets and shield generation remain independent."
    calendar["quality_unchanged"] = "Preserve source quality/wear, fixed equipment floors and explicit main hands. Calendar enchantment growth and death-drop policy apply separately."
    calendar["anchors"] = []
    for day in (64, 65, 95, 96, 127, 128, 159, 160, 191, 192, 200, 223, 224, 255, 256, 287, 288, 319, 320, 351, 352, 353, 1280):
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

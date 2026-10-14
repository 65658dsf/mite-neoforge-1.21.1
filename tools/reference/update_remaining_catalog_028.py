"""Reconcile the final 60 default catalog leaves without rewriting historical snapshots. Author: NingMeng123."""
import argparse
import collections
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def write(path, data):
    (ROOT / path).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--verified", action="store_true")
    args = parser.parse_args()
    catalog = read("spec/item-catalog-015.json")
    prior_validation = catalog.get("remaining_registration_update_028", {})
    client_manual = prior_validation.get("client_manual", "pending")
    scope = read("spec/remaining-catalog-028.json")
    utility = {e["legacy_id"]: e["id"] for e in read("spec/utility-028.json")["entries"]}
    by_url = {e["url"]: e for e in scope["entries"]}
    for entry in catalog["entries"]:
        selected = by_url.get(entry["url"])
        if selected is None:
            continue
        actual = utility.get(selected["legacy_id"], selected["modern_id"])
        selected["modern_id"] = actual
        if "registration_baseline_015" not in entry:
            entry["registration_baseline_015"] = {k: entry.get(k) for k in ["current_registration", "status", "modern_id_is_proposal", "proposed_modern_id"]}
        entry.update(actual_modern_id=actual, proposed_modern_id=actual, modern_id_is_proposal=False,
                     current_registration="existing_mite" if actual.startswith("mite:") else "existing_vanilla",
                     status="added", status_label="已添加")
        entry["catalog_028_runtime_status"] = {"batch": "028", "existing_material_reconciled": entry["category"] == "材料",
                "automatic_tests": "passed" if args.verified else "pending", "client_manual": client_manual,
                "evidence": "docs/testing/028-remaining-content.md"}
    assert len(by_url) == 60
    included = [e for e in catalog["entries"] if e["default_included"]]
    assert len(included) == 347 and all(e["status"] == "added" for e in included)
    catalog["remaining_registration_update_028"] = {"author": "NingMeng123", "scope_leaves": 60,
        "existing_material_leaves": 18, "new_items": 36, "new_blocks": 13,
        "category_counts": scope["category_counts"], "default_included_total": 347, "excluded_leaves": 12,
        "current_labels": dict(collections.Counter(e["status"] for e in catalog["entries"])),
        "automatic_validation": "passed" if args.verified else "pending", "client_manual": client_manual,
        "semantics": "All included identities now exist. This is not a claim that all R196 monsters, curses, structures, world progression or global mechanisms are complete."}
    if "user_acceptance" in prior_validation:
        catalog["remaining_registration_update_028"]["user_acceptance"] = prior_validation["user_acceptance"]
    write("spec/item-catalog-015.json", catalog)
    write("spec/remaining-catalog-028.json", scope)
    fixture = {"author": "NingMeng123", "count": 347, "entries": [
        {"name": e["name"], "category": e["category"], "modern_id": e["actual_modern_id"],
         "source_kind": e["source_registry"]["kind"], "block_only": e["source_registry"]["id"] == 95}
        for e in included]}
    write("src/gametest/resources/mite/complete-catalog-028.json", fixture)
    p = ROOT / "docs/item-catalog-015.md"
    text = p.read_text(encoding="utf-8")
    lines = []
    for line in text.splitlines():
        if line.startswith("| "):
            for url, e in by_url.items():
                if f"]({url})" in line:
                    fields = line.split(" | ")
                    assert len(fields) == 5
                    fields[3], fields[4] = e["modern_id"], "已添加 |"
                    line = " | ".join(fields)
                    break
        lines.append(line)
    text = "\n".join(lines) + "\n"
    marker, next_marker = "## 028 剩余目录条目核对\n", "## 身份与现代适配边界"
    if marker in text:
        start = text.index(marker)
        text = text[:start] + text[text.index(next_marker, start):]
    section = '''## 028 剩余目录条目核对

本批补齐最后60个待办标签：红石6、杂项12、材料18、硬币6、武器6、装饰品12。其中18种材料及地狱沙砾已由此前批次注册，本次核对其实际身份并修正状态；四种原版花和原版铜门继续复用。实际新增36个物品、13个世界方块（包括无携带物品的盆栽大丽花），新增86条来源配方，另补投掷球实体与唱片声音/歌曲数据。

当前347个默认目录叶项均有实际映射并标“已添加”；12个废弃/冲突项仍排除，总叶数仍359。机器可读实时核对为 `remaining_registration_update_028`，历史summary和早期mechanism_status不覆写。材料18、函数方块和其他既有条目的来源机制范围仍按各自批次证据说明，不能由注册状态推导所有旧机制已完成。

石桶统一实际命名 `mite:<metal>_stone_bucket`，金属名为 `gold`，不同于旧建议的 `<metal>_bucket_stone`/`golden_bucket_stone`。完整范围、原版与现代适配及手测见 [028 剩余内容](testing/028-remaining-content.md)。R196玩家诅咒、专属怪物及其掉落、完整结构生成和成长仍属后续系统；已提供物品不冒称这些系统已完成。客户端手测等待用户反馈。

'''
    if client_manual == "user_confirmed":
        section = section.replace("客户端手测等待用户反馈。", "2026-10-14用户确认当前交付正常并授权提交GitHub，逐项截图、联机及长期测试未另行记录。")
    p.write_text(text.replace(next_marker, section + next_marker, 1), encoding="utf-8")
    print(json.dumps(catalog["remaining_registration_update_028"], ensure_ascii=False))


if __name__ == "__main__":
    main()

"""Maintain the requested four-category registration snapshot without rewriting historical totals.

Author: NingMeng123.
"""
import argparse
import collections
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--verified", action="store_true")
    parser.add_argument("--user-confirmed", action="store_true", help="Record the user's explicit manual-test acceptance")
    args = parser.parse_args()
    manual_status = "user_confirmed" if args.user_confirmed else "pending"
    catalog = read("spec/item-catalog-015.json")
    scope = read("spec/content-catalog-027.json")
    armor = read("src/main/resources/mite/armor-content-027.json")
    requested = {(row["category"], row["legacy_id"], row["name"]): row for row in scope["entries"]}
    chains = {row["legacy_id"]: row["id"] for row in armor["chains"]}
    changed = []
    for entry in catalog["entries"]:
        source = entry.get("source_registry")
        if not source or not entry["default_included"]:
            continue
        key = (entry["category"], source["id"], entry["name"])
        target = requested.get(key)
        dependency = source["id"] in chains or source["id"] == 252
        if target is None and not dependency:
            continue
        modern = target["modern_id"] if target else chains.get(source["id"], "mite:ancient_metal_block")
        if "registration_baseline_015" not in entry:
            entry["registration_baseline_015"] = {field: entry.get(field) for field in
                    ["current_registration", "status", "modern_id_is_proposal", "proposed_modern_id"]}
        entry.update(actual_modern_id=modern, proposed_modern_id=modern, modern_id_is_proposal=False,
                     current_registration="existing_mite" if modern.startswith("mite:") else "existing_vanilla",
                     status="added", status_label="已添加")
        entry["catalog_027_runtime_status"] = {
            "batch": "027", "requested_category": target is not None, "necessary_dependency": dependency,
            "registration": "verified_data_generation", "automatic_tests": "passed" if args.verified else "pending",
            "client_manual": manual_status, "evidence": "docs/testing/027-content-categories.md",
            "semantics": "已添加表示实际身份和本批代码接入；原mechanism_status与015历史summary保留，适配和未完成机制见对应来源文档。"
        }
        changed.append(entry)
    assert len([e for e in changed if e["catalog_027_runtime_status"]["requested_category"]]) == 111
    assert len(changed) == 120
    catalog["category_registration_update_027"] = {
        "author": "NingMeng123", "requested_leaves": 111, "category_counts": scope["category_counts"],
        "dependency_leaves": 9, "updated_leaves": 120,
        "current_labels": dict(collections.Counter(e["status"] for e in catalog["entries"])),
        "validation": "automatic_passed" if args.verified else "data_generation_passed_automatic_pending",
        "client_manual": manual_status, "scope": "箭、装备、钓鱼竿、功能性方块；另补8种链材料与远古金属块，关联胡萝卜钓竿不伪造百科叶项。"
    }
    (ROOT / "spec/item-catalog-015.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    markdown = ROOT / "docs/item-catalog-015.md"
    text = markdown.read_text(encoding="utf-8")
    rows = {entry["url"]: entry for entry in changed}
    lines = []
    for line in text.splitlines():
        if line.startswith("| "):
            for url, entry in rows.items():
                if f"]({url})" in line:
                    fields = line.split(" | ")
                    assert len(fields) == 5
                    fields[3] = entry["actual_modern_id"]
                    fields[4] = "已添加 |"
                    line = " | ".join(fields)
                    break
        lines.append(line)
    section = """## 027 四类物品与功能方块注册核对

本批核对 **111 个指定目录叶项**：箭10、装备61、钓鱼竿9、功能性方块31。已有炉体、符文石、地幔和工作台沿用实际ID；修正旧目录中燧石/铜/银台的过期建议，实际均为 `mite:<material>_crafting_table`。

新增指定身份共91个：9箭、8钓鱼竿、57装备、17功能方块。另新增7种链材料、远古金属块与8种关联胡萝卜钓竿，铁链及铁胡萝卜钓竿沿用原版；这16个必要关联新增身份中，只有8条链材料与远古金属块共9条属于本目录其他分类。关联胡萝卜钓竿没有原目录叶项，不为它们伪造百科页面。合计新增107个注册ID，目录总叶数仍359。

源无配方的锈铁板甲、锈铁箭和马铠不伪造配方；来源获取与创造限定情况分别见装备及箭的来源文档。护甲、品质、箭伤害/回收、钩材质、箱子所有权和砧磨损按本批规则接入；现代附魔、部分战利品和其他适配明确另行记录。这是当前注册核对，不将015历史 `mechanism_status` 或全模组完成状态改写为已完成。

具体说明与用户手测步骤见 [027 四类内容验收](testing/027-content-categories.md)。客户端由用户测试，未补写客户端通过记录；本批自动验证状态存于 JSON 的 `category_registration_update_027`，历史summary不覆盖。

"""
    if args.user_confirmed:
        section = section.replace("客户端由用户测试，未补写客户端通过记录", "用户已确认本批测试没有问题并授权提交；不补写未反馈的逐项、联机或长期运行结果")
    # The updater is repeatable and only replaces its own narrative section.
    text = "\n".join(lines) + "\n"
    marker = "## 027 四类物品与功能方块注册核对\n"
    if marker in text:
        start = text.index(marker)
        end = text.index("## 身份与现代适配边界", start)
        text = text[:start] + text[end:]
    text = text.replace("## 身份与现代适配边界", section + "## 身份与现代适配边界", 1)
    markdown.write_text(text, encoding="utf-8")
    print(json.dumps(catalog["category_registration_update_027"], ensure_ascii=False))


if __name__ == "__main__":
    main()

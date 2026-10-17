"""Extract reviewable content facts without copying third-party method bodies.

Author: NingMeng123. This is an inventory of declarations and call sites, not an
interpreter and not proof that a recipe, feature or probability is active.
"""
from __future__ import annotations
import argparse
import ast
from dataclasses import dataclass
import json
from pathlib import Path
import re

from analyze import ROOT, digest, write_json

TOKEN = re.compile(r'(?P<comment>//[^\n]*|/\*[\s\S]*?\*/)|(?P<string>"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\')|(?P<number>0[xX][\da-fA-F]+[lL]?|(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?[fFdDlL]?)|(?P<identifier>[A-Za-z_$][\w$]*)|(?P<operator>==|!=|<=|>=|&&|\|\||\+\+|--|>>>|>>|<<|[^\s])')


@dataclass(frozen=True)
class Token:
    text: str
    line: int
    offset: int


def tokenize(text):
    return [Token(m.group(), text.count("\n", 0, m.start()) + 1, m.start())
            for m in TOKEN.finditer(text) if m.lastgroup != "comment"]


def matching(tokens, start):
    pairs = {"(": ")", "[": "]", "{": "}"}
    stack = []
    for i in range(start, len(tokens)):
        value = tokens[i].text
        if value in pairs: stack.append(pairs[value])
        elif value in pairs.values():
            if not stack or value != stack.pop(): raise ValueError("Unbalanced expression")
            if not stack: return i
    raise ValueError("Unterminated expression")


def split_arguments(tokens):
    result, start, index = [], 0, 0
    while index < len(tokens):
        value = tokens[index].text
        if value in {"(", "[", "{"}:
            index = matching(tokens, index) + 1
            continue
        if value == ",":
            result.append(tokens[start:index])
            start = index + 1
        index += 1
    if start < len(tokens): result.append(tokens[start:])
    return result


def normalized(tokens):
    return " ".join(t.text for t in tokens)


def literal(tokens):
    text = "".join(t.text for t in tokens)
    if text in {"true", "false", "null"}: return {"kind": "literal", "value": {"true": True, "false": False, "null": None}[text]}
    if re.fullmatch(r'-?0[xX][\da-fA-F]+[lL]?', text): return {"kind": "literal", "value": int(text.rstrip("lL"), 16)}
    if re.fullmatch(r'-?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?[fFdDlL]?', text):
        clean = text.rstrip("fFdDlL")
        return {"kind": "literal", "value": float(clean) if any(x in clean.lower() for x in [".", "e"]) else int(clean)}
    if len(tokens) == 1 and text.startswith(('"', "'")):
        try: return {"kind": "character" if text.startswith("'") else "literal", "value": ast.literal_eval(text)}
        except (ValueError, SyntaxError): pass
    return None


def expression(tokens):
    """Only literal values are evaluated. Symbols, calls and arithmetic stay explicit."""
    if not tokens: return {"kind": "empty"}
    result = literal(tokens)
    if result is not None: return result
    text = "".join(t.text for t in tokens)
    if re.fullmatch(r'[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*', text):
        return {"kind": "symbol", "reference": text, "evaluation": "pending"}
    if tokens[0].text == "{" and matching(tokens, 0) == len(tokens) - 1:
        return {"kind": "array", "elements": [expression(part) for part in split_arguments(tokens[1:-1])]}
    if tokens[0].text == "(":
        end = matching(tokens, 0)
        if end == len(tokens) - 1: return expression(tokens[1:-1])
        if re.fullmatch(r'[\w$.[\]]+', "".join(t.text for t in tokens[1:end])):
            return {"kind": "cast", "type": text[1:text.index(")")], "value": expression(tokens[end + 1:])}
    if tokens[0].text == "new" and len(tokens) > 2:
        type_end = 1
        while type_end < len(tokens) and tokens[type_end].text not in {"(", "[", "{"}: type_end += 1
        type_name = "".join(t.text for t in tokens[1:type_end])
        if type_end < len(tokens) and tokens[type_end].text == "[":
            dims, pos = [], type_end
            while pos < len(tokens) and tokens[pos].text == "[":
                end = matching(tokens, pos)
                dims.append(expression(tokens[pos+1:end]))
                pos = end + 1
            if pos < len(tokens) and tokens[pos].text == "{" and matching(tokens, pos) == len(tokens)-1:
                return {"kind": "array", "type": type_name, "dimensions": dims,
                        "elements": [expression(part) for part in split_arguments(tokens[pos+1:-1])]}
            if pos == len(tokens): return {"kind": "array_allocation", "type": type_name, "dimensions": dims}
        if type_end < len(tokens) and tokens[type_end].text == "(":
            end = matching(tokens, type_end)
            base = {"kind": "construct", "type": type_name, "arguments": [expression(part) for part in split_arguments(tokens[type_end+1:end])]}
            return chained(base, tokens[end+1:])
    # A direct function/member call and any following fluent configuration calls.
    for index, token in enumerate(tokens):
        if token.text == "(":
            prefix = "".join(t.text for t in tokens[:index])
            if re.fullmatch(r'[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*', prefix):
                end = matching(tokens, index)
                base = {"kind": "call", "target": prefix, "arguments": [expression(part) for part in split_arguments(tokens[index+1:end])]}
                return chained(base, tokens[end+1:])
            break
    return {"kind": "expression", "expression": normalized(tokens), "evaluation": "pending"}


def chained(base, tail):
    calls, pos = [], 0
    while pos + 2 < len(tail) and tail[pos].text == "." and tail[pos+2].text == "(":
        end = matching(tail, pos+2)
        calls.append({"method": tail[pos+1].text, "arguments": [expression(part) for part in split_arguments(tail[pos+3:end])]})
        pos = end + 1
    if pos < len(tail):
        return {"kind": "partially_parsed", "base": base, "calls": calls, "tail_expression": normalized(tail[pos:]), "evaluation": "pending"}
    if calls: base["calls"] = calls
    return base


def base_construct(value):
    while value.get("kind") == "cast": value = value["value"]
    return value if value.get("kind") == "construct" else None


def statement_end(tokens, start):
    index = start
    while index < len(tokens):
        if tokens[index].text in {"(", "[", "{"}: index = matching(tokens, index) + 1
        elif tokens[index].text == ";": return index
        else: index += 1
    raise ValueError("Missing semicolon")


def scope_kinds(tokens):
    """Only distinguish top-level fields/static initializer from methods."""
    scope, result = [], []
    for index, token in enumerate(tokens):
        result.append(tuple(scope))
        if token.text == "{":
            if not scope: scope.append("class")
            elif len(scope) == 1 and index and tokens[index-1].text == "static": scope.append("static")
            else: scope.append("nested")
        elif token.text == "}" and scope: scope.pop()
    return result


def static_initializers(tokens, field_names):
    scopes = scope_kinds(tokens)
    found = []
    for index, token in enumerate(tokens[:-1]):
        if token.text not in field_names or tokens[index+1].text != "=": continue
        scope = scopes[index]
        if scope not in {("class",), ("class", "static")}: continue
        if index and tokens[index-1].text == ".": continue
        # A static initializer can declare loop/local variables shadowing fields.
        # Direct field assignments begin at a statement boundary, unlike `int i =`.
        if scope == ("class", "static") and index and tokens[index-1].text not in {"{", "}", ";"}: continue
        end = statement_end(tokens, index+2)
        found.append({"field": token.text, "line": token.line, "initializer": expression(tokens[index+2:end])})
    return found


def reference(class_info, line=None):
    result = {"class": class_info["name"], "source_file": class_info["source_file"], "class_sha256": class_info["sha256"]}
    if line is not None: result["decompiled_line"] = line
    return result


def recipe_calls(tokens, info):
    calls = []
    known = {"addShapelessRecipe", "addShapelessRecipes", "addRecipe", "addRecipes", "addSmelting", "addFurnaceRecipes", "addCraftingRecipes"}
    for index in range(1, len(tokens)-1):
        method = tokens[index].text
        if method not in known | {"a"} or tokens[index-1].text != "." or tokens[index+1].text != "(": continue
        end = matching(tokens, index+1)
        args_tokens = split_arguments(tokens[index+2:end])
        if method == "a" and (not args_tokens or not re.search(r'\bnew ye\b', normalized(args_tokens[0]))): continue
        start = index-2
        while start >= 2 and tokens[start-1].text == "." and re.fullmatch(r'[\w$]+', tokens[start-2].text): start -= 2
        receiver = "".join(t.text for t in tokens[max(start,0):index-1])
        tail_end = end+1
        while tail_end+2 < len(tokens) and tokens[tail_end].text == "." and tokens[tail_end+2].text == "(":
            tail_end = matching(tokens, tail_end+2)+1
        value = chained({"kind": "call", "target": receiver+"."+method, "arguments": [expression(part) for part in args_tokens]}, tokens[end+1:tail_end])
        calls.append({"source": reference(info, tokens[index].line), "call": value,
                      "status": "call_site_only_conditions_and_loops_require_review"})
    return calls


def enum_materials(text, info):
    tokens = tokenize(text)
    start = next(i for i,t in enumerate(tokens) if t.text == "{") + 1
    end = statement_end(tokens, start)
    result = []
    for values in split_arguments(tokens[start:end]):
        if len(values) < 3 or values[1].text != "(": continue
        params = [expression(p) for p in split_arguments(values[2:-1])]
        result.append({"symbol": info["name"]+"."+values[0].text, "source": reference(info, values[0].line),
                       "constructor_arguments": dict(zip(["durability", "enchantability", "max_quality", "name"], params))})
    return result


def catalog(workspace, output):
    details = json.loads((workspace / "r196/class-details.json").read_text(encoding="utf-8"))
    by_name = {c["name"]: c for c in details}
    source_lookup = {c["source_file"]: c for c in details}
    def source(info): return (workspace / "r196/decompiled" / (info["name"] + ".java")).read_text(encoding="utf-8")
    def inherits(name, expected):
        seen = set()
        while name and name not in seen:
            if name == expected: return True
            seen.add(name)
            name = by_name.get(name, {}).get("super")
        return False
    content = {"items": [], "blocks": [], "materials": [], "recipe_arrays": [], "recipe_calls": [], "generation_probability_candidates": []}
    for category, key in [("items", "Item.java"), ("blocks", "Block.java"), ("materials", "Material.java")]:
        info = source_lookup[key]
        fields = {f["name"]: f for f in info["fields"] if f["access"] & 8 and f["descriptor"].startswith("L") and inherits(f["descriptor"][1:-1], info["name"])}
        initializers = static_initializers(tokenize(source(info)), fields)
        matched = set()
        for row in initializers:
            matched.add(row["field"])
            constructor = base_construct(row["initializer"])
            content[category].append({"symbol": info["name"]+"."+row["field"], "declared_type": fields[row["field"]]["descriptor"],
                                      "source": reference(info,row["line"]), "initializer": row["initializer"],
                                      "constructor_source_file": by_name.get(constructor["type"], {}).get("source_file") if constructor else None,
                                      "status": "declaration_not_runtime_registration_proof"})
        content[category+"_unresolved_fields"] = sorted(set(fields)-matched)
    material_info = source_lookup["EnumEquipmentMaterial.java"]
    content["equipment_material_parameters"] = enum_materials(source(material_info), material_info)
    for info in details:
        if "Recipes" not in info["source_file"] and info["source_file"] != "CraftingManager.java": continue
        tokens = tokenize(source(info))
        content["recipe_calls"].extend(recipe_calls(tokens, info))
        fields = {f["name"] for f in info["fields"] if f["access"] & 8 and f["descriptor"].startswith("[")}
        for row in static_initializers(tokens, fields):
            content["recipe_arrays"].append({"symbol": info["name"]+"."+row["field"], "source": reference(info,row["line"]),
                                              "initializer": row["initializer"], "status": "matrix_not_expanded"})
    for info in details:
        if not re.search(r"WorldGen|Biome|ChunkProvider|SpawnerAnimals", info["source_file"]): continue
        for method in info["methods"]:
            code = method.get("code", {})
            random_refs = [r for r in code.get("references", []) if isinstance(r["target"], dict) and r["target"].get("owner") == "java/util/Random" and r["target"].get("name") in {"nextInt", "nextFloat", "nextDouble", "nextBoolean", "nextGaussian"}]
            if not random_refs: continue
            content["generation_probability_candidates"].append({"source": reference(info), "method": method["name"], "descriptor": method["descriptor"],
                                                                  "random_calls": random_refs, "numeric_literals": [v for v in code.get("literals", []) if isinstance(v["value"], (int,float))],
                                                                  "status": "random_call_and_nearby_method_literals_do_not_establish_probability"})
    content["metadata"] = {"author": "NingMeng123", "archive_sha256": json.loads((output / "summary.json").read_text(encoding="utf-8"))["archive_sha256"],
                           "source": "CFR 0.152 output cross-indexed with class metadata",
                           "counts": {k: len(v) for k,v in content.items() if isinstance(v,list)},
                           "limitations": ["No code is evaluated and no full methods are copied.", "Only numeric/string/boolean/null literals are resolved; arithmetic and symbols remain pending.", "Item constructor IDs may be offsets and are not asserted to be final legacy IDs.", "Recipe calls inside loops/conditionals are not expanded and are not final recipe counts.", "Inherited constructor behavior, configuration gates, conflicts and override order require semantic review.", "Generation literals are candidate evidence, not inferred final spawn rates."]}
    write_json(output / "content-catalog.json", content)
    return content["metadata"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path, default=ROOT / ".reference")
    parser.add_argument("--output", type=Path, default=ROOT / "reference-data")
    args = parser.parse_args()
    print(json.dumps(catalog(args.workspace, args.output), ensure_ascii=False, indent=2))


if __name__ == "__main__": main()

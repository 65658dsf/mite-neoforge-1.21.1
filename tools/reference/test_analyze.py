"""Independent binary fixtures and extraction adversarial tests. Author: NingMeng123."""
import io
import json
from pathlib import Path
import stat
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import analyze


def u1(v): return struct.pack(">B", v)
def u2(v): return struct.pack(">H", v)
def u4(v): return struct.pack(">I", v)


class Pool:
    def __init__(self): self.entries = []
    def add(self, tag, data, double=False):
        self.entries.append(u1(tag) + data)
        index = len(self.entries)
        if double: self.entries.append(b"")
        return index
    def utf(self, value):
        data = value.encode("utf-8")
        return self.add(1, u2(len(data)) + data)
    def cls(self, value): return self.add(7, u2(self.utf(value)))
    def packed(self): return u2(len(self.entries) + 1) + b"".join(self.entries)


def fixture():
    cp = Pool()
    this, parent = cp.cls("test/Fixture"), cp.cls("java/lang/Object")
    source_key, source_value = cp.utf("SourceFile"), cp.utf("Fixture.java")
    code_key, lines_key = cp.utf("Code"), cp.utf("LineNumberTable")
    method_name, descriptor = cp.utf("calculate"), cp.utf("()I")
    field_name, field_descriptor, constant_key = cp.utf("LIMIT"), cp.utf("I"), cp.utf("ConstantValue")
    integer = cp.add(3, struct.pack(">i", 144))
    cp.add(5, struct.pack(">q", 9876543210), double=True)
    cp.add(6, struct.pack(">d", 1.25), double=True)
    cp.add(4, struct.pack(">f", -0.5))
    text = cp.add(8, u2(cp.utf("nutrition")))
    owner = cp.cls("test/Rules")
    nt = cp.add(12, u2(cp.utf("apply")) + u2(descriptor))
    target = cp.add(10, u2(owner) + u2(nt))
    def attr(key, content): return u2(key) + u4(len(content)) + content
    # bipush -7, pop, ldc "nutrition", pop, invokestatic Rules.apply, ireturn
    code = bytes([16, 249, 87, 18, text, 87, 184]) + u2(target) + bytes([172])
    lines = u2(2) + u2(0) + u2(20) + u2(6) + u2(23)
    body = u2(1) + u2(0) + u4(len(code)) + code + u2(0) + u2(1) + attr(lines_key, lines)
    field = u2(0x19) + u2(field_name) + u2(field_descriptor) + u2(1) + attr(constant_key, u2(integer))
    method = u2(0x09) + u2(method_name) + u2(descriptor) + u2(1) + attr(code_key, body)
    raw = b"\xca\xfe\xba\xbe" + u2(0) + u2(51) + cp.packed() + u2(0x21) + u2(this) + u2(parent) + u2(0)
    raw += u2(1) + field + u2(1) + method + u2(1) + attr(source_key, u2(source_value))
    return raw


def zipped(entries):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as z:
        for name, data in entries: z.writestr(name, data)
    return stream.getvalue()


class ClassParserTest(unittest.TestCase):
    def test_source_members_constants_and_actual_instruction_references(self):
        parsed = analyze.parse_class(fixture())
        self.assertEqual(parsed["name"], "test/Fixture")
        self.assertEqual(parsed["source_file"], "Fixture.java")
        self.assertEqual(parsed["fields"][0]["constant_value"], 144)
        values = [c["value"] for c in parsed["constants"]]
        self.assertTrue(all(v in values for v in [144, 9876543210, 1.25, -0.5, "nutrition"]))
        method = parsed["methods"][0]
        self.assertEqual(method["descriptor"], "()I")
        self.assertEqual(method["code"]["lines"], [{"offset": 0, "line": 20}, {"offset": 6, "line": 23}])
        target = method["code"]["references"][-1]
        self.assertEqual((target["offset"], target["source_line"], target["opcode"]), (6, 23, 184))
        self.assertEqual(target["target"], {"kind": "method", "owner": "test/Rules", "name": "apply", "descriptor": "()I"})
        self.assertEqual(method["code"]["literals"][0]["value"], -7)
        json.dumps(parsed, allow_nan=False)

    def test_invalid_magic_and_every_truncation(self):
        original = fixture()
        with self.assertRaises(ValueError): analyze.parse_class(b"\x00" * 12)
        for length in range(len(original)):
            with self.subTest(length=length), self.assertRaises((ValueError, UnicodeDecodeError)):
                analyze.parse_class(original[:length])
        with self.assertRaises(ValueError): analyze.parse_class(original + b"unexpected")

    def test_modified_utf8_nul_and_surrogates(self):
        self.assertEqual(analyze.modified_utf8(b"a\xc0\x80b"), "a\0b")
        self.assertEqual(analyze.modified_utf8(b"\xed\xa0\xbd\xed\xb8\x80"), "\U0001f600")

    def test_switch_and_wide_instruction_boundaries(self):
        parser = analyze.ClassFile(b"")
        # tableswitch occupies bytes 0..23; bipush at byte 24 must remain aligned.
        table = bytes([170, 0, 0, 0]) + struct.pack(">iiiii", 24, 1, 2, 24, 24)
        refs, constants = parser.instructions(table + bytes([16, 42, 177]), [{"offset": 24, "line": 99}])
        self.assertEqual(constants, [{"offset": 24, "source_line": 99, "value": 42}])
        lookup = bytes([171, 0, 0, 0]) + struct.pack(">iiii", 20, 1, 3, 20)
        _, constants = parser.instructions(lookup + bytes([17, 1, 0, 177]), [])
        self.assertEqual(constants[0]["offset"], 20)
        self.assertEqual(constants[0]["value"], 256)
        wide = bytes([196, 132, 1, 0, 0, 1, 196, 21, 1, 0, 4, 172])
        _, constants = parser.instructions(wide, [])
        self.assertEqual(constants[0]["offset"], 10)
        with self.assertRaises(ValueError): parser.instructions(bytes([196, 0]), [])
        with self.assertRaises(ValueError): parser.instructions(table[:-1], [])

    def test_diff_detects_addition_removal_and_code_change(self):
        original = analyze.parse_class(fixture())
        modified = json.loads(json.dumps(original))
        modified["sha256"] = "different"
        modified["methods"][0]["code"]["sha256"] = "different_code"
        modified["fields"] = [{"name": "NEW", "descriptor": "J", "access": 1}]
        diff = analyze.compare_class(modified, original)
        self.assertEqual(diff["status"], "modified")
        self.assertEqual(diff["fields"]["added"], [{"name": "NEW", "descriptor": "J"}])
        self.assertEqual(diff["fields"]["removed"], [{"name": "LIMIT", "descriptor": "I"}])
        self.assertEqual(diff["methods"]["changed"], [{"name": "calculate", "descriptor": "()I"}])
        self.assertEqual(analyze.compare_class(original, original)["status"], "identical")
        self.assertEqual(analyze.compare_class(original, None)["status"], "added_class")


class SafeExtractionTest(unittest.TestCase):
    def test_traversal_and_platform_ambiguities(self):
        unsafe = ["../escape", "/tmp/escape", "C:/escape", "a/../escape", "a\\escape", "a/./b", "a//b", "NUL.class", "com1/file", "a./b", "a /b", "a\0b", "file:stream"]
        for name in unsafe:
            with self.subTest(name=name), self.assertRaises(ValueError): analyze.safe_name(name)
        self.assertEqual(str(analyze.safe_name("assets/minecraft/lang/en_US.lang")), "assets/minecraft/lang/en_US.lang")

    def test_collision_symlink_and_size_limits(self):
        data = zipped([("file.txt", b"1"), ("FILE.txt", b"2")])
        with zipfile.ZipFile(io.BytesIO(data)) as z, self.assertRaises(ValueError): analyze.validated_entries(z)
        entry = zipfile.ZipInfo("link")
        entry.create_system = 3
        entry.external_attr = (stat.S_IFLNK | 0o777) << 16
        with zipfile.ZipFile(io.BytesIO(zipped([(entry, b"../escape")]))) as z, self.assertRaises(ValueError):
            analyze.validated_entries(z)
        with zipfile.ZipFile(io.BytesIO(zipped([("large", b"12345")]))) as z:
            with patch.object(analyze, "MAX_ENTRY", 4), self.assertRaises(ValueError): analyze.validated_entries(z)
            with patch.object(analyze, "MAX_TOTAL", 4), self.assertRaises(ValueError): analyze.validated_entries(z)

    def test_archive_extracts_nested_language_and_skips_installers(self):
        nested = zipped([("assets/minecraft/lang/en_US.lang", b"# comment\nitem.test=Test\nkey=value=tail\n")])
        original = zipped([("installation/version/class/test/Fixture.class", fixture()), ("installation/Setup.exe", b"DO NOT RUN"),
                           ("installation/Setup.command", b"DO NOT RUN"), ("installation/README.txt", b"metadata"),
                           ("installation/resources.zip", nested)])
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            archive = root / "input.zip"
            archive.write_bytes(original)
            records, languages = analyze.extract_archive(archive, root / "result")
            self.assertTrue((root / "result/r196/classes/test/Fixture.class").is_file())
            self.assertEqual(languages[0]["entries"], [{"key": "item.test", "value": "Test"}, {"key": "key", "value": "value=tail"}])
            self.assertEqual([r["action"] for r in records if "Setup" in r["path"]], ["not_extracted", "not_extracted"])
            self.assertFalse(any(p.name.startswith("Setup") for p in (root / "result").rglob("*")))

    def test_reindex_ignores_stale_classes_from_other_archive(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            archive = root / "input.zip"
            archive.write_bytes(zipped([("installation/version/class/test/Fixture.class", fixture())]))
            workspace = root / "workspace"
            records, languages = analyze.extract_archive(archive, workspace)
            (workspace / "r196/classes/stale.class").write_bytes(b"not part of this archive")
            classes, summary = analyze.index(workspace, root / "index", archive, records, languages)
            self.assertEqual(summary["class_count"], 1)
            self.assertEqual(classes[0]["name"], "test/Fixture")

    def test_nested_traversal_never_writes_outside_destination(self):
        nested = zipped([("../../../escaped.txt", b"no")])
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            archive = root / "bad.zip"
            archive.write_bytes(zipped([("resources.zip", nested)]))
            with self.assertRaises(ValueError): analyze.extract_archive(archive, root / "result")
            self.assertFalse((root / "escaped.txt").exists())

    def test_existing_symlink_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            destination = root / "out"
            destination.mkdir()
            other = root / "other"
            other.mkdir()
            try:
                (destination / "link").symlink_to(other, target_is_directory=True)
            except OSError:
                self.skipTest("Host does not permit symlink creation")
            with self.assertRaises(ValueError): analyze.safe_write(destination, "link/escaped", b"no")
            self.assertFalse((other / "escaped").exists())

    def test_resolved_escape_guard_without_symlink_privileges(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            destination, outside = root / "out", root / "outside"
            with patch.object(analyze.Path, "resolve", side_effect=[destination, outside]), self.assertRaises(ValueError):
                analyze.safe_write(destination, "link/escaped", b"no")
            self.assertFalse(outside.exists())

    def test_decompiler_refuses_unverified_jar_before_launch(self):
        with tempfile.TemporaryDirectory() as temp:
            workspace = Path(temp)
            (workspace / "downloads").mkdir()
            (workspace / "downloads/cfr-0.152.jar").write_bytes(b"not a trusted tool")
            with patch.object(analyze.subprocess, "run") as launch, self.assertRaises(ValueError):
                analyze.decompile(workspace, "java")
            launch.assert_not_called()


if __name__ == "__main__": unittest.main()

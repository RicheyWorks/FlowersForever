import hashlib
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock
import xml.etree.ElementTree as ET

import classify_sast as gate


class SourceBoundSastGateTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.source = self.root / "src/main/java/example/Chooser.java"
        self.source.parent.mkdir(parents=True)
        self.source.write_text("class Chooser { /* reviewed desktop chooser */ }\n")
        (self.root / "pom.xml").write_text("<project><!-- reviewed build --></project>\n")
        self.plugin = self.root / "plugin.jar"
        self.plugin.write_bytes(b"synthetic fixture analyzer")
        self.report = self.root / "spotbugs.xml"
        self.manifest = self.root / "contexts.json"
        self.finding = {"type": "PATH_TRAVERSAL_IN", "priority": 2, "instance_hash": "exact-fingerprint",
                        "occurrence": 0, "occurrence_max": 0,
                        "source": "src/main/java/example/Chooser.java",
                        "caller_bindings": ["src/main/java/example/Chooser.java"],
                        "rationale": "Explicitly reviewed chooser suggestion, approved selection is the I/O path."}
        self.data = {"schema": 1, "source_hash_algorithm": "sha256-utf8-lf",
                     "analyzer": {"spotbugs_version": "4.8.6", "findsecbugs_version": "1.14.0",
                                  "findsecbugs_jar_sha256": hashlib.sha256(self.plugin.read_bytes()).hexdigest()},
                     "production_java_inventory": gate.production_inventory(self.root),
                     "build_configuration": gate.build_inventory(self.root),
                     "reviewed_findings": [self.finding]}
        self.write_manifest()
        self.write_report([self.finding])

    def write_manifest(self):
        self.manifest.write_text(json.dumps(self.data))

    def write_report(self, findings, version="4.8.6"):
        root = ET.Element("BugCollection", version=version)
        ET.SubElement(root, "FindBugsSummary", total_classes="1")
        ET.SubElement(root, "Errors", errors="0", missingClasses="0")
        for finding in findings:
            bug = ET.SubElement(root, "BugInstance", category="SECURITY", type=finding["type"],
                                priority=str(finding["priority"]), instanceHash=finding["instance_hash"],
                                instanceOccurrenceNum=str(finding["occurrence"]),
                                instanceOccurrenceMax=str(finding["occurrence_max"]))
            if all(field in finding for field in ("source", "class", "method", "method_signature")):
                ET.SubElement(bug, "SourceLine", primary="true", classname=finding["class"],
                              sourcepath=finding["source"].removeprefix("src/main/java/"))
                ET.SubElement(bug, "Method", primary="true", classname=finding["class"],
                              name=finding["method"], signature=finding["method_signature"])
        self.report.write_bytes(ET.tostring(root))

    def exact_context(self, review_kind):
        category, source, classname, method, signature = gate.CONTEXTUAL_REVIEW_SELECTORS[review_kind]
        path = self.root / source
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("/* specific reviewed contextual source fixture */\n")
        finding = dict(self.finding, type=category, source=source, **{
            "class": classname, "method": method, "method_signature": signature,
            "review_kind": review_kind, "caller_bindings": [source],
            "rationale": "Specific source/caller review of the named trust boundary, not a category exclusion."})
        self.data["production_java_inventory"] = gate.production_inventory(self.root)
        self.data["reviewed_findings"] = [finding]
        self.write_manifest()
        self.write_report([finding])
        return finding

    def classify(self):
        return gate.classify(self.report, self.manifest, self.root, self.plugin)

    def test_raw_reviewed_findings_remain_visible_and_low_findings_are_reported(self):
        self.write_report([self.finding, dict(self.finding, priority=3, instance_hash="low")])
        result = self.classify()
        self.assertEqual(result["raw_medium_high"], 1)
        self.assertEqual(result["reviewed_contextual_findings"], 1)
        self.assertEqual(result["unreviewed_medium_high"], 0)
        self.assertEqual(result["raw_low"], 1)

    def test_every_new_changed_duplicate_or_unused_finding_fails(self):
        for findings in ([], [self.finding, self.finding],
                         [dict(self.finding, priority=1)],
                         [dict(self.finding, instance_hash="new-http-path")],
                         [dict(self.finding, occurrence=1)],
                         [self.finding, dict(self.finding, type="SQL_INJECTION", instance_hash="unsafe")]):
            self.write_report(findings)
            with self.subTest(findings=findings), self.assertRaises(ValueError):
                self.classify()

    def test_changed_removed_or_new_production_caller_requires_review(self):
        self.source.write_text("class Chooser { /* changed trust boundary */ }\n")
        with self.assertRaises(ValueError):
            self.classify()
        self.source.write_text("class Chooser { /* reviewed desktop chooser */ }\n")
        added = self.source.with_name("HttpCaller.java")
        added.write_text("class HttpCaller { /* new HTTP caller, same raw fingerprint */ }\n")
        with self.assertRaises(ValueError):
            self.classify()
        added.unlink()
        self.source.unlink()
        with self.assertRaises(ValueError):
            self.classify()

    def test_lf_normalization_is_portable_without_ignoring_source_edits(self):
        self.source.write_bytes(b"class Chooser { /* reviewed desktop chooser */ }\r\n")
        (self.root / "pom.xml").write_bytes(b"<project><!-- reviewed build --></project>\r\n")
        self.assertEqual(self.classify()["production_java_files"], 1)

    def test_maven_configuration_change_requires_contextual_review(self):
        (self.root / "pom.xml").write_text("<project><!-- new generated source plugin --></project>\n")
        with self.assertRaisesRegex(ValueError, "Maven build/dependency"):
            self.classify()

    def test_nested_source_links_and_windows_reparse_directories_fail(self):
        original = Path.lstat
        for attributes in (SimpleNamespace(st_mode=gate.stat.S_IFLNK, st_file_attributes=0),
                           SimpleNamespace(st_mode=gate.stat.S_IFDIR, st_file_attributes=0x400)):
            def filesystem(path, *args, **kwargs):
                return attributes if path == self.source.parent else original(path, *args, **kwargs)
            with self.subTest(attributes=attributes), mock.patch.object(Path, "lstat", filesystem):
                with self.assertRaisesRegex(ValueError, "links/reparse points"):
                    self.classify()

    def test_changed_analyzer_and_broad_or_unbound_contexts_fail(self):
        self.write_report([self.finding], version="4.9.0")
        with self.assertRaises(ValueError):
            self.classify()
        self.write_report([self.finding])
        self.plugin.write_bytes(b"changed analyzer")
        with self.assertRaises(ValueError):
            self.classify()
        self.plugin.write_bytes(b"synthetic fixture analyzer")
        for change in ({"type": "SQL_INJECTION"}, {"priority": 1}, {"caller_bindings": []},
                       {"caller_bindings": ["unbound.java"]}, {"rationale": ""}):
            self.data["reviewed_findings"] = [dict(self.finding, **change)]
            self.write_manifest()
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.classify()

    def test_only_the_four_exact_contextual_boundaries_can_be_reviewed(self):
        for review_kind in gate.CONTEXTUAL_REVIEW_SELECTORS:
            with self.subTest(review_kind=review_kind):
                self.exact_context(review_kind)
                self.assertEqual(self.classify()["reviewed_contextual_findings"], 1)

    def test_context_category_priority_source_class_method_signature_and_kind_are_not_wildcards(self):
        changes = ({"type": "SQL_INJECTION"}, {"priority": 1},
                   {"source": "src/main/java/example/Chooser.java"}, {"class": "example.Other"},
                   {"method": "unsafeHttpCommand"}, {"method_signature": "(Ljava/lang/String;)V"},
                   {"review_kind": "generic_process"})
        for review_kind in gate.CONTEXTUAL_REVIEW_SELECTORS:
            original = self.exact_context(review_kind)
            for change in changes:
                changed = dict(original, **change)
                self.data["reviewed_findings"] = [changed]
                self.write_manifest()
                self.write_report([changed])
                with self.subTest(review_kind=review_kind, change=change), self.assertRaises(ValueError):
                    self.classify()

    def test_contextual_raw_locations_must_match_even_with_an_unchanged_fingerprint(self):
        original = self.exact_context("installed_app_relaunch")
        for change in ({"source": "src/main/java/example/Chooser.java"}, {"class": "example.Other"},
                       {"method": "unsafeHttpCommand"}, {"method_signature": "(Ljava/lang/String;)V"}):
            self.write_report([dict(original, **change)])
            with self.subTest(change=change), self.assertRaisesRegex(ValueError, "finding location differs"):
                self.classify()
        missing = dict(original)
        missing.pop("method_signature")
        self.write_report([missing])
        with self.assertRaisesRegex(ValueError, "primary contextual finding location"):
            self.classify()

    def test_contextual_findings_still_reject_new_stale_and_duplicate_fingerprints(self):
        original = self.exact_context("h2_metadata_identifier")
        for findings in ([], [original, original], [dict(original, instance_hash="changed")]):
            self.write_report(findings)
            with self.subTest(findings=findings), self.assertRaisesRegex(ValueError, "Security gate"):
                self.classify()
        self.write_report([original])
        self.data["reviewed_findings"] = [original, original]
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, "Duplicate reviewed fingerprint"):
            self.classify()


if __name__ == "__main__":
    unittest.main()

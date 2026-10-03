import hashlib
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock

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
        bugs = "".join(f'<BugInstance category="SECURITY" type="{f["type"]}" priority="{f["priority"]}" '
                       f'instanceHash="{f["instance_hash"]}" instanceOccurrenceNum="{f["occurrence"]}" '
                       f'instanceOccurrenceMax="{f["occurrence_max"]}"/>' for f in findings)
        self.report.write_text(f'<BugCollection version="{version}"><FindBugsSummary total_classes="1"/>'
                               '<Errors errors="0" missingClasses="0"/>' + bugs + '</BugCollection>')

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


if __name__ == "__main__":
    unittest.main()

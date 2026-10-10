"""Fault injection for the harness evidence gate; run with unittest discovery."""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

import verify_harness as harness


class HarnessEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.started = time.time() - 3

    def report(self, name="example.ContractTest", count=2, phase="surefire-reports", **attributes):
        folder = self.root / "target" / phase
        folder.mkdir(parents=True, exist_ok=True)
        suite = ET.Element("testsuite", name=name, tests=str(count), errors="0", failures="0", skipped="0")
        suite.attrib.update(attributes)
        for index in range(count):
            ET.SubElement(suite, "testcase", name="case" + str(index), classname=name)
        path = folder / ("TEST-" + name + ".xml")
        ET.ElementTree(suite).write(path, encoding="utf-8")
        return path

    def valid_reports(self):
        self.report()
        self.report(name="example.PackagedTest", phase="failsafe-reports")

    def test_requires_both_unit_and_packaged_evidence(self):
        with self.assertRaisesRegex(ValueError, "No unit"):
            harness.collect_reports(self.root, self.started)
        self.report()
        with self.assertRaisesRegex(ValueError, "No packaged"):
            harness.collect_reports(self.root, self.started)

    def test_separates_same_class_in_both_test_phases(self):
        self.report()
        self.report(phase="failsafe-reports")
        self.assertEqual(harness.collect_reports(self.root, self.started),
                         {"unit:example.ContractTest": 2, "packaged:example.ContractTest": 2})

    def test_rejects_stale_reports(self):
        self.valid_reports()
        path = self.root / "target/surefire-reports/TEST-example.ContractTest.xml"
        os.utime(path, (self.started - 30, self.started - 30))
        with self.assertRaisesRegex(ValueError, "Stale"):
            harness.collect_reports(self.root, self.started)

    def test_rejects_malformed_xml(self):
        path = self.report()
        path.write_text("<testsuite>", encoding="utf-8")
        with self.assertRaises(ET.ParseError):
            harness.collect_reports(self.root, self.started)

    def test_rejects_failed_skipped_and_flaky_suites(self):
        for attribute in ("failures", "errors", "skipped", "flakes"):
            with self.subTest(attribute=attribute):
                self.report(**{attribute: "1"})
                with self.assertRaises(ValueError):
                    harness.collect_reports(self.root, self.started)

    def test_rejects_failure_hidden_by_summary_counts(self):
        for tag in ("failure", "error", "skipped", "flakyFailure", "rerunError"):
            with self.subTest(tag=tag):
                path = self.report()
                tree = ET.parse(path)
                ET.SubElement(tree.getroot().find("testcase"), tag)
                tree.write(path)
                with self.assertRaisesRegex(ValueError, "test case"):
                    harness.collect_reports(self.root, self.started)

    def test_rejects_inconsistent_count_and_duplicate_suite(self):
        path = self.report()
        tree = ET.parse(path)
        tree.getroot().set("tests", "3")
        tree.write(path)
        with self.assertRaisesRegex(ValueError, "Inconsistent"):
            harness.collect_reports(self.root, self.started)
        path = self.report()
        path.with_name("TEST-duplicate.xml").write_bytes(path.read_bytes())
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            harness.collect_reports(self.root, self.started)

    def test_growth_in_one_class_cannot_hide_deleted_or_reduced_class(self):
        floors = {"unit:A": 3, "unit:B": 2}
        for suites in ({"unit:A": 5}, {"unit:A": 4, "unit:B": 1}):
            with self.subTest(suites=suites), self.assertRaisesRegex(ValueError, "floor dropped"):
                harness.validate_floors(suites, floors)
        harness.validate_floors({"unit:A": 4, "unit:B": 2, "unit:C": 3}, floors)

    def test_rejects_empty_or_nonpositive_floors(self):
        for floors in ({}, {"unit:A": 0}, {"unit:A": True}, {"unit:A": -1}):
            with self.subTest(floors=floors), self.assertRaises(ValueError):
                harness.validate_floors({}, floors)

    def test_source_digest_covers_untracked_modified_and_deleted_inputs(self):
        subprocess.run(["git", "init", "--quiet", str(self.root)], check=True)
        (self.root / "src").mkdir()
        path = self.root / "src/Example.java"
        path.write_text("original", encoding="utf-8")
        original = harness.source_digest(self.root)
        subprocess.run(["git", "add", "src"], cwd=self.root, check=True)
        self.assertEqual(original, harness.source_digest(self.root))
        path.write_text("modified", encoding="utf-8")
        self.assertNotEqual(original, harness.source_digest(self.root))
        path.unlink()
        self.assertNotEqual(original, harness.source_digest(self.root))

    def evidence_fixture(self):
        self.valid_reports()
        self.quality_fixture()
        suites = harness.collect_reports(self.root, self.started)
        (self.root / "config").mkdir()
        (self.root / harness.FLOORS).write_text(json.dumps(suites), encoding="utf-8")
        evidence = {"status": "passed", "sourceSha256": "source", "startedAt": self.started,
                    "suites": suites, "reportsSha256": harness.report_digest(self.root),
                    "quality": harness.quality_evidence(self.root, self.started)}
        harness.write_evidence(self.root, evidence)
        return evidence

    def quality_fixture(self):
        contents = ("<checkstyle><file name='Example.java'/></checkstyle>",
                    "<BugCollection><Errors errors='0' missingClasses='0'/></BugCollection>",
                    "<report><counter type='LINE' covered='85' missed='15'/>"
                    "<counter type='BRANCH' covered='65' missed='35'/></report>")
        for name, content in zip(harness.QUALITY_REPORTS, contents):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
        (self.root / "pom.xml").write_text(
            "<project xmlns='http://maven.apache.org/POM/4.0.0'><build><plugins>"
            "<plugin><artifactId>jacoco-maven-plugin</artifactId><limits>"
            "<limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.75</minimum></limit>"
            "<limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>0.55</minimum></limit>"
            "</limits></plugin></plugins></build></project>", encoding="utf-8")

    def test_counts_distinct_nested_cases_when_surefire_header_collides(self):
        self.valid_reports()
        path = self.root / "target/surefire-reports/TEST-example.ContractTest.xml"
        tree = ET.parse(path)
        root = tree.getroot()
        ET.SubElement(root, "testcase", name="case0", classname="NestedTest")
        tree.write(path)
        self.assertEqual(harness.collect_reports(self.root, self.started)["unit:example.ContractTest"], 3)
        ET.SubElement(root, "testcase", name="case0", classname="NestedTest")
        tree.write(path)
        with self.assertRaisesRegex(ValueError, "duplicate test identity"):
            harness.collect_reports(self.root, self.started)

    def test_requires_quality_reports_even_when_maven_returned_success(self):
        self.quality_fixture()
        self.assertAlmostEqual(harness.quality_evidence(self.root, self.started)["coverage"]["LINE"], .85)
        (self.root / harness.QUALITY_REPORTS[0]).unlink()
        with self.assertRaisesRegex(ValueError, "Missing or stale quality"):
            harness.quality_evidence(self.root, self.started)

    def test_rejects_quality_findings_or_coverage_drop(self):
        for index, text in ((0, "<checkstyle><file><error severity='error'/></file></checkstyle>"),
                            (1, "<BugCollection><Errors errors='0' missingClasses='0'/>"
                                "<BugInstance priority='2'/></BugCollection>"),
                            (2, "<report><counter type='LINE' covered='10' missed='90'/>"
                                "<counter type='BRANCH' covered='65' missed='35'/></report>")):
            with self.subTest(index=index):
                self.quality_fixture()
                (self.root / harness.QUALITY_REPORTS[index]).write_text(text, encoding="utf-8")
                with self.assertRaises(ValueError):
                    harness.quality_evidence(self.root, self.started)

    def test_refuses_source_edits_during_successful_maven_run(self):
        self.evidence_fixture()
        with patch.object(harness, "source_digest", side_effect=["source", "changed"]), \
                patch.object(harness.subprocess, "check_output", return_value="revision"), \
                patch.object(harness.shutil, "which", return_value="mvn"), \
                patch.object(harness.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)):
            result = harness.verify(self.root)
        self.assertEqual(result["status"], "failed")
        self.assertIn("Source changed", result["error"])

    def test_digest_includes_maven_configuration_and_ignored_sources(self):
        (self.root / ".gitignore").write_text("src/Ignored.java\n", encoding="utf-8")
        original = harness.source_digest(self.root)
        (self.root / "src").mkdir()
        (self.root / "src/Ignored.java").write_text("ignored source", encoding="utf-8")
        changed = harness.source_digest(self.root)
        self.assertNotEqual(original, changed)
        (self.root / ".mvn").mkdir()
        (self.root / ".mvn/maven.config").write_text("-DskipTests", encoding="utf-8")
        self.assertNotEqual(changed, harness.source_digest(self.root))

    def test_rejects_source_or_report_changes_after_success(self):
        self.evidence_fixture()
        with patch.object(harness, "source_digest", return_value="changed"):
            with self.assertRaisesRegex(ValueError, "current source"):
                harness.check_evidence(self.root)
        with patch.object(harness, "source_digest", return_value="source"):
            harness.check_evidence(self.root)
            path = self.root / "target/surefire-reports/TEST-example.ContractTest.xml"
            path.write_bytes(path.read_bytes().replace(b"case0", b"different-case"))
            with self.assertRaisesRegex(ValueError, "contents changed"):
                harness.check_evidence(self.root)

    def test_setup_failure_invalidates_previous_success(self):
        self.evidence_fixture()
        with patch.object(harness, "source_digest", side_effect=OSError("setup failed")):
            result = harness.verify(self.root)
        self.assertEqual(result["status"], "failed")
        with self.assertRaisesRegex(ValueError, "did not pass"):
            harness.check_evidence(self.root)

    def test_maven_failure_invalidates_previous_success(self):
        self.evidence_fixture()
        with patch.object(harness, "source_digest", return_value="source"), \
                patch.object(harness.subprocess, "check_output", return_value="revision"), \
                patch.object(harness.shutil, "which", return_value="mvn"), \
                patch.object(harness.subprocess, "run", return_value=subprocess.CompletedProcess([], 1)):
            result = harness.verify(self.root)
        self.assertEqual(result["status"], "failed")
        self.assertEqual(result["mavenExitCode"], 1)


if __name__ == "__main__":
    unittest.main()

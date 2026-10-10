"""Failure controls for the security evidence guards; no network is required."""
import base64
from datetime import datetime, timedelta, timezone
from email.utils import format_datetime
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import security_checks as checks


class EvidenceChecksTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.now = datetime(2026, 10, 3, tzinfo=timezone.utc)

    def write_json(self, name, value):
        path = self.root / name
        path.write_text(json.dumps(value), encoding="utf-8")
        return path

    def xml(self, classes=1, errors=0, missing=0, details=""):
        path = self.root / "spotbugs.xml"
        path.write_text(f'<BugCollection><FindBugsSummary total_classes="{classes}"/>'
                        f'<Errors errors="{errors}" missingClasses="{missing}">{details}</Errors>'
                        '</BugCollection>', encoding="utf-8")
        return path

    def test_complete_sast_and_zero_class_error_missing_controls(self):
        self.assertEqual(checks.spotbugs_complete(self.xml())["analyzed_classes"], 1)
        for parameters in ({"classes": 0}, {"errors": 1}, {"missing": 1},
                           {"details": "<Error/>"}, {"details": "<MissingClass/>"}):
            with self.subTest(parameters=parameters), self.assertRaises(ValueError):
                checks.spotbugs_complete(self.xml(**parameters))

    def test_missing_sast_summary_is_not_a_clean_scan(self):
        path = self.root / "spotbugs.xml"
        path.write_text("<BugCollection/>", encoding="utf-8")
        with self.assertRaises(ValueError):
            checks.spotbugs_complete(path)

    def archive(self, entries):
        path = self.root / "all.zip"
        with zipfile.ZipFile(path, "w") as archive:
            for name, record in entries:
                archive.writestr(name, record if isinstance(record, str) else json.dumps(record))
        payload = path.read_bytes()
        metadata = {"bucket": "osv-vulnerabilities", "name": "Maven/all.zip", "generation": "123",
                    "timeFinalized": (self.now - timedelta(hours=1)).isoformat(),
                    "size": str(len(payload)),
                    "md5Hash": base64.b64encode(hashlib.md5(payload).digest()).decode()}
        return path, metadata

    def advisory(self):
        return {"id": "GHSA-test", "modified": "2026-10-02T23:32:40Z",
                "affected": [{"package": {"ecosystem": "Maven", "name": "g:a"},
                                                "versions": ["1"]}]}

    def test_database_all_records_decode_and_checksum(self):
        path, metadata = self.archive([("one.json", self.advisory())])
        self.assertEqual(checks.database_valid(path, metadata, self.now)["json_records"], 1)
        for field, value in (("size", "1"), ("md5Hash", base64.b64encode(b"0" * 16).decode())):
            wrong = dict(metadata, **{field: value})
            with self.subTest(field=field), self.assertRaises(ValueError):
                checks.database_valid(path, wrong, self.now)

    def test_valid_json_with_native_type_errors_is_rejected(self):
        for field, value in (("modified", 42), ("modified", "not a timestamp"),
                             ("modified", "2026-10-02T23:32:40.1234567890Z"),
                             ("modified", "9999-12-31T23:59:59-01:00"),
                             ("affected", ["not an object"]), ("aliases", "not a list"),
                             ("severity", [{"type": "CVSS_V3", "score": 42}]),
                             ("severity", [{"type": "CVSS_V3", "score": "test", "source": "UNKNOWN"}]),
                             ("database_specific", []), ("references", ["not an object"])):
            record = dict(self.advisory(), **{field: value})
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                checks.advisory_valid(record)

    def test_duplicate_fields_and_non_json_numbers_are_rejected(self):
        for payload in ('{"id":"first","id":"second"}', '{"value":NaN}',
                        '{"database_specific":{"nested":[1e999]}}', '{"summary":"\\ud800"}',
                        '{"id":"first","\\u0069d":"second"}',
                        '{"database_specific":{"huge":' + '9' * 400 + '}}'):
            with self.subTest(payload=payload), self.assertRaises(ValueError):
                checks.strict_json(payload)
        for invalid_range in ({"type": "ECOSYSTEM", "events": [{"fixed": 42}]},
                              {"type": "ECOSYSTEM", "events": [{"introduced": "0", "fixed": "1"}]},
                              {"type": "UNKNOWN", "events": [{"introduced": "0"}]},
                              {"type": "ECOSYSTEM", "events": "not a list"}):
            record = self.advisory()
            record["affected"][0]["ranges"] = [invalid_range]
            with self.subTest(invalid_range=invalid_range), self.assertRaises(ValueError):
                checks.advisory_valid(record)

    def test_advisory_bytes_must_be_plain_utf8(self):
        text = json.dumps(self.advisory())
        self.assertEqual(checks.strict_json(text.encode("utf-8"))["id"], "GHSA-test")
        for payload in (text.encode("utf-16"), text.encode("utf-32"), b"\xef\xbb\xbf" + text.encode("utf-8")):
            with self.subTest(encoding=payload[:4]), self.assertRaises(ValueError):
                checks.strict_json(payload)

    def test_database_stale_future_generation_controls(self):
        _, metadata = self.archive([("one.json", self.advisory())])
        for delta in (timedelta(hours=-25), timedelta(minutes=6)):
            wrong = dict(metadata, timeFinalized=(self.now + delta).isoformat())
            with self.subTest(delta=delta), self.assertRaises(ValueError):
                checks.metadata_fresh(wrong, self.now)
        with self.assertRaises(ValueError):
            checks.metadata_fresh(dict(metadata, generation="123&other=value"), self.now)

    def test_unknown_typed_fields_cannot_silently_drop_advisories(self):
        for location in ("root", "package", "range", "affected", "severity", "reference", "credit"):
            record = self.advisory()
            if location == "root":
                record["unknown"] = True
            elif location == "package":
                record["affected"][0]["package"]["unknown"] = True
            elif location == "range":
                record["affected"][0]["ranges"] = [{"type": "ECOSYSTEM", "events": [{"introduced": "0"}], "unknown": True}]
            elif location == "affected":
                record["affected"][0]["unknown"] = True
            elif location == "severity":
                record["severity"] = [{"type": "CVSS_V3", "score": "test", "unknown": True}]
            elif location == "reference":
                record["references"] = [{"type": "WEB", "url": "https://example.org", "unknown": True}]
            else:
                record["credits"] = [{"name": "example", "unknown": True}]
            with self.subTest(location=location), self.assertRaises(ValueError):
                checks.advisory_valid(record)
        record = self.advisory()
        record["database_specific"] = {"arbitrary_provider_metadata": {"unknown": True}}
        self.assertEqual(checks.advisory_valid(record), 1)

    def test_empty_malformed_and_partial_database_cannot_pass(self):
        cases = ([], [("bad.json", "not JSON")],
                 [("good.json", self.advisory()), ("bad.json", "not JSON")],
                 [("one.json", {"id": "test", "affected": []})])
        for entries in cases:
            path, metadata = self.archive(entries)
            with self.subTest(entries=entries), self.assertRaises(ValueError):
                checks.database_valid(path, metadata, self.now)

    def test_advisory_identity_and_version_data_are_required(self):
        advisory = self.advisory()
        bad_range = self.advisory()
        bad_range["affected"][0].pop("versions")
        for entries in ([("one.json", advisory), ("two.json", advisory)],
                        [("one.json", bad_range)]):
            path, metadata = self.archive(entries)
            with self.subTest(entries=entries), self.assertRaises(ValueError):
                checks.database_valid(path, metadata, self.now)
        fresh = self.advisory()
        fresh["id"] = "NEW-ADVISORY-SOURCE"
        path, metadata = self.archive([("one.json", fresh)])
        self.assertEqual(checks.database_valid(path, metadata, self.now)["json_records"], 1)

    def component(self, group="org.example", name="artifact", version="1"):
        return {"group": group, "name": name, "version": version,
                "purl": f"pkg:maven/{group}/{name}@{version}"}

    def inventory(self, components, packages=None):
        sbom = self.write_json("application.cdx.json", {"bomFormat": "CycloneDX", "components": components})
        if packages is None:
            packages = [{"package": {"ecosystem": "Maven", "name": c["name"], "version": c["version"]}}
                        for c in components]
        report = self.write_json("osv.json", {"results": [{"source": {"type": "sbom", "path": str(sbom)},
                                                         "packages": packages}]})
        return sbom, report

    def test_exact_inventory_identity_not_just_package_count(self):
        sbom, report = self.inventory([self.component()])
        self.assertEqual(checks.inventory_complete(sbom, report)["scanned_packages"], 1)
        wrong = [{"package": {"ecosystem": "Maven", "name": "other", "version": "1"}}]
        sbom, report = self.inventory([self.component()], wrong)
        with self.assertRaises(ValueError):
            checks.inventory_complete(sbom, report)

    def test_missing_unmapped_duplicate_and_namespace_ambiguity_rejected(self):
        for components in ([], [dict(self.component(), purl="")],
                           [dict(self.component(), version="2")],
                           [self.component(), self.component()],
                           [self.component(), self.component(group="different.group")]):
            sbom, report = self.inventory(components)
            with self.subTest(components=components), self.assertRaises(ValueError):
                checks.inventory_complete(sbom, report)
        sbom, report = self.inventory([self.component()], [])
        with self.assertRaises(ValueError):
            checks.inventory_complete(sbom, report)

    def test_findings_preserved_and_wrong_source_rejected(self):
        packages = [{"package": {"ecosystem": "Maven", "name": "artifact", "version": "1"},
                     "vulnerabilities": [{"id": "GHSA-test"}]}]
        sbom, report = self.inventory([self.component()], packages)
        self.assertEqual(checks.inventory_complete(sbom, report)["vulnerability_ids"], ["GHSA-test"])
        with self.assertRaises(ValueError):
            checks.inventory_complete(sbom, report, fail_on_findings=True)
        data = json.loads(report.read_text())
        data["results"][0]["source"]["path"] = str(self.root / "other.cdx.json")
        self.write_json("osv.json", data)
        with self.assertRaises(ValueError):
            checks.inventory_complete(sbom, report)

    def test_live_exact_group_identity_and_paginated_full_advisories(self):
        sbom, _ = self.inventory([self.component(group="g", name="a"),
                                 self.component(group="other", name="a")])
        calls = []
        def fetch(url, payload):
            calls.append((url, payload))
            if url.endswith("querybatch"):
                if len(payload["queries"]) == 2:
                    self.assertEqual([q["package"]["name"] for q in payload["queries"]], ["g:a", "other:a"])
                    return {"results": [{"next_page_token": "page-2"}, {}]}, {}
                self.assertEqual(payload["queries"][0]["page_token"], "page-2")
                return {"results": [{"vulns": [{"id": "GHSA-test", "modified": self.advisory()["modified"]}]}]}, {}
            return self.advisory(), {}
        report = self.root / "live.json"
        result = checks.live_osv(sbom, report, fetch=fetch)
        self.assertEqual(result["scanned_packages"], 2)
        self.assertEqual(result["vulnerability_ids"], ["GHSA-test"])
        self.assertEqual(len(calls), 3)
        self.assertEqual(json.loads(report.read_text())["advisories"], [self.advisory()])
        with self.assertRaises(ValueError):
            checks.live_osv(sbom, report, fail_on_findings=True, fetch=fetch)
        self.assertTrue(json.loads(report.read_text())["completed"])

    def test_live_incomplete_malformed_duplicate_and_repeated_pages_fail(self):
        sbom, _ = self.inventory([self.component()])
        cases = ({}, {"results": []}, {"results": [{"unknown": True}]},
                 {"results": [{"vulns": "not-a-list"}]},
                 {"results": [{"vulns": [{"id": "../../other", "modified": "x"}]}]},
                 {"results": [{"vulns": [{"id": "GHSA-test"}]}]},
                 {"results": [{"next_page_token": "repeat"}]})
        for response in cases:
            with self.subTest(response=response), self.assertRaises(ValueError):
                checks.live_osv(sbom, self.root / "live.json", fetch=lambda *_: (response, {}))
        with self.assertRaises(OSError):
            checks.live_osv(sbom, self.root / "live.json", fetch=lambda *_: (_ for _ in ()).throw(OSError("offline")))

    def test_live_advisory_mismatch_and_invalid_sbom_fail(self):
        sbom, _ = self.inventory([self.component(group="g", name="a")])
        for mutation in ({"id": "different"}, {"modified": "2026-01-01T00:00:00Z"},
                         {"affected": []}, {"affected": "bad"}):
            def fetch(url, payload):
                if url.endswith("querybatch"):
                    return {"results": [{"vulns": [{"id": "GHSA-test", "modified": self.advisory()["modified"]}]}]}, {}
                return dict(self.advisory(), **mutation), {}
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                checks.live_osv(sbom, self.root / "live.json", fetch=fetch)
        for components in ([], [self.component(), self.component()],
                           [dict(self.component(), version="wrong")], [dict(self.component(), purl="")]):
            sbom, _ = self.inventory(components)
            with self.subTest(components=components), self.assertRaises(ValueError):
                checks.live_osv(sbom, self.root / "live.json", fetch=lambda *_: ({"results": [{}]}, {}))

    def test_live_api_rejects_stale_http_evidence_and_bad_json(self):
        class Response:
            status = 200
            def __enter__(self): return self
            def __exit__(self, *_): pass
            def geturl(self): return "https://api.osv.dev/v1/querybatch"
            def read(self, _): return self.payload
        response = Response()
        response.payload = b'{"results":[]}'
        response.headers = {"Date": "Fri, 03 Oct 2025 00:00:00 GMT"}
        with patch.object(checks, "urlopen", return_value=response), self.assertRaises(ValueError):
            checks.live_api_json(response.geturl(), {"queries": []})
        with self.assertRaises(ValueError):
            checks.live_api_json("https://example.org/querybatch", {})
        response.headers = {"Date": format_datetime(datetime.now(timezone.utc), usegmt=True)}
        with patch.object(checks, "urlopen", return_value=response):
            self.assertEqual(checks.live_api_json(response.geturl(), {})[0], {"results": []})
            response.payload = b'{"results":[],"results":[]}'
            with self.assertRaises(ValueError):
                checks.live_api_json(response.geturl(), {})
            response.payload = b'{"results":[]}'
            response.status = 503
            with self.assertRaises(ValueError):
                checks.live_api_json(response.geturl(), {})

    def test_live_batches_cover_every_full_maven_identity(self):
        sbom, _ = self.inventory([self.component(group="g" + str(i)) for i in range(101)])
        sizes = []
        def fetch(url, payload):
            self.assertTrue(url.endswith("querybatch"))
            sizes.append(len(payload["queries"]))
            return {"results": [{} for _ in payload["queries"]]}, {}
        result = checks.live_osv(sbom, self.root / "live.json", fail_on_findings=True, fetch=fetch)
        self.assertEqual(sizes, [100, 1])
        self.assertEqual(result["scanned_packages"], 101)
        self.assertEqual(result["vulnerability_ids"], [])


if __name__ == "__main__":
    unittest.main()

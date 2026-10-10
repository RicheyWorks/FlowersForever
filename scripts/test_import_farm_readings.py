import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from decimal import Decimal

spec = importlib.util.spec_from_file_location("farm_readings_import", Path(__file__).with_name("import-farm-readings.py"))
importer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(importer)


class ReadingsImportTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)

    def file(self, name, text):
        path = Path(self.directory.name) / name
        path.write_text(text, encoding="utf-8")
        return path

    def row(self, **changes):
        row = dict(deviceId="sensor-1", metric="air.temperature", unit="C", value="21.123456789012",
                   observedAt="2026-10-01T12:00:00Z")
        row.update(changes)
        return row

    def test_csv_mapping_and_constants_preserve_exact_numbers_and_stable_identity(self):
        path = self.file("logger.csv", 'time,temp_c,note\n2026-10-01T12:00:00Z,21.123456789012,"bed, north"\n')
        mapping = dict(observedAt="time", value="temp_c")
        constants = dict(deviceId="sensor-1", metric="air.temperature", unit="C")
        first = importer.load_rows(path, mapping=mapping, constants=constants)[0]
        self.assertEqual(first["value"], Decimal("21.123456789012"))
        self.assertIn('"note":"bed, north"', first["rawFrame"])
        self.assertIn('"value":21.123456789012', importer.encode_json(first))
        path.write_text('time,temp_c,note\n2026-10-01T12:00:00Z,22,"bed, north"\n', encoding="utf-8")
        changed = importer.load_rows(path, mapping=mapping, constants=constants)[0]
        self.assertEqual(first["readingKey"], changed["readingKey"])
        self.assertNotEqual(first["value"], changed["value"])

    def test_json_missing_and_error_readings_keep_absence_instead_of_zero(self):
        rows = [self.row(quality="MISSING"), self.row(quality="ERROR")]
        for row in rows:
            del row["value"]
        records = importer.load_rows(self.file("readings.json", json.dumps(rows)))
        self.assertNotIn("value", records[0])
        self.assertNotIn("value", records[1])

    def test_json_numeric_precision_does_not_pass_through_float(self):
        text = '[{"deviceId":"a","metric":"flow","unit":"L/min","observedAt":"2026-10-01T12:00:00Z","value":999999999999999999.123456789012}]'
        row = importer.load_rows(self.file("readings.json", text))[0]
        self.assertEqual(row["value"], Decimal("999999999999999999.123456789012"))
        self.assertIn("999999999999999999.123456789012", importer.encode_json(row))

    def test_rejects_bad_precision_nonfinite_and_boolean_values(self):
        for value in ("NaN", "Infinity", "1e1000000", "1e-1000000", "0.0000000000001", True):
            with self.subTest(value=value), self.assertRaises(ValueError):
                importer.load_rows(self.file("bad.json", json.dumps([self.row(value=value)])))
        with self.assertRaises(ValueError):
            importer.encode_json(Decimal("NaN"))

    def test_rejects_missing_units_timezone_and_contradictory_constants(self):
        for changes in (dict(unit=""), dict(observedAt="2026-10-01T12:00:00"), dict(quality="MISSING")):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                importer.load_rows(self.file("bad.json", json.dumps([self.row(**changes)])))
        with self.assertRaises(ValueError):
            importer.load_rows(self.file("bad.json", json.dumps([self.row()])), constants=dict(unit="F"))

    def test_rejects_duplicate_headers_malformed_width_and_unknown_mapping(self):
        for text in ("time,time\na,b\n", "time,value\na\n", "time,value\na,b,c\n"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                importer.load_rows(self.file("bad.csv", text))
        with self.assertRaises(ValueError):
            importer.load_rows(self.file("a.json", json.dumps([self.row()])), mapping={"pumpCommand": "value"})
        with self.assertRaises(ValueError):
            importer.load_rows(self.file("a.json", "{}"))

    def test_raw_frame_exact_text_and_byte_limit(self):
        exact = " \ufeffraw α\r\n "
        row = importer.load_rows(self.file("a.json", json.dumps([self.row(rawFrame=exact)])))[0]
        self.assertEqual(row["rawFrame"], exact)
        with self.assertRaises(ValueError):
            importer.load_rows(self.file("a.json", json.dumps([self.row(rawFrame="é" * 4097)])))

    def test_duplicate_json_fields_cannot_silently_replace_a_measurement_or_mapping(self):
        with self.assertRaises(ValueError):
            importer.load_rows(self.file("a.json", '[{"value":21,"value":22}]'))
        with self.assertRaises(ValueError):
            importer.decode_input_json('{"value":"temperature_c","value":"humidity"}')

    def test_default_preview_does_not_make_network_requests(self):
        path = self.file("a.json", json.dumps([self.row()]))
        self.assertEqual(importer.main([str(path), "--source-id", "sample"]), 0)

    def test_credentials_cannot_redirect_or_travel_over_remote_plain_http(self):
        token = "test-only-token-123456789012345"
        for url in ("http://farm.example", "https://user:password@farm.example", "file:///tmp/farm",
                    "https://farm.example/path", "https://farm.example?token=secret"):
            with self.subTest(url=url), self.assertRaises(ValueError):
                importer.FarmClient(url, token)
        with self.assertRaises(ValueError):
            importer.NoRedirects().redirect_request(None, None, 302, "redirect", {}, "https://other.example")
        self.assertIsNotNone(importer.FarmClient("http://127.0.0.1:8080", token))

    def test_client_uses_fresh_csrf_and_sends_bounded_measurement_batches_only(self):
        client = importer.FarmClient("http://localhost:8080", "test-only-token-123456789012345")
        calls = []
        def request(route, body=None, extra_headers=None):
            calls.append((route, body, extra_headers))
            if route == "/api/auth/csrf":
                return dict(headerName="X-XSRF-TOKEN", token="csrf-sample")
            return dict(success=True, output=dict(receipt=dict(inserted=len(body["arguments"]["readings"]), replayed=0)))
        client.request = request
        rows = [self.row(readingKey=str(i)) for i in range(51)]
        self.assertEqual(client.send("source", rows), (51, 0))
        self.assertEqual(len(calls), 6)
        for route, body, headers in calls[1::2]:
            self.assertEqual(body["action"], "record-readings")
            self.assertLessEqual(len(body["arguments"]["readings"]), 25)
            self.assertEqual(headers["X-XSRF-TOKEN"], "csrf-sample")


if __name__ == "__main__":
    unittest.main()

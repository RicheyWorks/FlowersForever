#!/usr/bin/env python3
"""Preview or send CSV/JSON logger records through the protected farm readings contract."""

import argparse
import base64
import csv
from datetime import datetime
from decimal import Decimal, InvalidOperation
import hashlib
from http.cookiejar import CookieJar
import io
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import HTTPCookieProcessor, HTTPRedirectHandler, Request, build_opener
import uuid

FIELDS = {"readingKey", "deviceId", "metric", "unit", "value", "quality", "observedAt", "location", "rawFrame"}
MAX_FILE_BYTES = 10 * 1024 * 1024


def decode_input_json(text):
    def unique_object(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("JSON input must not repeat object field names")
            result[key] = value
        return result
    return json.loads(text, parse_float=Decimal, parse_int=Decimal, object_pairs_hook=unique_object,
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError("Nonfinite JSON number")))


def encode_json(value):
    """Emit Decimal as an exact JSON numeric token, without an intermediate binary float."""
    if isinstance(value, Decimal):
        if not value.is_finite():
            raise ValueError("Reading values must be finite")
        return str(value)
    if isinstance(value, dict):
        return "{" + ",".join(json.dumps(str(key)) + ":" + encode_json(item) for key, item in value.items()) + "}"
    if isinstance(value, list):
        return "[" + ",".join(encode_json(item) for item in value) + "]"
    return json.dumps(value, ensure_ascii=False, allow_nan=False)


def load_rows(path, delimiter=",", mapping=None, constants=None):
    mapping = mapping or {}
    constants = constants or {}
    if not set(mapping).issubset(FIELDS) or not all(isinstance(value, str) for value in mapping.values()):
        raise ValueError("Column mapping must map declared reading fields to text column names")
    if path.stat().st_size > MAX_FILE_BYTES:
        raise ValueError("Logger files must be at most 10 MiB")
    text = path.read_text(encoding="utf-8-sig", errors="strict")
    if path.suffix.lower() == ".json":
        rows = decode_input_json(text)
        if not isinstance(rows, list):
            raise ValueError("JSON input must be an array of logger objects")
    elif path.suffix.lower() == ".csv":
        reader = csv.DictReader(io.StringIO(text, newline=""), delimiter=delimiter)
        if not reader.fieldnames or len(reader.fieldnames) != len(set(reader.fieldnames)):
            raise ValueError("CSV requires unique named columns")
        rows = list(reader)
        if any(None in row or None in row.values() for row in rows):
            raise ValueError("CSV row width differs from the headers")
    else:
        raise ValueError("Use a UTF-8 .csv or .json file")
    result = []
    for original in rows:
        if not isinstance(original, dict):
            raise ValueError("Every logger row must be an object")
        row = {field: original.get(mapping.get(field, field)) for field in FIELDS
               if original.get(mapping.get(field, field)) not in (None, "")}
        for field, constant in constants.items():
            if constant is not None:
                if field in row and row[field] != constant:
                    raise ValueError("A constant disagrees with a populated input column")
                row[field] = constant
        for field in ("deviceId", "metric", "unit", "observedAt"):
            if not isinstance(row.get(field), str) or not row[field].strip():
                raise ValueError("Each row needs deviceId, metric, unit, and observedAt with an explicit offset")
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}T.+(?:Z|[+-]\d{2}:\d{2})", row["observedAt"]):
            raise ValueError("Observation time must include an ISO-8601 offset")
        datetime.fromisoformat(row["observedAt"].replace("Z", "+00:00"))
        quality = row.setdefault("quality", "GOOD")
        if quality not in ("GOOD", "SUSPECT", "MISSING", "ERROR"):
            raise ValueError("Use GOOD, SUSPECT, MISSING, or ERROR quality")
        if "value" in row:
            if isinstance(row["value"], bool):
                raise ValueError("Reading values must be numeric, not boolean")
            try:
                value = Decimal(str(row["value"]))
            except InvalidOperation as error:
                raise ValueError("Reading value must be numeric") from error
            if not value.is_finite() or value.adjusted() > 17:
                raise ValueError("Reading number exceeds the finite 18-integer-digit contract")
            # The number may carry redundant trailing zeroes, but cannot lose precision.
            digits = value.as_tuple()
            fractional = max(0, -digits.exponent)
            trailing = 0
            for digit in reversed(digits.digits):
                if digit != 0:
                    break
                trailing += 1
            if value != 0 and fractional - trailing > 12:
                raise ValueError("Reading number exceeds 12 fractional digits")
            row["value"] = value
        elif quality in ("GOOD", "SUSPECT"):
            raise ValueError("GOOD and SUSPECT rows require a value")
        if quality == "MISSING" and "value" in row:
            raise ValueError("MISSING rows must omit value; missing is not zero")
        if "readingKey" not in row:
            identity = encode_json([row["deviceId"], row["metric"], row["observedAt"]]).encode("utf-8")
            row["readingKey"] = hashlib.sha256(identity).hexdigest()
        row.setdefault("rawFrame", encode_json(original))
        if not isinstance(row["rawFrame"], str) or len(row["rawFrame"].encode("utf-8")) > 8192:
            raise ValueError("Each raw frame must fit 8192 UTF-8 bytes")
        result.append(row)
    if not result:
        raise ValueError("No logger rows were found")
    return result


class NoRedirects(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("Redirects are refused; configure the exact farm URL")


class FarmClient:
    def __init__(self, base_url, token, username=None, password=None):
        parsed = urlsplit(base_url)
        if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
            raise ValueError("Use a farm origin URL without credentials, path, query, or fragment")
        if parsed.scheme not in ("http", "https") or not parsed.hostname:
            raise ValueError("Use an HTTP(S) farm origin")
        if parsed.scheme == "http" and parsed.hostname not in ("localhost", "127.0.0.1", "::1"):
            raise ValueError("Remote farm connections require HTTPS")
        if not token or len(token) < 24:
            raise ValueError("Set FLOWERFARM_HARNESS_TOKEN to the farm's harness token")
        if bool(username) != bool(password):
            raise ValueError("Supply both FLOWERFARM_HTTP_USER and FLOWERFARM_HTTP_PASSWORD when required")
        self.base = base_url.rstrip("/")
        self.token = token
        self.authorization = None if not username else "Basic " + base64.b64encode(
            (username + ":" + password).encode("utf-8")).decode("ascii")
        self.opener = build_opener(NoRedirects(), HTTPCookieProcessor(CookieJar()))

    def request(self, route, body=None, extra_headers=None):
        headers = {"Accept": "application/json"}
        if self.authorization:
            headers["Authorization"] = self.authorization
        headers.update(extra_headers or {})
        data = None if body is None else encode_json(body).encode("utf-8")
        if data is not None:
            headers["Content-Type"] = "application/json"
        with self.opener.open(Request(self.base + route, data=data, headers=headers), timeout=30) as response:
            payload = response.read(2 * 1024 * 1024 + 1)
        if len(payload) > 2 * 1024 * 1024:
            raise ValueError("Farm response exceeded the client limit")
        return json.loads(payload)

    def send(self, source_id, rows):
        inserted = replayed = 0
        for offset in range(0, len(rows), 25):
            csrf = self.request("/api/auth/csrf")
            response = self.request("/api/harness/farm-readings/commands", {
                "requestId": str(uuid.uuid4()), "action": "record-readings",
                "arguments": {"source-id": source_id, "readings": rows[offset:offset + 25]},
            }, {"X-FlowersForever-Harness-Token": self.token, csrf["headerName"]: csrf["token"]})
            if not response.get("success"):
                raise ValueError("Farm rejected a readings batch")
            receipt = response["output"]["receipt"]
            inserted += receipt["inserted"]
            replayed += receipt["replayed"]
        return inserted, replayed


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", type=Path)
    parser.add_argument("--source-id", required=True)
    parser.add_argument("--columns", type=Path, help="JSON mapping from reading field to source column")
    parser.add_argument("--delimiter", choices=("comma", "tab", "semicolon"), default="comma")
    for field in ("device-id", "metric", "unit", "location"):
        parser.add_argument("--" + field)
    parser.add_argument("--url", default="http://127.0.0.1:8080")
    parser.add_argument("--send", action="store_true", help="Write after previewing; default only previews")
    args = parser.parse_args(argv)
    mapping = None if args.columns is None else decode_input_json(args.columns.read_text(encoding="utf-8-sig"))
    if mapping is not None and not isinstance(mapping, dict):
        raise ValueError("Column mapping must be a JSON object")
    rows = load_rows(args.file, {"comma": ",", "tab": "\t", "semicolon": ";"}[args.delimiter], mapping,
                     {"deviceId": args.device_id, "metric": args.metric, "unit": args.unit, "location": args.location})
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._:/-]{0,79}", args.source_id):
        raise ValueError("Use a source identifier of 1-80 letters, numbers, dots, underscores, colons, slashes, or hyphens")
    print(f"Prepared {len(rows)} readings from {args.source_id}; metrics: " + ", ".join(sorted({row["metric"] for row in rows})))
    if not args.send:
        print("Preview only. Add --send to submit these readings.")
        return 0
    client = FarmClient(args.url, os.getenv("FLOWERFARM_HARNESS_TOKEN"),
                        os.getenv("FLOWERFARM_HTTP_USER"), os.getenv("FLOWERFARM_HTTP_PASSWORD"))
    inserted, replayed = client.send(args.source_id, rows)
    print(f"Saved {inserted} new readings; {replayed} exact retries already present.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except HTTPError as error:
        print(f"Farm returned HTTP {error.code}. Earlier successful batches remain saved; retry the same file after correcting the issue.", file=sys.stderr)
        sys.exit(1)
    except (ValueError, OSError, KeyError, csv.Error) as error:
        print(f"Readings import did not finish: {error}", file=sys.stderr)
        sys.exit(1)

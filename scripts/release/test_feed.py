"""Feed contract tests shared across Python, Kotlin, and TypeScript.

These fixtures mirror release/fixtures/feed_valid.json. Keep all three
implementations in sync: any rule change here MUST update UpdateFeedTest.kt
and updates.test.ts with the same case.
"""

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from feed import (  # noqa: E402
    MAX_PAYLOAD_BYTES,
    canonical_payload_bytes,
    decode_envelope,
    select_candidate,
    stable_waiting,
    validate_payload,
)

FIXTURE = Path(__file__).resolve().parents[2] / "release" / "fixtures" / "feed_valid.json"


def load_valid():
    return json.loads(FIXTURE.read_text())


class FeedContractTest(unittest.TestCase):
    def test_valid_fixture_passes(self):
        validate_payload(load_valid(), expected_repo="furkancodes/lamphaus")

    def test_rejects_oversized_payload(self):
        import base64

        raw = base64.b64encode(b"x" * (MAX_PAYLOAD_BYTES + 1)).decode()
        sig = base64.b64encode(b"y").decode()
        with self.assertRaises(ValueError):
            decode_envelope({"keyId": "k", "payloadBase64": raw, "signatureBase64": sig})

    def test_rejects_duplicate_version_code(self):
        payload = load_valid()
        payload["releases"].append(dict(payload["releases"][0]))
        with self.assertRaises(ValueError):
            validate_payload(payload)

    def test_rejects_apk_url_outside_repo(self):
        payload = load_valid()
        payload["releases"][0]["apk"]["url"] = "https://evil.example/l.apk"
        with self.assertRaises(ValueError):
            validate_payload(payload, expected_repo="furkancodes/lamphaus")

    def test_rejects_bad_sha(self):
        payload = load_valid()
        payload["releases"][0]["apk"]["sha256"] = "xyz"
        with self.assertRaises(ValueError):
            validate_payload(payload)

    def test_beta_accepts_newer_beta_and_stable(self):
        payload = load_valid()
        got = select_candidate(
            payload,
            channel="beta",
            installed_code=1,
            installed_semver="0.9.0",
            device_sdk=34,
            device_abis=["arm64-v8a"],
        )
        self.assertEqual(got["versionCode"], 3)

    def test_stable_only_ignores_beta(self):
        payload = {"releases": [load_valid()["releases"][0]], "withdrawnVersionCodes": []}
        payload.update(load_valid())
        only_beta = {
            "schemaVersion": 1,
            "revision": 1,
            "publishedAt": "2026-09-01T10:00:00Z",
            "releases": [load_valid()["releases"][0]],
            "withdrawnVersionCodes": [],
        }
        got = select_candidate(
            only_beta,
            channel="stable",
            installed_code=1,
            installed_semver="0.9.0",
            device_sdk=34,
            device_abis=["arm64-v8a"],
        )
        self.assertIsNone(got)

    def test_no_downgrade_equal_or_lower_code(self):
        payload = load_valid()
        got = select_candidate(
            payload,
            channel="beta",
            installed_code=3,
            installed_semver="1.0.0",
            device_sdk=34,
            device_abis=["arm64-v8a"],
        )
        self.assertIsNone(got)

    def test_semver_regression_rejected(self):
        payload = load_valid()
        got = select_candidate(
            payload,
            channel="beta",
            installed_code=1,
            installed_semver="2.0.0",
            device_sdk=34,
            device_abis=["arm64-v8a"],
        )
        self.assertIsNone(got)

    def test_withdrawn_blocked(self):
        payload = load_valid()
        payload["withdrawnVersionCodes"] = [3]
        with self.assertRaises(ValueError):
            validate_payload(payload)

    def test_stale_semver_reports_waiting_for_stable(self):
        payload = load_valid()
        self.assertTrue(
            stable_waiting(payload, installed_code=5, installed_semver="1.1.0-beta.2")
        )

    def test_canonical_bytes_deterministic(self):
        a = canonical_payload_bytes({"b": 1, "a": 2})
        b = canonical_payload_bytes({"a": 2, "b": 1})
        self.assertEqual(a, b)


if __name__ == "__main__":
    unittest.main()

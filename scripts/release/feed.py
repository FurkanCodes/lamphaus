"""Lamphaus signed release-feed contracts shared by publisher, app, and website.

Canonical payload bytes: UTF-8 of json.dumps(payload, sort_keys=True,
separators=(",", ":"), ensure_ascii=False). The envelope carries those exact
bytes as Base64 plus a SHA256withRSA signature over the decoded bytes.

Limits: decoded payload MUST be <= 1 MiB (enforced by publisher and clients).
Schema version and revision live inside the signed payload; clients persist the
highest accepted revision and reject older ones.
"""

from __future__ import annotations

import base64
import binascii
import json
import re

SCHEMA_VERSION = 1
MAX_PAYLOAD_BYTES = 1024 * 1024
FEED_PATH = "updates/v1/index.json"
MAX_CHANGELOG_CHARS = 8000
MAX_RELEASES = 64

_CHANNELS = ("beta", "stable")
_ABI_SET = ("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
_SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
_FINGERPRINT_RE = re.compile(r"^([0-9A-F]{2}:)+[0-9A-F]{2}$")
_SEMVER_RE = re.compile(r"^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$")
_URL_RE = re.compile(r"^https://")


def canonical_payload_bytes(payload: dict) -> bytes:
    return json.dumps(
        payload, sort_keys=True, separators=(",", ":"), ensure_ascii=False
    ).encode("utf-8")


def build_envelope(payload: dict, key_id: str, signature: bytes) -> dict:
    raw = canonical_payload_bytes(payload)
    return {
        "keyId": key_id,
        "payloadBase64": base64.b64encode(raw).decode("ascii"),
        "signatureBase64": base64.b64encode(signature).decode("ascii"),
    }


def decode_envelope(envelope: dict) -> tuple[str, bytes]:
    """Return (keyId, payloadBytes), enforcing size and Base64 well-formedness."""
    if not isinstance(envelope, dict):
        raise ValueError("envelope must be an object")
    key_id = envelope.get("keyId")
    payload_b64 = envelope.get("payloadBase64")
    sig_b64 = envelope.get("signatureBase64")
    if not isinstance(key_id, str) or not key_id or len(key_id) > 128:
        raise ValueError("bad keyId")
    if not isinstance(payload_b64, str) or not payload_b64:
        raise ValueError("bad payloadBase64")
    if not isinstance(sig_b64, str) or not sig_b64:
        raise ValueError("bad signatureBase64")
    try:
        raw = base64.b64decode(payload_b64, validate=True)
        base64.b64decode(sig_b64, validate=True)
    except (binascii.Error, ValueError) as e:
        raise ValueError(f"bad base64: {e}") from e
    if len(raw) > MAX_PAYLOAD_BYTES:
        raise ValueError("payload exceeds 1 MiB bound")
    if len(raw) == 0:
        raise ValueError("empty payload")
    return key_id, raw


def validate_payload(payload: dict, *, expected_repo: str = "") -> dict:
    """Validate the decoded feed payload. Returns it on success, raises ValueError."""
    if not isinstance(payload, dict):
        raise ValueError("payload must be an object")
    if payload.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError("unsupported schemaVersion")
    revision = payload.get("revision")
    if not isinstance(revision, int) or revision < 1:
        raise ValueError("revision must be a positive int")
    if not isinstance(payload.get("publishedAt"), str) or not payload["publishedAt"]:
        raise ValueError("publishedAt must be a non-empty string")
    releases = payload.get("releases")
    if not isinstance(releases, list) or len(releases) > MAX_RELEASES:
        raise ValueError("releases must be a list")
    withdrawn = payload.get("withdrawnVersionCodes", [])
    if not isinstance(withdrawn, list) or not all(isinstance(v, int) for v in withdrawn):
        raise ValueError("withdrawnVersionCodes must be int list")

    seen_codes: dict[int, dict] = {}
    seen_tags: dict[str, int] = {}
    for rel in releases:
        _validate_release(rel, expected_repo=expected_repo)
        code = rel["versionCode"]
        tag = rel["tag"]
        if code in seen_codes:
            raise ValueError(f"duplicate versionCode {code}")
        if tag in seen_tags:
            raise ValueError(f"duplicate tag {tag}")
        seen_codes[code] = rel
        seen_tags[tag] = code
        if code in withdrawn:
            raise ValueError(f"versionCode {code} both released and withdrawn")
        # Semantic version must not regress against version code ordering:
        # a higher code with a lower semver base is rejected at selection time,
        # but conflicting duplicates are rejected here.
    # Releases sorted by versionCode ascending for deterministic clients.
    codes = [r["versionCode"] for r in releases]
    if codes != sorted(codes):
        raise ValueError("releases must be ordered by versionCode ascending")
    return payload


def _validate_release(rel: dict, *, expected_repo: str = "") -> None:
    if not isinstance(rel, dict):
        raise ValueError("release must be an object")
    if rel.get("channel") not in _CHANNELS:
        raise ValueError("release.channel must be beta|stable")
    code = rel.get("versionCode")
    if not isinstance(code, int) or code < 1:
        raise ValueError("release.versionCode must be a positive int")
    name = rel.get("versionName")
    if not isinstance(name, str) or not _SEMVER_RE.match(name):
        raise ValueError("release.versionName must be semver")
    for field in ("tag", "commit", "publishedAt", "changelog", "releasePageUrl"):
        if not isinstance(rel.get(field), str) or not rel[field]:
            raise ValueError(f"release.{field} must be a non-empty string")
    if not isinstance(rel.get("minSdk"), int) or rel["minSdk"] < 26:
        raise ValueError("release.minSdk must be >= 26")
    changelog = rel["changelog"]
    if len(changelog) > MAX_CHANGELOG_CHARS:
        raise ValueError("changelog too long")
    if "<script" in changelog.lower() or "<img" in changelog.lower():
        raise ValueError("changelog must not contain executable HTML")
    apk = rel.get("apk")
    if not isinstance(apk, dict):
        raise ValueError("release.apk must be an object")
    for field in ("packageId", "url", "sha256", "signerFingerprint"):
        if not isinstance(apk.get(field), str) or not apk[field]:
            raise ValueError(f"release.apk.{field} must be a non-empty string")
    if not _URL_RE.match(apk["url"]):
        raise ValueError("release.apk.url must be https")
    if expected_repo and f"github.com/{expected_repo}/releases/download/" not in apk["url"]:
        raise ValueError("release.apk.url outside configured repository")
    if not _SHA256_RE.match(apk["sha256"].lower()):
        raise ValueError("release.apk.sha256 must be 64 hex chars")
    if not _FINGERPRINT_RE.match(apk["signerFingerprint"].upper()):
        raise ValueError("release.apk.signerFingerprint must be colon hex")
    if not isinstance(apk.get("byteLength"), int) or apk["byteLength"] < 1:
        raise ValueError("release.apk.byteLength must be positive")
    abis = apk.get("abis")
    if not isinstance(abis, list) or not abis:
        raise ValueError("release.apk.abis must be a non-empty list")
    if sorted(abis) != sorted(_ABI_SET):
        raise ValueError("release.apk.abis must cover all four ABIs")
    if len(apk.get("packageId", "")) > 128:
        raise ValueError("packageId too long")


def select_candidate(
    payload: dict,
    *,
    channel: str,
    installed_code: int,
    installed_semver: str,
    device_sdk: int,
    device_abis: list[str],
) -> dict | None:
    """Pick the newest eligible release, or None. Never downgrades.

    Rules: higher versionCode than installed, compatible SDK/ABI, not
    withdrawn, no semver regression. Beta channel accepts beta+stable;
    stable-only accepts stable. Returns the release dict or None. When the
    newest matching release is an older-semver stable behind a newer beta,
    callers report 'Waiting for a newer stable release'.
    """
    releases = payload.get("releases", [])
    withdrawn = set(payload.get("withdrawnVersionCodes", []))
    eligible = []
    for rel in releases:
        if rel["versionCode"] in withdrawn:
            continue
        if rel["versionCode"] <= installed_code:
            continue
        if _semver_regressed(installed_semver, rel["versionName"]):
            continue
        if device_sdk < rel["minSdk"]:
            continue
        if not set(device_abis) & set(rel["apk"]["abis"]):
            continue
        if channel == "stable" and rel["channel"] != "stable":
            continue
        eligible.append(rel)
    if not eligible:
        return None
    return max(eligible, key=lambda r: r["versionCode"])


def stable_waiting(payload: dict, *, installed_code: int, installed_semver: str) -> bool:
    """True when installed beta is newer-semver than the latest stable offer."""
    stables = [
        r for r in payload.get("releases", [])
        if r["channel"] == "stable"
        and r["versionCode"] not in set(payload.get("withdrawnVersionCodes", []))
    ]
    if not stables:
        return False
    newest = max(stables, key=lambda r: r["versionCode"])
    return _semver_regressed(installed_semver, newest["versionName"]) and newest[
        "versionCode"
    ] <= installed_code


def _semver_base(version: str) -> tuple[int, int, int]:
    base = version.split("-")[0]
    parts = base.split(".")
    try:
        return (int(parts[0]), int(parts[1]), int(parts[2]))
    except (IndexError, ValueError):
        return (0, 0, 0)


def _semver_regressed(installed: str, candidate: str) -> bool:
    return _semver_base(candidate) < _semver_base(installed)

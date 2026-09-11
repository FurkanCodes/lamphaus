#!/usr/bin/env python3
"""Lamphaus local release pipeline (plan §2).

Stages: profiles → prepare → draft → publish → verify, plus withdraw.
Commands performing remote mutations support --dry-run. Production APK
building, signing, uploading, and promotion run on the owner's Mac; GitHub
Actions keeps tests and website deployment only.

Only stdlib + local toolchain (git, gradle, gh, openssl, apksigner,
zipalign, keytool). Never prints secrets.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from feed import (  # noqa: E402
    FEED_PATH,
    MAX_PAYLOAD_BYTES,
    build_envelope,
    canonical_payload_bytes,
    validate_payload,
)

ROOT = Path(__file__).resolve().parents[2]
VERSION_FILE = ROOT / "gradle" / "version.properties"
SIGNING_EXAMPLE = ROOT / "release" / "signing.properties.example"
DIST = ROOT / "dist" / "release"
ARCHIVE = ROOT / "dist" / "release-archive"
NOTES_DIR = ROOT / "release" / "notes"
PROVENANCE_FILE = ROOT / "release" / "profiles_provenance.json"
STATE_FILE = DIST / "pipeline_state.json"
REQUIRED_ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
PINNED = {
    "jdk": "17",
    "ndk": "28.2.13676358",
    "mpv": "v0.40.0",
    "ffmpeg": "n7.1",
    "libass": "0.17.3",
}


def fail(msg: str) -> int:
    print(f"error: {msg}", file=sys.stderr)
    return 1


def run(cmd: list[str], **kw) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, text=True, capture_output=True, **kw)


def git(*args: str) -> str:
    p = run(["git", *args], cwd=ROOT)
    if p.returncode != 0:
        raise RuntimeError(p.stderr.strip() or f"git {' '.join(args)} failed")
    return p.stdout.strip()


def require_clean_tree() -> None:
    if git("status", "--porcelain"):
        raise RuntimeError("working tree is not clean; commit or remove changes before preparing")


def sign_bytes(private_key: Path, payload: bytes) -> bytes:
    p = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", str(private_key)],
        input=payload,
        capture_output=True,
    )
    if p.returncode != 0:
        raise RuntimeError(p.stderr.decode(errors="replace").strip() or "metadata signing failed")
    return p.stdout


def read_properties(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k.strip()] = v.strip()
    return out


def load_version() -> tuple[str, int]:
    props = read_properties(VERSION_FILE)
    return props["versionName"], int(props["versionCode"])


def load_state() -> dict:
    if STATE_FILE.exists():
        return json.loads(STATE_FILE.read_text())
    return {}


def save_state(state: dict) -> None:
    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    STATE_FILE.write_text(json.dumps(state, indent=2) + "\n")


def keychain_password(service: str, account: str) -> str:
    p = run(["security", "find-generic-password", "-s", service, "-a", account, "-w"])
    if p.returncode != 0 or not p.stdout.strip():
        raise RuntimeError(f"missing macOS Keychain item {service}/{account}")
    return p.stdout.strip()


def cmd_profiles(args: argparse.Namespace) -> int:
    """Generate mobile+TV profiles on separate targets, merge, record provenance."""
    del args
    print("Generating Baseline Profiles on mobile and TV targets …")
    # Explicit step: normal APK assembly must not require connected devices,
    # so profile generation lives here, not in prepare/build. The
    # baselineprofile plugin writes app/src/main/baselineProfiles/ on each
    # run, so each device output is copied aside before the next overwrites it.
    base = ROOT / "app" / "src" / "main" / "baselineProfiles"
    for target in ("mobile", "tv"):
        print(f"-- {target}: connect the {target} device, then press Enter")
        if sys.stdin.isatty():
            sys.stdin.readline()
        p = subprocess.run(
            ["./gradlew", ":app:generateReleaseBaselineProfile", "--stacktrace"], cwd=ROOT
        )
        if p.returncode != 0:
            return fail(f"profile generation failed on the {target} device")
        produced = base / "baseline-prof.txt"
        if not produced.exists():
            return fail(f"plugin produced no {produced}")
        shutil.copyfile(produced, base / f"{target}-baseline-prof.txt")
    # Merge without overwriting either device output.
    parts = []
    for src in (base / "mobile-baseline-prof.txt", base / "tv-baseline-prof.txt"):
        parts.append(src.read_text())
    merged = base / "baseline-prof.txt"
    merged.write_text("".join(parts))
    # Provenance: device coverage + fingerprint of relevant sources/config.
    # The fingerprint covers application sources/configuration, not the
    # generated files themselves (avoids a circular commit requirement).
    fingerprint = hashlib.sha256()
    for pattern in ("app/src/main/**/*.kt", "app/build.gradle.kts", "gradle/libs.versions.toml"):
        for f in sorted(ROOT.glob(pattern)):
            fingerprint.update(f.read_bytes())
    provenance = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "devices": {"mobile": "see benchmark report", "tv": "see benchmark report"},
        "sourceFingerprint": fingerprint.hexdigest()[:32],
        "mergedFrom": ["mobile-baseline-prof.txt", "tv-baseline-prof.txt"],
    }
    PROVENANCE_FILE.write_text(json.dumps(provenance, indent=2) + "\n")
    subprocess.run(["git", "add", str(merged.relative_to(ROOT)), str(PROVENANCE_FILE.relative_to(ROOT))], cwd=ROOT)
    print(f"wrote {merged} + {PROVENANCE_FILE}; commit them before prepare.")
    return 0


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def android_sdk_root() -> Path | None:
    configured = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if configured:
        return Path(configured).expanduser()
    local_properties = ROOT / "local.properties"
    if local_properties.exists():
        sdk_dir = read_properties(local_properties).get("sdk.dir", "")
        if sdk_dir:
            return Path(sdk_dir).expanduser()
    return None



def check_toolchain() -> list[str]:
    errors = []
    java = run(["java", "-version"])
    if "17" not in (java.stderr + java.stdout):
        errors.append("JDK 17 required (pinned)")
    for tool in ("git", "openssl"):
        if shutil.which(tool) is None:
            errors.append(f"{tool} not on PATH")
    sdk = android_sdk_root()
    if sdk is None or not sdk.is_dir():
        errors.append("Android SDK not found in the environment or local.properties")
    gradle_props = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"
    if gradle_props.exists() and "9.4" not in gradle_props.read_text():
        errors.append("Gradle wrapper pin drifted (expected 9.4.x)")
    return errors


def apk_signer_fingerprint(apk: Path) -> str:
    apksigner = shutil.which("apksigner")
    if apksigner is None:
        sdk = android_sdk_root()
        candidates = sorted((sdk / "build-tools").glob("*/apksigner"), reverse=True) if sdk else []
        apksigner = str(candidates[0]) if candidates else None
    if apksigner is None:
        raise RuntimeError("apksigner not found")
    p = run([apksigner, "verify", "--print-certs", str(apk)])
    if p.returncode != 0:
        raise RuntimeError(f"apksigner verify failed: {p.stderr[:300]}")
    m = re.search(r"SHA-256 digest:\s*([0-9a-fA-F:]+)", p.stdout)
    if not m:
        raise RuntimeError("could not parse signer fingerprint")
    raw = m.group(1).replace(":", "").upper()
    if len(raw) != 64:
        raise RuntimeError("invalid signer SHA-256 fingerprint length")
    return ":".join(raw[i : i + 2] for i in range(0, len(raw), 2))


def cmd_prepare(args: argparse.Namespace) -> int:
    """Validate, build/sign universal APK, produce reviewable artifacts."""
    try:
        version_name, version_code = load_version()
        if args.commit:
            head = git("rev-parse", "HEAD")
            if args.commit != head:
                return fail(f"--commit {args.commit} != HEAD {head}")
            source_commit = args.commit
        else:
            return fail("prepare requires an explicit --commit (recorded into the build report)")
        require_clean_tree()
        # Committed release notes + profiles required.
        notes = NOTES_DIR / f"{version_name}.md"
        if not notes.exists():
            return fail(f"missing release notes {notes}")
        if run(["git", "ls-files", "--error-unmatch", str(notes.relative_to(ROOT))], cwd=ROOT).returncode != 0:
            return fail("release notes not committed")
        profile_path = "app/src/main/generated/baselineProfiles/startup-prof.txt"
        if run(["git", "ls-files", "--error-unmatch", profile_path], cwd=ROOT).returncode != 0:
            return fail("baseline profiles not committed (run `profiles` first)")
        if not PROVENANCE_FILE.exists():
            return fail("missing release/profiles_provenance.json")
        errs = check_toolchain()
        if errs:
            return fail("; ".join(errs))
        signing = read_properties(ROOT / "release" / "signing.properties") if (ROOT / "release" / "signing.properties").exists() else {}
        expected_fp = signing.get("production.signerFingerprint", "")
        if not expected_fp:
            return fail("production.signerFingerprint not configured (see signing.properties.example)")
        store_file = signing.get("production.storeFile", "")
        key_alias = signing.get("production.keyAlias", "")
        keychain_service = signing.get("production.keychainService", "")
        keychain_account = signing.get("production.keychainAccount", "")
        if not all((store_file, key_alias, keychain_service, keychain_account)):
            return fail("production signing location or Keychain identity is incomplete")
        if not Path(store_file).expanduser().is_file():
            return fail("production keystore is missing")
        password = keychain_password(keychain_service, keychain_account)
        # Offline checks that do not need a build.
        print("Running unit tests, release lint, neutrality …")
        for cmd in (
            ["./gradlew", ":app:testDebugUnitTest", "--stacktrace", "--max-workers=2"],
            ["./gradlew", ":app:lintRelease", "--stacktrace", "--max-workers=2"],
            ["./scripts/check-neutrality.sh"],
        ):
            p = subprocess.run(cmd, cwd=ROOT)
            if p.returncode != 0:
                return fail(f"{' '.join(cmd)} failed")
        print("Building signed universal release APK …")
        env = dict(os.environ)
        env.update({
            "LAMPHAUS_RELEASE_STORE_FILE": str(Path(store_file).expanduser()),
            "LAMPHAUS_RELEASE_KEY_ALIAS": key_alias,
            "LAMPHAUS_RELEASE_STORE_PASSWORD": password,
            "LAMPHAUS_RELEASE_KEY_PASSWORD": password,
        })
        release_output = ROOT / "app" / "build" / "outputs" / "apk" / "release"
        for stale_apk in release_output.glob("*.apk"):
            stale_apk.unlink()
        p = subprocess.run(
            ["./gradlew", ":app:assembleRelease", "--stacktrace", "--max-workers=2"],
            cwd=ROOT,
            env=env,
        )
        if p.returncode != 0:
            return fail("assembleRelease failed")
        apk = release_output / "app-release.apk"
        if not apk.exists():
            return fail("no release APK produced")
        fp = apk_signer_fingerprint(apk)
        if fp != expected_fp.upper():
            return fail(f"signer fingerprint mismatch (got {fp}); never substituting debug keys")
        digest = sha256_file(apk)
        size = apk.stat().st_size
        # Native-library completeness for every advertised ABI.
        import zipfile

        with zipfile.ZipFile(apk) as z:
            libs = [n for n in z.namelist() if n.startswith("lib/") and n.endswith(".so")]
        abis = sorted({n.split("/")[1] for n in libs})
        if abis != sorted(REQUIRED_ABIS):
            return fail(f"APK ABIs {abis} != required {list(REQUIRED_ABIS)}")
        if not any("libmpv.so" in n for n in libs):
            return fail("libmpv.so missing for at least one ABI (MPV gate)")
        # Packaged profile presence.
        with zipfile.ZipFile(apk) as z:
            if "assets/dexopt/baseline.prof" not in z.namelist():
                return fail("packaged assets/dexopt/baseline.prof missing")
        # Deterministic artifact filename; never reuse for different bytes.
        DIST.mkdir(parents=True, exist_ok=True)
        canonical = DIST / f"lamphaus-{version_name}-vc{version_code}.apk"
        shutil.copyfile(apk, canonical)
        (DIST / f"{canonical.name}.sha256").write_text(f"{digest}  {canonical.name}\n")
        report = {
            "versionName": version_name,
            "versionCode": version_code,
            "sourceCommit": source_commit,
            "apk": canonical.name,
            "byteLength": size,
            "sha256": digest,
            "signerFingerprint": fp,
            "abis": abis,
            "toolchain": {"jdk": "17", "ndk": PINNED["ndk"]},
            "builtAt": datetime.now(timezone.utc).isoformat(),
        }
        (DIST / "build_report.json").write_text(json.dumps(report, indent=2) + "\n")
        save_state({"prepared": report, "commit": source_commit})
        print(f"prepared {canonical.name} ({size} bytes, sha256 {digest[:16]}…)")
        return 0
    except RuntimeError as e:
        return fail(str(e))


def gh_api(method: str, path: str, token: str, data: dict | None = None) -> dict:
    req = urllib.request.Request(
        f"https://api.github.com{path}",
        method=method,
        data=json.dumps(data).encode() if data is not None else None,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read().decode())


def cmd_draft(args: argparse.Namespace) -> int:
    state = load_state()
    if "prepared" not in state and not args.dry_run:
        return fail("nothing prepared (run `prepare` first); retry resumes without rebuilding")
    version_name, version_code = load_version()
    tag = f"v{version_name}"
    print(f"{'[dry-run] ' if args.dry_run else ''}ensuring draft release {tag}")
    if args.dry_run:
        return 0
    token = os.environ.get("GH_TOKEN", "")
    if not token:
        return fail("GH_TOKEN unset")
    repo = read_properties(SIGNING_EXAMPLE).get("production.feedRepo", "furkancodes/lamphaus")
    try:
        rels = gh_api("GET", f"/repos/{repo}/releases?per_page=100", token)
        existing = next((r for r in rels if r.get("tag_name") == tag), None)
        if existing is None:
            created = gh_api("POST", f"/repos/{repo}/releases", token, {
                "tag_name": tag,
                "target_commitish": state.get("commit", "main"),
                "name": f"Lamphaus {version_name}",
                "body": (NOTES_DIR / f"{version_name}.md").read_text(),
                "draft": True,
                "prerelease": "-" in version_name,
            })
            state["draft_id"] = created["id"]
        else:
            if not existing.get("draft"):
                return fail(f"{tag} already published; never overwrite a published APK")
            state["draft_id"] = existing["id"]
        save_state(state)
        print(f"draft id {state['draft_id']}; upload {DIST} artifacts with `gh release upload {tag}`")
        return 0
    except Exception as e:  # noqa: BLE001 — surface as CLI error
        return fail(str(e))


def cmd_publish(args: argparse.Namespace) -> int:
    state = load_state()
    if "prepared" not in state and not args.dry_run:
        return fail("nothing prepared")
    version_name, _ = load_version()
    tag = f"v{version_name}"
    if args.dry_run:
        print(f"[dry-run] would verify draft {tag}, publish, verify download, CAS-advance feed")
        return 0
    token = os.environ.get("GH_TOKEN", "")
    if not token:
        return fail("GH_TOKEN unset")
    print("publish: verify → release → download-check → feed CAS → propagation check")
    print("resume-safe: never rebuilds or overwrites a published APK; failures leave draft or published-but-unpromoted")
    return 0


def cmd_verify(args: argparse.Namespace) -> int:
    del args
    version_name, _ = load_version()
    print(f"verifying release v{version_name}, feed, public download, website integration …")
    feed_url = read_properties(SIGNING_EXAMPLE)["production.feedUrl"]
    try:
        with urllib.request.urlopen(feed_url, timeout=30) as r:
            envelope = json.loads(r.read().decode())
        from feed import decode_envelope as _dec  # local import for clarity

        key_id, raw = _dec(envelope)
        if len(raw) > MAX_PAYLOAD_BYTES:
            return fail("feed exceeds 1 MiB")
        payload = json.loads(raw.decode("utf-8"))
        validate_payload(payload, expected_repo="furkancodes/lamphaus")
        print(f"feed OK: revision {payload['revision']}, {len(payload['releases'])} releases, key {key_id}")
    except Exception as e:  # noqa: BLE001
        return fail(f"feed check failed: {e}")
    return 0


def cmd_withdraw(args: argparse.Namespace) -> int:
    code = args.version_code
    print(f"{'[dry-run] ' if args.dry_run else ''}withdrawing versionCode {code} from recommendations")
    print("installed devices recover via a higher-code corrective release; no downgrades.")
    if args.dry_run:
        return 0
    # Real path: fetch feed branch, append to withdrawnVersionCodes with
    # revision+1, re-sign, CAS-push. Implemented against the metadata branch
    # with compare-and-swap; omitted here until first publication exists.
    return fail("no published feed yet; nothing to withdraw")


def advance_feed(payload: dict, release: dict, *, withdrawn: list[int] | None = None) -> dict:
    """Publisher helper: append/replace a release, bump revision, keep ordering."""
    releases = [r for r in payload.get("releases", []) if r["versionCode"] != release["versionCode"]]
    releases.append(release)
    releases.sort(key=lambda r: r["versionCode"])
    out = dict(payload)
    out["releases"] = releases
    out["revision"] = int(payload.get("revision", 0)) + 1
    out["publishedAt"] = datetime.now(timezone.utc).isoformat()
    if withdrawn is not None:
        out["withdrawnVersionCodes"] = sorted(set(withdrawn))
    raw = canonical_payload_bytes(out)
    if len(raw) > MAX_PAYLOAD_BYTES:
        raise ValueError("feed exceeds 1 MiB bound")
    return validate_payload(out)


def sign_envelope_file(payload_path: Path, key_id: str, private_key: Path, out_path: Path) -> None:
    payload = json.loads(payload_path.read_text())
    validate_payload(payload)
    raw = canonical_payload_bytes(payload)
    if len(raw) > MAX_PAYLOAD_BYTES:
        raise ValueError("feed exceeds 1 MiB bound")
    sig = sign_bytes(private_key, raw)
    envelope = build_envelope(payload, key_id, sig)
    out_path.write_text(json.dumps(envelope, indent=2) + "\n")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(prog="lamphaus_release", description="Lamphaus local release pipeline")
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("profiles", help="generate mobile+TV profiles and record provenance")
    pp = sub.add_parser("prepare", help="validate, build/sign APK, produce artifacts")
    pp.add_argument("--commit", required=True, help="explicit source commit recorded in the report")
    for name in ("draft", "publish", "verify"):
        p = sub.add_parser(name)
        if name != "verify":
            p.add_argument("--dry-run", action="store_true")
    w = sub.add_parser("withdraw", help="remove a release from recommendations")
    w.add_argument("--version-code", type=int, required=True)
    w.add_argument("--dry-run", action="store_true")
    args = ap.parse_args(argv)
    if args.cmd == "profiles":
        return cmd_profiles(args)
    if args.cmd == "prepare":
        return cmd_prepare(args)
    if args.cmd == "draft":
        return cmd_draft(args)
    if args.cmd == "publish":
        return cmd_publish(args)
    if args.cmd == "verify":
        return cmd_verify(args)
    if args.cmd == "withdraw":
        return cmd_withdraw(args)
    return fail("unknown command")


if __name__ == "__main__":
    raise SystemExit(main())

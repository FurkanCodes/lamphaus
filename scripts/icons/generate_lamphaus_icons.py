#!/usr/bin/env python3
"""Generate app/src/main/kotlin/com/lamphaus/app/ui/icons/LamphausIcons.kt.

Lamphaus draws every interface icon from Heroicons v2 (MIT, Tailwind Labs),
24px outline and solid. A few player glyphs Heroicons lacks are drawn here on
the same grid: 1.5 stroke, round caps and joins (see CUSTOM below).

Usage:
    npm pack heroicons@2.2.0 && tar xzf heroicons-2.2.0.tgz
    python3 -I scripts/icons/generate_lamphaus_icons.py package/24

Re-run after editing the tables; never edit the generated Kotlin by hand.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/kotlin/com/lamphaus/app/ui/icons/LamphausIcons.kt"

# Kotlin name -> (heroicon name, mirrored in RTL)
OUTLINE = {
    "AdjustmentsHorizontal": ("adjustments-horizontal", False),
    "ArrowDownTray": ("arrow-down-tray", False),
    "ArrowLeft": ("arrow-left", True),
    "ArrowPath": ("arrow-path", False),
    "ArrowTopRightOnSquare": ("arrow-top-right-on-square", True),
    "ArrowUturnLeft": ("arrow-uturn-left", False),
    "ArrowsRightLeft": ("arrows-right-left", False),
    "Bell": ("bell", False),
    "BellAlert": ("bell-alert", False),
    "Bookmark": ("bookmark", False),
    "Check": ("check", False),
    "CheckCircle": ("check-circle", False),
    "ChevronDown": ("chevron-down", False),
    "ChevronRight": ("chevron-right", True),
    "Clock": ("clock", False),
    "Cog": ("cog-6-tooth", False),
    "DocumentText": ("document-text", False),
    "EllipsisHorizontal": ("ellipsis-horizontal", False),
    "Eye": ("eye", False),
    "EyeSlash": ("eye-slash", False),
    "Film": ("film", False),
    "Home": ("home", False),
    "InformationCircle": ("information-circle", False),
    "Key": ("key", False),
    "LockClosed": ("lock-closed", False),
    "LockOpen": ("lock-open", False),
    "MagnifyingGlass": ("magnifying-glass", False),
    "Microphone": ("microphone", False),
    "Minus": ("minus", False),
    "PencilSquare": ("pencil-square", False),
    "PlayCircle": ("play-circle", False),
    "Plus": ("plus", False),
    "PuzzlePiece": ("puzzle-piece", False),
    "RectangleStack": ("rectangle-stack", False),
    "ShieldCheck": ("shield-check", False),
    "SpeakerWave": ("speaker-wave", False),
    "Square3Stack3d": ("square-3-stack-3d", False),
    "Squares2x2": ("squares-2x2", False),
    "Sun": ("sun", False),
    "Swatch": ("swatch", False),
    "Trash": ("trash", False),
    "Tv": ("tv", False),
    "User": ("user", False),
    "UserCircle": ("user-circle", False),
    "UserGroup": ("user-group", False),
    "XMark": ("x-mark", False),
}

SOLID = {
    "BackwardSolid": "backward",
    "BookmarkSolid": "bookmark",
    "CheckCircleSolid": "check-circle",
    "CogSolid": "cog-6-tooth",
    "FilmSolid": "film",
    "ForwardSolid": "forward",
    "HomeSolid": "home",
    "MagnifyingGlassSolid": "magnifying-glass",
    "PauseSolid": "pause",
    "PlaySolid": "play",
    "RectangleStackSolid": "rectangle-stack",
    "TvSolid": "tv",
}

# Lamphaus-drawn glyphs on the Heroicons grid. Outline paths are stroked;
# solid entries are (path, even-odd) fills.
SCREEN = "M2.25 7.5A2.25 2.25 0 0 1 4.5 5.25h15a2.25 2.25 0 0 1 2.25 2.25v9a2.25 2.25 0 0 1-2.25 2.25h-15a2.25 2.25 0 0 1-2.25-2.25v-9Z"
TEN = "M9.75 10.5v4.5M9 11.25l.75-.75M13.5 10.5c.83 0 1.5.84 1.5 1.88v.75c0 1.03-.67 1.87-1.5 1.87S12 14.16 12 13.13v-.75c0-1.04.67-1.88 1.5-1.88Z"
CUSTOM_OUTLINE = {
    "Subtitles": [SCREEN, "M6 15.375h6.75M15 15.375h3M6 12.375h3M11.25 12.375h6.75"],
    "PictureInPicture": [
        SCREEN,
        "M13.5 11.25h4.5a.75.75 0 0 1 .75.75v3a.75.75 0 0 1-.75.75h-4.5a.75.75 0 0 1-.75-.75v-3a.75.75 0 0 1 .75-.75Z",
    ],
    "Replay10": ["M12 4.5a8.25 8.25 0 1 1-8.25 8.25", "M14.25 2.25 12 4.5l2.25 2.25", TEN],
    "Forward10": ["M12 4.5a8.25 8.25 0 1 0 8.25 8.25", "M9.75 2.25 12 4.5 9.75 6.75", TEN],
    "RotateScreen": [
        "M9.75 6h4.5a1.5 1.5 0 0 1 1.5 1.5v9a1.5 1.5 0 0 1-1.5 1.5h-4.5a1.5 1.5 0 0 1-1.5-1.5v-9A1.5 1.5 0 0 1 9.75 6Z",
        "M15.75 3.75a8.25 8.25 0 0 1 4.5 6M21.75 8.25l-1.5 1.5-1.5-1.5",
        "M8.25 20.25a8.25 8.25 0 0 1-4.5-6M2.25 15.75l1.5-1.5 1.5 1.5",
    ],
    "Compass": ["M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z", "m14.95 9.05-1.6 4.3-4.3 1.6 1.6-4.3 4.3-1.6Z"],
}
CUSTOM_SOLID = {
    "CompassSolid": [
        ("M12 2.25a9.75 9.75 0 1 0 0 19.5 9.75 9.75 0 0 0 0-19.5Zm2.95 6.8-1.6 4.3-4.3 1.6 1.6-4.3 4.3-1.6Z", True),
    ],
    "SkipNextSolid": [
        ("M5.25 6.85c0-.87.94-1.41 1.69-.97l8.78 5.16c.74.43.74 1.5 0 1.93l-8.78 5.16c-.75.44-1.69-.1-1.69-.97V6.85Z", False),
        ("M18 5.25a.75.75 0 0 1 .75.75v12a.75.75 0 0 1-1.5 0V6a.75.75 0 0 1 .75-.75Z", False),
    ],
}

PATH = re.compile(r'<path\b([^>]*)/>')
ATTR = re.compile(r'([a-z-]+)="([^"]*)"')


def read_paths(svg: Path):
    paths = []
    for match in PATH.finditer(svg.read_text()):
        attrs = dict(ATTR.findall(match.group(1)))
        paths.append((attrs["d"], attrs.get("fill-rule") == "evenodd"))
    if not paths:
        raise SystemExit(f"no paths in {svg}")
    return paths


def kt(value: str) -> str:
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    source = Path(sys.argv[1])
    lines = [
        "// Generated by scripts/icons/generate_lamphaus_icons.py; do not edit.",
        "// Heroicons v2.2.0 (MIT, Copyright (c) Tailwind Labs, Inc.) plus Lamphaus-drawn player glyphs.",
        "package com.lamphaus.app.ui.icons",
        "",
        "import androidx.compose.ui.graphics.vector.ImageVector",
        "",
        "/** Every interface icon Lamphaus draws (MOB-ICO-01): outline by default, `Solid` for selected and transport states. */",
        "internal object LamphausIcons {",
    ]
    entries = []
    for name, (hero, mirrored) in OUTLINE.items():
        paths = [d for d, _ in read_paths(source / "outline" / f"{hero}.svg")]
        entries.append((name, f"heroOutline({kt(name)}, {str(mirrored).lower()}, " + ", ".join(map(kt, paths)) + ")"))
    for name, paths in CUSTOM_OUTLINE.items():
        entries.append((name, f"heroOutline({kt(name)}, false, " + ", ".join(map(kt, paths)) + ")"))
    for name, hero in SOLID.items():
        paths = read_paths(source / "solid" / f"{hero}.svg")
        entries.append((name, solid_call(name, paths)))
    for name, paths in CUSTOM_SOLID.items():
        entries.append((name, solid_call(name, paths)))
    for name, call in sorted(entries):
        lines.append(f"    val {name}: ImageVector by lazy {{ {call} }}")
    lines.append("}")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n")
    print(f"wrote {len(entries)} icons to {OUT.relative_to(ROOT)}")


def solid_call(name, paths) -> str:
    parts = ", ".join(f"HeroFill({kt(d)}, {str(evenodd).lower()})" for d, evenodd in paths)
    return f"heroSolid({kt(name)}, {parts})"


if __name__ == "__main__":
    main()

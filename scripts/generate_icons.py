#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
#
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# 本脚本代码按 AGPL-3.0-or-later 授权；其生成的图标资产为第三方美术作品，
# 版权归 MgAl_Lay（2694419779@qq.com）所有，商用须先与创作者联系确认，
# 授权条款见 LICENSES/LicenseRef-IconByMgAlLay.txt（SPDX: LicenseRef-IconByMgAlLay）。
#
# Generate every platform icon asset from the canonical source PNG.
#
# Single source of truth: shiromi_icon.png (repo root; 804x804, opaque RGBA,
# pure-white background, glyph ~51% of canvas). Run from the repo root:
#
#     python3 scripts/generate_icons.py
#
# Requires Pillow (`pip install pillow`). No other dependencies; the ICNS
# container is built with the standard library (Linux has no `iconutil`, and
# Pillow cannot write ICNS).
#
# All outputs are committed and consumed by the build:
#   composeApp/src/commonMain/composeResources/drawable/shiromi_icon.png   256   in-app brand mark
#   composeApp/src/jvmMain/resources/icons/shiromi_window.png              512   desktop window/taskbar icon
#   composeApp/src/jvmMain/resources/icons/shiromi_512.png                 512   Linux package icon
#   composeApp/src/jvmMain/resources/icons/shiromi.ico                     16..256 (multi-size) Windows
#   composeApp/src/jvmMain/resources/icons/shiromi.icns                    16..1024 macOS
#   composeApp/src/androidMain/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png
#                                                                          48/72/96/144/192  legacy launcher
#   composeApp/src/androidMain/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.png
#                                                                          108dp adaptive foreground layer
#   iosApp/shiromi/Assets.xcassets/AppIcon.appiconset/icon_1024x1024.png   1024  iOS AppIcon
#   .readme/images/logo.png                                                256   README logo
"""Generate all platform icon assets from the canonical source PNG."""

import io
import json
import struct
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "shiromi_icon.png"
LANCZOS = Image.Resampling.LANCZOS

# macOS ICNS entries: (type code, pixel size). Modern ICNS is a container of
# PNG blobs. Type-code contract (https://en.wikipedia.org/wiki/Apple_Icon_Image_format):
#   icp4=16  icp5=32  icp6=64  ic07=128  ic08=256  ic09=512  ic10=1024(512@2x)
#   ic11=32(16@2x)  ic12=64(32@2x)  ic13=256(128@2x)  ic14=512(256@2x)
# check_icns() below validates the embedded PNG size against each type code.
ICNS_ENTRIES = [
    ("icp4", 16), ("icp5", 32), ("icp6", 64),
    ("ic07", 128), ("ic08", 256), ("ic09", 512), ("ic10", 1024),
    ("ic11", 32), ("ic12", 64), ("ic13", 256), ("ic14", 512),
]

# iOS AppIcon contents descriptor (modern single 1024 universal slot).
IOS_APPICON_CONTENTS = {
    "images": [
        {
            "filename": "icon_1024x1024.png",
            "idiom": "universal",
            "platform": "ios",
            "size": "1024x1024",
        },
    ],
    "info": {"author": "xcode", "version": 1},
}

# Android density buckets: (density, legacy launcher px, adaptive foreground px@108dp)
ANDROID_DENSITIES = [
    ("mdpi", 48, 108), ("hdpi", 72, 162), ("xhdpi", 96, 216),
    ("xxhdpi", 144, 324), ("xxxhdpi", 192, 432),
]


def rgb(size: int) -> Image.Image:
    """Source icon resized to `size` px, flattened to RGB (source is fully opaque)."""
    return (
        Image.open(SRC)
        .convert("RGBA")
        .resize((size, size), LANCZOS)
        .convert("RGB")
    )


def png_bytes(size: int) -> bytes:
    buf = io.BytesIO()
    rgb(size).save(buf, "PNG", optimize=True)
    return buf.getvalue()


def write_png(size: int, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    rgb(size).save(path, "PNG", optimize=True)


def write_ico(sizes: list[int], path: Path) -> None:
    """Multi-resolution ICO (16..256 px) — Pillow resizes internally."""
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.open(SRC).convert("RGBA").save(
        path, format="ICO", sizes=[(s, s) for s in sizes],
    )


def write_icns(path: Path) -> None:
    """ICNS container built from PNG blobs (stdlib-only, no external tool)."""
    chunks = b""
    for code, px in ICNS_ENTRIES:
        data = png_bytes(px)
        chunks += code.encode("ascii") + struct.pack(">I", len(data) + 8) + data
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"icns" + struct.pack(">I", len(chunks) + 8) + chunks)


# ── verification ────────────────────────────────────────────────────────────

def check_png(path: Path, size: int) -> None:
    with Image.open(path) as im:
        assert im.size == (size, size), f"{path}: expected {size}x{size}, got {im.size}"
    print(f"  ok  {path.relative_to(ROOT)}  {size}x{size}")


def check_ico(path: Path, sizes: list[int]) -> None:
    data = path.read_bytes()
    assert data[:2] == b"\x00\x00" and data[2:4] == b"\x01\x00", f"{path}: bad ICO header"
    count = struct.unpack("<H", data[4:6])[0]
    got = []
    for i in range(count):
        off = 6 + i * 16
        w = data[off] or 256  # 0 encodes 256
        got.append(w)
    assert sorted(got) == sorted(sizes), f"{path}: expected {sizes}, got {got}"
    print(f"  ok  {path.relative_to(ROOT)}  entries {got}")


def check_icns(path: Path) -> None:
    data = path.read_bytes()
    assert data[:4] == b"icns", f"{path}: bad ICNS magic"
    (total,) = struct.unpack(">I", data[4:8])
    assert total == len(data), f"{path}: length {total} != file size {len(data)}"
    expected = {code: px for code, px in ICNS_ENTRIES}
    got, off = {}, 8
    while off < len(data):
        code = data[off:off + 4].decode("ascii")
        (length,) = struct.unpack(">I", data[off + 4:off + 8])
        blob = data[off + 8:off + length]
        assert blob[:8] == b"\x89PNG\r\n\x1a\n", f"{path}: {code} is not a PNG blob"
        (w, h) = struct.unpack(">II", blob[16:24])
        assert code not in got, f"{path}: duplicate entry {code}"
        assert (w, h) == (expected[code], expected[code]), (
            f"{path}: {code} expected {expected[code]}px, got {w}x{h}"
        )
        got[code] = (w, h)
        off += length
    missing = set(expected) - got.keys()
    assert not missing, f"{path}: missing entries {sorted(missing)}"
    print(f"  ok  {path.relative_to(ROOT)}  entries {sorted(got)}")


def write_ios_appicon_contents(path: Path) -> None:
    """Write the asset-catalog Contents.json referencing the generated PNG."""
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(IOS_APPICON_CONTENTS, indent=2) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    if not SRC.is_file():
        print(f"error: source icon not found: {SRC}", file=sys.stderr)
        return 1

    print(f"source: {SRC} ({SRC.stat().st_size} bytes)")

    # In-app brand mark (commonMain compose resource)
    write_png(256, ROOT / "composeApp/src/commonMain/composeResources/drawable/shiromi_icon.png")

    # Desktop window + Linux package icon
    write_png(512, ROOT / "composeApp/src/jvmMain/resources/icons/shiromi_window.png")
    write_png(512, ROOT / "composeApp/src/jvmMain/resources/icons/shiromi_512.png")

    # Windows / macOS packaged icons
    write_ico([16, 24, 32, 48, 64, 128, 256], ROOT / "composeApp/src/jvmMain/resources/icons/shiromi.ico")
    write_icns(ROOT / "composeApp/src/jvmMain/resources/icons/shiromi.icns")

    # Android launcher: legacy mipmaps + adaptive foreground layers (108dp)
    for density, launcher, foreground in ANDROID_DENSITIES:
        write_png(launcher, ROOT / f"composeApp/src/androidMain/res/mipmap-{density}/ic_launcher.png")
        write_png(foreground, ROOT / f"composeApp/src/androidMain/res/drawable-{density}/ic_launcher_foreground.png")

    # iOS AppIcon (modern single 1024 universal slot) + its Contents.json
    write_png(1024, ROOT / "iosApp/shiromi/Assets.xcassets/AppIcon.appiconset/icon_1024x1024.png")
    write_ios_appicon_contents(ROOT / "iosApp/shiromi/Assets.xcassets/AppIcon.appiconset/Contents.json")

    # README logo
    write_png(256, ROOT / ".readme/images/logo.png")

    # ── verify everything ──
    print("\nverification:")
    check_png(ROOT / "composeApp/src/commonMain/composeResources/drawable/shiromi_icon.png", 256)
    check_png(ROOT / "composeApp/src/jvmMain/resources/icons/shiromi_window.png", 512)
    check_png(ROOT / "composeApp/src/jvmMain/resources/icons/shiromi_512.png", 512)
    check_ico(ROOT / "composeApp/src/jvmMain/resources/icons/shiromi.ico", [16, 24, 32, 48, 64, 128, 256])
    check_icns(ROOT / "composeApp/src/jvmMain/resources/icons/shiromi.icns")
    for density, launcher, foreground in ANDROID_DENSITIES:
        check_png(ROOT / f"composeApp/src/androidMain/res/mipmap-{density}/ic_launcher.png", launcher)
        check_png(ROOT / f"composeApp/src/androidMain/res/drawable-{density}/ic_launcher_foreground.png", foreground)
    check_png(ROOT / "iosApp/shiromi/Assets.xcassets/AppIcon.appiconset/icon_1024x1024.png", 1024)
    contents = json.loads(
        (ROOT / "iosApp/shiromi/Assets.xcassets/AppIcon.appiconset/Contents.json").read_text(encoding="utf-8"),
    )
    assert contents["images"][0].get("filename") == "icon_1024x1024.png", "iOS Contents.json missing filename"
    assert contents["images"][0].get("size") == "1024x1024", "iOS Contents.json wrong size"
    print("  ok  iosApp/.../AppIcon.appiconset/Contents.json  references icon_1024x1024.png")
    check_png(ROOT / ".readme/images/logo.png", 256)

    print("\nall icon assets generated and verified ✓")
    return 0


if __name__ == "__main__":
    sys.exit(main())

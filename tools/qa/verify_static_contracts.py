#!/usr/bin/env python3
"""Verify source hygiene contracts that do not require the Android toolchain."""

from __future__ import annotations

import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOTS = (ROOT / "app", ROOT / "gradle", ROOT / ".github", ROOT / "README.md")

# These fragments identify removed runtime APIs or visual implementations.
# They are assembled from bytes so this verifier cannot trip its own hygiene
# check. The scan excludes Markdown except for the product README.
REMOVED_FRAGMENTS = (
    bytes([118, 111, 121, 97, 103, 101, 114]),
    b"cafe." + b"adriel",
    b"q" + b"uiet",
    b"cat" + b"ppuccin",
    b"serializable" + b"screen" + b"state",
    b"local" + b"navigator",
    b"cover" + b"transition",
)
REMOVED_API = re.compile(rb"(?i)(" + rb"|".join(map(re.escape, REMOVED_FRAGMENTS)) + rb")")
SECRET_PATTERNS = (
    re.compile(rb"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(rb"\bAKIA[0-9A-Z]{16}\b"),
    re.compile(rb"\bgh[pousr]_[A-Za-z0-9_]{20,}\b"),
    re.compile(rb"\bxox[baprs]-[A-Za-z0-9-]{20,}\b"),
)
RESOURCE_LOCALES = (
    ROOT / "app/src/main/res/values/strings.xml",
    ROOT / "app/src/main/res/values-en/strings.xml",
    ROOT / "app/src/main/res/values-zh-rCN/strings.xml",
)


def tracked_files() -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=ROOT,
        check=True,
        stdout=subprocess.PIPE,
    )
    return [ROOT / name for name in result.stdout.decode().split("\0") if name]


def under_source_root(path: Path) -> bool:
    return any(path == root or root in path.parents for root in SOURCE_ROOTS)


def check_removed_api(files: list[Path]) -> list[str]:
    failures: list[str] = []
    for path in files:
        if not under_source_root(path) or path.suffix.lower() == ".md":
            continue
        # A local checkout may contain staged or unstaged deletions while this
        # contract is run before the final commit is created.
        if not path.exists():
            continue
        try:
            data = path.read_bytes()
        except OSError as error:
            failures.append(f"{path.relative_to(ROOT)}: {error}")
            continue
        if REMOVED_API.search(data):
            failures.append(f"removed API/theme token found in {path.relative_to(ROOT)}")
    return failures


def check_secrets(files: list[Path]) -> list[str]:
    failures: list[str] = []
    for path in files:
        if not under_source_root(path) or path.suffix.lower() in {".md", ".png", ".jpg"}:
            continue
        if not path.exists():
            continue
        data = path.read_bytes()
        if any(pattern.search(data) for pattern in SECRET_PATTERNS):
            failures.append(f"credential-like token found in {path.relative_to(ROOT)}")
    return failures


def resource_names(path: Path) -> set[str]:
    root = ET.parse(path).getroot()
    return {
        child.attrib["name"]
        for child in root
        if child.tag in {"string", "plurals", "string-array"} and "name" in child.attrib
    }


def check_resource_symmetry() -> list[str]:
    failures: list[str] = []
    expected = resource_names(RESOURCE_LOCALES[0])
    for path in RESOURCE_LOCALES[1:]:
        actual = resource_names(path)
        missing = sorted(expected - actual)
        extra = sorted(actual - expected)
        if missing or extra:
            failures.append(
                f"{path.relative_to(ROOT)} resource mismatch: "
                f"missing={missing}, extra={extra}"
            )
    return failures


def main() -> int:
    files = tracked_files()
    failures = check_removed_api(files)
    failures.extend(check_secrets(files))
    failures.extend(check_resource_symmetry())
    if failures:
        print("Static contract verification failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1
    print("Static contract verification passed: removed APIs, credentials, and resources")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

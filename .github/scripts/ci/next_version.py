#!/usr/bin/env python3
"""Compute the next SemVer for the Release workflow.

Environment:
  INPUT_VERSION  optional exact X.Y.Z (or vX.Y.Z)
  INPUT_BUMP     auto | patch | minor | major  (used when INPUT_VERSION is empty)

Prints X.Y.Z to stdout. Exits non-zero on invalid input or a non-increasing version.

1.0.0 is never chosen automatically — pass INPUT_VERSION=1.0.0 for the academic ship.
While major is 0, a breaking-change auto bump increments minor (SemVer §4).
The first tag in a repository with no tags is 0.1.0.
"""

from __future__ import annotations

import os
import re
import subprocess
import sys

SEMVER = re.compile(r"^v?(\d+)\.(\d+)\.(\d+)$")
BREAKING = re.compile(r"^\w+(\([^)]*\))?!:")


def run(args: list[str]) -> str:
    return subprocess.check_output(args, text=True, stderr=subprocess.DEVNULL).strip()


def parsed_tags() -> list[tuple[int, int, int]]:
    try:
        raw = run(["git", "tag", "-l", "v*"])
    except subprocess.CalledProcessError:
        return []
    found: list[tuple[int, int, int]] = []
    for line in raw.splitlines():
        match = SEMVER.match(line.strip())
        if match:
            found.append(tuple(int(g) for g in match.groups()))  # type: ignore[arg-type]
    found.sort()
    return found


def fmt(ver: tuple[int, int, int]) -> str:
    return f"{ver[0]}.{ver[1]}.{ver[2]}"


def apply_bump(current: tuple[int, int, int], bump: str) -> tuple[int, int, int]:
    major, minor, patch = current
    if bump == "major":
        if major == 0:
            return (0, minor + 1, 0)
        return (major + 1, 0, 0)
    if bump == "minor":
        return (major, minor + 1, 0)
    if bump == "patch":
        return (major, minor, patch + 1)
    sys.exit(f"Unknown bump: {bump}")


def subjects_since(tag: str | None) -> list[str]:
    rev = f"{tag}..HEAD" if tag else "HEAD"
    try:
        return run(["git", "log", rev, "--pretty=%s"]).splitlines()
    except subprocess.CalledProcessError:
        return []


def auto_bump(current: tuple[int, int, int], subjects: list[str]) -> tuple[int, int, int]:
    kind = "patch"
    for subject in subjects:
        lower = subject.lower()
        if BREAKING.match(subject) or "breaking change" in lower:
            kind = "major"
            break
        if lower.startswith("feat:") or lower.startswith("feat("):
            kind = "minor"
    return apply_bump(current, kind)


def main() -> None:
    manual = os.environ.get("INPUT_VERSION", "").strip()
    bump = (os.environ.get("INPUT_BUMP") or "auto").strip().lower() or "auto"
    tags = parsed_tags()
    current = tags[-1] if tags else (0, 0, 0)

    if not tags and not manual:
        nxt = (0, 1, 0)
    elif manual:
        match = SEMVER.match(manual)
        if not match:
            sys.exit(f"Invalid SemVer: {manual!r} (expected X.Y.Z)")
        nxt = (int(match.group(1)), int(match.group(2)), int(match.group(3)))
    elif bump == "auto":
        last_tag = f"v{fmt(current)}"
        nxt = auto_bump(current, subjects_since(last_tag))
    elif bump in {"patch", "minor", "major"}:
        nxt = apply_bump(current, bump)
    else:
        sys.exit(f"Unknown bump: {bump}")

    if tags and nxt <= current:
        sys.exit(f"Next version {fmt(nxt)} is not greater than current {fmt(current)}")

    if nxt[0] >= 1 and not manual:
        sys.exit(
            "Refusing to publish 1.0.0 (or higher) automatically. "
            "Re-run with version=1.0.0 when that is the academic ship."
        )

    print(fmt(nxt))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Write the Release SemVer into pom.xml project <version>.

Analogous to gym-buddy-openapi sync_package_version.py. Only the project
version is updated (not the Spring Boot parent or dependency versions).
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

POM_PATH = Path("pom.xml")
SEMVER = re.compile(r"^\d+\.\d+\.\d+$")
PROJECT_VERSION = re.compile(
    r"(<artifactId>gym-buddy-service</artifactId>\s*<version>)([^<]+)(</version>)",
    re.DOTALL,
)


def write_project_version(pom: str, version: str) -> str:
    updated, count = PROJECT_VERSION.subn(
        lambda match: f"{match.group(1)}{version}{match.group(3)}",
        pom,
        count=1,
    )
    if count != 1:
        raise SystemExit("could not update pom.xml project <version>")
    return updated


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("usage: sync_pom_version.py X.Y.Z")
    version = sys.argv[1]
    if not SEMVER.fullmatch(version):
        sys.exit(f"invalid version: {version}")

    if not POM_PATH.is_file():
        sys.exit("pom.xml not found")

    original = POM_PATH.read_text(encoding="utf-8")
    updated = write_project_version(original, version)
    POM_PATH.write_text(updated, encoding="utf-8")
    print(f"Synced pom.xml project version to {version}")


if __name__ == "__main__":
    main()

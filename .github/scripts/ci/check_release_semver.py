#!/usr/bin/env python3
"""Static checks: Release writes SemVer into pom.xml and never auto-picks 1.0.0."""

from __future__ import annotations

import importlib.util
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]


def fail(message: str) -> None:
    print(f"TEST FAIL: {message}", file=sys.stderr)
    sys.exit(1)


def load_module(name: str, relative: str):
    path = ROOT / relative
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        fail(f"could not load {relative}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main() -> None:
    workflow = (ROOT / ".github" / "workflows" / "release.yml").read_text()
    if "python3 .github/scripts/ci/sync_pom_version.py" not in workflow:
        fail("release.yml must run sync_pom_version.py so Release writes pom.xml")
    if 'sync_pom_version.py "${{ steps.version.outputs.version }}"' not in workflow:
        fail("release.yml must pass the computed SemVer into sync_pom_version.py")

    changelog_at = workflow.find("prepare_changelog.py")
    sync_at = workflow.find("sync_pom_version.py")
    commit_at = workflow.find("Commit release prep on develop")
    if changelog_at == -1 or sync_at == -1 or commit_at == -1:
        fail("release.yml must keep changelog, pom sync, and commit-prep steps")
    if not (changelog_at < sync_at < commit_at):
        fail("Release must write pom.xml after changelog and before the tag commit")

    next_version = load_module("next_version", ".github/scripts/ci/next_version.py")
    if next_version.apply_bump((0, 1, 1), "major") != (0, 2, 0):
        fail("auto/major bump on 0.y.z must stay on major 0 (never 1.0.0)")
    if next_version.apply_bump((0, 1, 1), "minor") != (0, 2, 0):
        fail("minor bump on 0.1.1 must be 0.2.0, not 1.0.0")
    if next_version.apply_bump((0, 9, 0), "major") != (0, 10, 0):
        fail("major bump on 0.9.0 must be 0.10.0, not 1.0.0")

    source = (ROOT / ".github" / "scripts" / "ci" / "next_version.py").read_text()
    if "1.0.0 is never chosen automatically" not in source:
        fail("next_version.py must keep the academic-ship 1.0.0 guard")
    if "if nxt[0] >= 1 and not manual" not in source:
        fail("next_version.py must refuse automatic 1.0.0 or higher")

    sync = load_module("sync_pom_version", ".github/scripts/ci/sync_pom_version.py")
    pom = (ROOT / "pom.xml").read_text()
    if "<version>1.1.0</version>" not in pom:
        fail("working pom.xml must stay on the tagged 1.1.0 line until the next Release")
    if "<version>2.0.0</version>" in pom:
        fail("do not invent 2.0.0 in pom.xml")

    updated = sync.write_project_version(pom, "0.1.2")
    if "<artifactId>gym-buddy-service</artifactId>\n    <version>0.1.2</version>" not in updated:
        fail("sync_pom_version.py must write the project <version>")
    if "<artifactId>spring-boot-starter-parent</artifactId>\n        <version>4.1.0</version>" not in updated:
        fail("sync_pom_version.py must not change the Spring Boot parent version")
    if "<java.version>25</java.version>" not in updated:
        fail("sync_pom_version.py must not rewrite other pom versions")

    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        (work / "pom.xml").write_text(pom, encoding="utf-8")
        script = ROOT / ".github" / "scripts" / "ci" / "sync_pom_version.py"
        result = subprocess.run(
            [sys.executable, str(script), "0.1.3"],
            cwd=work,
            check=True,
            capture_output=True,
            text=True,
        )
        written = (work / "pom.xml").read_text(encoding="utf-8")
        if "<version>0.1.3</version>" not in written:
            fail(f"sync_pom_version.py CLI did not write 0.1.3: {result.stdout}")
        if "<version>4.1.0</version>" not in written:
            fail("sync_pom_version.py CLI rewrote the parent version")

    print("TEST OK: Release writes SemVer into pom.xml; auto bump never picks 1.x unattended")


if __name__ == "__main__":
    main()

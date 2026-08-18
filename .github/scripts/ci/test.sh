#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$root"

if [[ -f pom.xml ]]; then
  mvn -B test
  python3 .github/scripts/ci/check_vps_deploy.py
  exit 0
fi

python3 - <<'PY'
from pathlib import Path
import sys

required = [
    Path("Dockerfile"),
    Path("probe/index.html"),
    Path("deploy/replace.sh"),
    Path(".github/workflows/ci.yml"),
    Path(".github/workflows/release.yml"),
    Path(".github/workflows/deploy.yml"),
    Path(".github/scripts/ci/smoke.sh"),
    Path(".github/scripts/ci/next_version.py"),
    Path(".github/scripts/ci/prepare_changelog.py"),
    Path("CHANGELOG.md"),
]
missing = [str(p) for p in required if not p.is_file()]
if missing:
    print("TEST FAIL: missing\n  - " + "\n  - ".join(missing), file=sys.stderr)
    sys.exit(1)
print("TEST OK: pipeline files present (Maven tests start when pom.xml exists)")
PY

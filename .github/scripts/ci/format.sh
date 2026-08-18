#!/usr/bin/env bash
# Format. Spotless once pom.xml exists; until then this is an explicit no-op.
set -euo pipefail

root="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$root"

mode="${1:---check}"

if [[ -f pom.xml ]]; then
  if [[ "$mode" == "--write" ]]; then
    mvn -B spotless:apply
  else
    mvn -B spotless:check
  fi
else
  echo "FORMAT OK: no pom.xml yet (Spotless will run once the Java project exists)"
fi

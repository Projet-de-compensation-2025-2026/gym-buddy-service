#!/usr/bin/env bash
# The built container must answer HTTP. A compile-only check is not enough.
set -euo pipefail

root="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$root"

image="gym-buddy-service:smoke"
name="gym-buddy-service-smoke"
port="${SMOKE_PORT:-18080}"

cleanup() {
  docker rm -f "$name" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker build -t "$image" .
docker run -d --name "$name" -p "127.0.0.1:${port}:8080" "$image"

ok=0
if [[ -f pom.xml ]]; then
  for _ in $(seq 1 90); do
    body="$(curl -fsS "http://127.0.0.1:${port}/api/v1/healthz" 2>/dev/null || true)"
    if [[ "$body" == *'"status"'* && "$body" == *'"ok"'* ]]; then
      echo "SMOKE OK: GET /api/v1/healthz returned ${body}"
      ok=1
      break
    fi
    sleep 1
  done
  if [[ "$ok" -ne 1 ]]; then
    echo "SMOKE FAIL: container never answered /api/v1/healthz on port ${port}" >&2
    docker logs "$name" >&2 || true
    exit 1
  fi
  exit 0
fi

for _ in $(seq 1 40); do
  body="$(curl -fsS "http://127.0.0.1:${port}/" 2>/dev/null || true)"
  if [[ "$body" == *"Gym Buddy"* ]]; then
    echo "SMOKE OK: container answered HTTP with 'Gym Buddy'"
    ok=1
    break
  fi
  sleep 0.25
done

if [[ "$ok" -ne 1 ]]; then
  echo "SMOKE FAIL: container never answered on port ${port}" >&2
  docker logs "$name" >&2 || true
  exit 1
fi

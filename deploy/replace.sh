#!/usr/bin/env bash
# Runs on the target VM. Ensures the image is present (pull registry refs; inspect
# local tags) and replaces the running API container.
# The container joins the private data-plane network created by deploy/compose.yaml
# and receives application env from a VPS file that is not in git.
set -euo pipefail

IMAGE="${1:?image:tag required}"
NAME="${DEPLOY_CONTAINER_NAME:-gym-buddy-service}"
PORT="${DEPLOY_HOST_PORT:-8080}"
BIND="${DEPLOY_BIND:-127.0.0.1}"
NETWORK="${DEPLOY_NETWORK:-gym-buddy-data}"
ENV_FILE="${DEPLOY_ENV_FILE:-/etc/gym-buddy/vps.env}"

required_keys=(
  DATABASE_URL
  REDIS_URL
  JWT_ACCESS_SECRET
  S3_ENDPOINT
  S3_BUCKET
  S3_ACCESS_KEY
  S3_SECRET_KEY
)

if [[ "${BIND}" == "0.0.0.0" || "${BIND}" == "::" || "${BIND}" == "[::]" ]]; then
  echo "DEPLOY_BIND=${BIND} would publish the API on all interfaces." >&2
  echo "Public story is Caddy → 127.0.0.1:8080. Keep DEPLOY_BIND=127.0.0.1." >&2
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing VPS env file: ${ENV_FILE}" >&2
  echo "Copy deploy/vps.env.example, fill values on the host, chmod 600." >&2
  echo "See docs/vps-data-plane.md." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"
S3_REGION="${S3_REGION:-us-east-1}"

if [[ "${SPRING_PROFILES_ACTIVE}" != "prod" ]]; then
  echo "replace.sh is the VPS path; SPRING_PROFILES_ACTIVE must be prod (got ${SPRING_PROFILES_ACTIVE})." >&2
  exit 1
fi

missing=0
for key in "${required_keys[@]}"; do
  if [[ -z "${!key:-}" ]]; then
    echo "Missing or blank VPS env: ${key} (file ${ENV_FILE})" >&2
    missing=1
  fi
done
if [[ "${missing}" -ne 0 ]]; then
  echo "Keys only are documented in docs/vps-data-plane.md. Values stay on the VPS." >&2
  exit 1
fi

# Remote vs local: Docker treats the first path component as a registry host
# when it contains '.' or ':' or is 'localhost' (see distribution/reference).
# A slash alone is not enough — myorg/app:local is still a local/Hub-style name.
# Bare names (gym-buddy-service:local) have no slash and stay on the host.
image_from_registry() {
  local ref="${1%%@*}"
  [[ "$ref" == */* ]] || return 1
  local first="${ref%%/*}"
  [[ "$first" == *.* || "$first" == *:* || "$first" == "localhost" ]]
}

if image_from_registry "$IMAGE"; then
  if [[ -n "${GHCR_TOKEN:-}" ]]; then
    echo "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USERNAME:?GHCR_USERNAME required when GHCR_TOKEN is set}" --password-stdin
  fi
  docker pull "$IMAGE"
else
  if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
    echo "Local image ${IMAGE} is not on this host." >&2
    echo "Build it first (docker build -t ${IMAGE} .) or pass a registry ref (ghcr.io/...)." >&2
    exit 1
  fi
fi

if ! docker network inspect "$NETWORK" >/dev/null 2>&1; then
  echo "Docker network ${NETWORK} is missing." >&2
  echo "Start the data plane first:" >&2
  echo "  docker compose --env-file ${ENV_FILE} -f deploy/compose.yaml up -d" >&2
  echo "See docs/vps-data-plane.md." >&2
  exit 1
fi

optional_env=()
for key in GYM_BUDDY_BOOTSTRAP_STAFF DEMO_ADMIN_PASSWORD DEMO_MOD_PASSWORD; do
  if [[ -n "${!key:-}" ]]; then
    optional_env+=(-e "${key}=${!key}")
  fi
done

docker stop "$NAME" 2>/dev/null || true
docker rm "$NAME" 2>/dev/null || true
docker run -d \
  --name "$NAME" \
  --restart unless-stopped \
  --network "$NETWORK" \
  -p "${BIND}:${PORT}:8080" \
  -e "SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}" \
  -e "DATABASE_URL=${DATABASE_URL}" \
  -e "REDIS_URL=${REDIS_URL}" \
  -e "JWT_ACCESS_SECRET=${JWT_ACCESS_SECRET}" \
  -e "S3_ENDPOINT=${S3_ENDPOINT}" \
  -e "S3_BUCKET=${S3_BUCKET}" \
  -e "S3_ACCESS_KEY=${S3_ACCESS_KEY}" \
  -e "S3_SECRET_KEY=${S3_SECRET_KEY}" \
  -e "S3_REGION=${S3_REGION}" \
  "${optional_env[@]}" \
  "$IMAGE"
echo "Replaced ${NAME} with ${IMAGE} on ${BIND}:${PORT} (network ${NETWORK}, profile ${SPRING_PROFILES_ACTIVE})"

#!/usr/bin/env bash
# Runs on the target VM. Pulls the tagged image and replaces the running container.
set -euo pipefail

IMAGE="${1:?image:tag required}"
NAME="${DEPLOY_CONTAINER_NAME:-gym-buddy-service}"
PORT="${DEPLOY_HOST_PORT:-8080}"

docker pull "$IMAGE"
docker stop "$NAME" 2>/dev/null || true
docker rm "$NAME" 2>/dev/null || true
docker run -d --name "$NAME" --restart unless-stopped -p "${PORT}:8080" "$IMAGE"
echo "Replaced ${NAME} with ${IMAGE}"

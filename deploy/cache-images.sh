#!/usr/bin/env bash
# Cache reviewed images only; never replace containers or application configuration.
set -euo pipefail
username="${1:?registry username required}"
shift
(( $# > 0 )) || exit 2
for image in "$@"; do
  [[ "$image" =~ ^ghcr\.io/projet-de-compensation-2025-2026/gym-buddy-service@sha256:[0-9a-f]{64}$ ]] || exit 2
done
auth_dir=$(mktemp -d /tmp/gym-buddy-image-auth.XXXXXX)
chmod 700 "$auth_dir"
trap 'rm -rf -- "$auth_dir"' EXIT
export DOCKER_CONFIG="$auth_dir"
docker login ghcr.io --username "$username" --password-stdin
for image in "$@"; do
  docker pull "$image"
  docker image inspect "$image" --format '{{range .RepoDigests}}{{println .}}{{end}}' | grep -Fx -- "$image"
done

#!/usr/bin/env bash
# Fetch the versioned gym-buddy-openapi package tree at a git tag.
# Maven / OpenAPI Generator cannot npm-install that package as a jar.
# The tagged checkout is the consumer input; generate from openapi/openapi.yaml
# ($ref tree). Do not treat bundled.yaml or Spring /v3/api-docs as SoT.
set -euo pipefail

tag="${1:-v0.1.0}"
dest="${2:-target/openapi/gym-buddy-openapi}"
owner="Projet-de-compensation-2025-2026"
repo="gym-buddy-openapi"
archive_url="https://codeload.github.com/${owner}/${repo}/tar.gz/refs/tags/${tag}"

if [[ "${tag}" == develop ]] || [[ "${tag}" == *bundled.yaml* ]]; then
  echo "fetch-openapi-tree: refuse develop / bundled.yaml; pin a package tag (v0.1.0)" >&2
  exit 1
fi

rm -rf "${dest}"
mkdir -p "${dest}"

auth_headers=()
if [[ -n "${GITHUB_TOKEN:-}" ]]; then
  auth_headers+=(-H "Authorization: Bearer ${GITHUB_TOKEN}")
elif [[ -n "${GH_TOKEN:-}" ]]; then
  auth_headers+=(-H "Authorization: Bearer ${GH_TOKEN}")
fi

echo "fetch-openapi-tree: ${archive_url} -> ${dest}"
curl -fsSL "${auth_headers[@]}" "${archive_url}" | tar -xz -C "${dest}" --strip-components=1

spec="${dest}/openapi/openapi.yaml"
if [[ ! -f "${spec}" ]]; then
  echo "fetch-openapi-tree: missing ${spec} in tag ${tag}" >&2
  exit 1
fi

if [[ ! -f "${dest}/package.json" ]]; then
  echo "fetch-openapi-tree: missing package.json in tag ${tag} (not the versioned package)" >&2
  exit 1
fi

echo "fetch-openapi-tree: generator input is ${spec}"

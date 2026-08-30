#!/usr/bin/env bash
# Put JDK 25 on PATH. Prefer the runner image, otherwise Adoptium.
set -euo pipefail

if [[ -n "${JAVA_HOME_25_X64:-}" && -x "${JAVA_HOME_25_X64}/bin/java" ]]; then
  echo "JAVA_HOME=${JAVA_HOME_25_X64}" >> "${GITHUB_ENV:?}"
  echo "${JAVA_HOME_25_X64}/bin" >> "${GITHUB_PATH:?}"
  echo "Using runner JAVA_HOME_25_X64"
  exit 0
fi

if [[ -n "${JAVA_HOME_25_ARM64:-}" && -x "${JAVA_HOME_25_ARM64}/bin/java" ]]; then
  echo "JAVA_HOME=${JAVA_HOME_25_ARM64}" >> "${GITHUB_ENV:?}"
  echo "${JAVA_HOME_25_ARM64}/bin" >> "${GITHUB_PATH:?}"
  echo "Using runner JAVA_HOME_25_ARM64"
  exit 0
fi

arch="$(uname -m)"
case "$arch" in
  x86_64) adoptium_arch=x64 ;;
  aarch64|arm64) adoptium_arch=aarch64 ;;
  *) echo "Unsupported arch: $arch" >&2; exit 1 ;;
esac

echo "No preinstalled JDK 25; downloading Temurin 25 (${adoptium_arch}) from Adoptium"
curl -fsSL "https://api.adoptium.net/v3/binary/latest/25/ga/linux/${adoptium_arch}/jdk/hotspot/normal/eclipse?project=jdk" -o /tmp/jdk25.tar.gz
sudo mkdir -p /opt/jdk25
sudo tar -xzf /tmp/jdk25.tar.gz -C /opt/jdk25 --strip-components=1
echo "JAVA_HOME=/opt/jdk25" >> "${GITHUB_ENV:?}"
echo "/opt/jdk25/bin" >> "${GITHUB_PATH:?}"

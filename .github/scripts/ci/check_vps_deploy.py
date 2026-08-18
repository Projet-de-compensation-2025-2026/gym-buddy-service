#!/usr/bin/env python3
"""Static checks: VPS compose stays private; laptop compose stays local; replace.sh joins the data plane."""

from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[3]
FORBIDDEN_HOST_PORTS = {5432, 6379, 9000, 9001}
REQUIRED_REPLACE_KEYS = (
    "DATABASE_URL",
    "REDIS_URL",
    "JWT_ACCESS_SECRET",
    "S3_ENDPOINT",
    "S3_BUCKET",
    "S3_ACCESS_KEY",
    "S3_SECRET_KEY",
    "S3_REGION",
    "SPRING_PROFILES_ACTIVE",
)


def fail(message: str) -> None:
    print(f"TEST FAIL: {message}", file=sys.stderr)
    sys.exit(1)


def published_ports(service: dict) -> list:
    return service.get("ports") or []


def host_port(mapping) -> int | None:
    if isinstance(mapping, int):
        return mapping
    if isinstance(mapping, str):
        # "127.0.0.1:5432:5432" or "5432:5432" or "5432"
        parts = mapping.split(":")
        try:
            return int(parts[-2] if len(parts) >= 2 else parts[0])
        except ValueError:
            return None
    if isinstance(mapping, dict):
        published = mapping.get("published")
        if published is None:
            return None
        return int(published)
    return None


def main() -> None:
    vps_path = ROOT / "deploy" / "compose.yaml"
    laptop_path = ROOT / "compose.yaml"
    replace_path = ROOT / "deploy" / "replace.sh"
    deploy_workflow = ROOT / ".github" / "workflows" / "deploy.yml"
    pom_path = ROOT / "pom.xml"

    vps = yaml.safe_load(vps_path.read_text())
    laptop = yaml.safe_load(laptop_path.read_text())
    replace = replace_path.read_text()
    workflow = deploy_workflow.read_text()
    pom = pom_path.read_text()

    if "api" in (vps.get("services") or {}):
        fail("deploy/compose.yaml must not start the API; replace.sh docker-runs GHCR")

    for name, service in (vps.get("services") or {}).items():
        ports = published_ports(service)
        if ports:
            fail(f"deploy/compose.yaml service {name} publishes ports {ports}; data plane must stay private")

    network = (vps.get("networks") or {}).get("data") or {}
    if network.get("name") != "gym-buddy-data":
        fail("deploy/compose.yaml must name the network gym-buddy-data so replace.sh can join it")

    postgres = (vps.get("services") or {}).get("postgres") or {}
    if postgres.get("image") != "postgres:18.6":
        fail("VPS postgres image must be postgres:18.6")

    laptop_api_ports = published_ports((laptop.get("services") or {}).get("api") or {})
    if not any("127.0.0.1:8080:8080" in str(p) for p in laptop_api_ports):
        fail("laptop compose.yaml must keep the API on 127.0.0.1:8080")

    for name in ("postgres", "redis", "minio"):
        ports = published_ports((laptop.get("services") or {}).get(name) or {})
        if not ports:
            fail(f"laptop compose.yaml service {name} lost its 127.0.0.1 published ports")
        for mapping in ports:
            if "127.0.0.1" not in str(mapping):
                fail(f"laptop compose.yaml service {name} must bind 127.0.0.1, got {mapping}")
            port = host_port(mapping)
            if port in FORBIDDEN_HOST_PORTS and "127.0.0.1" not in str(mapping):
                fail(f"laptop compose.yaml must not publish {port} on 0.0.0.0")

    if not re.search(r'DEPLOY_BIND:-127\.0\.0\.1', replace):
        fail("replace.sh must default DEPLOY_BIND to 127.0.0.1")
    if "--network" not in replace or "gym-buddy-data" not in replace:
        fail("replace.sh must join gym-buddy-data")
    if "0.0.0.0" not in replace:
        fail("replace.sh must refuse publishing the API on 0.0.0.0")
    for key in REQUIRED_REPLACE_KEYS:
        if key not in replace:
            fail(f"replace.sh must pass {key} into the API container")
    if "/etc/gym-buddy/vps.env" not in replace:
        fail("replace.sh must read VPS env from /etc/gym-buddy/vps.env (not git)")
    if "docker run" not in replace or "docker pull" not in replace:
        fail("replace.sh must keep docker pull + docker run (Release → Deploy)")

    if "deploy/replace.sh" not in workflow:
        fail("deploy.yml must still copy and run deploy/replace.sh")

    version = re.search(r"<version>([^<]+)</version>", pom)
    if version and version.group(1).startswith("0.3."):
        fail("do not bump the application artifact to 0.3.0")

    print("TEST OK: VPS compose is private, laptop compose stays local, replace.sh joins gym-buddy-data")


if __name__ == "__main__":
    main()

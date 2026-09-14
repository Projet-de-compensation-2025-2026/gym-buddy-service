# Gym Buddy service

Java 25 / Spring Boot API for Gym Buddy. Controllers implement the pinned contract from [gym-buddy-openapi](https://github.com/Projet-de-compensation-2025-2026/gym-buddy-openapi).

## Run locally

Install Docker and copy `.env.example` to `.env`, then fill in the local settings:

```sh
docker compose up -d
```

The API is at `http://localhost:8080/api/v1`; `GET /healthz` checks liveness. See the [environment guide](https://github.com/Projet-de-compensation-2025-2026/gym-buddy-documentation/blob/develop/10-Getting-started/04-Environment-and-pipeline.md) for configuration and [VPS runbook](docs/vps-data-plane.md) for deployment.

## Test

With Java 25, Maven, Bash, and Docker running:

```sh
mvn -B test
mvn -B spotless:check
python .github/scripts/ci/check_release_semver.py
```

Database and object-storage integration tests use disposable Testcontainers. Maven fetches the pinned API contract, so the first build needs network access.

[Specifications, architecture, and test plan](https://github.com/Projet-de-compensation-2025-2026/gym-buddy-documentation)

# gym-buddy-service

Java API for Gym Buddies (Spring Boot, Java 26, PostgreSQL 18). Product decisions live in [`gym-buddy-documentation`](https://github.com/Projet-de-compensation-2025-2026/gym-buddy-documentation).

This repository is **pipeline-first**. Application code is not here yet. Until `pom.xml` exists, CI/Release/Deploy run against a small Docker probe so the smoke and VM replace path are real.

| Workflow | Trigger | Promise |
| --- | --- | --- |
| CI | PR / push on `develop` | format, tests, container actually answers HTTP |
| Release | `workflow_dispatch` | squash `develop` → `main`, tag `vX.Y.Z` |
| Deploy | that tag | push `ghcr.io/.../gym-buddy-service:vX.Y.Z` and replace the VM container when `DEPLOY_*` secrets exist |

See [07-CI-CD.md](https://github.com/Projet-de-compensation-2025-2026/gym-buddy-documentation/blob/develop/70-Engineering-practices/07-CI-CD.md).

## Local data plane

Postgres 18, Redis, MinIO, and the probe API. This is the laptop stack from the [runbook](https://github.com/Projet-de-compensation-2025-2026/gym-buddy-documentation/blob/develop/10-Getting-started/04-Environment-and-pipeline.md). Do **not** run `compose.yaml` on the VPS.

```bash
cp .env.example .env
docker compose up -d
```

Every published port binds `127.0.0.1`:

| Service | Port |
| --- | --- |
| API (probe today) | 8080 |
| PostgreSQL 18 | 5432 |
| Redis | 6379 |
| MinIO S3 | 9000 |
| MinIO console | 9001 |
| MailHog (optional) | SMTP 1025, UI 8025 |

MailHog is opt-in:

```bash
docker compose --profile mail up -d
```

The probe does not talk to Postgres, Redis, or MinIO yet. Those services are up so Spring work can start without inventing ports or env names.
